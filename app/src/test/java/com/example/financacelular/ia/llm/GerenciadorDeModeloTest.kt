package com.example.financacelular.ia.llm

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class GerenciadorDeModeloTest {
    private enum class ComRange { RESPEITA, IGNORA_COM_200, RECUSA_COM_416 }

    private lateinit var pasta: File
    private lateinit var servidor: HttpServer
    private val dados = ByteArray(200_000) { (it * 31 + 7).toByte() }
    private var rangeRecebido: String? = null
    private var requisicoes = 0
    private var comRange = ComRange.RESPEITA

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun url(nome: String) = "http://127.0.0.1:${servidor.address.port}/$nome"
    private fun arq(nome: String = "m.gguf", sha: String = sha(dados), bytes: Long = dados.size.toLong()) =
        ArquivoDeModelo(nome, url(nome), sha, bytes)

    private fun noDisco(a: ArquivoDeModelo) = File(pasta, GerenciadorDeModelo.nomeNoDisco(a))
    private fun parcial(a: ArquivoDeModelo) = File(pasta, GerenciadorDeModelo.nomeNoDisco(a) + ".part")

    @Before fun subir() {
        pasta = Files.createTempDirectory("ia").toFile()
        servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext("/") { ex ->
            requisicoes++
            rangeRecebido = ex.requestHeaders.getFirst("Range")
            val inicio = rangeRecebido?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            when {
                rangeRecebido != null && comRange == ComRange.RECUSA_COM_416 -> ex.sendResponseHeaders(416, -1)
                rangeRecebido != null && comRange == ComRange.RESPEITA -> {
                    val corpo = dados.copyOfRange(inicio, dados.size)
                    ex.responseHeaders.add("Content-Range", "bytes $inicio-${dados.size - 1}/${dados.size}")
                    ex.sendResponseHeaders(206, corpo.size.toLong())
                    ex.responseBody.use { it.write(corpo) }
                }
                else -> {
                    ex.sendResponseHeaders(200, dados.size.toLong())
                    ex.responseBody.use { it.write(dados) }
                }
            }
            ex.close()
        }
        servidor.start()
    }

    @After fun parar() { servidor.stop(0); pasta.deleteRecursively() }

    @Test fun baixaDoZeroEConfereHash() {
        val a = arq()
        assertTrue(GerenciadorDeModelo.baixar(a, pasta) {})
        assertTrue(noDisco(a).readBytes().contentEquals(dados))
        assertNull(rangeRecebido)
    }

    @Test fun retomaComRangeDepoisDeCortado() {
        val a = arq()
        parcial(a).writeBytes(dados.copyOfRange(0, 80_000))
        assertTrue(GerenciadorDeModelo.baixar(a, pasta) {})
        assertEquals("bytes=80000-", rangeRecebido)
        assertTrue(noDisco(a).readBytes().contentEquals(dados))
        assertFalse(parcial(a).exists())
    }

    @Test fun servidorQueIgnoraRangeRecomecaDoZero() {
        comRange = ComRange.IGNORA_COM_200
        val a = arq()
        parcial(a).writeBytes(dados.copyOfRange(0, 80_000))
        assertTrue(GerenciadorDeModelo.baixar(a, pasta) {})
        assertEquals("bytes=80000-", rangeRecebido)
        assertTrue(noDisco(a).readBytes().contentEquals(dados))
    }

    @Test fun faixaRecusada416ApagaOParcialERecomecaUmaVez() {
        comRange = ComRange.RECUSA_COM_416
        val a = arq()
        parcial(a).writeBytes(dados.copyOfRange(0, 80_000))
        assertTrue(GerenciadorDeModelo.baixar(a, pasta) {})
        assertEquals(2, requisicoes)
        assertNull(rangeRecebido) // a segunda requisição já foi sem Range
        assertTrue(noDisco(a).readBytes().contentEquals(dados))
    }

    @Test fun parcialDeOutroHashNaoEReaproveitado() {
        val velho = arq(sha = "a".repeat(64))
        val novo = arq()
        parcial(velho).writeBytes(ByteArray(80_000) { 9 })
        assertTrue(GerenciadorDeModelo.baixar(novo, pasta) {})
        assertNull(rangeRecebido) // o parcial velho tem outro nome: baixou inteiro, sem Range
        assertTrue(noDisco(novo).readBytes().contentEquals(dados))
    }

    @Test fun hashErradoApagaOArquivo() {
        val a = arq(sha = "0".repeat(64))
        assertFalse(GerenciadorDeModelo.baixar(a, pasta) {})
        assertFalse(noDisco(a).exists())
        assertFalse(parcial(a).exists())
    }

    @Test fun cancelamentoInterrompeNoMeioDoDownload() {
        val a = arq()
        var blocos = 0
        try {
            GerenciadorDeModelo.baixar(a, pasta, verificarAtivo = { if (++blocos > 1) throw CancellationException() }) {}
            org.junit.Assert.fail("devia ter cancelado")
        } catch (_: CancellationException) { }
        assertFalse(noDisco(a).exists())
    }

    private fun modelo(vararg a: ArquivoDeModelo, modo: ModoIa = ModoIa.RAPIDA) = ModeloDisponivel(modo, a.toList(), 1L, 1000)

    @Test fun tamanhoDiferenteNaoEstaPronto() {
        val a = arq()
        noDisco(a).writeBytes(dados.copyOf(dados.size - 1))
        assertNull(GerenciadorDeModelo.caminhoPronto(pasta, modelo(a)))
        noDisco(a).writeBytes(dados)
        assertNotNull(GerenciadorDeModelo.caminhoPronto(pasta, modelo(a)))
    }

    @Test fun shardFaltandoNaoEstaPronto() {
        val a = arq("m-1.gguf"); val b = arq("m-2.gguf")
        noDisco(a).writeBytes(dados)
        assertNull(GerenciadorDeModelo.caminhoPronto(pasta, modelo(a, b)))
        noDisco(b).writeBytes(dados)
        assertEquals(noDisco(a).absolutePath, GerenciadorDeModelo.caminhoPronto(pasta, modelo(a, b)))
    }

    @Test fun doisModosCompartilhamOArquivoIgualEATrocaOPreserva() {
        val base = arq("base.gguf"); val extra = arq("extra.gguf", sha = "b".repeat(64))
        val media = modelo(base, modo = ModoIa.MEDIA)
        val inteligente = modelo(base, extra, modo = ModoIa.INTELIGENTE)
        assertEquals(GerenciadorDeModelo.nomeNoDisco(base), GerenciadorDeModelo.nomeNoDisco(media.arquivos[0]))
        noDisco(base).writeBytes(dados); noDisco(extra).writeBytes(dados)
        GerenciadorDeModelo.apagarNaoReferenciados(pasta, media) // troca para Média: o extra some, o base fica
        assertTrue(noDisco(base).exists())
        assertFalse(noDisco(extra).exists())
        assertNotNull(GerenciadorDeModelo.caminhoPronto(pasta, media))
        assertNull(GerenciadorDeModelo.caminhoPronto(pasta, inteligente))
    }

    @Test fun mesmoNomeComHashDiferenteNaoColide() {
        val v1 = arq("m.gguf", sha = "1".repeat(64)); val v2 = arq("m.gguf", sha = "2".repeat(64))
        assertTrue(GerenciadorDeModelo.nomeNoDisco(v1) != GerenciadorDeModelo.nomeNoDisco(v2))
        noDisco(v1).writeBytes(dados)
        assertNull(GerenciadorDeModelo.caminhoPronto(pasta, modelo(v2)))
        assertNotNull(GerenciadorDeModelo.caminhoPronto(pasta, modelo(v1)))
    }

    @Test fun espacoInsuficienteEBytesRestantes() {
        val mb = 1024L * 1024
        assertFalse(GerenciadorDeModelo.espacoSuficiente(1000 * mb, 1000 * mb + 49 * mb))
        assertTrue(GerenciadorDeModelo.espacoSuficiente(1000 * mb, 1000 * mb + 50 * mb))
        val a = arq()
        assertEquals(dados.size.toLong(), GerenciadorDeModelo.bytesRestantes(pasta, modelo(a)))
        parcial(a).writeBytes(dados.copyOf(50_000))
        assertEquals(150_000L, GerenciadorDeModelo.bytesRestantes(pasta, modelo(a)))
        noDisco(a).writeBytes(dados)
        assertEquals(0L, GerenciadorDeModelo.bytesRestantes(pasta, modelo(a)))
    }

    @Test fun jobAntigoNaoEVigenteDepoisDeSubstituido() {
        assertTrue(GerenciadorDeModelo.jobVigente(111L)) // adota o primeiro
        assertFalse(GerenciadorDeModelo.jobVigente(222L))
        assertTrue(GerenciadorDeModelo.jobVigente(111L))
    }

    @Test fun modosSuportadosFiltraPorRam() {
        val lista = listOf(
            ModeloDisponivel(ModoIa.RAPIDA, emptyList(), ModelosIa.RAM_MINIMA_RAPIDA, 1),
            ModeloDisponivel(ModoIa.MEDIA, emptyList(), ModelosIa.RAM_MINIMA_MEDIA, 1),
        )
        assertEquals(listOf(ModoIa.RAPIDA), GerenciadorDeModelo.filtrarPorRam(lista, 4_000_000_000L, true).map { it.modo })
        assertEquals(2, GerenciadorDeModelo.filtrarPorRam(lista, 5_760_000_000L, true).size)
        assertTrue(GerenciadorDeModelo.filtrarPorRam(lista, 8_000_000_000L, false).isEmpty())
    }
}
