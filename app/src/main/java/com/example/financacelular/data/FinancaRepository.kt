package com.example.financacelular.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID

private const val MESES_A_FRENTE = 12L

/**
 * Data em que o pagamento de uma fatura aparece no calendário/extrato.
 * - Fatura paga dentro do próprio mês dela (mês da fatura ou mês do vencimento): vale o dia em que foi paga.
 * - Fatura de outro mês paga fora do mês dela: vale o dia de vencimento da fatura.
 * Se o cartão não for encontrado, usa o dia em que foi paga.
 */
internal fun dataDoPagamentoDeFatura(cartao: CartaoEntity?, anoMes: String, hoje: LocalDate): LocalDate {
    if (cartao == null) return hoje
    val mesDaFatura = runCatching { YearMonth.parse(anoMes) }.getOrNull() ?: return hoje
    val vencimento = calcularVencimentoFatura(mesDaFatura, cartao.diaVencimento, cartao.diaFechamento)
    val mesDeHoje = YearMonth.from(hoje)
    val pagouNoMesDela = mesDeHoje == mesDaFatura || mesDeHoje == YearMonth.from(vencimento)
    return if (pagouNoMesDela) hoje else vencimento
}

/**
 * Divide [total] em [n] parcelas em centavos, de modo que a soma seja exatamente o total.
 * A primeira parcela absorve o resto da divisão (ex.: 100,00 em 3x = 33,34 / 33,33 / 33,33).
 */
