package com.example.financacelular.ia

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.data.idDaCategoriaFatura
import com.example.financacelular.ia.llm.AssistenteLlm
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Gasto pedido pelo chat; só é gravado depois que a pessoa confirma. Valor, data e categoria null = não foram ditos. */
data class GastoPedido(
    val descricao: String,
    val valorCentavos: Long?,
    val data: LocalDate? = null,
    val categoriaId: Long? = null,
)

data class RespostaDeAtalho(val texto: String, val gasto: GastoPedido? = null)

/**
 * Pedidos respondidos por regra fixa, antes de qualquer modelo: cumprimento, registrar gasto, porcentagem da renda,
 * investimento e dívida. null = a pergunta segue para o assistente. As orientações seguem o Caderno de Cidadania
 * Financeira do Banco Central e não indicam produto nem corretora.
 */
object Atalhos {
    private val CUMPRIMENTO = Regex(
        "((oi+e?|ola|opa|e ai|eae|bom dia|boa tarde|boa noite|tudo (bem|bom|certo)|como (vai|voce esta)|com voce|beleza) ?)+",
    )
    private val AGRADECIMENTO = Regex("((muito )?obrigad[oa]|valeu|vlw|brigad[oa]) ?")
    private val PEDE_REGISTRO = Regex(
        "((oi|ola|pode|por favor|pfv|vamos|bora|quero|queria|vou|preciso|gostaria de|me ajuda a) )*" +
            "(adicion|registr|anot|lanc|coloc|bot[ae]|inclu|salv)\\w*\\b.*",
    )
    private val CITA_GASTO = Regex("\\b(gasto|despesa|compra|reais|real)\\b|\\d")
    private val INVESTIMENTO = Regex("\\b(invest\\w*|aplicar|renda fixa|tesouro|cdb|reserva de emergencia)\\b")
    private val DIVIDA = Regex("\\b(divida\\w*|endividad\\w*|educacao financeira)\\b")
    private val PORCENTAGEM = Regex("\\b(porcento|por cento|porcentagem|percentual)\\b")
    private val SOBRE_A_RENDA = Regex("\\b(ganh\\w*|receb\\w*|renda|salario|isso)\\b")

    /** Alguma destas raízes (ou o nome de uma categoria) mostra que a pergunta é sobre o dinheiro da pessoa. */
    private val SOBRE_FINANCAS = Regex(
        "\\b(gast|ganh|receb|receit|renda|salario|despes|compr|pag|conta|fatura|cart|econom|poup|orcament|limite|" +
            "dinheiro|grana|reais|real|valor|custo|custa|caro|cara|barat|sald|sobr(a|o|ar)|falt|assinatur|recorren|parcel|" +
            "mensal|fix|meta|guard|lanc|categoria|resumo|padrao|atipic|estranh|financ|mes|semana|ano|hoje|ontem|" +
            "janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro|quanto|cort|" +
            "reduz|aument|diminu|pix|boleto|debito|credito|aluguel|mercado|comida|aliment|transporte|lazer|saude)",
    )
    private const val SO_FINANCAS =
        "Só consigo ajudar com as suas finanças: gastos, ganhos, orçamento e como economizar."

    private val NUMERO = Regex(
        "(?iu)(r\\$\\s*)?\\b((?:\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:[,.]\\d{1,2})?)(\\s*(?:reais|real|contos?)\\b)?",
    )
    private val VERBO = Regex("(?iu)^.*?\\b(?:adicion|registr|anot|lan[cç]|coloc|bot[ae]|inclu|salv)\\p{L}*")
    private val SOBRA_NO_INICIO =
        Regex("(?iu)^(?:(?:a[ií]|pra mim|um|uma|o|a|novo|nova|gasto|despesa|compra|de|do|da|com|no|na|em|por)(?:\\s+|$))+")
    private val SOBRA_NO_FIM = Regex("(?iu)(?:\\s+(?:de|do|da|com|no|na|em|por|pfv|por favor))+$")
    private val VIRGULAS_SOBRANDO = Regex("\\s*,(?=\\s*(?:,|$))")

    // Procuradas no texto sem acento e minúsculo (ver [dobrar])
    private val DIA_RELATIVO = Regex("\\b(?:(?:no|na|em|de) )?(?:dia (?:de )?)?(anteontem|ontem|hoje)\\b")
    private val DIA_E_MES = Regex("\\b(?:(?:no|em) )?(?:dia )?(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?\\b")
    private val DIA_DO_MES = Regex("\\b(?:(?:no|em) )?dia (\\d{1,2})\\b(?!\\s*(?:reais|real|contos?|[,.]\\d))")

