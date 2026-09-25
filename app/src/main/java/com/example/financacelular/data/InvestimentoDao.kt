package com.example.financacelular.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestimentoDao {
    @Query("SELECT * FROM investimentos")
    fun listarTodos(): Flow<List<InvestimentoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(investimento: InvestimentoEntity)

    @Update
    suspend fun atualizar(investimento: InvestimentoEntity)

    @Delete
    suspend fun deletar(investimento: InvestimentoEntity)
}