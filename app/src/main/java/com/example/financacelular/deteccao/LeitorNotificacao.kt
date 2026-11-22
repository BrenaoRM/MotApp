package com.example.financacelular.deteccao

import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import java.util.Locale

/** O que o app entendeu de uma notificação de banco. Categoria e cartão ficam para a pessoa escolher. */
data class TransacaoDetectada(
    val valor: Double,
    val tipo: TipoTransacao,
    val descricao: String?,
    val formaPagamento: FormaPagamento
)

private val REGEX_VALOR = Regex("""R\$\s?(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{2}))?""")
private val REGEX_LIMITE_DE_CREDITO = Regex("""limite\s+(?:de\s+)?cr[ée]dito""")
private val REGEX_ESTABELECIMENTO_INTER = Regex(
    """(?:débito|crédito|debito|credito)\s+no\s+(.+?)\s+o valor de""",
    RegexOption.IGNORE_CASE
)
private val REGEX_DESTINO = Regex("""^\s*(?:em|para|de)\s+(.+?)(?=[.,;!]|\s+via\s|$)""", RegexOption.IGNORE_CASE)

// Vem antes das de gasto: "Fulano te enviou um Pix ... creditado na sua conta" (Inter) tem "enviou" e é ganho
private val PALAVRAS_RECEITA = listOf(
    "recebeu", "recebido", "depósito", "deposito", "estorno", "te enviou", "creditado", "creditada"
)
private val PALAVRAS_DESPESA = listOf(
    "compr", "pagamento", "enviou", "enviado", "débito", "debito", "transferência enviada"
)

/** Lê só o corpo da notificação (o título nunca entra). Devolve null se não parecer uma transação. */
fun lerNotificacao(textoBruto: String): TransacaoDetectada? {
    // \s não casa espaço não separável (NBSP) nem o estreito (U+202F), que alguns bancos usam depois de "R$"
    val texto = textoBruto.replace('\u00A0', ' ').replace('\u202F', ' ')
    val minusculo = texto.lowercase()
    val tipo = when {
        PALAVRAS_RECEITA.any { it in minusculo } -> TipoTransacao.RECEITA
        PALAVRAS_DESPESA.any { it in minusculo } -> TipoTransacao.DESPESA
        else -> return null
    }

    val inicioDoValorDe = minusculo.indexOf("valor de")
    val achado = (if (inicioDoValorDe >= 0) REGEX_VALOR.find(texto, inicioDoValorDe) else null)
        ?: REGEX_VALOR.find(texto)
        ?: return null
    val valor = "${achado.groupValues[1].replace(".", "")}.${achado.groupValues[2].ifEmpty { "00" }}".toDouble()
    if (valor <= 0.0) return null

    // Ganho nunca é crédito; e "limite de crédito" (saldo disponível) não diz como a compra foi paga
    val semLimite = minusculo.replace(REGEX_LIMITE_DE_CREDITO, "")
    val forma = if (tipo == TipoTransacao.DESPESA && ("crédito" in semLimite || "credito" in semLimite)) {
        FormaPagamento.CARTAO_CREDITO
    } else {
        FormaPagamento.DEBITO
    }

    val descricao = REGEX_ESTABELECIMENTO_INTER.find(texto)?.groupValues?.get(1)?.trim()
        ?: REGEX_DESTINO.find(texto.substring(achado.range.last + 1))?.groupValues?.get(1)?.trim()

    return TransacaoDetectada(valor, tipo, descricao?.ifEmpty { null }, forma)
}

/** Chave para reconhecer a mesma notificação chegando duas vezes: pacote + corpo, nunca o título. */
fun chaveDeRepeticao(pacote: String, texto: String): String =
    "$pacote|${texto.lowercase().replace(Regex("""\s+"""), " ").trim()}"

/** Valor no formato do campo de Nova Transação: vírgula decimal, sem separador de milhar. */
fun TransacaoDetectada.valorParaCampo(): String =
    String.format(Locale.forLanguageTag("pt-BR"), "%.2f", valor)
