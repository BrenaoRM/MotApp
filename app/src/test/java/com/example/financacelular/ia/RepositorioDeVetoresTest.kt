package com.example.financacelular.ia

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.financacelular.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositorioDeVetoresTest {
    private lateinit var banco: AppDatabase

    private class VetorizadorFalso(override val versao: Int = 1) : Vetorizador {
        val chamadas = mutableListOf<List<String>>()
        override fun vetorizar(textos: List<String>): List<FloatArray> {
            chamadas += textos
            return textos.map { floatArrayOf(it.length.toFloat(), 1f) }
        }
    }

    @Before
    fun abrir() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        banco = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun fechar() = banco.close()

    @Test
    fun vetorizaSoOQueFalta() = runBlocking {
        val falso = VetorizadorFalso()
        val repo = RepositorioDeVetores(banco.vetorIaDao(), falso)
        repo.vetores(setOf("mercado", "uber"))
        val segundo = repo.vetores(setOf("mercado", "farmacia"))
        assertEquals(listOf(listOf("mercado", "uber"), listOf("farmacia")), falso.chamadas.map { it.sorted() })
        assertEquals(setOf("mercado", "farmacia"), segundo.keys)
        assertArrayEquals(floatArrayOf(8f, 1f), segundo.getValue("farmacia"), 0f)
    }

    @Test
    fun ignoraVetorDeOutraVersao() = runBlocking {
        banco.vetorIaDao().gravar(listOf(VetorIa("mercado", floatArrayOf(9f).emBytes(), 0)))
        val falso = VetorizadorFalso(versao = 1)
        val resultado = RepositorioDeVetores(banco.vetorIaDao(), falso).vetores(setOf("mercado"))
        assertEquals(listOf(listOf("mercado")), falso.chamadas)
        assertArrayEquals(floatArrayOf(7f, 1f), resultado.getValue("mercado"), 0f)
    }

    @Test
    fun bytesIdaEVolta() {
        val original = floatArrayOf(1f, -0.5f, 3.25f)
        assertArrayEquals(original, original.emBytes().emFloats(), 0f)
    }
}
