package com.example.financacelular.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class GastoCategoria(val categoriaId: Long, val totalCentavos: Long) {
    val total: Double get() = centavosEmReais(totalCentavos)
}

/** Quantos registros usam cada categoria (lançamentos ou recorrências). */
data class ContagemPorCategoria(val categoriaId: Long, val total: Int)

@Dao
interface TransacaoDao {

    @Query("SELECT * FROM transacoes WHERE anoMes = :anoMes ORDER BY data DESC")
    fun listarTransacoesPorMes(anoMes: String): Flow<List<Transacao>>

    @Query("SELECT * FROM transacoes ORDER BY data DESC")
    fun listarTransacoes(): Flow<List<Transacao>>

    @Query("SELECT * FROM transacoes WHERE data = :data")
    suspend fun listarTransacoesDoDia(data: LocalDate): List<Transacao>

    @Query("SELECT * FROM transacoes WHERE cartaoId = :cartaoId AND anoMes = :anoMes")
    fun listarTransacoesFatura(cartaoId: Long, anoMes: String): Flow<List<Transacao>>

    /** Pagamentos de fatura (de qualquer cartão) registrados em um mês. */
    @Query("SELECT * FROM transacoes WHERE anoMes = :anoMes AND pagaFaturaDoCartaoId IS NOT NULL")
    suspend fun pagamentosDeFaturaDoMes(anoMes: String): List<Transacao>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTransacao(transacao: Transacao): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTransacoes(transacoes: List<Transacao>)

    @Update
    suspend fun atualizarTransacao(transacao: Transacao)

    @Delete
    suspend fun excluirTransacao(transacao: Transacao)

    @Query("SELECT categoriaId, SUM(valorCentavos) as totalCentavos FROM transacoes WHERE anoMes = :anoMes AND tipo = 'DESPESA' GROUP BY categoriaId")
    fun gastoPorCategoriaNoMes(anoMes: String): Flow<List<GastoCategoria>>

    @Query("SELECT categoriaId, COUNT(*) AS total FROM transacoes GROUP BY categoriaId")
    fun contarPorCategoria(): Flow<List<ContagemPorCategoria>>

    @Query("DELETE FROM transacoes WHERE categoriaId = :categoriaId")
    suspend fun excluirPorCategoria(categoriaId: Long)

    @Query("UPDATE transacoes SET categoriaId = :para WHERE categoriaId = :de")
    suspend fun moverPorCategoria(de: Long, para: Long)

    // ---- Recorrentes / assinaturas ----

    /**
     * Última cobrança já gerada para uma assinatura (a de data mais recente). Ordena pela data, não pelo
     * anoMes: no cartão o anoMes é o mês da fatura e pode ser o mês seguinte ao da cobrança.
     */
    @Query("SELECT * FROM transacoes WHERE recorrenteId = :recorrenteId ORDER BY data DESC LIMIT 1")
    suspend fun ultimaDaRecorrente(recorrenteId: Long): Transacao?

    /**
     * Quantas cobranças da assinatura têm a data no mês calendário [mes] ("yyyy-MM"; a data é gravada como
     * "yyyy-MM-dd"). Não usa o anoMes: o mês da fatura pode coincidir em dois meses seguidos quando o dia é
     * limitado ao fim do mês (fevereiro). Conta SOMENTE pelo vínculo (recorrenteId): comparar pela descrição
     * fazia uma assinatura nova com o mesmo nome de outra ser tratada como "já lançada".
     */
    @Query("SELECT COUNT(*) FROM transacoes WHERE recorrenteId = :recorrenteId AND substr(data, 1, 7) = :mes")
    suspend fun contarDaRecorrenteComDataNoMes(recorrenteId: Long, mes: String): Int

    /** Cobranças de assinatura lançadas em cartão (para o reparo do anoMes da fatura). */
    @Query("SELECT * FROM transacoes WHERE recorrenteId IS NOT NULL AND cartaoId IS NOT NULL AND data >= :hoje")
    suspend fun cobrancasDeRecorrenteNoCartaoDesde(hoje: LocalDate): List<Transacao>

    /** Pagamentos de fatura de qualquer mês (para saber quais faturas estão pagas). */
    @Query("SELECT * FROM transacoes WHERE pagaFaturaDoCartaoId IS NOT NULL")
    suspend fun todosPagamentosDeFatura(): List<Transacao>

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