package com.example.financacelular.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ApkDownloader {

    suspend fun baixarEInstalarApk(
        context: Context,
        urlApk: String,
        onProgresso: (Float) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlApk)
            val conexao = url.openConnection() as HttpURLConnection
            conexao.requestMethod = "GET"
            conexao.connectTimeout = 15_000
            conexao.readTimeout = 15_000
            conexao.connect()

            if (conexao.responseCode != HttpURLConnection.HTTP_OK) {
                withContext(Dispatchers.Main) { onError("Erro HTTP: ${conexao.responseCode}") }
                return@withContext
            }

            val tamanhoTotal = conexao.contentLength
            val arquivoApk = File(context.cacheDir, "atualizacao.apk")
            if (arquivoApk.exists()) arquivoApk.delete()

            val inputStream = conexao.inputStream
            val outputStream = FileOutputStream(arquivoApk)
            val buffer = ByteArray(8192)
            var bytesLidos: Int
            var totalBaixado = 0L

            while (inputStream.read(buffer).also { bytesLidos = it } != -1) {
                outputStream.write(buffer, 0, bytesLidos)
                totalBaixado += bytesLidos
                if (tamanhoTotal > 0) {
                    val progresso = totalBaixado.toFloat() / tamanhoTotal.toFloat()
                    withContext(Dispatchers.Main) { onProgresso(progresso) }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            withContext(Dispatchers.Main) {
                instalarApk(context, arquivoApk)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Erro ao baixar arquivo") }
        }
    }

    fun instalarApk(context: Context, arquivo: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intentSettings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intentSettings)
            return
        }

        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            arquivo
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}