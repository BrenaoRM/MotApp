package com.example.financacelular.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class GastoCategoria(val categoriaId: Long, val total: Double)
data class TotalMensal(val anoMes: String, val totalReceitas: Double, val totalDespesas: Double)

@Dao
interface TransacaoDao {

    @Query("SELECT * FROM transacoes WHERE anoMes = :anoMes ORDER BY data DESC")
    fun listarTransacoesPorMes(anoMes: String): Flow<List<Transacao>>

    @Query("SELECT * FROM transacoes ORDER BY data DESC")
    fun listarTransacoes(): Flow<List<Transacao>>

    @Query("SELECT * FROM transacoes WHERE anoMes = :anoMes AND (formaPagamento != 'CARTAO_CREDITO' OR cartaoId IS NULL) ORDER BY data DESC")
    fun listarTransacoesDashboard(anoMes: String): Flow<List<Transacao>>

    @Query("SELECT * FROM transacoes WHERE cartaoId = :cartaoId AND anoMes = :anoMes")
    fun listarTransacoesFatura(cartaoId: Long, anoMes: String): Flow<List<Transacao>>

    @Query("SELECT * FROM transacoes WHERE cartaoId = :cartaoId AND anoMes = :anoMes")
    fun transacoesCartaoNoMes(cartaoId: Long, anoMes: String): Flow<List<Transacao>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTransacao(transacao: Transacao): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTransacoes(transacoes: List<Transacao>)

    @Update
    suspend fun atualizarTransacao(transacao: Transacao)

    @Delete
    suspend fun excluirTransacao(transacao: Transacao)

    @Query("SELECT SUM(valor) FROM transacoes WHERE anoMes = :anoMes AND tipo = 'DESPESA' AND (formaPagamento != 'CARTAO_CREDITO' OR cartaoId IS NULL)")
    fun totalDespesasDoMes(anoMes: String): Flow<Double?>

    @Query("SELECT SUM(valor) FROM transacoes WHERE anoMes = :anoMes AND tipo = 'RECEITA'")
    fun totalReceitasDoMes(anoMes: String): Flow<Double?>

    @Query("SELECT SUM(valor) FROM transacoes WHERE cartaoId = :cartaoId AND anoMes = :anoMes")
    fun totalCartaoNoMes(cartaoId: Long, anoMes: String): Flow<Double?>

    @Query("SELECT categoriaId, SUM(valor) as total FROM transacoes WHERE anoMes = :anoMes AND tipo = 'DESPESA' GROUP BY categoriaId")
    fun gastoPorCategoriaNoMes(anoMes: String): Flow<List<GastoCategoria>>

    @Query("SELECT anoMes, SUM(CASE WHEN tipo = 'RECEITA' THEN valor ELSE 0.0 END) as totalReceitas, SUM(CASE WHEN tipo = 'DESPESA' THEN valor ELSE 0.0 END) as totalDespesas FROM transacoes GROUP BY anoMes ORDER BY anoMes DESC LIMIT :limite")
    fun evolucaoMensal(limite: Int): Flow<List<TotalMensal>>
}