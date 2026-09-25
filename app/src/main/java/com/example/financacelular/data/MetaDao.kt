package com.example.financacelular.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MetaDao {
    @Insert
    suspend fun inserir(meta: Meta): Long

    @Update
    suspend fun atualizar(meta: Meta)

    @Delete
    suspend fun excluir(meta: Meta)

    @Query("SELECT * FROM metas ORDER BY id DESC")
    fun listarTodas(): Flow<List<Meta>>
}
