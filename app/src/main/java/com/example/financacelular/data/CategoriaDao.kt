package com.example.financacelular.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriaDao {
    @Insert
    suspend fun inserir(categoria: Categoria): Long

    @Update
    suspend fun atualizar(categoria: Categoria)

    @Delete
    suspend fun excluir(categoria: Categoria)

    @Query("SELECT * FROM categorias ORDER BY nome")
    fun listarTodas(): Flow<List<Categoria>>

    @Query("SELECT * FROM categorias WHERE tipo = :tipo ORDER BY nome")
    fun listarPorTipo(tipo: TipoTransacao): Flow<List<Categoria>>
}
