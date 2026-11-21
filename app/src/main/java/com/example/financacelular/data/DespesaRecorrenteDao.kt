package com.example.financacelular.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DespesaRecorrenteDao {
    @Insert
    suspend fun inserir(despesa: DespesaRecorrente): Long

    @Delete
    suspend fun excluir(despesa: DespesaRecorrente)

    @Query("SELECT categoriaId, COUNT(*) AS total FROM despesas_recorrentes GROUP BY categoriaId")
    fun contarPorCategoria(): Flow<List<ContagemPorCategoria>>

    @Query("DELETE FROM despesas_recorrentes WHERE categoriaId = :categoriaId")
    suspend fun excluirPorCategoria(categoriaId: Long)

    @Query("UPDATE despesas_recorrentes SET categoriaId = :para WHERE categoriaId = :de")
    suspend fun moverPorCategoria(de: Long, para: Long)

    /** Apaga todas as assinaturas cobradas em um cartão (usado ao excluir o cartão). */
    @Query("DELETE FROM despesas_recorrentes WHERE cartaoId = :cartaoId")
    suspend fun excluirDoCartao(cartaoId: Long)

    @Query("SELECT * FROM despesas_recorrentes ORDER BY diaDoMes")
    fun listarTodas(): Flow<List<DespesaRecorrente>>

    @Query("SELECT * FROM despesas_recorrentes")
    suspend fun listarTodasSync(): List<DespesaRecorrente>
}