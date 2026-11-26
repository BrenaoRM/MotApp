package com.example.financacelular.ia

import org.json.JSONObject

/** Parâmetros do modelo lidos de `assets/ia/modelo_ia.json` (a T5 acrescenta classificador, genérico e atípicos). */
data class ConfigIa(
    val versaoModelo: Int,
    val dimensao: Int,
    val maxTokens: Int,
) {
    companion object {
        fun deJson(json: String): ConfigIa {
            val o = JSONObject(json)
            return ConfigIa(o.getInt("versaoModelo"), o.getInt("dimensao"), o.getInt("maxTokens"))
        }
    }
}
