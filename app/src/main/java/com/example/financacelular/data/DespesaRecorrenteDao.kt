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

    /** Apaga todas as assinaturas cobradas em um cartão (usado ao excluir o cartão). */
    @Query("DELETE FROM despesas_recorrentes WHERE cartaoId = :cartaoId")
    suspend fun excluirDoCartao(cartaoId: Long)

    @Query("SELECT * FROM despesas_recorrentes ORDER BY diaDoMes")
    fun listarTodas(): Flow<List<DespesaRecorrente>>

    @Query("SELECT * FROM despesas_recorrentes")
    suspend fun listarTodasSync(): List<DespesaRecorrente>
}