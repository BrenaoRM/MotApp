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

class GerenciadorDeModeloTest {
    private lateinit var pasta: File
    private lateinit var servidor: HttpServer
    private val dados = ByteArray(200_000) { (it * 31 + 7).toByte() }
    private var rangeRecebido: String? = null

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun url(nome: String) = "http://127.0.0.1:${servidor.address.port}/$nome"
    private fun arq(nome: String = "m.gguf", sha: String = sha(dados), bytes: Long = dados.size.toLong()) =
        ArquivoDeModelo(nome, url(nome), sha, bytes)

    @Before fun subir() {
        pasta = Files.createTempDirectory("ia").toFile()
        servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext("/") { ex ->
            rangeRecebido = ex.requestHeaders.getFirst("Range")
            val inicio = rangeRecebido?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            val corpo = dados.copyOfRange(inicio, dados.size)
            ex.sendResponseHeaders(if (rangeRecebido != null) 206 else 200, corpo.size.toLong())
            ex.responseBody.use { it.write(corpo) }
        }
        servidor.start()
    }

    @After fun parar() { servidor.stop(0); pasta.deleteRecursively() }

    @Test fun baixaDoZeroEConfereHash() {
        assertTrue(GerenciadorDeModelo.baixar(arq(), pasta) {})
        assertTrue(File(pasta, "m.gguf").readBytes().contentEquals(dados))
        assertNull(rangeRecebido)
    }

    @Test fun retomaComRangeDepoisDeCortado() {
        File(pasta, "m.gguf.part").writeBytes(dados.copyOfRange(0, 80_000))
        assertTrue(GerenciadorDeModelo.baixar(arq(), pasta) {})
        assertEquals("bytes=80000-", rangeRecebido)
        assertTrue(File(pasta, "m.gguf").readBytes().contentEquals(dados))
        assertFalse(File(pasta, "m.gguf.part").exists())
    }

    @Test fun hashErradoApagaOArquivo() {
        assertFalse(GerenciadorDeModelo.baixar(arq(sha = "0".repeat(64)), pasta) {})
        assertFalse(File(pasta, "m.gguf").exists())
    }

    private fun modelo(vararg a: ArquivoDeModelo, ram: Long = 1L) = ModeloDisponivel(ModoIa.RAPIDA, a.toList(), ram, 1000)

    @Test fun tamanhoDiferenteNaoEstaPronto() {
        File(pasta, "m.gguf").writeBytes(dados.copyOf(dados.size - 1))
        assertNull(GerenciadorDeModelo.caminhoPronto(pasta, modelo(arq())))
        File(pasta, "m.gguf").writeBytes(dados)
        assertNotNull(GerenciadorDeModelo.caminhoPronto(pasta, modelo(arq())))
    }

    @Test fun shardFaltandoNaoEstaPronto() {
        val a = arq("m-1.gguf"); val b = arq("m-2.gguf")
        File(pasta, "m-1.gguf").writeBytes(dados)
        assertNull(GerenciadorDeModelo.caminhoPronto(pasta, modelo(a, b)))
        File(pasta, "m-2.gguf").writeBytes(dados)
        assertEquals(File(pasta, "m-1.gguf").absolutePath, GerenciadorDeModelo.caminhoPronto(pasta, modelo(a, b)))
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
