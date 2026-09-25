package com.example.financacelular.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GoogleDriveService {

    private const val TAG = "GoogleDriveService"
    private const val NOME_BACKUP = "backup_financa.db"

    /**
     * Procura o backup mais recente já existente no Drive (por nome).
     * Retorna o fileId, ou null se não existir nenhum.
     */
    private fun procurarIdBackupExistente(tokenAcesso: String): String? {
        val query = URLEncoder.encode("name='$NOME_BACKUP' and trashed=false", "UTF-8")
        val url = URL(
            "https://www.googleapis.com/drive/v3/files" +
                    "?q=$query&orderBy=modifiedTime desc&pageSize=1&fields=files(id)"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $tokenAcesso")

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            Log.e(TAG, "Erro HTTP ao procurar ficheiro no Drive: ${connection.responseCode}")
            return null
        }

        val corpo = connection.inputStream.bufferedReader().use { it.readText() }
        val arquivos = JSONObject(corpo).optJSONArray("files") ?: return null
        if (arquivos.length() == 0) return null

        return arquivos.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
    }

    /**
     * Envia o backup para o Drive. Se já existir um backup anterior, atualiza o
     * conteúdo dele (PATCH) em vez de criar um ficheiro novo a cada chamada —
     * antes disso não acontecia e os backups iam se acumulando duplicados.
     */
    suspend fun uploadBackup(tokenAcesso: String, arquivoDb: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (tokenAcesso.isBlank()) {
                Log.e(TAG, "Token de acesso vazio ou inválido.")
                return@withContext false
            }

            val idExistente = procurarIdBackupExistente(tokenAcesso)

            val boundary = "Boundary_${System.currentTimeMillis()}"
            val url = if (idExistente != null) {
                URL("https://www.googleapis.com/upload/drive/v3/files/$idExistente?uploadType=multipart")
            } else {
                URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            }

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = if (idExistente != null) "PATCH" else "POST"
            connection.setRequestProperty("Authorization", "Bearer $tokenAcesso")
            connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            connection.doOutput = true

            // Ao atualizar um ficheiro existente não reenviamos "name" (nem precisa);
            // ao criar um novo, definimos nome e tipo.
            val metadata = if (idExistente != null) {
                "{}"
            } else {
                "{\"name\": \"$NOME_BACKUP\", \"mimeType\": \"application/x-sqlite3\"}"
            }

            val bodyStream = connection.outputStream
            bodyStream.write("--$boundary\r\n".toByteArray())
            bodyStream.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            bodyStream.write("$metadata\r\n".toByteArray())

            bodyStream.write("--$boundary\r\n".toByteArray())
            bodyStream.write("Content-Type: application/x-sqlite3\r\n\r\n".toByteArray())

            arquivoDb.inputStream().use { input ->
                input.copyTo(bodyStream)
            }

            bodyStream.write("\r\n--$boundary--\r\n".toByteArray())
            bodyStream.flush()
            bodyStream.close()

            val respostaCode = connection.responseCode
            if (respostaCode == HttpURLConnection.HTTP_OK || respostaCode == HttpURLConnection.HTTP_CREATED) {
                true
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Erro HTTP $respostaCode ao enviar para o Drive: $errorStream")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exceção durante o upload para o Drive", e)
            false
        }
    }

    suspend fun restaurarBackup(tokenAcesso: String, ficheiroDestino: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (tokenAcesso.isBlank()) {
                Log.e(TAG, "Token de acesso vazio ou inválido para restauro.")
                return@withContext false
            }

            val fileId = procurarIdBackupExistente(tokenAcesso)
            if (fileId == null) {
                Log.w(TAG, "Ficheiro '$NOME_BACKUP' não encontrado no Google Drive.")
                return@withContext false
            }

            val downloadUrl = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            val downloadConnection = downloadUrl.openConnection() as HttpURLConnection
            downloadConnection.requestMethod = "GET"
            downloadConnection.setRequestProperty("Authorization", "Bearer $tokenAcesso")

            if (downloadConnection.responseCode == HttpURLConnection.HTTP_OK) {
                downloadConnection.inputStream.use { input ->
                    ficheiroDestino.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                Log.e(TAG, "Erro HTTP ao descarregar conteúdo do Drive: ${downloadConnection.responseCode}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exceção durante o restauro do Drive", e)
            false
        }
    }
}