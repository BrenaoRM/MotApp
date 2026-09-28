package com.example.financacelular.data

import android.content.Context
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

    /**
     * Consulta o GitHub Releases diretamente toda vez que o app abre.
     * Retorna a versão se for estritamente superior à versão instalada.
     */
    suspend fun verificarAtualizacao(context: Context, versaoAtual: String): AtualizacaoDisponivel? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val corpo = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(corpo)

                    val versaoRemota = json.optString("tag_name").removePrefix("v").removePrefix("V")

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

                    if (versaoRemota.isBlank() || !versaoEhMaisNova(versaoRemota, versaoAtual)) {
                        return@withContext null
                    }

                    return@withContext AtualizacaoDisponivel(
                        versao = versaoRemota,
                        notas = notas,
                        urlApk = urlApk,
                        urlPagina = urlPagina
                    )
                } else {
                    Log.e(TAG, "Erro HTTP ao verificar release no GitHub: ${connection.responseCode}")
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exceção ao verificar atualização", e)
                return@withContext null
            }
        }

    /**
     * Compara a versão remota com a versão instalada.
     * Retorna true apenas se a versão remota for estritamente maior.
     */
    private fun versaoEhMaisNova(remota: String, atual: String): Boolean {
        val remotaLimpa = remota.split("-")[0].replace(Regex("[^0-9.]"), "")
        val atualLimpa = atual.split("-")[0].replace(Regex("[^0-9.]"), "")

        val partesRemota = remotaLimpa.split(".").mapNotNull { it.toIntOrNull() }
        val partesAtual = atualLimpa.split(".").mapNotNull { it.toIntOrNull() }

        val tamanho = maxOf(partesRemota.size, partesAtual.size)

        for (i in 0 until tamanho) {
            val r = partesRemota.getOrElse(i) { 0 }
            val a = partesAtual.getOrElse(i) { 0 }

            if (r > a) return true
            if (r < a) return false
        }
        return false
    }
}