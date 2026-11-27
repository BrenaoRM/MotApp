package com.example.financacelular

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.financacelular.ia.Intencao
import com.example.financacelular.ia.llm.ContratoLlm
import com.example.financacelular.ia.llm.Fato
import com.example.financacelular.ia.llm.MotorLlm
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger

/**
 * llama.cpp no aparelho. Antes: `adb push <modelo>.gguf /data/local/tmp/motapp-teste.gguf`.
 * A medição só roda com `-Pandroid.testInstrumentationRunnerArguments.medir=<nome>` (vai para o logcat, tag MedicaoLlm).
 */
@RunWith(AndroidJUnit4::class)
class MotorLlmTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val contrato = ContratoLlm.doAsset(context)
    private val mes = YearMonth.of(2026, 11)
    private val categorias = mapOf(1L to "Mercado", 2L to "Transporte", 3L to "Lazer", 4L to "Saúde", 5L to "🍔 Lanche")
    private var motor: MotorLlm? = null

    @Before
    fun carregar() {
        val caminho = modelo().absolutePath
        val inicio = System.nanoTime()
        motor = MotorLlm.carregar(caminho)
        Log.i(MEDICAO, "carga: %.2f s".format((System.nanoTime() - inicio) / 1e9))
        assertNotNull("modelo não abriu", motor)
    }

    @After
    fun liberar() {
        motor?.liberar()
    }

    private fun promptPlano(pergunta: String) = contrato.promptPlano(pergunta, mes, categorias.values.toList(), emptyList())

    @Test
    fun geraPlanoQueFazParseComAGramatica(): Unit = runBlocking(Dispatchers.Default) {
        val pedacos = StringBuilder()
        val texto = motor!!.gerar(promptPlano("quanto gastei no mercado esse mês?"), contrato.gramaticaPlano(mes, categorias.values.toList()), 96) {
            pedacos.append(it)
        }
        Log.i(TAG, "plano: $texto")
        assertEquals(texto, pedacos.toString())
        assertNotNull("plano inválido: $texto", contrato.lerPlano(texto, mes, categorias))
    }

    @Test
    fun cancelaNoMeioSemCrashEGeraDeNovo(): Unit = runBlocking(Dispatchers.Default) {
        val m = motor!!
        val primeiroPedaco = CompletableDeferred<Unit>()
        val pedacos = AtomicInteger(0)
        val geracao = async {
            m.gerar(contrato.promptResposta("me conta uma história longa", emptyList()), null, 400) {
                pedacos.incrementAndGet()
                primeiroPedaco.complete(Unit)
            }
        }
        primeiroPedaco.await()
        geracao.cancelAndJoin()
        assertTrue(geracao.isCancelled)
        Log.i(TAG, "cancelado depois de $pedacos pedaços")
        assertTrue("não parou: $pedacos pedaços", pedacos.get() < 400)

        val texto = m.gerar(promptPlano("qual foi meu total do mês?"), contrato.gramaticaPlano(mes, categorias.values.toList()), 96) {}
        assertNotNull("plano inválido depois de cancelar: $texto", contrato.lerPlano(texto, mes, categorias))
    }

    /** Cancelar uma geração que ainda está na fila não pode cortar a que está rodando. */
    @Test
    fun cancelarAFilaNaoCortaAGeracaoEmAndamento(): Unit = runBlocking(Dispatchers.Default) {
        val m = motor!!
        val prompt = contrato.promptResposta("me conta uma história longa", emptyList())
        val referencia = m.gerar(prompt, null, 80) {}
        val primeiroPedaco = CompletableDeferred<Unit>()
        val rodando = async { m.gerar(prompt, null, 80) { primeiroPedaco.complete(Unit) } }
        primeiroPedaco.await()
        val naFila = async { m.gerar(promptPlano("total do mês"), null, 8) {} }
        delay(50)
        naFila.cancelAndJoin()
        assertTrue(naFila.isCancelled)
        assertEquals("a geração em andamento foi cortada", referencia, rodando.await())
    }

    /** Uma exceção em aoPedaco sobe para quem chamou, sem derrubar o processo (CheckJNI do debug), e o motor continua usável. */
    @Test
    fun excecaoNoAoPedacoSobeSemCrash(): Unit = runBlocking(Dispatchers.Default) {
        val m = motor!!
        val erro = runCatching { m.gerar(promptPlano("total do mês"), null, 16) { error("falhou no pedaço") } }.exceptionOrNull()
        assertTrue("esperava IllegalStateException: $erro", erro is IllegalStateException && erro.message == "falhou no pedaço")
        val texto = m.gerar(promptPlano("qual foi meu total do mês?"), contrato.gramaticaPlano(mes, categorias.values.toList()), 96) {}
        assertNotNull("plano inválido depois da exceção: $texto", contrato.lerPlano(texto, mes, categorias))
    }

    @Test
    fun gerarDepoisDeLiberarFalhaSemCrash(): Unit = runBlocking(Dispatchers.Default) {
        val m = motor!!
        m.liberar()
        m.liberar()
        val erro = runCatching { m.gerar(promptPlano("total do mês"), null, 8) {} }.exceptionOrNull()
        assertTrue("esperava IllegalStateException: $erro", erro is IllegalStateException)
    }

    /** Medição da Task 9: 5 perguntas seguidas (plano com gramática + resposta), PSS e temperatura. */
    @Test
    fun medicao(): Unit = runBlocking(Dispatchers.Default) {
        val nome = InstrumentationRegistry.getArguments().getString("medir")
        assumeTrue("sem -e medir", nome != null)
        val m = motor!!
        val pss = ArrayList<Int>()
        pss.add(pssKb())
        val tempAntes = temperaturaBateria()
        val perguntas = listOf(
            "quanto gastei no mercado esse mês?",
            "qual foi meu total do mês passado?",
            "em que categoria eu mais gastei em novembro?",
            "quanto ainda resta do meu orçamento de lazer?",
            "compara meus gastos desse mês com o anterior",
        )
        val fato = Fato(Intencao.TOTAL_DO_MES, "2026-11", "Você gastou R$ 1.234,56 em novembro de 2026, sendo R$ 412,30 em Mercado.", emptyList())
        var promptTok = 0L; var promptUs = 0L; var genTok = 0L; var genUs = 0L
        val tempos = ArrayList<Double>()
        for (p in perguntas) {
            val inicio = System.nanoTime()
            val plano = m.gerar(promptPlano(p), contrato.gramaticaPlano(mes, categorias.values.toList()), 96) {}
            m.ultimaGeracao!!.let { promptTok += it.tokensPrompt; promptUs += it.microsPrompt; genTok += it.tokensGerados; genUs += it.microsGeracao }
            pss.add(pssKb())
            val resposta = m.gerar(contrato.promptResposta(p, listOf(fato)), null, 120) {}
            m.ultimaGeracao!!.let { promptTok += it.tokensPrompt; promptUs += it.microsPrompt; genTok += it.tokensGerados; genUs += it.microsGeracao }
            pss.add(pssKb())
            val s = (System.nanoTime() - inicio) / 1e9
            tempos.add(s)
            Log.i(MEDICAO, "$nome pergunta '%s': %.1f s plano=%s resposta=%s".format(p, s, plano, resposta.replace('\n', ' ')))
        }
        val tempDepois = temperaturaBateria()
        Log.i(
            MEDICAO,
            "$nome RESUMO prompt=%.1f tok/s (%d tok) geracao=%.1f tok/s (%d tok) pssMax=%d MB porPergunta=%s s tempBateria=%.1f->%.1f C termico=%s".format(
                promptTok * 1e6 / promptUs, promptTok, genTok * 1e6 / genUs, genTok, pss.max() / 1024,
                tempos.joinToString("/") { "%.1f".format(it) }, tempAntes, tempDepois, shell("dumpsys thermalservice").lineSequence()
                    .firstOrNull { it.contains("Thermal Status") }?.trim(),
            ),
        )
    }

    private fun shell(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** TOTAL PSS do `dumpsys meminfo` do processo do app (o teste roda nele). */
    private fun pssKb(): Int {
        val linha = shell("dumpsys meminfo ${context.packageName}").lineSequence().firstOrNull { it.trim().startsWith("TOTAL PSS:") || it.trim().startsWith("TOTAL ") }
        return linha?.trim()?.split(Regex("\\s+"))?.firstNotNullOfOrNull { it.toIntOrNull() } ?: -1
    }

    private fun temperaturaBateria(): Double =
        shell("dumpsys battery").lineSequence().firstOrNull { it.trim().startsWith("temperature:") }
            ?.substringAfter(":")?.trim()?.toDoubleOrNull()?.div(10) ?: Double.NaN

    /** O app nem sempre lê /data/local/tmp (SELinux); se não der, o shell copia para o cache do app. */
    private fun modelo(): File {
        val origem = File(CAMINHO)
        if (origem.canRead()) return origem
        val destino = File(context.cacheDir, "motapp-teste.gguf")
        val tamanho = shell("stat -c %s $CAMINHO").trim().toLongOrNull()
        if (destino.length() != tamanho) {
            val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat $CAMINHO")
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { entrada -> destino.outputStream().use { entrada.copyTo(it, 1 shl 20) } }
        }
        return destino
    }

    private companion object {
        const val TAG = "MotorLlmTest"
        const val MEDICAO = "MedicaoLlm"
        const val CAMINHO = "/data/local/tmp/motapp-teste.gguf"
    }
}
