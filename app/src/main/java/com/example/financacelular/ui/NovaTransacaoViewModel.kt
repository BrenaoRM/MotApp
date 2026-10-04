package com.example.financacelular.ui

import android.app.Application
import android.util.Log
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
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class NovaTransacaoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository

    init {
        val database = AppDatabase.getInstance(application)
        repository = FinancaRepository.getInstance(database)
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

    fun definirTipoInicial(novoTipo: TipoTransacao) {
        tipo = novoTipo
        if (novoTipo == TipoTransacao.RECEITA) {
            formaPagamento = FormaPagamento.DEBITO
            ehParcelado = false
        }
    }

    fun onValorChange(novoValor: String) {
        valor = novoValor
    }

    fun onDataChange(novaData: LocalDate) {
        data = novaData
    }

    fun onCategoriaChange(categoria: Categoria?) {
        categoriaSelecionada = categoria
    }

    fun onDescricaoChange(novaDescricao: String) {
        descricao = novaDescricao
    }

    fun onCartaoSelecionado(id: Long) {
        cartaoIdSelecionado = id
    }

    fun onFormaPagamentoChange(novaForma: FormaPagamento) {
        formaPagamento = novaForma
        if (novaForma != FormaPagamento.CARTAO_CREDITO) {
            ehRecorrente = false
            ehParcelado = false
        }
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

    fun excluirCategoria(categoria: Categoria, onError: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.excluirCategoria(categoria)
            } catch (e: Exception) {
                Log.e("NovaTransacaoViewModel", "Erro ao excluir categoria", e)
                onError()
            }
        }
    }

    fun salvar(
        nomeCategoriaDigitada: String,
        aoErro: (String) -> Unit = {},
        aoSalvarComSucesso: () -> Unit
    ) {
        if (salvando) return
        val valorNumerico = valor.replace(".", "").replace(',', '.').toDoubleOrNull() ?: 0.0
        if (valorNumerico <= 0.0) return
        if (parcelasInvalidas) return

        val ehCredito = formaPagamento == FormaPagamento.CARTAO_CREDITO
        if (ehCredito && cartaoIdSelecionado == null) {
            aoErro("Cadastre e selecione um cartão para lançar no crédito.")
            return
        }

        salvando = true
        viewModelScope.launch {
            try {
                // Compra no crédito sempre precisa de um cartão válido, senão ficaria fora de qualquer fatura
                val cartaoIdAsLong = if (ehCredito) cartaoIdSelecionado else null
                val cartao = if (cartaoIdAsLong != null) repository.obterCartaoSync(cartaoIdAsLong) else null
                if (ehCredito && cartao == null) {
                    aoErro("O cartão selecionado não foi encontrado. Escolha outro cartão.")
                    return@launch
                }

                var catId = categoriaSelecionada?.id
                var nomeCategoriaFinal = categoriaSelecionada?.nome
                if (catId == null && nomeCategoriaDigitada.isNotBlank()) {
                    val nomeTratado = nomeCategoriaDigitada.trim()
                    val novaCat = Categoria(nome = nomeTratado, tipo = tipo)
                    catId = repository.salvarCategoria(novaCat)
                    nomeCategoriaFinal = nomeTratado
                }
                if (catId == null) {
                    salvando = false
                    return@launch
                }
                val fallbackTipo = if (tipo == TipoTransacao.RECEITA) "Receita" else "Despesa"
                val nomeFinal = descricao.ifBlank { nomeCategoriaFinal ?: fallbackTipo }

                // Em meses curtos o cartão fecha no último dia (ex.: fechamento dia 31 em fevereiro)
                val diaFechamento = minOf(cartao?.diaFechamento ?: 31, data.lengthOfMonth())

                val dataFaturaAjustada = if (ehCredito && data.dayOfMonth >= diaFechamento) {
                    data.plusMonths(1)
                } else {
                    data
                }
                val anoBase = dataFaturaAjustada.year
                val mesBase = dataFaturaAjustada.monthValue
                val anoMesFormatado = String.format(Locale.ROOT, "%04d-%02d", anoBase, mesBase)

                if (ehRecorrente) {
                    repository.salvarAssinaturaCartao(
                        nome = nomeFinal,
                        valor = valorNumerico,
                        categoriaId = catId,
                        diaDoMes = data.dayOfMonth,
                        cartaoId = cartaoIdAsLong,
                        tipo = tipo,
                        dataInicio = dataFaturaAjustada
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
                        mesInicio = mesBase
                    )
                } else {
                    val transacao = Transacao(
                        valor = valorNumerico,
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
                aoSalvarComSucesso()
            } finally {
                salvando = false
            }
        }
    }
}