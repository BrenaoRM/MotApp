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

    // Busca o registro exato da categoria para o mês selecionado (priorizando o ID mais recente)
    @Query("SELECT * FROM orcamentos WHERE categoriaId = :categoriaId AND anoMes = :anoMes ORDER BY id DESC LIMIT 1")
    suspend fun buscarPorCategoriaEMes(categoriaId: Long, anoMes: String): Orcamento?

    @Query("SELECT * FROM orcamentos WHERE anoMes <= :anoMes ORDER BY anoMes DESC, id DESC")
    fun listarHistoricoAteMes(anoMes: String): Flow<List<Orcamento>>
}