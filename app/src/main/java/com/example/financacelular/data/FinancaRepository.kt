package com.example.financacelular.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID

private const val MESES_A_FRENTE = 12L
private const val ID_CARTAO_PADRAO = 1L

@Suppress("unused")
class FinancaRepository(private val database: AppDatabase) {
    private val dao = database.transacaoDao()
    private val categoriaDao = database.categoriaDao()
    private val metaDao = database.metaDao()
    private val despesaRecorrenteDao = database.despesaRecorrenteDao()
    private val orcamentoDao = database.orcamentoDao()
    private val investimentoDao = database.investimentoDao()
    private val afazerDao = database.afazerDao()
    private val cartaoDao = database.cartaoDao()

    fun obterCartao(id: Long): Flow<CartaoEntity?> = cartaoDao.obterPorId(id)
    suspend fun obterCartaoSync(id: Long): CartaoEntity? = cartaoDao.obterPorIdSync(id)
    suspend fun salvarCartao(cartao: CartaoEntity) { cartaoDao.inserir(cartao) }

    fun listarTransacoes(): Flow<List<Transacao>> =
        dao.listarTransacoes()

    fun listarTransacoesFatura(cartaoId: Long, anoMes: String): Flow<List<Transacao>> =
        dao.listarTransacoesFatura(cartaoId, anoMes)

    fun transacoesCartaoNoMes(anoMes: String, cartaoId: Long = 1L): Flow<List<Transacao>> =
        dao.transacoesCartaoNoMes(cartaoId, anoMes)

    fun totalCartaoNoMes(anoMes: String, cartaoId: Long = 1L): Flow<Double> =
        dao.totalCartaoNoMes(cartaoId, anoMes).map { it ?: 0.0 }

    suspend fun salvarTransacao(transacao: Transacao) {
        dao.inserirTransacao(transacao)
    }

    suspend fun atualizarTransacao(transacao: Transacao) {
        dao.atualizarTransacao(transacao)
    }

    suspend fun excluirTransacao(transacao: Transacao) {
        dao.excluirTransacao(transacao)
    }

    /** Exclui várias transações de uma vez: ou apaga todas ou nenhuma. */
    suspend fun excluirTransacoes(transacoes: List<Transacao>) {
        database.withTransaction {
            transacoes.forEach { dao.excluirTransacao(it) }
        }
    }

    suspend fun pagarFatura(anoMes: String, valorTotal: Double) {
        val categorias = categoriaDao.listarTodas().first()
        val categoriaFatura = categorias.find { it.nome.equals("Fatura", ignoreCase = true) }

        val catId = if (categoriaFatura != null) {
            categoriaFatura.id
        } else {
            categoriaDao.inserir(Categoria(nome = "Fatura", tipo = TipoTransacao.DESPESA))
        }

        val transacaoPagamento = Transacao(
            valor = valorTotal,
            data = LocalDate.now(),
            categoriaId = catId,
            tipo = TipoTransacao.DESPESA,
            descricao = "Pagamento de Fatura - $anoMes",
            formaPagamento = FormaPagamento.DINHEIRO,
            cartaoId = null,
            anoMes = anoMes,
            numeroParcela = 1,
            totalParcelas = 1
        )

        dao.inserirTransacao(transacaoPagamento)
    }

    fun verificarFaturaPaga(anoMes: String): Flow<Boolean> =
        dao.listarTransacoesPorMes(anoMes).map { lista ->
            lista.any { it.cartaoId == null && it.descricao == "Pagamento de Fatura - $anoMes" }
        }

    suspend fun cancelarPagamentoFatura(anoMes: String) {
        val lista = dao.listarTransacoesPorMes(anoMes).first()
        val pagamento = lista.find { it.cartaoId == null && it.descricao == "Pagamento de Fatura - $anoMes" }
        if (pagamento != null) {
            dao.excluirTransacao(pagamento)
        }
    }

