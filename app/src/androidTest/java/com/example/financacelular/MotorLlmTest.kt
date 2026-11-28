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

    /** Com decodificação gulosa, o cache do prefixo não muda nenhum byte da saída; plano e resposta ficam os dois no cache. */
    @Test
    fun cacheDoPrefixoNaoMudaASaida(): Unit = runBlocking(Dispatchers.Default) {
        val m = motor!!
        val gramatica = contrato.gramaticaPlano(mes, categorias.values.toList())
        PERGUNTAS.forEachIndexed { i, p ->
            val semPlano = m.gerar(promptPlano(p), gramatica, 96) {}
            val semResposta = m.gerar(contrato.promptResposta(p, listOf(FATO)), null, 120) {}
            val comPlano = m.gerar(promptPlano(p), gramatica, 96, contrato.prefixoPlano) {}
            val cachePlano = m.ultimaGeracao!!.tokensDoCache
            val comResposta = m.gerar(contrato.promptResposta(p, listOf(FATO)), null, 120, contrato.prefixoResposta) {}
            val cacheResposta = m.ultimaGeracao!!.tokensDoCache
            Log.i(TAG, "pergunta $i: prefixo do cache plano=$cachePlano resposta=$cacheResposta")
            assertEquals("plano mudou com o cache", semPlano, comPlano)
            assertEquals("resposta mudou com o cache", semResposta, comResposta)
            if (i > 0) assertTrue("prefixo não veio do cache", cachePlano > 0 && cacheResposta > 0)
        }
    }

    /** Cancelamento e exceção no aoPedaco com o prefixo em cache não estragam o cache. */
    @Test
    fun cacheSobreviveACancelamentoEExcecao(): Unit = runBlocking(Dispatchers.Default) {
        val m = motor!!
        val prompt = contrato.promptResposta("me conta uma história longa", emptyList())
        val referencia = m.gerar(prompt, null, 80) {}
        m.gerar(prompt, null, 80, contrato.prefixoResposta) {}
        val primeiroPedaco = CompletableDeferred<Unit>()
        val geracao = async { m.gerar(prompt, null, 400, contrato.prefixoResposta) { primeiroPedaco.complete(Unit) } }
        primeiroPedaco.await()
        geracao.cancelAndJoin()
        assertTrue(geracao.isCancelled)
        val erro = runCatching { m.gerar(prompt, null, 16, contrato.prefixoResposta) { error("falhou no pedaço") } }.exceptionOrNull()
        assertTrue("esperava IllegalStateException: $erro", erro is IllegalStateException)
        assertEquals(referencia, m.gerar(prompt, null, 80, contrato.prefixoResposta) {})
        assertTrue(m.ultimaGeracao!!.tokensDoCache > 0)
    }

    /** KV q8_0 contra f16: aceita diferença, mas mede (logcat, tag MotorLlmTest). */
    @Test
    fun kvQ8ContraF16(): Unit = runBlocking(Dispatchers.Default) {
        val gramatica = contrato.gramaticaPlano(mes, categorias.values.toList())
        suspend fun saidas(m: MotorLlm) = PERGUNTAS.flatMap { p ->
            listOf(
                m.gerar(promptPlano(p), gramatica, 96, contrato.prefixoPlano) {},
                m.gerar(contrato.promptResposta(p, listOf(FATO)), null, 120, contrato.prefixoResposta) {},
            )
        }
        val q8 = saidas(motor!!)
        motor!!.liberar()
        val f16 = MotorLlm.carregar(modelo().absolutePath, kvQuantizado = false)!!
        motor = f16
        val referencia = saidas(f16)
        val iguais = q8.indices.count { q8[it] == referencia[it] }
        Log.i(TAG, "KV q8_0 x f16: $iguais de ${q8.size} saídas iguais")
        q8.indices.filter { q8[it] != referencia[it] }.forEach { Log.i(TAG, "diferente:\n f16=${referencia[it]}\n q8 =${q8[it]}") }
        q8.filterIndexed { i, _ -> i % 2 == 0 }.forEach { assertNotNull("plano inválido com q8_0: $it", contrato.lerPlano(it, mes, categorias)) }
    }

    /**
     * Medição da Task 9: 5 perguntas seguidas (plano com gramática + resposta), PSS e temperatura. Com o cache do prefixo,
     * como no app; `-e kv f16` troca o KV q8_0 pelo f16 e `-e cache nao` desliga o cache.
     */
    @Test
    fun medicao(): Unit = runBlocking(Dispatchers.Default) {
        val args = InstrumentationRegistry.getArguments()
        val nome = args.getString("medir")
        assumeTrue("sem -e medir", nome != null)
        if (args.getString("kv") == "f16") {
            motor!!.liberar()
            motor = MotorLlm.carregar(modelo().absolutePath, kvQuantizado = false)
        }
        val m = motor!!
        val comCache = args.getString("cache") != "nao"
        val prefixoPlano = if (comCache) contrato.prefixoPlano else ""
        val prefixoResposta = if (comCache) contrato.prefixoResposta else ""
        val pss = ArrayList<Int>()
        pss.add(pssKb())
        val tempAntes = temperaturaBateria()
        var promptTok = 0L; var cacheTok = 0L; var promptUs = 0L; var genTok = 0L; var genUs = 0L
        val tempos = ArrayList<Double>()
        val ttftPlano = ArrayList<Double>(); val ttftResposta = ArrayList<Double>()
        val msPromptPlano = ArrayList<Double>(); val msPromptResposta = ArrayList<Double>()
        for (p in PERGUNTAS) {
            val inicio = System.nanoTime()
            var primeiro = 0L
            val plano = m.gerar(promptPlano(p), contrato.gramaticaPlano(mes, categorias.values.toList()), 96, prefixoPlano) { if (primeiro == 0L) primeiro = System.nanoTime() }
            ttftPlano.add((primeiro - inicio) / 1e6)
            m.ultimaGeracao!!.let { promptTok += it.tokensPrompt; cacheTok += it.tokensDoCache; promptUs += it.microsPrompt; genTok += it.tokensGerados; genUs += it.microsGeracao; msPromptPlano.add(it.microsPrompt / 1e3) }
            pss.add(pssKb())
            val inicioResposta = System.nanoTime()
            primeiro = 0L
            val resposta = m.gerar(contrato.promptResposta(p, listOf(FATO)), null, 120, prefixoResposta) { if (primeiro == 0L) primeiro = System.nanoTime() }
            ttftResposta.add((primeiro - inicioResposta) / 1e6)
            m.ultimaGeracao!!.let { promptTok += it.tokensPrompt; cacheTok += it.tokensDoCache; promptUs += it.microsPrompt; genTok += it.tokensGerados; genUs += it.microsGeracao; msPromptResposta.add(it.microsPrompt / 1e3) }
            pss.add(pssKb())
            val s = (System.nanoTime() - inicio) / 1e9
            tempos.add(s)
            Log.i(MEDICAO, "$nome pergunta '%s': %.1f s plano=%s resposta=%s".format(p, s, plano, resposta.replace('\n', ' ')))
        }
        val lista = { l: List<Double> -> l.joinToString("/") { "%.0f".format(it) } }
        Log.i(
            MEDICAO,
            "$nome TEMPOS ms promptPlano=%s promptResposta=%s ttftPlano=%s ttftResposta=%s".format(
                lista(msPromptPlano), lista(msPromptResposta), lista(ttftPlano), lista(ttftResposta),
            ),
        )
        val tempDepois = temperaturaBateria()
        Log.i(
            MEDICAO,
            "$nome RESUMO prompt=%.1f tok/s (%d tok, %d do cache) geracao=%.1f tok/s (%d tok) pssMax=%d MB porPergunta=%s s tempBateria=%.1f->%.1f C termico=%s".format(
                promptTok * 1e6 / promptUs, promptTok, cacheTok, genTok * 1e6 / genUs, genTok, pss.max() / 1024,
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
        val PERGUNTAS = listOf(
            "quanto gastei no mercado esse mês?",
            "qual foi meu total do mês passado?",
            "em que categoria eu mais gastei em novembro?",
            "quanto ainda resta do meu orçamento de lazer?",
            "compara meus gastos desse mês com o anterior",
        )
        val FATO = Fato(Intencao.TOTAL_DO_MES, "2026-11", "Você gastou R$ 1.234,56 em novembro de 2026, sendo R$ 412,30 em Mercado.", emptyList())
    }
}
