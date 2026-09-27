package com.example.financacelular.data

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

    suspend fun verificarAtualizacao(versaoAtual: String): AtualizacaoDisponivel? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Erro HTTP ao verificar release: ${connection.responseCode}")
                return@withContext null
            }

            val corpo = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(corpo)

            val versaoRemota = json.optString("tag_name").removePrefix("v")

            if (versaoRemota.isBlank() || !versaoEhMaisNova(versaoRemota, versaoAtual)) {
                return@withContext null
            }

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

            AtualizacaoDisponivel(
                versao = versaoRemota,
                notas = json.optString("body").ifBlank { "Sem notas de lançamento." },
                urlApk = urlApk,
                urlPagina = json.optString("html_url")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao verificar atualização", e)
            null
        }
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