    fun responder(
        texto: String,
        transacoes: List<Transacao>,
        categorias: List<Categoria>,
        mesGlobal: YearMonth,
        hoje: LocalDate,
    ): RespostaDeAtalho? {
        val t = NormalizacaoTexto.semAcento(texto.lowercase(Locale.ROOT)).replace(Regex("[^a-z0-9%]+"), " ").trim()
        fun totais(): Triple<String, Long, Long> {
            val mes = Perguntas.extrairMes(texto, mesGlobal, hoje)
            val (renda, gasto) = RespondedorDePerguntas.rendaEGasto(transacoes, categorias, mes, hoje)
            return Triple(Perguntas.NOMES_DOS_MESES[mes.monthValue - 1], renda, gasto)
        }
        return when {
            PEDE_REGISTRO.matches(t) && CITA_GASTO.containsMatchIn(t) -> registrar(texto, categorias, hoje)
            CUMPRIMENTO.matches("$t ") -> RespostaDeAtalho(
                "Oi! Posso mostrar seus gastos, anotar um gasto novo ou ajudar você a economizar. Como posso ajudar?",
            )
            AGRADECIMENTO.matches("$t ") -> RespostaDeAtalho("De nada! Quando quiser, é só perguntar.")
            INVESTIMENTO.containsMatchIn(t) -> totais().let { (mes, renda, gasto) -> investir(mes, renda, gasto) }
            DIVIDA.containsMatchIn(t) -> RespostaDeAtalho(
                "Liste o que deve, com juros e parcelas. Pague primeiro a dívida de juros mais altos (cartão e cheque " +
                    "especial), tente renegociar e evite parcelar compras novas até as contas fecharem.",
            )
            (PORCENTAGEM.containsMatchIn(t) || '%' in t) && SOBRE_A_RENDA.containsMatchIn(t) ->
                totais().let { (mes, renda, gasto) -> porcentagem(mes, renda, gasto) }
            // Continuações curtas ("e com Lazer?") seguem para o assistente, que tem o histórico
            SOBRE_FINANCAS.containsMatchIn(t) || AssistenteLlm.ehContinuacao(texto) || citaCategoria(t, categorias) -> null
            else -> RespostaDeAtalho(SO_FINANCAS)
        }
    }

    private fun citaCategoria(t: String, categorias: List<Categoria>): Boolean = categorias.any { c ->
        val nome = NormalizacaoTexto.semAcento(c.nome.lowercase(Locale.ROOT)).replace(Regex("[^a-z0-9]+"), " ").trim()
        nome.isNotEmpty() && " $nome " in " $t "
    }

    /** Com centavos: é o valor que a pessoa vai confirmar. */
    private fun formatarReais(centavos: Long): String =
        formatarReaisInteiros(centavos / 100 * 100) + "," + (centavos % 100).toString().padStart(2, '0')

    /** Minúsculas e sem acento, um caractere para cada um do original: os índices valem nos dois textos. */
    private fun dobrar(texto: String): String = buildString(texto.length) {
        for (c in texto) append((NormalizacaoTexto.semAcento(c.toString()).singleOrNull() ?: c).lowercaseChar())
    }

    /** A data dita no pedido (null = nenhuma) e o texto sem ela. Dia ou mês que ainda não chegou é o anterior. */
    private fun separarData(texto: String, hoje: LocalDate): Pair<LocalDate?, String> {
        val dobrado = dobrar(texto)
        fun sem(m: MatchResult) = texto.removeRange(m.range)
        DIA_RELATIVO.find(dobrado)?.let { m ->
            val atras = when (m.groupValues[1]) { "anteontem" -> 2L; "ontem" -> 1L; else -> 0L }
            return hoje.minusDays(atras) to sem(m)
        }
        DIA_E_MES.find(dobrado)?.let { m ->
            val (dia, mes, ano) = m.destructured
            fun em(a: Int) = runCatching { LocalDate.of(a, mes.toInt(), dia.toInt()) }.getOrNull()
            val data = if (ano.isNotEmpty()) em(ano.toInt()) else em(hoje.year)?.takeIf { it <= hoje } ?: em(hoje.year - 1)
            if (data != null) return data to sem(m)
        }
        DIA_DO_MES.find(dobrado)?.let { m ->
            val dia = m.groupValues[1].toInt()
            val mes = YearMonth.from(hoje).let { if (dia > hoje.dayOfMonth) it.minusMonths(1) else it }
            if (mes.isValidDay(dia)) return mes.atDay(dia) to sem(m)
        }
        return null to texto
    }

