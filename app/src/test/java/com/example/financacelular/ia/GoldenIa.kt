package com.example.financacelular.ia

import org.json.JSONArray
import java.io.File

/** Carrega os assets e os golden do Python nos testes JVM (o diretório de trabalho é `app/`). */
internal object GoldenIa {
    fun recurso(nome: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("ia/$nome")) { nome }.readText(Charsets.UTF_8)

    fun asset(nome: String): File = File("src/main/assets/ia/$nome")

    fun tokenizador(): Tokenizador = Tokenizador.deJson(asset("tokenizador.json").readText(Charsets.UTF_8))

    fun config(): ConfigIa = ConfigIa.deJson(asset("modelo_ia.json").readText(Charsets.UTF_8))

    fun array(nome: String): JSONArray = JSONArray(recurso(nome))
}
