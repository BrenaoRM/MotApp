package com.example.financacelular.ia

import android.content.Context
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.chaveDoEstabelecimento
import com.example.financacelular.data.idDaCategoriaFatura
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Porta de entrada da IA de categorias. [iniciar] não bloqueia: lê o config e abre o modelo em I/O. Se o modelo
 * não abrir, [disponivel] fica false e só a memória exata do estabelecimento funciona. A IA só sugere; quem decide
 * preencher `categoriaSelecionada` (e só quando está vazia) é a tela.
 */
object IaFinanceira {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var pronto: Deferred<MotorIa>? = null

    @Volatile
    private var motor: MotorIa? = null

    val disponivel: Boolean get() = motor?.disponivel == true

    @Synchronized
    fun iniciar(context: Context) {
        if (pronto != null) return
        val app = context.applicationContext
        pronto = escopo.async {
            val cfg = runCatching {
                ConfigIa.deJson(app.assets.open("ia/modelo_ia.json").use { it.readBytes().toString(Charsets.UTF_8) })
            }.getOrNull()
            val vetorizador = cfg?.let { VetorizadorOnnx.abrir(app, it) }
            val banco = { AppDatabase.getInstance(app) }
            if (cfg != null) runCatching { banco().vetorIaDao().limparOutrasVersoes(cfg.versaoModelo) }
            MotorIa(banco, cfg, vetorizador).also { motor = it }
        }
    }

    /** Categoria sugerida para a descrição, ou null (sem base, abaixo do limiar, IA não iniciada). */
    suspend fun sugerir(descricao: String, tipo: TipoTransacao): Probabilidade? =
        pronto?.await()?.sugerir(descricao, tipo)

    /** Ids das categorias do tipo, da mais provável para a menos provável (vazio se não há o que ordenar). */
    suspend fun ranking(descricao: String, tipo: TipoTransacao): List<Long> =
        pronto?.await()?.ranking(descricao, tipo).orEmpty()

    /** Config lido do asset (null se não abriu ou a IA não foi iniciada); não depende do modelo ONNX. */
    suspend fun config(): ConfigIa? = pronto?.await()?.config

    /** Vetor da pergunta, sem passar pelo cache do banco (o texto da pergunta nunca é gravado); null sem modelo. */
    suspend fun vetorizarPergunta(texto: String): FloatArray? = pronto?.await()?.vetorizarPergunta(texto)

    /** Vetores dos nomes das categorias (cache do banco), para citar categoria na pergunta; vazio sem modelo. */
    suspend fun vetoresDasCategorias(categorias: List<Categoria>): List<Pair<Long, FloatArray>> =
        pronto?.await()?.vetoresDasCategorias(categorias).orEmpty()

    /** Genérica de cada categoria (mesmo critério do treino); sem modelo, só pelo nome exato. Vazio sem config. */
    suspend fun genericasDasCategorias(categorias: List<Categoria>): Map<Long, String> =
        pronto?.await()?.genericasDasCategorias(categorias).orEmpty()

    /** Treina em segundo plano o modelo do tipo (se faltar), para o próximo [sugerir] não esperar. Não bloqueia. */
    fun aquecer(tipo: TipoTransacao) {
        val iniciando = pronto ?: return
        escopo.launch { iniciando.await().aquecer(tipo) }
    }

    /** Descarta os modelos; o próximo [sugerir] treina de novo com os lançamentos e categorias atuais. */
    fun invalidar() {
        motor?.invalidar()
    }
}

