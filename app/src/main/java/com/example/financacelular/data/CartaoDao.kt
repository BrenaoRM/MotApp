package com.example.financacelular.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CartaoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(cartao: CartaoEntity)

    @Delete
    suspend fun excluir(cartao: CartaoEntity)

    @Query("SELECT * FROM cartoes WHERE id = :id")
    fun obterPorId(id: Long): Flow<CartaoEntity?>

    @Query("SELECT * FROM cartoes WHERE id = :id")
    suspend fun obterPorIdSync(id: Long): CartaoEntity?

    @Query("SELECT * FROM cartoes ORDER BY id")
    fun listarTodos(): Flow<List<CartaoEntity>>

    @Query("SELECT * FROM cartoes ORDER BY id")
    suspend fun listarTodosSync(): List<CartaoEntity>
}