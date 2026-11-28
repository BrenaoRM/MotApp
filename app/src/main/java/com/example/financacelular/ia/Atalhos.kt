package com.example.financacelular.ia

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.Transacao
import com.example.financacelular.ia.llm.AssistenteLlm
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Gasto pedido pelo chat; só é gravado depois que a pessoa confirma. [valorCentavos] null = não foi dito. */
data class GastoPedido(val descricao: String, val valorCentavos: Long?)

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
        "((oi|ola|pode|por favor|pfv) )*(adicion|registr|anot|lanc|coloc|bot[ae]|inclu|salv)\\w*\\b.*",
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
            PEDE_REGISTRO.matches(t) && CITA_GASTO.containsMatchIn(t) -> registrar(texto)
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

    private fun registrar(texto: String): RespostaDeAtalho {
        val numeros = NUMERO.findAll(texto).toList()
        // Com mais de um número ("2 pastéis por 12 reais"), vale o que vem com moeda; senão o último
        val numero = numeros.lastOrNull { it.groupValues[1].isNotEmpty() || it.groupValues[3].isNotEmpty() }
            ?: numeros.lastOrNull()
        val centavos = numero?.let { emCentavos(it.groupValues[2]) }
        val semValor = if (numero == null) texto else texto.removeRange(numero.range)
        val descricao = semValor.replace(VERBO, "").replace(Regex("\\s+"), " ").trim(' ', '.', ',', '!', '?', ':', '-')
            .replace(SOBRA_NO_INICIO, "").replace(SOBRA_NO_FIM, "").trim(' ', '.', ',', ':', '-')
            .replaceFirstChar { it.uppercase() }
        val oQue = descricao.ifEmpty { "gasto" }
        val resposta = if (centavos == null) {
            "Qual foi o valor de \"$oQue\"? Escreva, por exemplo: adiciona $oQue 12 reais."
        } else {
            "Confira e confirme: $oQue, ${formatarReais(centavos)}."
        }
        return RespostaDeAtalho(resposta, GastoPedido(descricao, centavos))
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
