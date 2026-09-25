package com.example.financacelular.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Locale

class FinancaRepository(database: AppDatabase) {
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
                    totalParcelas = numeroParcelas
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

    suspend fun salvarAssinaturaCartao(
        nome: String,
        valor: Double,
        categoriaId: Long,
        diaDoMes: Int,
        cartaoId: Long?,
        tipo: TipoTransacao,
        dataInicio: LocalDate = LocalDate.now()
    ) {
        val recorrente = DespesaRecorrente(
            nome = nome,
            valor = valor,
            categoriaId = categoriaId,
            diaDoMes = diaDoMes,
            tipo = tipo,
            dataCriacao = dataInicio
        )
        despesaRecorrenteDao.inserir(recorrente)

        val isReceita = tipo == TipoTransacao.RECEITA
        val formaPag = if (isReceita) FormaPagamento.DEBITO else FormaPagamento.CARTAO_CREDITO
        val cId = if (isReceita) null else cartaoId
        val descricaoSufixo = if (isReceita) "$nome (Recorrente)" else "$nome (Assinatura)"

        var dataAtual = dataInicio
        val listaTransacoes = mutableListOf<Transacao>()

        repeat(12) {
            val anoMesStr = String.format(Locale.getDefault(), "%d-%02d", dataAtual.year, dataAtual.monthValue)
            val diaReal = minOf(diaDoMes, dataAtual.lengthOfMonth())
            val dataTransacao = LocalDate.of(dataAtual.year, dataAtual.monthValue, diaReal)

            listaTransacoes.add(
                Transacao(
                    valor = valor,
                    data = dataTransacao,
                    categoriaId = categoriaId,
                    tipo = tipo,
                    descricao = descricaoSufixo,
                    formaPagamento = formaPag,
                    cartaoId = cId,
                    anoMes = anoMesStr,
                    numeroParcela = 1,
                    totalParcelas = 1
                )
            )
            dataAtual = dataAtual.plusMonths(1)
        }
        dao.inserirTransacoes(listaTransacoes)
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
    suspend fun excluirRecorrente(despesa: DespesaRecorrente) {
        despesaRecorrenteDao.excluir(despesa)
        val todas = dao.listarTransacoes().first()
        val hoje = LocalDate.now()
        todas.filter { it.descricao?.startsWith(despesa.nome) == true && it.data.isAfter(hoje) }.forEach {
            dao.excluirTransacao(it)
        }
    }

    fun listarOrcamentosDoMes(anoMes: String): Flow<List<Orcamento>> = orcamentoDao.listarDoMes(anoMes)
    suspend fun definirOrcamento(orcamento: Orcamento) = orcamentoDao.definir(orcamento)

    fun listarInvestimentos(): Flow<List<InvestimentoEntity>> = investimentoDao.listarTodos()
    suspend fun inserirInvestimento(investimento: InvestimentoEntity) = investimentoDao.inserir(investimento)
    suspend fun atualizarInvestimento(investimento: InvestimentoEntity) = investimentoDao.atualizar(investimento)
    suspend fun deletarInvestimento(investimento: InvestimentoEntity) = investimentoDao.deletar(investimento)

    fun gastoPorCategoriaNoMes(anoMes: String): Flow<List<GastoCategoria>> = dao.gastoPorCategoriaNoMes(anoMes)

    fun listarTransacoesUmaVez(): List<Transacao> = emptyList()

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