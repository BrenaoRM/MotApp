package com.example.financacelular.ia

import java.text.Normalizer
import java.util.Locale

/** Espelho de `ia/texto.py::normalizar_para_modelo`: os acentos ficam, o tokenizador do modelo usa. */
object NormalizacaoTexto {
    private val BOILERPLATE = setOf(
        "compra", "cartao", "cartão", "debito", "débito", "credito", "crédito", "pag", "pgto", "pix",
        "enviado", "recebido", "boleto", "automatico", "automático",
    )

    // Cidades como sequência de palavras sem acento, as maiores primeiro.
    private val CIDADES = listOf(
        "patos de minas", "rio paranaiba", "sao gotardo", "belo horizonte", "sao paulo", "vicosa",
    ).map { it.split(" ") }.sortedByDescending { it.size }

    fun paraModelo(descricao: String?): String {
        if (descricao == null) return ""
        val minusculo = Normalizer.normalize(descricao, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        // Equivale a re.sub(r"[\d\W_]", " "): só letras (e números que não sejam dígitos decimais) sobrevivem.
        val sb = StringBuilder(minusculo.length)
        var i = 0
        while (i < minusculo.length) {
            val cp = minusculo.codePointAt(i)
            i += Character.charCount(cp)
            if (mantem(cp)) sb.appendCodePoint(cp) else sb.append(' ')
        }
        val palavras = sb.toString().split(' ').filter { it.isNotEmpty() }
        val base = palavras.map(::semAcento)
        val manter = BooleanArray(palavras.size) { true }
        for (cidade in CIDADES) {
            val n = cidade.size
            for (k in 0..palavras.size - n) {
                if ((0 until n).all { base[k + it] == cidade[it] }) for (m in k until k + n) manter[m] = false
            }
        }
        return palavras.filterIndexed { idx, p -> manter[idx] && p !in BOILERPLATE }.joinToString(" ")
    }

    private fun mantem(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
        Character.MODIFIER_LETTER, Character.OTHER_LETTER,
        Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }

    /** NFKD e descarta o que não é ASCII, como `.encode("ascii", "ignore")`. */
    private fun semAcento(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKD).filter { it.code < 128 }
}
