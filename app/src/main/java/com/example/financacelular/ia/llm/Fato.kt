package com.example.financacelular.ia.llm

import com.example.financacelular.ia.Intencao
import org.json.JSONArray
import org.json.JSONObject

enum class TipoValor { REAIS, PORCENTO, NUMERO }

data class ItemDoFato(val rotulo: String?, val tipo: TipoValor, val valor: String)

/** Resultado estruturado de uma ferramenta: a [frase] pronta mais os números que ela cita. */
data class Fato(val ferramenta: Intencao, val periodo: String?, val frase: String, val itens: List<ItemDoFato>) {
    fun paraJson(): JSONObject = JSONObject()
        .put("ferramenta", ferramenta.name)
        .put("periodo", periodo ?: JSONObject.NULL)
        .put("frase", frase)
        .put(
            "itens",
            JSONArray(
                itens.map {
                    JSONObject().put("rotulo", it.rotulo ?: JSONObject.NULL).put("tipo", it.tipo.name.lowercase()).put("valor", it.valor)
                },
            ),
        )
}
