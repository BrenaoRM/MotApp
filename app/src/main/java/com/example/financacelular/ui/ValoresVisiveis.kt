package com.example.financacelular.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.FieldPosition
import java.text.NumberFormat
import java.text.ParsePosition

/** Olho do cartão do saldo: esconde os valores em dinheiro no Início e na Análise (mesmo estado nas duas telas, guardado ao fechar o app). */
object ValoresVisiveis {
    var visivel by mutableStateOf(true)
        private set

    private const val CHAVE = "valores_visiveis"
    private var prefs: android.content.SharedPreferences? = null

    /** Lê a escolha guardada; chamar uma vez ao abrir o app, antes de desenhar as telas. */
    fun iniciar(context: Context) {
        val p = context.applicationContext.getSharedPreferences("financacelular_prefs", Context.MODE_PRIVATE)
        prefs = p
        visivel = p.getBoolean(CHAVE, true)
    }

    fun alternar() {
        visivel = !visivel
        prefs?.edit()?.putBoolean(CHAVE, visivel)?.apply()
    }

    /** O formato de moeda a usar: o real quando visível, o [FormatoOculto] quando escondido. */
    fun formato(real: NumberFormat): NumberFormat = if (visivel) real else FormatoOculto
}

/** Formata qualquer valor como "R$ ••••••", para esconder dinheiro sem mexer em cada tela. */
object FormatoOculto : NumberFormat() {
    private const val TEXTO = "R$ ••••••"

    override fun format(number: Double, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer =
        toAppendTo.append(TEXTO)

    override fun format(number: Long, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer =
        toAppendTo.append(TEXTO)

    override fun parse(source: String, parsePosition: ParsePosition): Number? = null
}
