package com.example.financacelular.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AtualizacaoDisponivel(
    val versao: String,
    val notas: String,
    val urlApk: String,
    val urlPagina: String
)

object AtualizacaoService {

    private const val TAG = "AtualizacaoService"

    private const val GITHUB_OWNER = "BrenaoRM"
    private const val GITHUB_REPO = "MotApp"

    // Mesmo arquivo de preferências já usado pelo resto do app (ConfiguracoesViewModel)
    private const val PREFS_NOME = "financacelular_prefs"
    private const val CHAVE_ULTIMO_CHECK = "atualizacao_ultimo_check_ms"
    private const val CHAVE_ETAG = "atualizacao_etag"
    private const val CHAVE_CACHE_VERSAO = "atualizacao_cache_versao"
    private const val CHAVE_CACHE_NOTAS = "atualizacao_cache_notas"
    private const val CHAVE_CACHE_URL_APK = "atualizacao_cache_url_apk"
    private const val CHAVE_CACHE_URL_PAGINA = "atualizacao_cache_url_pagina"

    // A API do GitHub sem autenticação tem limite de 60 chamadas/hora POR IP.
    // Usuários atrás de CGNAT (comum em operadoras no Brasil) compartilham IP,
    // então evitamos checar em toda abertura do app: só de fato busca na rede
    // se já se passou esse intervalo desde a última checagem bem-sucedida.
    private val INTERVALO_MINIMO_MS = 6 * 60 * 60 * 1000L // 6 horas

    suspend fun verificarAtualizacao(context: Context, versaoAtual: String): AtualizacaoDisponivel? =
        withContext(Dispatchers.IO) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
            val agora = System.currentTimeMillis()
            val ultimoCheck = prefs.getLong(CHAVE_ULTIMO_CHECK, 0L)

            if (agora - ultimoCheck < INTERVALO_MINIMO_MS) {
                // Ainda dentro da janela de throttle: reaproveita o último resultado, sem rede.
                return@withContext montarResultadoDoCache(prefs, versaoAtual)
            }

            try {
                val url = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")

                // Requisição condicional: se nada mudou desde o último ETag salvo, o GitHub
                // responde 304 e ISSO NÃO CONTA no limite de taxa da API.
                prefs.getString(CHAVE_ETAG, null)?.takeIf { it.isNotBlank() }?.let { etagSalvo ->
                    connection.setRequestProperty("If-None-Match", etagSalvo)
                }

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> {
                        prefs.edit().putLong(CHAVE_ULTIMO_CHECK, agora).apply()
                        return@withContext montarResultadoDoCache(prefs, versaoAtual)
                    }

                    HttpURLConnection.HTTP_OK -> {
                        val corpo = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(corpo)

                        val versaoRemota = json.optString("tag_name").removePrefix("v").removePrefix("V")

                        // Procura o .apk anexado à release
                        var urlApk = ""
                        json.optJSONArray("assets")?.let { assets ->
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                if (asset.optString("name").endsWith(".apk")) {
                                    urlApk = asset.optString("browser_download_url")
                                    break
                                }
                            }
                        }
                        val notas = json.optString("body").ifBlank { "Sem notas de lançamento." }
                        val urlPagina = json.optString("html_url")

                        prefs.edit()
                            .putLong(CHAVE_ULTIMO_CHECK, agora)
                            .putString(CHAVE_ETAG, connection.getHeaderField("ETag"))
                            .putString(CHAVE_CACHE_VERSAO, versaoRemota)
                            .putString(CHAVE_CACHE_NOTAS, notas)
                            .putString(CHAVE_CACHE_URL_APK, urlApk)
                            .putString(CHAVE_CACHE_URL_PAGINA, urlPagina)
                            .apply()

                        if (versaoRemota.isBlank() || !versaoEhMaisNova(versaoRemota, versaoAtual)) {
                            return@withContext null
                        }

                        return@withContext AtualizacaoDisponivel(
                            versao = versaoRemota,
                            notas = notas,
                            urlApk = urlApk,
                            urlPagina = urlPagina
                        )
                    }

                    else -> {
                        Log.e(TAG, "Erro HTTP ao verificar release: ${connection.responseCode}")
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exceção ao verificar atualização", e)
                return@withContext null
            }
        }

    /** Reconstrói o último resultado conhecido (sem chamar a rede) e reaplica a comparação de versão. */
    private fun montarResultadoDoCache(prefs: SharedPreferences, versaoAtual: String): AtualizacaoDisponivel? {
        val versaoRemota = prefs.getString(CHAVE_CACHE_VERSAO, null) ?: return null
        if (!versaoEhMaisNova(versaoRemota, versaoAtual)) return null
        return AtualizacaoDisponivel(
            versao = versaoRemota,
            notas = prefs.getString(CHAVE_CACHE_NOTAS, "") ?: "",
            urlApk = prefs.getString(CHAVE_CACHE_URL_APK, "") ?: "",
            urlPagina = prefs.getString(CHAVE_CACHE_URL_PAGINA, "") ?: ""
        )
    }

    /** Compara versões tipo "1.4.0" pedaço a pedaço. Retorna true se `remota` > `atual`. */
    /** Compara versões. Retorna true apenas se `remota` for estritamente MAIOR que `atual`. */
    private fun versaoEhMaisNova(remota: String, atual: String): Boolean {
        val remotaLimpa = remota.replace(Regex("[^0-9.]"), "")
        val atualLimpa = atual.replace(Regex("[^0-9.]"), "")

        val partesRemota = remotaLimpa.split(".").mapNotNull { it.toIntOrNull() }
        val partesAtual = atualLimpa.split(".").mapNotNull { it.toIntOrNull() }

        val tamanho = maxOf(partesRemota.size, partesAtual.size)
        for (i in 0 until tamanho) {
            val r = partesRemota.getOrElse(i) { 0 }
            val a = partesAtual.getOrElse(i) { 0 }
            if (r > a) return true      // Se a parte remota for maior, tem atualização
            if (r < a) return false     // Se a parte remota for menor, está atualizado (ou com versão adiantada)
        }
        return false // Se forem exatamente iguais (ex: 1.0.20 e 1.0.20), retorna false e não mostra o aviso!
    }
}