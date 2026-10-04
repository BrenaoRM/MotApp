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

    /** Pagamentos de fatura (de qualquer cartão) registrados em um mês. */
    @Query("SELECT * FROM transacoes WHERE anoMes = :anoMes AND cartaoId IS NULL AND descricao LIKE 'Pagamento de Fatura - %'")
    suspend fun pagamentosDeFaturaDoMes(anoMes: String): List<Transacao>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTransacao(transacao: Transacao): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTransacoes(transacoes: List<Transacao>)

    @Update
    suspend fun atualizarTransacao(transacao: Transacao)

    @Delete
    suspend fun excluirTransacao(transacao: Transacao)

    @Query("SELECT categoriaId, SUM(valor) as total FROM transacoes WHERE anoMes = :anoMes AND tipo = 'DESPESA' GROUP BY categoriaId")
    fun gastoPorCategoriaNoMes(anoMes: String): Flow<List<GastoCategoria>>

    // ---- Recorrentes / assinaturas ----

    /** Última cobrança já gerada para uma assinatura (a de mês mais recente). */
    @Query("SELECT * FROM transacoes WHERE recorrenteId = :recorrenteId ORDER BY anoMes DESC, data DESC LIMIT 1")
    suspend fun ultimaDaRecorrente(recorrenteId: Long): Transacao?

    /**
     * Quantas cobranças da assinatura já existem no mês. Conta SOMENTE pelo vínculo (recorrenteId):
     * comparar pela descrição fazia uma assinatura nova com o mesmo nome de outra (ou de uma
     * cobrança antiga sem vínculo) ser tratada como "já lançada" e nunca gerar as cobranças.
     */
    @Query("SELECT COUNT(*) FROM transacoes WHERE anoMes = :anoMes AND recorrenteId = :recorrenteId")
    suspend fun contarDaRecorrenteNoMes(recorrenteId: Long, anoMes: String): Int

    /** Apaga todas as compras/cobranças lançadas em um cartão (os pagamentos de fatura não têm cartaoId e ficam). */
    @Query("DELETE FROM transacoes WHERE cartaoId = :cartaoId")
    suspend fun excluirTransacoesDoCartao(cartaoId: Long)

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