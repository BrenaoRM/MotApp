package com.example.financacelular.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AfazerDao {
    @Query("SELECT * FROM afazeres")
    fun listarTodos(): Flow<List<AfazerEntity>>

    @Insert
    suspend fun inserir(afazer: AfazerEntity)

    @Update
    suspend fun atualizar(afazer: AfazerEntity)

    @Delete
    suspend fun excluir(afazer: AfazerEntity)
}