    /** "categoria X", com X igual a uma categoria de despesa da pessoa; sem isso o texto fica como está. */
    private fun separarCategoria(texto: String, categorias: List<Categoria>): Pair<Long?, String> {
        val dobrado = dobrar(texto)
        val fatura = idDaCategoriaFatura(categorias)
        // Nome mais longo primeiro: "Casa nova" antes de "Casa"
        for (c in categorias.filter { it.tipo == TipoTransacao.DESPESA && it.id != fatura }.sortedByDescending { it.nome.length }) {
            val nome = Regex.escape(dobrar(c.nome.trim()))
            val m = Regex("(?:\\b(?:na|em|da|de) )?\\bcategoria (?:de )?$nome(?![a-z0-9])").find(dobrado) ?: continue
            return c.id to texto.removeRange(m.range)
        }
        return null to texto
    }

    private fun registrar(pedido: String, categorias: List<Categoria>, hoje: LocalDate): RespostaDeAtalho {
        // Data e categoria saem antes do valor: os números delas não são dinheiro
        val (data, semData) = separarData(pedido, hoje)
        val (categoriaId, texto) = separarCategoria(semData, categorias)
        val numeros = NUMERO.findAll(texto).toList()
        // Com mais de um número ("2 pastéis por 12 reais"), vale o que vem com moeda; senão o último
        val numero = numeros.lastOrNull { it.groupValues[1].isNotEmpty() || it.groupValues[3].isNotEmpty() }
            ?: numeros.lastOrNull()
        val centavos = numero?.let { emCentavos(it.groupValues[2]) }
        val semValor = if (numero == null) texto else texto.removeRange(numero.range)
        val descricao = semValor.replace(VERBO, "").replace(Regex("\\s+"), " ").replace(VIRGULAS_SOBRANDO, "")
            .trim(' ', '.', ',', '!', '?', ':', '-')
            .replace(SOBRA_NO_INICIO, "").replace(SOBRA_NO_FIM, "").trim(' ', '.', ',', ':', '-')
            .replaceFirstChar { it.uppercase() }
        val oQue = descricao.ifEmpty { "gasto" }
        val resposta = if (centavos == null && descricao.isEmpty()) {
            "O que foi e quanto custou? Escreva, por exemplo: adiciona lanche 12 reais."
        } else if (centavos == null) {
            "Qual foi o valor de \"$oQue\"? Escreva, por exemplo: adiciona $oQue 12 reais."
        } else {
            "Confira e confirme: $oQue, ${formatarReais(centavos)}."
        }
        return RespostaDeAtalho(resposta, GastoPedido(descricao, centavos, data, categoriaId))
    }

    private fun emCentavos(numero: String): Long? {
        val limpo = when {
            ',' in numero -> numero.replace(".", "").replace(',', '.')
            Regex("\\d{1,3}(\\.\\d{3})+").matches(numero) -> numero.replace(".", "")
            else -> numero
        }
        return BigDecimal(limpo).movePointRight(2).takeIf { it.signum() > 0 && it < BigDecimal(1_000_000_000) }?.toLong()
    }

    private fun porcentagem(mes: String, renda: Long, gasto: Long): RespostaDeAtalho {
        if (renda <= 0) return RespostaDeAtalho("Não encontrei receitas em $mes, então não dá para calcular a porcentagem.")
        val usado = (gasto * 100.0 / renda).roundToInt()
        val diferenca = abs(usado - 100)
        val resumo = if (gasto > renda) "$diferenca% a mais do que recebeu" else "sobraram $diferenca%"
        return RespostaDeAtalho("Em $mes você gastou $usado% do que recebeu: $resumo.")
    }

    private fun investir(mes: String, renda: Long, gasto: Long): RespostaDeAtalho {
        val reserva = "comece por uma reserva de emergência de 3 a 6 meses de gastos, em algo seguro e fácil de resgatar."
        val texto = when {
            renda <= 0 -> "Não encontrei receitas em $mes. Como regra geral, separe cerca de 10% do que ganha e $reserva"
            gasto >= renda -> "Em $mes não sobrou dinheiro (faltaram ${formatarReais(gasto - renda)}). Antes de investir, " +
                "ajuste os gastos e quite dívidas caras. Quando sobrar, $reserva"
            else -> {
                val sugestao = minOf(renda / 10, renda - gasto)
                "Em $mes sobraram ${formatarReais(renda - gasto)}. Um bom começo é separar ${formatarReais(sugestao)} " +
                    "(${(sugestao * 100.0 / renda).roundToInt()}% da renda) logo que receber e $reserva"
            }
        }
        return RespostaDeAtalho(texto)
    }
}
