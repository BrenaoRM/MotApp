package com.example.financacelular.ia

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sqrt

enum class Intencao {
    RANKING_CATEGORIAS, GASTO_EM_CATEGORIA, TOTAL_DO_MES, COMPARAR_MES_ANTERIOR, MAIOR_GASTO, ORCAMENTO_RESTANTE,
    ASSINATURAS, POUPANCA, RECEITAS, MEDIA_MENSAL, ATIPICOS, COMO_ECONOMIZAR, DESCONHECIDA,
}

/** O que a pessoa perguntou: a [intencao], quão segura ela é, o [mes] pedido e a categoria citada (se houver). */
data class Pergunta(val intencao: Intencao, val confianca: Float, val mes: YearMonth, val categoriaId: Long?)

/** Linha da conversa do Assistente. A conversa fica só em memória: o texto das perguntas nunca é gravado. */
data class Mensagem(val deUsuario: Boolean, val texto: String, val gasto: GastoPedido? = null, val registrado: Boolean = false)

/** Entende a pergunta (intenção + mês + categoria) a partir do embedding dela. Puro e sem Android. */
object Perguntas {
    /** Chips da primeira tela e de quando a pergunta não foi entendida. */
    val SUGESTOES_INICIAIS = listOf(
        "Onde estou gastando mais?", "Quanto gastei este mês?", "Gastei mais que no mês passado?", "Como posso economizar?",
    )

    internal val NOMES_DOS_MESES = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )
    private val MESES_SEM_ACENTO = NOMES_DOS_MESES.map { NormalizacaoTexto.semAcento(it) }
    private val ANO = Regex(" (20\\d\\d) ")

    /** Minúsculas, sem acento, só letras/dígitos separados por espaço único e com um espaço em cada ponta. */
    private fun normalizar(texto: String): String =
        " " + NormalizacaoTexto.semAcento(texto.lowercase(Locale.ROOT)).replace(Regex("[^a-z0-9]+"), " ").trim() + " "

    /**
     * Mês da pergunta: nome do mês ("em outubro" = o mais recente que já passou; "outubro de 2025" = o do ano dito),
     * "mês passado/anterior" = [mesGlobal] − 1, senão o [mesGlobal].
     */
    fun extrairMes(texto: String, mesGlobal: YearMonth, hoje: LocalDate): YearMonth =
        mesPeloNome(texto, hoje) ?: if (pedeMesPassado(texto)) mesGlobal.minusMonths(1) else mesGlobal

    private fun mesPeloNome(texto: String, hoje: LocalDate): YearMonth? {
        val t = normalizar(texto)
        val numero = MESES_SEM_ACENTO.indexOfFirst { " $it " in t } + 1
        if (numero == 0) return null
        val ano = ANO.find(t)?.groupValues?.get(1)?.toInt() ?: if (numero <= hoje.monthValue) hoje.year else hoje.year - 1
        return YearMonth.of(ano, numero)
    }

    private fun pedeMesPassado(texto: String): Boolean {
        val t = normalizar(texto)
        return " mes passado " in t || " mes anterior " in t
    }

    /**
     * [vetor] é o embedding da pergunta (o mesmo modelo do treino das intenções). Abaixo do limiar de confiança, ou
     * longe de todas as intenções conhecidas, ou com vetor de outro tamanho: [Intencao.DESCONHECIDA].
     */
    fun entender(
        texto: String,
        vetor: FloatArray,
        categorias: List<Pair<Long, FloatArray>>,
        nomes: Map<Long, String>,
        mesGlobal: YearMonth,
        hoje: LocalDate,
        cfg: ConfigIntencoes,
    ): Pergunta {
        val (indice, confianca) = melhorIntencao(vetor, cfg)
        val intencao = intencaoDe(indice, confianca, vetor, cfg)
        // Comparar com "o mês passado" já é a própria comparação: o mês da pergunta continua sendo o global
        val mes = if (intencao == Intencao.COMPARAR_MES_ANTERIOR) {
            mesPeloNome(texto, hoje) ?: mesGlobal
        } else {
            extrairMes(texto, mesGlobal, hoje)
        }
        return Pergunta(intencao, confianca, mes, categoriaCitada(texto, vetor, categorias, nomes, cfg))
    }

    private fun melhorIntencao(vetor: FloatArray, cfg: ConfigIntencoes): Pair<Int, Float> {
        if (cfg.nomes.isEmpty() || cfg.pesos.size != cfg.nomes.size || cfg.pesos.any { it.size != vetor.size }) return -1 to 0f
        val logits = DoubleArray(cfg.nomes.size) { k ->
            var soma = cfg.vieses[k].toDouble()
            for (i in vetor.indices) soma += cfg.pesos[k][i] * vetor[i]
            soma
        }
        val maximo = logits.max()
        val exps = logits.map { exp(it - maximo) }
        val melhor = exps.indices.maxBy { exps[it] }
        return melhor to (exps[melhor] / exps.sum()).toFloat()
    }

    private fun intencaoDe(indice: Int, confianca: Float, vetor: FloatArray, cfg: ConfigIntencoes): Intencao {
        if (indice < 0 || confianca < cfg.limiar) return Intencao.DESCONHECIDA
        val longeDeTudo = cfg.centroides.isNotEmpty() &&
            cfg.centroides.maxOf { if (it.size == vetor.size) cosseno(vetor, it) else -1f } < cfg.similaridadeMinima
        if (longeDeTudo) return Intencao.DESCONHECIDA
        return Intencao.entries.firstOrNull { it.name == cfg.nomes[indice] && it != Intencao.DESCONHECIDA } ?: Intencao.DESCONHECIDA
    }

    /** Nome (normalizado, palavra inteira, o mais longo primeiro) contido na frase; senão a mais parecida por vetor. */
    private fun categoriaCitada(
        texto: String,
        vetor: FloatArray,
        categorias: List<Pair<Long, FloatArray>>,
        nomes: Map<Long, String>,
        cfg: ConfigIntencoes,
    ): Long? {
        val t = normalizar(texto)
        val peloNome = categorias.map { it.first }
            .mapNotNull { id -> nomes[id]?.let { normalizar(it).trim() }?.takeIf { it.isNotEmpty() }?.let { id to it } }
            .filter { (_, nome) -> " $nome " in t }
            .maxWithOrNull(compareBy({ it.second.length }, { -it.first }))
        if (peloNome != null) return peloNome.first
        return categorias.filter { it.second.size == vetor.size }
            .map { it.first to cosseno(vetor, it.second) }
            .filter { it.second >= cfg.similaridadeCategoria }
            .maxByOrNull { it.second }?.first
    }

    private fun cosseno(a: FloatArray, b: FloatArray): Float {
        var ab = 0.0
        var aa = 0.0
        var bb = 0.0
        for (i in a.indices) {
            ab += a[i] * b[i]
            aa += a[i] * a[i]
            bb += b[i] * b[i]
        }
        return if (aa == 0.0 || bb == 0.0) 0f else (ab / sqrt(aa * bb)).toFloat()
    }
}
