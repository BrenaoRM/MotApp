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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

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

    /** Modelo de um tipo, válido enquanto as categorias (ids e nomes) forem as mesmas de [assinatura]. */
    private class Treinado(
        val assinatura: List<Pair<Long, String>>,
        val chavesDosNomes: Set<String>,
        val memoria: Map<String, Long>,
        val ids: List<Long>,
        val modelo: ModeloDeCategoria?,
    )

    private val mutex = Mutex()
    private val modelos = ConcurrentHashMap<TipoTransacao, Treinado>()

    @Volatile
    private var geracao = 0

    fun invalidar() {
        geracao++
        modelos.clear()
    }

    suspend fun sugerir(descricao: String, tipo: TipoTransacao): Probabilidade? {
        val chave = chaveDoEstabelecimento(descricao)
        if (chave.isEmpty()) return null
        val t = treinado(tipo)
        if (chave in t.chavesDosNomes) return null
        val modelo = t.modelo ?: return t.memoria[chave]?.let { Probabilidade(it, 1f) }
        val vetor = vetor(descricao) ?: return t.memoria[chave]?.let { Probabilidade(it, 1f) }
        return withContext(calculo) { modelo.sugerir(vetor, descricao) }
    }

    suspend fun ranking(descricao: String, tipo: TipoTransacao): List<Long> {
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

    private suspend fun treinado(tipo: TipoTransacao): Treinado = mutex.withLock {
        val categorias = withContext(io) { categoriasDoTipo(tipo) }
        val assinatura = categorias.map { it.id to it.nome }
        modelos[tipo]?.takeIf { it.assinatura == assinatura }?.let { return it }
        val inicio = geracao
        val exemplos = withContext(io) { FinancaRepository(banco()).exemplosParaIa(tipo) }
        val ids = categorias.map { it.id }.toSet()
        // O rótulo mais recente de cada estabelecimento vence (exemplos vêm do mais antigo ao mais novo)
        val memoria = LinkedHashMap<String, Long>()
        exemplos.filter { it.second in ids }.forEach { (descricao, id) -> memoria[chaveDoEstabelecimento(descricao)] = id }
        val novo = Treinado(
            assinatura, categorias.map { chaveDoEstabelecimento(it.nome) }.toSet(), memoria, categorias.map { it.id },
            treinarModelo(exemplos, categorias, memoria),
        )
        if (geracao == inicio) modelos[tipo] = novo
        novo
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
