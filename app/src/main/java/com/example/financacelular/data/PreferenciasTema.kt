package com.example.financacelular.data

import android.content.Context

enum class TemaPreferencia { CLARO, ESCURO, SISTEMA }

object PreferenciasTema {
    private const val ARQUIVO = "financa_celular_prefs"
    private const val CHAVE_TEMA = "tema"

    fun obter(context: Context): TemaPreferencia {
        val prefs = context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        val valor = prefs.getString(CHAVE_TEMA, TemaPreferencia.SISTEMA.name)
        return try {
            TemaPreferencia.valueOf(valor ?: TemaPreferencia.SISTEMA.name)
        } catch (e: IllegalArgumentException) {
            TemaPreferencia.SISTEMA
        }
    }

    fun salvar(context: Context, tema: TemaPreferencia) {
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
            .edit()
            .putString(CHAVE_TEMA, tema.name)
            .apply()
    }
}
