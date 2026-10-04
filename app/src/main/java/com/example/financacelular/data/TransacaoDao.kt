package com.example.financacelular.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class GastoCategoria(val categoriaId: Long, val total: Double)
data class TotalMensal(val anoMes: String, val totalReceitas: Double, val totalDespesas: Double)

@Dao
interface TransacaoDao {

    @Query("SELECT * FROM transacoes WHERE anoMes = :anoMes ORDER BY data DESC")
    fun listarTransacoesPorMes(anoMes: String): Flow<List<Transacao>>

    @Query("SELECT * FROM transacoes ORDER BY data DESC")
    fun listarTransacoes(): Flow<List<Transacao>>

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

    @Query("SELECT SUM(valor) FROM transacoes WHERE cartaoId = :cartaoId AND anoMes = :anoMes")
    fun totalCartaoNoMes(cartaoId: Long, anoMes: String): Flow<Double?>

    @Query("SELECT categoriaId, SUM(valor) as total FROM transacoes WHERE anoMes = :anoMes AND tipo = 'DESPESA' GROUP BY categoriaId")
    fun gastoPorCategoriaNoMes(anoMes: String): Flow<List<GastoCategoria>>

    // ---- Recorrentes / assinaturas ----

    /** Última cobrança já gerada para uma assinatura (a de mês mais recente). */
    @Query("SELECT * FROM transacoes WHERE recorrenteId = :recorrenteId ORDER BY anoMes DESC, data DESC LIMIT 1")
    suspend fun ultimaDaRecorrente(recorrenteId: Long): Transacao?

    /** Quantas cobranças da assinatura já existem no mês (por vínculo ou pela descrição exata). */
    @Query("SELECT COUNT(*) FROM transacoes WHERE anoMes = :anoMes AND (recorrenteId = :recorrenteId OR descricao = :descricao)")
    suspend fun contarDaRecorrenteNoMes(recorrenteId: Long, anoMes: String, descricao: String): Int

    /**
     * Apaga as cobranças futuras de uma assinatura: as vinculadas por ID e, para dados
     * antigos sem vínculo, as que têm exatamente a descrição gerada pela assinatura.
     */
    @Query(
        "DELETE FROM transacoes WHERE data > :hoje AND " +
                "(recorrenteId = :recorrenteId OR (recorrenteId IS NULL AND descricao IN (:descricoes)))"
    )
    suspend fun excluirFuturasDaRecorrente(recorrenteId: Long, descricoes: List<String>, hoje: LocalDate)
}