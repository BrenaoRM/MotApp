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

    @Query("SELECT * FROM despesas_recorrentes ORDER BY diaDoMes")
    fun listarTodas(): Flow<List<DespesaRecorrente>>
}