internal fun dividirEmParcelas(total: Double, n: Int): List<Double> {
    if (n <= 0) return emptyList()
    val centavos = Math.round(total * 100)
    val base = centavos / n
    val resto = centavos % n
    return List(n) { i -> (if (i == 0) base + resto else base) / 100.0 }
}

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
    private val estabelecimentoDao = database.estabelecimentoCategoriaDao()

    // ------------------------------------------------------------------
    // Cartões
    // ------------------------------------------------------------------
    fun obterCartao(id: Long): Flow<CartaoEntity?> = cartaoDao.obterPorId(id)
    suspend fun obterCartaoSync(id: Long): CartaoEntity? = cartaoDao.obterPorIdSync(id)
    suspend fun salvarCartao(cartao: CartaoEntity) { cartaoDao.inserir(cartao) }

    /**
     * Exclui o cartão junto com as compras/parcelas/assinaturas lançadas nele (ficariam órfãs,
     * fora de qualquer fatura). Pagamentos de fatura já registrados no extrato são mantidos.
     */
    suspend fun excluirCartao(cartao: CartaoEntity) {
        database.withTransaction {
            dao.excluirTransacoesDoCartao(cartao.id)
            despesaRecorrenteDao.excluirDoCartao(cartao.id)
            cartaoDao.excluir(cartao)
        }
    }
    fun listarTodosCartoes(): Flow<List<CartaoEntity>> = cartaoDao.listarTodos()
    suspend fun listarTodosCartoesSync(): List<CartaoEntity> = cartaoDao.listarTodosSync()

    // ------------------------------------------------------------------
    // Transações
    // ------------------------------------------------------------------
    fun listarTransacoes(): Flow<List<Transacao>> =
        dao.listarTransacoes()

    suspend fun listarTransacoesDoDia(data: LocalDate): List<Transacao> =
        dao.listarTransacoesDoDia(data)

    fun listarTransacoesFatura(cartaoId: Long, anoMes: String): Flow<List<Transacao>> =
        dao.listarTransacoesFatura(cartaoId, anoMes)

    suspend fun salvarTransacao(transacao: Transacao) {
        dao.inserirTransacao(transacao)
    }

    suspend fun atualizarTransacao(transacao: Transacao) {
        dao.atualizarTransacao(transacao)
    }

    suspend fun excluirTransacao(transacao: Transacao) {
        dao.excluirTransacao(transacao)
    }

    suspend fun excluirTransacoes(transacoes: List<Transacao>) {
        database.withTransaction {
            transacoes.forEach { dao.excluirTransacao(it) }
        }
    }

    // ------------------------------------------------------------------
    // Gestão de Faturas (Suporte a múltiplos cartões)
    // ------------------------------------------------------------------
    fun verificarFaturaPagaCartao(cartaoId: Long, anoMes: String): Flow<Boolean> =
        dao.listarTransacoesPorMes(anoMes).map { lista ->
            lista.any { it.faturaQuitadaPorEstePagamento() == FaturaChave(cartaoId, anoMes) }
        }

    suspend fun pagarFaturaCartao(cartaoId: Long, anoMes: String, valorTotal: Double) {
        database.withTransaction<Unit> {
            // Já existe pagamento deste cartão neste mês? Então não registra outro (evita toque duplo).
            val jaPaga = dao.pagamentosDeFaturaDoMes(anoMes).any {
                it.faturaQuitadaPorEstePagamento() == FaturaChave(cartaoId, anoMes)
            }
            if (jaPaga) return@withTransaction

            val categorias = categoriaDao.listarTodasSync()
            val categoriaFatura = categorias.find { it.nome.equals("Fatura", ignoreCase = true) }

            val catId = if (categoriaFatura != null) {
                categoriaFatura.id
            } else {
                categoriaDao.inserir(Categoria(nome = "Fatura", tipo = TipoTransacao.DESPESA))
            }

            val dataPagamento = dataDoPagamentoDeFatura(
                cartao = cartaoDao.obterPorIdSync(cartaoId),
                anoMes = anoMes,
                hoje = LocalDate.now()
            )

            dao.inserirTransacao(
                Transacao(
                    valor = valorTotal,
                    data = dataPagamento,
                    categoriaId = catId,
                    tipo = TipoTransacao.DESPESA,
                    descricao = descricaoPagamentoFatura(cartaoId, anoMes),
                    formaPagamento = FormaPagamento.DINHEIRO,
                    cartaoId = null,
                    anoMes = anoMes,
                    numeroParcela = 1,
                    totalParcelas = 1
                )
            )
        }
    }

    suspend fun cancelarPagamentoFaturaCartao(cartaoId: Long, anoMes: String) {
        database.withTransaction {
            dao.pagamentosDeFaturaDoMes(anoMes)
                .filter { it.faturaQuitadaPorEstePagamento() == FaturaChave(cartaoId, anoMes) }
                .forEach { dao.excluirTransacao(it) }
        }
    }

    // ------------------------------------------------------------------
    // Compras Parceladas
    // ------------------------------------------------------------------
    suspend fun salvarCompraParcelada(
        descricaoBase: String,
        valorTotalOuParcela: Double,
        tipoCalculo: String,
        numeroParcelas: Int,
        categoriaId: Long,
        cartaoId: Long?,
        diaVencimento: Int,
        anoInicio: Int,
        mesInicio: Int,
        dataCompra: LocalDate
    ) {
        val valoresParcelas =
            if (tipoCalculo == "TOTAL") dividirEmParcelas(valorTotalOuParcela, numeroParcelas)
            else List(numeroParcelas) { valorTotalOuParcela }
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
                    valor = valoresParcelas[i - 1],
                    data = dataTransacao,
                    categoriaId = categoriaId,
                    tipo = TipoTransacao.DESPESA,
                    descricao = "$descricaoBase ($i/$numeroParcelas)",
                    formaPagamento = FormaPagamento.CARTAO_CREDITO,
                    cartaoId = cartaoId,
                    anoMes = anoMesStr,
                    numeroParcela = i,
                    totalParcelas = numeroParcelas,
                    grupoParcelamentoId = grupoId,
                    dataCompra = dataCompra
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
    // Assinaturas / Recorrentes
    // ------------------------------------------------------------------
    private fun descricaoDaRecorrente(recorrente: DespesaRecorrente): String {
        val sufixo = if (recorrente.tipo == TipoTransacao.RECEITA) "(Recorrente)" else "(Assinatura)"
        return "${recorrente.nome} $sufixo"
    }

    private suspend fun gerarMeses(
        recorrente: DespesaRecorrente,
        formaPagamento: FormaPagamento,
        cartaoId: Long?,
        de: YearMonth,
        ate: YearMonth
    ) {
        val descricao = descricaoDaRecorrente(recorrente)
        val novas = mutableListOf<Transacao>()
        // No cartão, a cobrança entra na fatura pelo fechamento, igual a uma compra avulsa
        val diaFechamento = cartaoId?.let { cartaoDao.obterPorIdSync(it)?.diaFechamento }

        var mes = de
        while (!mes.isAfter(ate)) {
            val mesDaData = String.format(Locale.ROOT, "%04d-%02d", mes.year, mes.monthValue)

            if (dao.contarDaRecorrenteComDataNoMes(recorrente.id, mesDaData) == 0) {
                val dia = minOf(recorrente.diaDoMes, mes.lengthOfMonth())
                val data = mes.atDay(dia)
                val anoMes = anoMesDaCobrancaRecorrente(data, diaFechamento)
                novas.add(
                    Transacao(
                        valor = recorrente.valor,
                        data = data,
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
        dataInicio: LocalDate = LocalDate.now(),
        formaPagamento: FormaPagamento = FormaPagamento.CARTAO_CREDITO
    ) {
        // Receita e despesa no débito entram direto no saldo; só a despesa no crédito vai para uma fatura
        val semCartao = tipo == TipoTransacao.RECEITA || formaPagamento == FormaPagamento.DEBITO
        require(semCartao || cartaoId != null) { "Assinatura no cartão exige um cartão selecionado" }
        val formaPag = if (semCartao) FormaPagamento.DEBITO else FormaPagamento.CARTAO_CREDITO
        val cId = if (semCartao) null else cartaoId

        database.withTransaction {
            val recorrente = DespesaRecorrente(
                nome = nome,
                valor = valor,
                categoriaId = categoriaId,
                diaDoMes = diaDoMes,
                tipo = tipo,
                dataCriacao = dataInicio,
                cartaoId = cId,
                formaPagamento = formaPag
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

    suspend fun gerarRecorrentesPendentes() {
        database.withTransaction {
            val agora = YearMonth.now()
            val limite = agora.plusMonths(MESES_A_FRENTE)
            val cartoes = cartaoDao.listarTodosSync()
            val primeiroCartaoId = cartoes.firstOrNull()?.id
            repararAnoMesDasRecorrentesNoCartao(cartoes)

            for (recorrente in despesaRecorrenteDao.listarTodasSync()) {
                val ultima = dao.ultimaDaRecorrente(recorrente.id)
                // Mês da data da cobrança (no cartão o anoMes é o da fatura, que pode ser o seguinte)
                val mesDaUltima = ultima?.let { YearMonth.from(it.data) }

                // Sem nenhuma cobrança (todas excluídas à mão): recomeça no mês atual, ou no de início se for futuro
                val inicio = maxOf(agora, mesDaUltima?.plusMonths(1) ?: YearMonth.from(recorrente.dataCriacao))
                if (inicio.isAfter(limite)) continue

                val semCartao = recorrente.semCartao
                // Cartão da própria assinatura; se não houver, o da última cobrança; por fim o primeiro cartão.
                // Sem nenhum cartão, não gera cobrança (ficaria órfã, fora de qualquer fatura).
                val cartaoDaAssinatura = if (semCartao) null
                else (recorrente.cartaoId ?: ultima?.cartaoId ?: primeiroCartaoId ?: continue)
                gerarMeses(
                    recorrente = recorrente,
                    formaPagamento = if (semCartao) FormaPagamento.DEBITO else FormaPagamento.CARTAO_CREDITO,
                    cartaoId = cartaoDaAssinatura,
                    de = inicio,
                    ate = limite
                )
            }
        }
    }

    /**
     * Acerta o anoMes das cobranças futuras de assinatura no cartão para a fatura pelo fechamento atual
     * (as antigas eram gravadas no mês da data). Idempotente; não toca em lançamento passado nem em
     * fatura paga. Também corrige quando o usuário muda o dia de fechamento do cartão.
     */
    private suspend fun repararAnoMesDasRecorrentesNoCartao(cartoes: List<CartaoEntity>) {
        val hoje = LocalDate.now()
        val corrigidas = recorrentesNoCartaoComAnoMesErrado(
            transacoes = dao.cobrancasDeRecorrenteNoCartaoDesde(hoje),
            diaFechamentoPorCartao = cartoes.associate { it.id to it.diaFechamento },
            pagas = conjuntoFaturasPagas(dao.todosPagamentosDeFatura()),
            hoje = hoje
        )
        for (t in corrigidas) dao.atualizarTransacao(t)
    }

    suspend fun excluirRecorrente(despesa: DespesaRecorrente) {
        database.withTransaction {
            val descricoes = listOf("${despesa.nome} (Assinatura)", "${despesa.nome} (Recorrente)")
            dao.excluirFuturasDaRecorrente(despesa.id, descricoes, LocalDate.now())
            despesaRecorrenteDao.excluir(despesa)
        }
    }

    // ------------------------------------------------------------------
    // Demais entidades
    // ------------------------------------------------------------------
    fun listarAfazeres(): Flow<List<AfazerEntity>> = afazerDao.listarTodos()
    suspend fun salvarAfazer(afazer: AfazerEntity) = afazerDao.inserir(afazer)
    suspend fun atualizarAfazer(afazer: AfazerEntity) = afazerDao.atualizar(afazer)
    suspend fun excluirAfazer(afazer: AfazerEntity) = afazerDao.excluir(afazer)

    fun listarCategorias(): Flow<List<Categoria>> = categoriaDao.listarTodas()
    fun listarCategoriasPorTipo(tipo: TipoTransacao): Flow<List<Categoria>> = categoriaDao.listarPorTipo(tipo)
    suspend fun salvarCategoria(categoria: Categoria): Long = categoriaDao.inserir(categoria)
    suspend fun atualizarCategoria(categoria: Categoria) = categoriaDao.atualizar(categoria)

    fun contarTransacoesPorCategoria(): Flow<List<ContagemPorCategoria>> = dao.contarPorCategoria()
    fun contarRecorrentesPorCategoria(): Flow<List<ContagemPorCategoria>> = despesaRecorrenteDao.contarPorCategoria()

    /**
     * Exclui a categoria junto com tudo que depende dela: os lançamentos e as recorrências que a usam
     * (as chaves estrangeiras impedem apagar só a categoria) e, em cascata, os limites de orçamento.
     * Tudo numa transação: se algo falhar, nada é apagado.
     */
    suspend fun excluirCategoriaComDados(categoria: Categoria) = database.withTransaction {
        dao.excluirPorCategoria(categoria.id)
        despesaRecorrenteDao.excluirPorCategoria(categoria.id)
        estabelecimentoDao.excluirPorCategoria(categoria.id)
        categoriaDao.excluir(categoria)
    }

    /**
     * Passa os lançamentos, as recorrências e a categoria lembrada por estabelecimento para [destino] e só então
     * exclui [categoria]. O limite de orçamento da categoria excluída é descartado (cascata); o do destino fica.
     * Tudo numa transação.
     */
    suspend fun moverEExcluirCategoria(categoria: Categoria, destino: Categoria) = database.withTransaction {
        dao.moverPorCategoria(categoria.id, destino.id)
        despesaRecorrenteDao.moverPorCategoria(categoria.id, destino.id)
        estabelecimentoDao.moverPorCategoria(categoria.id, destino.id)
        categoriaDao.excluir(categoria)
    }

    /** Categoria usada da última vez para este estabelecimento detectado (null se nunca vista ou já excluída). */
    suspend fun sugerirCategoria(estabelecimento: String, tipo: TipoTransacao): Categoria? {
        val chave = chaveDoEstabelecimento(estabelecimento)
        if (chave.isEmpty()) return null
        val id = estabelecimentoDao.buscarCategoria(chave, tipo) ?: return null
        return categoriaDao.listarTodasSync().firstOrNull { it.id == id && it.tipo == tipo }
    }

    /** Guarda, sem mostrar a ninguém, a categoria escolhida para o estabelecimento detectado. */
    suspend fun lembrarCategoria(estabelecimento: String, tipo: TipoTransacao, categoriaId: Long) {
        val chave = chaveDoEstabelecimento(estabelecimento)
        if (chave.isNotEmpty()) estabelecimentoDao.definir(EstabelecimentoCategoria(chave, tipo, categoriaId))
    }

    fun listarMetas(): Flow<List<Meta>> = metaDao.listarTodas()
    suspend fun salvarMeta(meta: Meta) = metaDao.inserir(meta)
    suspend fun atualizarMeta(meta: Meta) = metaDao.atualizar(meta)
    suspend fun excluirMeta(meta: Meta) = metaDao.excluir(meta)

    fun listarRecorrentes(): Flow<List<DespesaRecorrente>> = despesaRecorrenteDao.listarTodas()

    fun listarHistoricoOrcamentos(anoMes: String): Flow<List<Orcamento>> = orcamentoDao.listarHistoricoAteMes(anoMes)
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

        /** Banco em que INSTANCE foi criada: um repositório sobre um banco já fechado não pode ser reaproveitado. */
        @Volatile
        private var BANCO: AppDatabase? = null

        fun getInstance(database: AppDatabase): FinancaRepository {
            val atual = INSTANCE
            if (atual != null && BANCO === database) return atual
            return synchronized(this) {
                val existente = INSTANCE
                if (existente != null && BANCO === database) {
                    existente
                } else {
                    FinancaRepository(database).also {
                        INSTANCE = it
                        BANCO = database
                    }
                }
            }
        }

        fun destruirInstancia() {
            INSTANCE = null
            BANCO = null
        }
    }
}