    suspend fun salvarCompraParcelada(
        descricaoBase: String,
        valorTotalOuParcela: Double,
        tipoCalculo: String,
        numeroParcelas: Int,
        categoriaId: Long,
        cartaoId: Long?,
        diaVencimento: Int,
        anoInicio: Int,
        mesInicio: Int
    ) {
        val valorParcela = if (tipoCalculo == "TOTAL") valorTotalOuParcela / numeroParcelas else valorTotalOuParcela
        // Identifica todas as parcelas desta compra como um grupo
        val grupoId = UUID.randomUUID().toString()
        var anoAtual = anoInicio
        var mesAtual = mesInicio
        val listaTransacoes = mutableListOf<Transacao>()

        for (i in 1..numeroParcelas) {
            val anoMesStr = String.format(Locale.getDefault(), "%d-%02d", anoAtual, mesAtual)
            val diaReal = minOf(diaVencimento, LocalDate.of(anoAtual, mesAtual, 1).lengthOfMonth())
            val dataTransacao = LocalDate.of(anoAtual, mesAtual, diaReal)

            listaTransacoes.add(
                Transacao(
                    valor = valorParcela,
                    data = dataTransacao,
                    categoriaId = categoriaId,
                    tipo = TipoTransacao.DESPESA,
                    descricao = "$descricaoBase ($i/$numeroParcelas)",
                    formaPagamento = FormaPagamento.CARTAO_CREDITO,
                    cartaoId = cartaoId,
                    anoMes = anoMesStr,
                    numeroParcela = i,
                    totalParcelas = numeroParcelas,
                    grupoParcelamentoId = grupoId
                )
            )

            mesAtual++
            if (mesAtual > 12) {
                mesAtual = 1
                anoAtual++
            }
        }
        dao.inserirTransacoes(listaTransacoes)
    }

    // ------------------------------------------------------------------
    // Assinaturas / recorrentes (duram até serem canceladas)
    // ------------------------------------------------------------------

    /** Texto que identifica as cobranças geradas por uma assinatura. */
    private fun descricaoDaRecorrente(recorrente: DespesaRecorrente): String {
        val sufixo = if (recorrente.tipo == TipoTransacao.RECEITA) "(Recorrente)" else "(Assinatura)"
        return "${recorrente.nome} $sufixo"
    }

    /**
     * Cria as cobranças de [de] até [ate] (inclusive) que ainda não existem.
     * Pode ser chamada várias vezes sem duplicar nada.
     */
    private suspend fun gerarMeses(
        recorrente: DespesaRecorrente,
        formaPagamento: FormaPagamento,
        cartaoId: Long?,
        de: YearMonth,
        ate: YearMonth
    ) {
        val descricao = descricaoDaRecorrente(recorrente)
        val novas = mutableListOf<Transacao>()

        var mes = de
        while (!mes.isAfter(ate)) {
            val anoMes = String.format(Locale.ROOT, "%04d-%02d", mes.year, mes.monthValue)

            if (dao.contarDaRecorrenteNoMes(recorrente.id, anoMes, descricao) == 0) {
                val dia = minOf(recorrente.diaDoMes, mes.lengthOfMonth())
                novas.add(
                    Transacao(
                        valor = recorrente.valor,
                        data = mes.atDay(dia),
                        categoriaId = recorrente.categoriaId,
                        tipo = recorrente.tipo,
                        descricao = descricao,
                        formaPagamento = formaPagamento,
                        cartaoId = cartaoId,
                        anoMes = anoMes,
                        numeroParcela = 1,
                        totalParcelas = 1,
                        recorrenteId = recorrente.id
                    )
                )
            }
            mes = mes.plusMonths(1)
        }

        if (novas.isNotEmpty()) dao.inserirTransacoes(novas)
    }

    suspend fun salvarAssinaturaCartao(
        nome: String,
        valor: Double,
        categoriaId: Long,
        diaDoMes: Int,
        cartaoId: Long?,
        tipo: TipoTransacao,
        dataInicio: LocalDate = LocalDate.now()
    ) {
        val isReceita = tipo == TipoTransacao.RECEITA
        val formaPag = if (isReceita) FormaPagamento.DEBITO else FormaPagamento.CARTAO_CREDITO
        val cId = if (isReceita) null else cartaoId

        database.withTransaction {
            val recorrente = DespesaRecorrente(
                nome = nome,
                valor = valor,
                categoriaId = categoriaId,
                diaDoMes = diaDoMes,
                tipo = tipo,
                dataCriacao = dataInicio
            )
            val idGerado = despesaRecorrenteDao.inserir(recorrente)

            val inicio = YearMonth.from(dataInicio)
            val referencia = maxOf(inicio, YearMonth.now())
            gerarMeses(
                recorrente = recorrente.copy(id = idGerado),
                formaPagamento = formaPag,
                cartaoId = cId,
                de = inicio,
                ate = referencia.plusMonths(MESES_A_FRENTE)
            )
        }
    }

