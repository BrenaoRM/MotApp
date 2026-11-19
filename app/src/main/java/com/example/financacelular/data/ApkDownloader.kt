package com.example.financacelular.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
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

            val instalando = context.packageManager.canRequestPackageInstalls() &&
                instalarPorSessao(context, arquivoApk)
            if (!instalando) {
                withContext(Dispatchers.Main) { instalarApk(context, arquivoApk) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Erro ao baixar arquivo") }
        }
    }

    /**
     * Instala via PackageInstaller. No Android 12+ a atualização do próprio app pode dispensar
     * a tela de confirmação; nos demais casos o [InstalacaoReceiver] abre a confirmação.
     * Devolve false se a sessão falhar, para cair no ACTION_VIEW.
     */
    private fun instalarPorSessao(context: Context, arquivo: File): Boolean {
        val instalador = context.packageManager.packageInstaller
        var id = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(arquivo.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setRequestUpdateOwnership(true)
                }
            }
            id = instalador.createSession(params)
            instalador.openSession(id).use { sessao ->
                arquivo.inputStream().use { entrada ->
                    sessao.openWrite("motapp.apk", 0, arquivo.length()).use { saida ->
                        entrada.copyTo(saida)
                        sessao.fsync(saida)
                    }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pendente = PendingIntent.getBroadcast(
                    context, id, Intent(context, InstalacaoReceiver::class.java), flags
                )
                sessao.commit(pendente.intentSender)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (id != -1) runCatching { instalador.abandonSession(id) }
            false
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