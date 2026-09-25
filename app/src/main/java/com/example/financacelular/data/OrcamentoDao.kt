package com.example.financacelular.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OrcamentoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun definir(orcamento: Orcamento)

    @Delete
    suspend fun excluir(orcamento: Orcamento)

    @Query("SELECT * FROM orcamentos WHERE anoMes = :anoMes")
    fun listarDoMes(anoMes: String): Flow<List<Orcamento>>
}
