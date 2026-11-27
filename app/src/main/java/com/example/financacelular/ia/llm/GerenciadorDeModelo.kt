package com.example.financacelular.ia.llm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface EstadoModelo {
    data object Desativado : EstadoModelo
    data class Baixando(val modo: ModoIa, val progresso: Float) : EstadoModelo
    data class Pronto(val modo: ModoIa) : EstadoModelo
    data class Falhou(val modo: ModoIa) : EstadoModelo
}

object GerenciadorDeModelo {
    internal const val NOME_TRABALHO = "ia_baixar_modelo"
    internal const val CHAVE_MODO = "modo"
    private const val PREFS = "ia_avancada"
    private const val PREF_MODO = "modo"
    private const val PREF_HASH_OK = "hash_ok"

    private val _estado = MutableStateFlow<EstadoModelo>(EstadoModelo.Desativado)
    val estado: StateFlow<EstadoModelo> = _estado

    internal fun definirEstado(novo: EstadoModelo) { _estado.value = novo }

    /** Lê o disco e as preferências: chamar ao abrir a tela, já que o estado em memória nasce Desativado. */
    fun restaurar(context: Context) {
        if (_estado.value is EstadoModelo.Baixando) return
        val modo = modoAtivo(context)
        _estado.value = if (modo != null && caminhoPronto(context) != null) EstadoModelo.Pronto(modo) else EstadoModelo.Desativado
    }

    fun modosSuportados(context: Context): List<ModeloDisponivel> {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return filtrarPorRam(ModelosIa.disponiveis, info.totalMem, Build.SUPPORTED_ABIS.contains("arm64-v8a"))
    }

    internal fun filtrarPorRam(lista: List<ModeloDisponivel>, ramTotal: Long, arm64: Boolean): List<ModeloDisponivel> =
        if (arm64) lista.filter { ramTotal >= it.ramMinimaBytes } else emptyList()

    /** Agenda o download (trabalho único, REPLACE). O modo anterior só é apagado quando este terminar. */
    fun ativar(context: Context, modo: ModoIa) {
        _estado.value = EstadoModelo.Baixando(modo, 0f)
        val pedido = OneTimeWorkRequestBuilder<BaixarModeloWorker>()
            .setInputData(workDataOf(CHAVE_MODO to modo.name))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOME_TRABALHO, ExistingWorkPolicy.REPLACE, pedido)
    }

    /** Cancela o download em andamento; os `.part` ficam para retomar depois. */
    fun cancelar(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NOME_TRABALHO)
        _estado.value = EstadoModelo.Desativado
        restaurar(context)
    }

    fun desativar(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NOME_TRABALHO)
        prefs(context).edit().clear().apply()
        pasta(context).deleteRecursively()
        _estado.value = EstadoModelo.Desativado
    }

    /** Primeiro shard do modo ativo, só se o hash já foi conferido e todos os arquivos existem com o tamanho certo. */
    fun caminhoPronto(context: Context): String? {
        val modo = modoAtivo(context) ?: return null
        if (!prefs(context).getBoolean(PREF_HASH_OK, false)) return null
        val modelo = ModelosIa.disponiveis.firstOrNull { it.modo == modo } ?: return null
        return caminhoPronto(pasta(context), modelo)
    }

    internal fun caminhoPronto(pasta: File, modelo: ModeloDisponivel): String? {
        if (modelo.arquivos.isEmpty()) return null
        val todos = modelo.arquivos.all { val f = File(pasta, it.nome); f.isFile && f.length() == it.bytes }
        return if (todos) File(pasta, modelo.arquivos.first().nome).absolutePath else null
    }

    /** Tamanho e SHA-256. */
    internal fun conferir(arquivo: File, esperado: ArquivoDeModelo): Boolean {
        if (!arquivo.isFile || arquivo.length() != esperado.bytes) return false
        val md = MessageDigest.getInstance("SHA-256")
        arquivo.inputStream().use { entrada ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = entrada.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }.equals(esperado.sha256, ignoreCase = true)
    }

    /**
     * Baixa [arquivo] para [pasta] (grava em `<nome>.part`, retoma com `Range`). Devolve false se o hash não bate
     * (o arquivo é apagado); lança [IOException] em falha de rede, mantendo o `.part`.
     * [aoBytes] recebe o total já gravado deste arquivo.
     */
    internal fun baixar(arquivo: ArquivoDeModelo, pasta: File, aoBytes: (Long) -> Unit): Boolean {
        pasta.mkdirs()
        val pronto = File(pasta, arquivo.nome)
        if (conferir(pronto, arquivo)) { aoBytes(arquivo.bytes); return true }
        pronto.delete()
        val parcial = File(pasta, arquivo.nome + ".part")
        if (parcial.length() > arquivo.bytes) parcial.delete()
        if (parcial.length() < arquivo.bytes) {
            val inicio = parcial.length()
            val con = URL(arquivo.url).openConnection() as HttpURLConnection
            try {
                con.connectTimeout = 20_000
                con.readTimeout = 30_000
                if (inicio > 0) con.setRequestProperty("Range", "bytes=$inicio-")
                val codigo = con.responseCode
                if (codigo == 416) { parcial.delete(); throw IOException("Faixa recusada (416)") }
                val anexar = codigo == HttpURLConnection.HTTP_PARTIAL
                if (codigo != HttpURLConnection.HTTP_OK && !anexar) throw IOException("HTTP $codigo")
                var gravado = if (anexar) inicio else 0L
                FileOutputStream(parcial, anexar).use { saida ->
                    con.inputStream.use { entrada ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = entrada.read(buf)
                            if (n < 0) break
                            saida.write(buf, 0, n)
                            gravado += n
                            aoBytes(gravado)
                        }
                    }
                }
            } finally { con.disconnect() }
        }
        if (parcial.length() != arquivo.bytes) throw IOException("Download incompleto")
        if (!parcial.renameTo(pronto)) throw IOException("Não deu para renomear ${parcial.name}")
        if (!conferir(pronto, arquivo)) { pronto.delete(); return false }
        return true
    }

    internal fun concluir(context: Context, modelo: ModeloDisponivel) {
        val nomes = modelo.arquivos.map { it.nome }.toSet()
        pasta(context).listFiles()?.filter { it.name !in nomes }?.forEach { it.delete() }
        prefs(context).edit().putString(PREF_MODO, modelo.modo.name).putBoolean(PREF_HASH_OK, true).apply()
        _estado.value = EstadoModelo.Pronto(modelo.modo)
    }

    internal fun pasta(context: Context) = File(context.filesDir, "ia/llm")
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun modoAtivo(context: Context): ModoIa? =
        prefs(context).getString(PREF_MODO, null)?.let { n -> ModoIa.entries.firstOrNull { it.name == n } }
}
