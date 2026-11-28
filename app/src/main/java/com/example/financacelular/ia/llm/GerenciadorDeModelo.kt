package com.example.financacelular.ia.llm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
    internal const val CHAVE_JOB = "job"
    internal const val CHAVE_PROGRESSO = "p"
    private const val TAG_JOB = "job:"
    internal const val FOLGA_BYTES = 50L * 1024 * 1024
    private const val PREFS = "ia_avancada"
    private const val PREF_MODO = "modo"
    private const val PREF_VERIFICADOS = "verificados" // SHA-256 dos arquivos que já passaram na conferência
    private const val PREF_FALHOU = "falhou"
    private const val PREF_BAIXANDO = "baixando"

    private val _estado = MutableStateFlow<EstadoModelo>(EstadoModelo.Desativado)
    val estado: StateFlow<EstadoModelo> = _estado

    // Identifica o download vigente: um worker antigo (cancelado, substituído ou de antes da morte do processo) é ignorado.
    private var jobAtual = 0L

    private fun novoJob() = System.nanoTime().coerceAtLeast(1)

    /** 0 = ainda não definido neste processo (worker retomado depois de o processo morrer): adota o job. */
    @Synchronized internal fun jobVigente(job: Long): Boolean {
        if (jobAtual == 0L) jobAtual = job
        return jobAtual == job
    }

    /** Só muda o estado se [job] ainda for o vigente. */
    @Synchronized internal fun definirEstado(job: Long, novo: EstadoModelo) { if (jobVigente(job)) _estado.value = novo }

    @Synchronized internal fun falhar(context: Context, job: Long, modo: ModoIa) {
        if (!jobVigente(job)) return
        prefs(context).edit().putString(PREF_FALHOU, modo.name).remove(PREF_BAIXANDO).apply()
        _estado.value = EstadoModelo.Falhou(modo)
    }

    /**
     * Reconstrói o estado do WorkManager e do disco (o estado em memória nasce Desativado): chamar ao abrir a tela.
     * Trabalho agendado ou rodando vira Baixando (progresso do WorkInfo); depois vêm Falhou (persistido) e Pronto.
     */
    fun restaurar(context: Context) {
        val app = context.applicationContext
        observarTrabalho(app)
        if (_estado.value is EstadoModelo.Baixando) return
        CoroutineScope(Dispatchers.IO).launch { restaurarCom { lerEstado(app) } }
    }

    private var observando = false

    /** Acompanha o WorkInfo do download: o worker pode morrer ou ser cancelado sem passar por [falhar]. */
    @Synchronized private fun observarTrabalho(app: Context) {
        if (observando) return
        observando = true
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(NOME_TRABALHO).collect { aoMudarTrabalho(app, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                synchronized(this@GerenciadorDeModelo) { observando = false }
            }
        }
    }

    /** Só vale o trabalho do job vigente (tag `job:<n>`): um cancelado por substituição ou pelo usuário é ignorado. */
    private fun aoMudarTrabalho(app: Context, infos: List<WorkInfo>) {
        synchronized(this) {
            val (job, trabalho) = infos.mapNotNull { i ->
                i.tags.firstOrNull { it.startsWith(TAG_JOB) }?.removePrefix(TAG_JOB)?.toLongOrNull()?.let { j -> j to i }
            }.firstOrNull { jobVigente(it.first) } ?: return
            val novo = reconciliar(_estado.value, modoDaPref(app, PREF_BAIXANDO), trabalho.state, trabalho.progress.getFloat(CHAVE_PROGRESSO, 0f))
                ?: return
            if (novo is EstadoModelo.Falhou) falhar(app, job, novo.modo) else _estado.value = novo
        }
    }

    /**
     * O novo estado quando o trabalho do download mudou, ou null se nada muda. [baixando] é o modo gravado enquanto há
     * download pedido (o usuário cancelar ou desativar apaga): sem ele não há o que reconciliar.
     */
    internal fun reconciliar(atual: EstadoModelo, baixando: ModoIa?, trabalho: WorkInfo.State, progresso: Float): EstadoModelo? {
        if (baixando == null) return null
        return when (trabalho) {
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> EstadoModelo.Falhou(baixando)
            // trabalho adotado depois de a tela restaurar o estado: a pílula não fica em Desativado até o primeiro progresso
            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                if (atual is EstadoModelo.Baixando) null else EstadoModelo.Baixando(baixando, progresso)
            WorkInfo.State.SUCCEEDED -> null
        }
    }

    private fun lerEstado(app: Context): EstadoModelo {
        val infos = try { WorkManager.getInstance(app).getWorkInfosForUniqueWork(NOME_TRABALHO).get() } catch (_: Exception) { emptyList() }
        val ativo = infos.firstOrNull {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
        }
        val baixando = modoDaPref(app, PREF_BAIXANDO)
        val falhou = modoDaPref(app, PREF_FALHOU)
        val modoAtual = modoDaPref(app, PREF_MODO)
        return when {
            ativo != null && baixando != null -> EstadoModelo.Baixando(baixando, ativo.progress.getFloat(CHAVE_PROGRESSO, 0f))
            baixando != null -> EstadoModelo.Falhou(baixando) // download pedido e nenhum trabalho vivo: morreu no caminho
            falhou != null -> EstadoModelo.Falhou(falhou)
            modoAtual != null && caminhoPronto(app) != null -> EstadoModelo.Pronto(modoAtual)
            else -> EstadoModelo.Desativado
        }
    }

    /** Fotografa job e estado, calcula o novo estado (lento, fora da trava) e só o aplica se nada mudou nesse meio tempo. */
    internal fun restaurarCom(calcular: () -> EstadoModelo) {
        val (job, antes) = synchronized(this) { jobAtual to _estado.value }
        if (antes is EstadoModelo.Baixando) return // o download começou agora: o WorkManager ainda pode não o listar
        aplicarSeIgual(job, antes, calcular())
    }

    @Synchronized private fun aplicarSeIgual(job: Long, antes: EstadoModelo, novo: EstadoModelo) {
        if (jobAtual == job && _estado.value == antes) _estado.value = novo
    }

    @Synchronized internal fun invalidarJob() { jobAtual = novoJob() }

    @Synchronized internal fun reiniciarParaTeste() { jobAtual = 0L; _estado.value = EstadoModelo.Desativado }

    fun modosSuportados(context: Context): List<ModeloDisponivel> {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return filtrarPorRam(ModelosIa.disponiveis, info.totalMem, Build.SUPPORTED_ABIS.contains("arm64-v8a"))
    }

    internal fun filtrarPorRam(lista: List<ModeloDisponivel>, ramTotal: Long, arm64: Boolean): List<ModeloDisponivel> =
        if (arm64) lista.filter { ramTotal >= it.ramMinimaBytes } else emptyList()

    /** Agenda o download (trabalho único, REPLACE). O modo anterior só é apagado quando este terminar. */
    @Synchronized fun ativar(context: Context, modo: ModoIa) {
        val job = novoJob()
        jobAtual = job
        prefs(context).edit().remove(PREF_FALHOU).putString(PREF_BAIXANDO, modo.name).apply()
        _estado.value = EstadoModelo.Baixando(modo, 0f)
        val pedido = OneTimeWorkRequestBuilder<BaixarModeloWorker>()
            .setInputData(workDataOf(CHAVE_MODO to modo.name, CHAVE_JOB to job))
            .addTag(TAG_JOB + job)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOME_TRABALHO, ExistingWorkPolicy.REPLACE, pedido)
    }

    /** Cancela o download em andamento; os `.part` ficam para retomar depois. */
    @Synchronized fun cancelar(context: Context) {
        jobAtual = novoJob()
        WorkManager.getInstance(context).cancelUniqueWork(NOME_TRABALHO)
        prefs(context).edit().remove(PREF_FALHOU).remove(PREF_BAIXANDO).apply()
        val modo = modoDaPref(context, PREF_MODO)
        _estado.value = if (modo != null && caminhoPronto(context) != null) EstadoModelo.Pronto(modo) else EstadoModelo.Desativado
    }

    @Synchronized fun desativar(context: Context) {
        jobAtual = novoJob()
        WorkManager.getInstance(context).cancelUniqueWork(NOME_TRABALHO)
        prefs(context).edit().clear().apply()
        pasta(context).deleteRecursively()
        _estado.value = EstadoModelo.Desativado
    }

    /** Primeiro shard do modo ativo, só se o hash de cada arquivo já foi conferido e todos existem com o tamanho certo. */
    fun caminhoPronto(context: Context): String? {
        val modo = modoDaPref(context, PREF_MODO) ?: return null
        val modelo = ModelosIa.disponiveis.firstOrNull { it.modo == modo } ?: return null
        val verificados = prefs(context).getStringSet(PREF_VERIFICADOS, emptySet()) ?: emptySet()
        if (!modelo.arquivos.all { it.sha256.lowercase() in verificados }) return null
        return caminhoPronto(pasta(context), modelo)
    }

    internal fun caminhoPronto(pasta: File, modelo: ModeloDisponivel): String? {
        if (modelo.arquivos.isEmpty()) return null
        val todos = modelo.arquivos.all { val f = File(pasta, nomeNoDisco(it)); f.isFile && f.length() == it.bytes }
        return if (todos) File(pasta, nomeNoDisco(modelo.arquivos.first())).absolutePath else null
    }

    /** Nome por conteúdo: versões diferentes do mesmo arquivo nunca colidem, e modos com o mesmo arquivo o compartilham. */
    internal fun nomeNoDisco(arquivo: ArquivoDeModelo) = arquivo.sha256.lowercase().take(12) + "-" + arquivo.nome

    internal fun espacoSuficiente(restanteBytes: Long, livreBytes: Long) = livreBytes >= restanteBytes + FOLGA_BYTES

    /** Bytes que ainda faltam baixar (o que já está no disco, pronto ou em `.part`, não conta). */
    internal fun bytesRestantes(pasta: File, modelo: ModeloDisponivel): Long = modelo.arquivos.sumOf { a ->
        val pronto = File(pasta, nomeNoDisco(a))
        if (pronto.isFile && pronto.length() == a.bytes) 0L
        else {
            val parcial = File(pasta, nomeNoDisco(a) + ".part").length()
            a.bytes - (if (parcial <= a.bytes) parcial else 0L)
        }
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
     * Baixa [arquivo] para [pasta] (grava em `<nome no disco>.part`, retoma com `Range`). Devolve false se o hash não bate
     * (o arquivo é apagado); lança [IOException] em falha de rede, mantendo o `.part`.
     * [aoBytes] recebe o total já gravado deste arquivo; [verificarAtivo] roda a cada bloco e lança
     * CancellationException para interromper.
     */
    internal fun baixar(arquivo: ArquivoDeModelo, pasta: File, verificarAtivo: () -> Unit = {}, aoBytes: (Long) -> Unit): Boolean {
        pasta.mkdirs()
        val pronto = File(pasta, nomeNoDisco(arquivo))
        if (conferir(pronto, arquivo)) { aoBytes(arquivo.bytes); return true }
        pronto.delete()
        val parcial = File(pasta, pronto.name + ".part")
        if (parcial.length() > arquivo.bytes) parcial.delete()
        if (parcial.length() < arquivo.bytes) {
            try {
                baixarParcial(arquivo, parcial, verificarAtivo, aoBytes)
            } catch (e: FaixaRecusada) {
                parcial.delete()
                baixarParcial(arquivo, parcial, verificarAtivo, aoBytes)
            }
        }
        if (parcial.length() != arquivo.bytes) throw IOException("Tamanho final diferente do esperado")
        if (!parcial.renameTo(pronto)) throw IOException("Não deu para renomear ${parcial.name}")
        if (!conferir(pronto, arquivo)) { pronto.delete(); return false }
        return true
    }

    private class FaixaRecusada : IOException("Faixa recusada (416)")

    private fun baixarParcial(arquivo: ArquivoDeModelo, parcial: File, verificarAtivo: () -> Unit, aoBytes: (Long) -> Unit) {
        val inicio = parcial.length()
        val con = URL(arquivo.url).openConnection() as HttpURLConnection
        try {
            con.connectTimeout = 20_000
            con.readTimeout = 30_000
            if (inicio > 0) con.setRequestProperty("Range", "bytes=$inicio-")
            val codigo = con.responseCode
            if (codigo == 416 && inicio > 0) throw FaixaRecusada()
            // 200 com Range = o servidor ignorou: recomeça do zero. 206 só vale com o Content-Range começando onde pedimos.
            val anexar = codigo == HttpURLConnection.HTTP_PARTIAL
            if (codigo != HttpURLConnection.HTTP_OK && !anexar) throw IOException("HTTP $codigo")
            if (anexar) {
                val inicioServidor = Regex("""bytes\s+(\d+)-""").find(con.getHeaderField("Content-Range") ?: "")
                    ?.groupValues?.get(1)?.toLongOrNull()
                if (inicioServidor != inicio) { parcial.delete(); throw IOException("Content-Range inesperado") }
            }
            var gravado = if (anexar) inicio else 0L
            FileOutputStream(parcial, anexar).use { saida ->
                con.inputStream.use { entrada ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        verificarAtivo()
                        val n = entrada.read(buf)
                        if (n < 0) break
                        verificarAtivo()
                        saida.write(buf, 0, n)
                        gravado += n
                        aoBytes(gravado)
                    }
                }
            }
        } finally { con.disconnect() }
    }

    /** Apaga o que o [modelo] não referencia (os arquivos compartilhados com ele ficam). */
    internal fun apagarNaoReferenciados(pasta: File, modelo: ModeloDisponivel) {
        val nomes = modelo.arquivos.map { nomeNoDisco(it) }.toSet()
        pasta.listFiles()?.filter { it.name !in nomes }?.forEach { it.delete() }
    }

    /** Conclui o download de [job]; ignorado se ele já não for o vigente (cancelado, substituído ou desativado). */
    @Synchronized internal fun concluir(context: Context, job: Long, modelo: ModeloDisponivel) {
        if (!jobVigente(job)) return
        apagarNaoReferenciados(pasta(context), modelo)
        prefs(context).edit()
            .putString(PREF_MODO, modelo.modo.name)
            .putStringSet(PREF_VERIFICADOS, modelo.arquivos.map { it.sha256.lowercase() }.toSet())
            .remove(PREF_FALHOU).remove(PREF_BAIXANDO).apply()
        _estado.value = EstadoModelo.Pronto(modelo.modo)
    }

    internal fun pasta(context: Context) = File(context.filesDir, "ia/llm")
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun modoDaPref(context: Context, chave: String): ModoIa? =
        prefs(context).getString(chave, null)?.let { n -> ModoIa.entries.firstOrNull { it.name == n } }
}
