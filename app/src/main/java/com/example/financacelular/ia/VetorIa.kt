package com.example.financacelular.ia

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Vetor em cache de um texto normalizado ([chave]); [versaoModelo] invalida o cache quando o modelo muda. */
@Entity(tableName = "ia_vetor")
data class VetorIa(
    @PrimaryKey val chave: String,
    val vetor: ByteArray,
    val versaoModelo: Int
)

@Dao
interface VetorIaDao {
    @Query("SELECT * FROM ia_vetor WHERE versaoModelo = :versao AND chave IN (:chaves)")
    suspend fun buscar(chaves: List<String>, versao: Int): List<VetorIa>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun gravar(itens: List<VetorIa>)

    @Query("DELETE FROM ia_vetor WHERE versaoModelo != :versao")
    suspend fun limparOutrasVersoes(versao: Int)
}

fun FloatArray.emBytes(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    forEach { buffer.putFloat(it) }
    return buffer.array()
}

fun ByteArray.emFloats(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buffer.getFloat() }
}

/**
 * Cache de vetores no banco: busca em lote, vetoriza só o que falta e grava. Banco em [io],
 * vetorização (CPU) em [calculo]; nunca roda na thread de quem chamou.
 */
class RepositorioDeVetores(
    private val dao: VetorIaDao,
    private val vetorizador: Vetorizador,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val calculo: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun vetores(textosNormalizados: Collection<String>): Map<String, FloatArray> {
        val chaves = textosNormalizados.distinct()
        if (chaves.isEmpty()) return emptyMap()
        val versao = vetorizador.versao
        val resultado = HashMap<String, FloatArray>(chaves.size)
        // O SQLite limita as variáveis de uma consulta: busca em pedaços
        withContext(io) {
            chaves.chunked(500).forEach { parte ->
                dao.buscar(parte, versao).forEach { resultado[it.chave] = it.vetor.emFloats() }
            }
        }
        val faltam = chaves.filter { it !in resultado }
        if (faltam.isNotEmpty()) {
            val novos = faltam.chunked(TAMANHO_LOTE).flatMap { lote ->
                val vetores = withContext(calculo) { vetorizador.vetorizar(lote) }
                lote.zip(vetores)
            }
            novos.forEach { (chave, vetor) -> resultado[chave] = vetor }
            withContext(io) {
                dao.gravar(novos.map { (chave, vetor) -> VetorIa(chave, vetor.emBytes(), versao) })
            }
        }
        return resultado
    }

    private companion object {
        const val TAMANHO_LOTE = 32
    }
}
