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

    // Histórico ordenado do mês mais recente pro mais antigo, ignorando limites zerados
// (usado pra "herdar" o orçamento do mês anterior quando o mês atual não tem um definido)
    @Query("SELECT * FROM orcamentos WHERE anoMes <= :anoMes AND valorLimite > 0 ORDER BY anoMes DESC")
    fun listarHistoricoAteMes(anoMes: String): Flow<List<Orcamento>>
}