/** O que [IaFinanceira] faz, separado do Context para testar com banco em memória e vetorizador falso. */
internal class MotorIa(
    private val banco: () -> AppDatabase,
    private val cfg: ConfigIa?,
    private val vetorizador: Vetorizador?,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val calculo: CoroutineDispatcher = Dispatchers.Default,
) {
    val disponivel: Boolean = cfg != null && vetorizador != null
    val config: ConfigIa? get() = cfg

    suspend fun vetorizarPergunta(texto: String): FloatArray? = semErro(null) {
        val normalizado = NormalizacaoTexto.paraModelo(texto)
        if (normalizado.isEmpty() || vetorizador == null) null
        else withContext(calculo) { vetorizador.vetorizar(listOf(normalizado)).first() }
    }

    suspend fun vetoresDasCategorias(categorias: List<Categoria>): List<Pair<Long, FloatArray>> = semErro(emptyList()) {
        if (vetorizador == null) return@semErro emptyList()
        val textos = categorias.associateWith { NormalizacaoTexto.paraModelo(it.nome) }
        val vetores = RepositorioDeVetores(banco().vetorIaDao(), vetorizador, io, calculo).vetores(textos.values.filter { it.isNotEmpty() })
        categorias.mapNotNull { c -> vetores[textos.getValue(c)]?.let { c.id to it } }
    }

    suspend fun genericasDasCategorias(categorias: List<Categoria>): Map<Long, String> = semErro(emptyMap()) {
        val gen = cfg?.generico ?: return@semErro emptyMap()
        val vetores = vetoresDasCategorias(categorias).toMap()
        val pelaIa = vetores.mapNotNull { (id, v) -> indiceGenerico(gen, v).takeIf { it >= 0 }?.let { id to gen.nomes[it] } }.toMap()
        // Sem vetor (modelo fora): casa o nome normalizado com o nome da genérica
        val chaves = gen.nomes.associateBy { chaveDoEstabelecimento(it) }
        pelaIa + categorias.filter { it.id !in pelaIa }.mapNotNull { c -> chaves[chaveDoEstabelecimento(c.nome)]?.let { c.id to it } }
    }

    /** Modelo de um tipo, válido enquanto as categorias (ids e nomes) forem as mesmas de [assinatura]. */
    private class Treinado(
        val assinatura: List<Pair<Long, String>>,
        val chavesDosNomes: Set<String>,
        val memoria: Map<String, Long>,
        val ids: List<Long>,
        val modelo: ModeloDeCategoria?,
    )

    /** Treino em andamento de um tipo, reaproveitado por quem chegar com a mesma geração e as mesmas categorias. */
    private class EmTreino(val geracao: Int, val assinatura: List<Pair<Long, String>>, val tarefa: Deferred<Treinado>)

    // O treino roda neste escopo, não no de quem pediu: cancelar o pedido (ex.: o usuário digitou de novo) não joga fora
    // segundos de treino, e o próximo pedido usa o mesmo resultado
    private val escopo = CoroutineScope(SupervisorJob() + calculo)
    private val mutex = Mutex()
    private val modelos = ConcurrentHashMap<TipoTransacao, Treinado>()
    private val emTreino = ConcurrentHashMap<TipoTransacao, EmTreino>()
    private val geracao = AtomicInteger()

    /** Quantos treinos começaram (para testes). */
    internal val treinos = AtomicInteger()

    fun invalidar() {
        geracao.incrementAndGet()
        modelos.clear()
    }

    suspend fun aquecer(tipo: TipoTransacao) = semErro(Unit) { treinado(tipo); Unit }

    /** Qualquer erro em tempo de execução (ONNX, memória, banco) vira "sem sugestão": a IA nunca derruba a tela. */
    suspend fun sugerir(descricao: String, tipo: TipoTransacao): Probabilidade? = semErro(null) { sugerirOuFalhar(descricao, tipo) }

    suspend fun ranking(descricao: String, tipo: TipoTransacao): List<Long> = semErro(emptyList()) { rankingOuFalhar(descricao, tipo) }

    private suspend fun <T> semErro(padrao: T, bloco: suspend () -> T): T = try {
        bloco()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        padrao
    }

    private suspend fun sugerirOuFalhar(descricao: String, tipo: TipoTransacao): Probabilidade? {
        val chave = chaveDoEstabelecimento(descricao)
        if (chave.isEmpty()) return null
        val t = treinado(tipo)
        if (chave in t.chavesDosNomes) return null
        val modelo = t.modelo ?: return t.memoria[chave]?.let { Probabilidade(it, 1f) }
        val vetor = vetor(descricao) ?: return t.memoria[chave]?.let { Probabilidade(it, 1f) }
        return withContext(calculo) { modelo.sugerir(vetor, descricao) }
    }

    private suspend fun rankingOuFalhar(descricao: String, tipo: TipoTransacao): List<Long> {
        val chave = chaveDoEstabelecimento(descricao)
        if (chave.isEmpty()) return emptyList()
        val t = treinado(tipo)
        val vetor = if (t.modelo != null) vetor(descricao) else null
        if (t.modelo == null || vetor == null) {
            val lembrada = t.memoria[chave] ?: return emptyList()
            return listOf(lembrada) + t.ids.filter { it != lembrada }
        }
        return withContext(calculo) { t.modelo.prever(vetor, descricao).map { it.categoriaId } }
    }

    private suspend fun vetor(descricao: String): FloatArray? {
        val texto = NormalizacaoTexto.paraModelo(descricao)
        if (texto.isEmpty() || vetorizador == null) return null
        return RepositorioDeVetores(banco().vetorIaDao(), vetorizador, io, calculo).vetores(listOf(texto))[texto]
    }

    private suspend fun treinado(tipo: TipoTransacao): Treinado {
        val tarefa = mutex.withLock {
            val categorias = withContext(io) { categoriasDoTipo(tipo) }
            val assinatura = categorias.map { it.id to it.nome }
            modelos[tipo]?.takeIf { it.assinatura == assinatura }?.let { return it }
            val atual = geracao.get()
            emTreino[tipo]?.takeIf { it.geracao == atual && it.assinatura == assinatura && !it.tarefa.isCompleted }
                ?.let { return@withLock it.tarefa }
            escopo.async { treinar(tipo, categorias, assinatura, atual) }
                .also { emTreino[tipo] = EmTreino(atual, assinatura, it) }
        }
        return tarefa.await()
    }

    private suspend fun treinar(tipo: TipoTransacao, categorias: List<Categoria>, assinatura: List<Pair<Long, String>>, inicio: Int): Treinado {
        treinos.incrementAndGet()
        val exemplos = withContext(io) { FinancaRepository(banco()).exemplosParaIa(tipo) }
        val ids = categorias.map { it.id }.toSet()
        // O rótulo mais recente de cada estabelecimento vence (exemplos vêm do mais antigo ao mais novo)
        val memoria = LinkedHashMap<String, Long>()
        exemplos.filter { it.second in ids }.forEach { (descricao, id) -> memoria[chaveDoEstabelecimento(descricao)] = id }
        val novo = Treinado(
            assinatura, categorias.map { chaveDoEstabelecimento(it.nome) }.toSet(), memoria, categorias.map { it.id },
            treinarModelo(exemplos, categorias, memoria),
        )
        // Se invalidar() rodou durante o treino (ou entre o put e a conferência), o modelo velho não fica no cache
        modelos[tipo] = novo
        if (geracao.get() != inicio) modelos.remove(tipo, novo)
        return novo
    }

    private suspend fun treinarModelo(
        exemplos: List<Pair<String, Long>>, categorias: List<Categoria>, memoria: Map<String, Long>,
    ): ModeloDeCategoria? {
        if (cfg == null || vetorizador == null || categorias.isEmpty()) return null
        val textos = exemplos.map { (descricao, id) -> NormalizacaoTexto.paraModelo(descricao) to id }.filter { it.first.isNotEmpty() }
        val nomes = categorias.map { NormalizacaoTexto.paraModelo(it.nome) }
        val vetores = RepositorioDeVetores(banco().vetorIaDao(), vetorizador, io, calculo)
            .vetores(textos.map { it.first } + nomes)
        return withContext(calculo) {
            // Exemplos iguais (mesmo texto e categoria) viram um só com peso = quantidade: o gradiente é o mesmo
            val rotulados = textos.groupingBy { it }.eachCount()
                .map { (par, n) -> ExemploRotulado(vetores.getValue(par.first), par.second, n.toFloat()) }
            val cats = categorias.mapIndexed { i, c -> CategoriaParaIa(c.id, c.nome, vetores.getValue(nomes[i])) }
            ClassificadorDeCategoria(cfg).treinar(rotulados, cats, memoria)
        }
    }

    private suspend fun categoriasDoTipo(tipo: TipoTransacao): List<Categoria> {
        val todas = banco().categoriaDao().listarTodasSync()
        val fatura = idDaCategoriaFatura(todas)
        return todas.filter { it.tipo == tipo && it.id != fatura }.sortedBy { it.id }
    }
}
