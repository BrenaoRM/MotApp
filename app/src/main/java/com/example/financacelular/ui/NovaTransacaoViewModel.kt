package com.example.financacelular.ui

import com.example.financacelular.data.reaisEmCentavos
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.mesDaFaturaDaCompra
import com.example.financacelular.data.Transacao
import com.example.financacelular.deteccao.TransacaoDetectada
import com.example.financacelular.data.chaveDoEstabelecimento
import com.example.financacelular.deteccao.valorParaCampo
import com.example.financacelular.ia.IaFinanceira
import com.example.financacelular.ia.Probabilidade
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Espera depois da última letra digitada antes de pedir a sugestão à IA. */
private const val ATRASO_SUGESTAO_MS = 400L
private const val MINIMO_DE_LETRAS = 3

/**
 * Categoria a pré-selecionar: a escolha da pessoa nunca muda; depois a memória exata do estabelecimento;
 * por último a IA, e só se a categoria sugerida ainda existe em [categorias].
 */
fun escolherSugestao(memoria: Categoria?, ia: Probabilidade?, categorias: List<Categoria>, jaEscolhida: Categoria?): Categoria? =
    jaEscolhida ?: memoria ?: ia?.let { sugestao -> categorias.firstOrNull { it.id == sugestao.categoriaId } }

class NovaTransacaoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository

    init {
        val database = AppDatabase.getInstance(application)
        repository = FinancaRepository.getInstance(database)
        // Idempotente e não bloqueia: a tela pode abrir antes de a MainActivity ter iniciado a IA
        IaFinanceira.iniciar(application)
        // Treina o modelo em segundo plano para a primeira sugestão não esperar (já treinado, não custa nada)
        IaFinanceira.aquecer(TipoTransacao.DESPESA)
    }

    val categorias: StateFlow<List<Categoria>> = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listaCartoes = repository.listarTodosCartoes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Armazena o ID do cartão selecionado no formulário
    var cartaoIdSelecionado by mutableStateOf<Long?>(null)
        private set

    var valor by mutableStateOf("")
        private set

    var data by mutableStateOf(LocalDate.now())
        private set

    var categoriaSelecionada by mutableStateOf<Categoria?>(null)
        private set

    var tipo by mutableStateOf(TipoTransacao.DESPESA)
        private set

    var descricao by mutableStateOf("")
        private set

    /** Ids das categorias do tipo, da mais provável para a menos provável, para a grade; vazio = ordem de sempre. */
    var rankingIa by mutableStateOf<List<Long>>(emptyList())
        private set

    /** Id da categoria que a IA (não a memória, nem a pessoa) pré-selecionou; a grade mostra o ✦ nela. */
    var sugeridaPelaIaId by mutableStateOf<Long?>(null)
        private set

    private var jobSugestao: Job? = null

    var formaPagamento by mutableStateOf(FormaPagamento.DEBITO)
        private set

    var ehParcelado by mutableStateOf(false)
        private set

    var numeroParcelas by mutableStateOf("2")
        private set

    val parcelasInvalidas: Boolean
        get() = ehParcelado && (numeroParcelas.toIntOrNull() ?: 0) !in 2..60

    var ehRecorrente by mutableStateOf(false)
        private set

    var salvando by mutableStateOf(false)
        private set

    // Nome original do estabelecimento vindo da notificação; nunca aparece na tela (a descrição pode ser renomeada)
    private var estabelecimentoDetectado: String? = null

    fun definirTipoInicial(novoTipo: TipoTransacao) {
        tipo = novoTipo
        if (novoTipo == TipoTransacao.RECEITA) {
            formaPagamento = FormaPagamento.DEBITO
            ehParcelado = false
        }
    }

    /** Preenche o que veio da notificação do banco; categoria e cartão ficam para a pessoa escolher. */
    fun aplicarDetectada(detectada: TransacaoDetectada) {
        estabelecimentoDetectado = detectada.descricao
        definirTipoInicial(detectada.tipo)
        valor = detectada.valorParaCampo()
        descricao = detectada.descricao.orEmpty()
        data = LocalDate.now()
        if (detectada.tipo == TipoTransacao.DESPESA) onFormaPagamentoChange(detectada.formaPagamento)
        sugerirCategoria(descricao, detectada.tipo, atrasoMs = 0, usarMemoria = detectada.descricao != null)
    }

    /**
     * Pré-seleciona a categoria da memória do estabelecimento ou, sem memória, a da IA, se a pessoa ainda não
     * escolheu. Roda fora da thread principal; um pedido novo cancela o anterior.
     */
    private fun sugerirCategoria(texto: String, tipoAlvo: TipoTransacao, atrasoMs: Long, usarMemoria: Boolean) {
        jobSugestao?.cancel()
        jobSugestao = viewModelScope.launch {
            if (atrasoMs > 0) delay(atrasoMs)
            val comLetras = chaveDoEstabelecimento(texto).count { it.isLetter() } >= MINIMO_DE_LETRAS
            if (!usarMemoria && !comLetras) {
                rankingIa = emptyList()
                limparPreSelecaoDaIa()
                return@launch
            }
            var memoria: Categoria? = null
            var ia: Probabilidade? = null
            var ranking = emptyList<Long>()
            coroutineScope {
                if (usarMemoria) launch { memoria = repository.sugerirCategoria(texto, tipoAlvo) }
                if (comLetras) {
                    launch { ia = IaFinanceira.sugerir(texto, tipoAlvo) }
                    launch { ranking = IaFinanceira.ranking(texto, tipoAlvo) }
                }
            }
            // A descrição ou o tipo mudou enquanto esperava: esta resposta é velha
            if (tipo != tipoAlvo || descricao != texto) return@launch
            rankingIa = ranking
            // O que a própria IA pôs antes não é escolha da pessoa e pode ser trocado
            val atual = categoriaSelecionada
            val daPessoa = atual?.takeIf { it.id != sugeridaPelaIaId }
            val escolhida = escolherSugestao(memoria, ia, categorias.value, daPessoa)
            if (daPessoa == null && escolhida != null) {
                categoriaSelecionada = escolhida
                sugeridaPelaIaId = if (memoria == null) escolhida.id else null
            } else if (daPessoa == null && escolhida == null) {
                limparPreSelecaoDaIa()
            }
        }
    }

    /** Tira a pré-seleção (e o ✦) só se a categoria atual ainda é a que a IA pôs; escolha da pessoa nunca sai. */
    private fun limparPreSelecaoDaIa() {
        val idDaIa = sugeridaPelaIaId ?: return
        if (categoriaSelecionada?.id == idDaIa) categoriaSelecionada = null
        sugeridaPelaIaId = null
    }

    fun onValorChange(novoValor: String) {
        valor = novoValor
    }

    fun onDataChange(novaData: LocalDate) {
        data = novaData
    }

    fun onCategoriaChange(categoria: Categoria?) {
        categoriaSelecionada = categoria
        sugeridaPelaIaId = null
    }

    fun onDescricaoChange(novaDescricao: String) {
        descricao = novaDescricao
        sugerirCategoria(novaDescricao, tipo, ATRASO_SUGESTAO_MS, usarMemoria = false)
    }

    fun onCartaoSelecionado(id: Long) {
        cartaoIdSelecionado = id
    }

    fun onFormaPagamentoChange(novaForma: FormaPagamento) {
        formaPagamento = novaForma
        // Só o crédito parcela; a recorrência vale no débito também
        if (novaForma != FormaPagamento.CARTAO_CREDITO) ehParcelado = false
    }

    fun onParceladoChange(parcelado: Boolean) {
        ehParcelado = parcelado
        if (parcelado) ehRecorrente = false
    }

    fun onNumeroParcelasChange(parcelas: String) {
        numeroParcelas = parcelas.filter { it.isDigit() }.take(2)
    }

    fun onRecorrenteChange(recorrente: Boolean) {
        ehRecorrente = recorrente
        if (recorrente) ehParcelado = false
    }

    fun salvar(
        nomeCategoriaDigitada: String,
        iconeNovaCategoria: String? = null,
        aoErro: (String) -> Unit = {},
        aoSalvarComSucesso: () -> Unit
    ) {
        if (salvando) return
        val valorNumerico = valor.replace(".", "").replace(',', '.').toDoubleOrNull() ?: 0.0
        if (valorNumerico <= 0.0) {
            aoErro("Informe o valor")
            return
        }
        if (parcelasInvalidas) return

        val ehCredito = formaPagamento == FormaPagamento.CARTAO_CREDITO
        if (ehCredito && cartaoIdSelecionado == null) {
            aoErro("Escolha um cartão")
            return
        }

        salvando = true
        viewModelScope.launch {
            try {
                // Compra no crédito sempre precisa de um cartão válido, senão ficaria fora de qualquer fatura
                val cartaoIdAsLong = if (ehCredito) cartaoIdSelecionado else null
                val cartao = if (cartaoIdAsLong != null) repository.obterCartaoSync(cartaoIdAsLong) else null
                if (ehCredito && cartao == null) {
                    aoErro("Cartão não encontrado")
                    return@launch
                }

                var catId = categoriaSelecionada?.id
                var nomeCategoriaFinal = categoriaSelecionada?.nome
                if (catId == null && nomeCategoriaDigitada.isNotBlank()) {
                    val nomeTratado = nomeCategoriaDigitada.trim()
                    val novaCat = Categoria(nome = nomeTratado, tipo = tipo, icone = iconeNovaCategoria)
                    catId = repository.salvarCategoria(novaCat)
                    nomeCategoriaFinal = nomeTratado
                }
                if (catId == null) {
                    aoErro("Escolha uma categoria")
                    return@launch
                }
                val fallbackTipo = if (tipo == TipoTransacao.RECEITA) "Ganho" else "Gasto"
                val nomeFinal = descricao.ifBlank { nomeCategoriaFinal ?: fallbackTipo }

                val mesFatura = if (ehCredito) {
                    mesDaFaturaDaCompra(data, cartao?.diaFechamento ?: 31)
                } else {
                    YearMonth.from(data)
                }
                val anoBase = mesFatura.year
                val mesBase = mesFatura.monthValue
                val anoMesFormatado = String.format(Locale.ROOT, "%04d-%02d", anoBase, mesBase)

                if (ehRecorrente) {
                    repository.salvarAssinaturaCartao(
                        nome = nomeFinal,
                        valor = valorNumerico,
                        categoriaId = catId,
                        diaDoMes = data.dayOfMonth,
                        cartaoId = cartaoIdAsLong,
                        tipo = tipo,
                        // A cobrança fica na data escolhida; o gerador põe na fatura pelo fechamento
                        dataInicio = data,
                        formaPagamento = formaPagamento
                    )
                } else if (formaPagamento == FormaPagamento.CARTAO_CREDITO && ehParcelado) {
                    val totalParcelasInt = numeroParcelas.toIntOrNull() ?: 1
                    repository.salvarCompraParcelada(
                        descricaoBase = nomeFinal,
                        valorTotalOuParcela = valorNumerico,
                        tipoCalculo = "TOTAL",
                        numeroParcelas = totalParcelasInt,
                        categoriaId = catId,
                        cartaoId = cartaoIdAsLong,
                        diaVencimento = cartao?.diaVencimento ?: data.dayOfMonth,
                        anoInicio = anoBase,
                        mesInicio = mesBase,
                        dataCompra = data
                    )
                } else {
                    val transacao = Transacao(
                        valorCentavos = reaisEmCentavos(valorNumerico),
                        data = data,
                        categoriaId = catId,
                        tipo = tipo,
                        descricao = nomeFinal,
                        formaPagamento = formaPagamento,
                        cartaoId = cartaoIdAsLong,
                        anoMes = anoMesFormatado,
                        numeroParcela = 1,
                        totalParcelas = 1,
                        grupoParcelamentoId = null
                    )
                    repository.salvarTransacao(transacao)
                }
                estabelecimentoDetectado?.let { repository.lembrarCategoria(it, tipo, catId) }
                // O repositório já invalidou o modelo; treina de novo agora para a próxima tela não esperar
                IaFinanceira.aquecer(tipo)
                aoSalvarComSucesso()
            } finally {
                salvando = false
            }
        }
    }
}