    /**
     * Mantém todas as assinaturas com cobranças geradas até 12 meses à frente.
     * Chamar ao abrir o app. Nunca gera meses passados.
     */
    suspend fun gerarRecorrentesPendentes() {
        database.withTransaction {
            val agora = YearMonth.now()
            val limite = agora.plusMonths(MESES_A_FRENTE)

            for (recorrente in despesaRecorrenteDao.listarTodasSync()) {
                val ultima = dao.ultimaDaRecorrente(recorrente.id)
                val mesDaUltima = ultima?.let {
                    runCatching { YearMonth.parse(it.anoMes ?: it.data.toString().take(7)) }.getOrNull()
                }

                // Sem nenhuma cobrança vinculada: começa só no mês que vem
                // (o mês atual continua aparecendo como "Previsto" no Extrato).
                val inicio = maxOf(agora, mesDaUltima?.plusMonths(1) ?: agora.plusMonths(1))
                if (inicio.isAfter(limite)) continue

                val ehReceita = recorrente.tipo == TipoTransacao.RECEITA
                gerarMeses(
                    recorrente = recorrente,
                    formaPagamento = if (ehReceita) FormaPagamento.DEBITO else FormaPagamento.CARTAO_CREDITO,
                    cartaoId = if (ehReceita) null else (ultima?.cartaoId ?: ID_CARTAO_PADRAO),
                    de = inicio,
                    ate = limite
                )
            }
        }
    }

    /** Cancela a assinatura: apaga só as cobranças futuras DELA e para de gerar novas. */
    suspend fun excluirRecorrente(despesa: DespesaRecorrente) {
        database.withTransaction {
            val descricoes = listOf("${despesa.nome} (Assinatura)", "${despesa.nome} (Recorrente)")
            dao.excluirFuturasDaRecorrente(despesa.id, descricoes, LocalDate.now())
            despesaRecorrenteDao.excluir(despesa)
        }
    }

    fun listarAfazeres(): Flow<List<AfazerEntity>> = afazerDao.listarTodos()
    suspend fun salvarAfazer(afazer: AfazerEntity) = afazerDao.inserir(afazer)
    suspend fun atualizarAfazer(afazer: AfazerEntity) = afazerDao.atualizar(afazer)
    suspend fun excluirAfazer(afazer: AfazerEntity) = afazerDao.excluir(afazer)

    fun listarCategorias(): Flow<List<Categoria>> = categoriaDao.listarTodas()
    fun listarCategoriasPorTipo(tipo: TipoTransacao): Flow<List<Categoria>> = categoriaDao.listarPorTipo(tipo)
    suspend fun salvarCategoria(categoria: Categoria): Long = categoriaDao.inserir(categoria)
    suspend fun excluirCategoria(categoria: Categoria) = categoriaDao.excluir(categoria)
    suspend fun atualizarCategoria(categoria: Categoria) = categoriaDao.atualizar(categoria)

    fun listarMetas(): Flow<List<Meta>> = metaDao.listarTodas()
    suspend fun salvarMeta(meta: Meta) = metaDao.inserir(meta)
    suspend fun atualizarMeta(meta: Meta) = metaDao.atualizar(meta)
    suspend fun excluirMeta(meta: Meta) = metaDao.excluir(meta)

    fun listarRecorrentes(): Flow<List<DespesaRecorrente>> = despesaRecorrenteDao.listarTodas()

    fun listarHistoricoOrcamentos(anoMes: String): Flow<List<Orcamento>> = orcamentoDao.listarHistoricoAteMes(anoMes)
    fun listarOrcamentosDoMes(anoMes: String): Flow<List<Orcamento>> = orcamentoDao.listarDoMes(anoMes)
    suspend fun buscarOrcamentoDoMes(categoriaId: Long, anoMes: String): Orcamento? = orcamentoDao.buscarPorCategoriaEMes(categoriaId, anoMes)
    suspend fun definirOrcamento(orcamento: Orcamento) = orcamentoDao.definir(orcamento)

    fun listarInvestimentos(): Flow<List<InvestimentoEntity>> = investimentoDao.listarTodos()
    suspend fun inserirInvestimento(investimento: InvestimentoEntity) = investimentoDao.inserir(investimento)
    suspend fun atualizarInvestimento(investimento: InvestimentoEntity) = investimentoDao.atualizar(investimento)
    suspend fun deletarInvestimento(investimento: InvestimentoEntity) = investimentoDao.deletar(investimento)

    fun gastoPorCategoriaNoMes(anoMes: String): Flow<List<GastoCategoria>> = dao.gastoPorCategoriaNoMes(anoMes)

    companion object {
        @Volatile
        private var INSTANCE: FinancaRepository? = null

        fun getInstance(database: AppDatabase): FinancaRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = FinancaRepository(database)
                INSTANCE = instance
                instance
            }
        }

        fun destruirInstancia() {
            INSTANCE = null
        }
    }
}