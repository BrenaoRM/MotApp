package com.example.financacelular.ui

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
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class NovaTransacaoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository

    init {
        val database = AppDatabase.getInstance(application)
        repository = FinancaRepository.getInstance(database)
    }

    val categorias: StateFlow<List<Categoria>> = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    var ehRecorrente by mutableStateOf(false)
        private set

    fun definirTipoInicial(novoTipo: TipoTransacao) {
        tipo = novoTipo
        if (novoTipo == TipoTransacao.RECEITA) {
            formaPagamento = FormaPagamento.DEBITO
            ehParcelado = false
        }
    }

    fun onValorChange(novoValor: String) {
        valor = novoValor.replace(',', '.')
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
        numeroParcelas = parcelas
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
                onError()
            }
        }
    }

    fun salvar(nomeCategoriaDigitada: String, aoSalvarComSucesso: () -> Unit) {
        val valorNumerico = valor.toDoubleOrNull() ?: 0.0
        if (valorNumerico <= 0.0) return

        viewModelScope.launch {
            var catId = categoriaSelecionada?.id
            var nomeCategoriaFinal = categoriaSelecionada?.nome

            if (catId == null && nomeCategoriaDigitada.isNotBlank()) {
                val nomeTratado = nomeCategoriaDigitada.trim()
                val novaCat = Categoria(nome = nomeTratado, tipo = tipo)
                catId = repository.salvarCategoria(novaCat)
                nomeCategoriaFinal = nomeTratado
            }
            if (catId == null) return@launch

            val fallbackTipo = if (tipo == TipoTransacao.RECEITA) "Receita" else "Despesa"
            val nomeFinal = descricao.ifBlank { nomeCategoriaFinal ?: fallbackTipo }

            val cartaoIdAsLong = if (formaPagamento == FormaPagamento.CARTAO_CREDITO) 1L else null

            // --- LÓGICA DE FECHAMENTO DE FATURA ---
            val cartao = if (cartaoIdAsLong != null) repository.obterCartaoSync(cartaoIdAsLong) else null
            val diaFechamento = cartao?.diaFechamento ?: 31 // Fallback caso não encontre

            // Joga a data de cobrança para o mês seguinte se a compra for feita do dia do fechamento em diante
            val dataFaturaAjustada = if (formaPagamento == FormaPagamento.CARTAO_CREDITO && data.dayOfMonth >= diaFechamento) {
                data.plusMonths(1)
            } else {
                data
            }

            val anoBase = dataFaturaAjustada.year
            val mesBase = dataFaturaAjustada.monthValue
            val anoMesFormatado = String.format("%04d-%02d", anoBase, mesBase)
            // --------------------------------------

            if (ehRecorrente) {
                repository.salvarAssinaturaCartao(
                    nome = nomeFinal,
                    valor = valorNumerico,
                    categoriaId = catId,
                    diaDoMes = data.dayOfMonth,
                    cartaoId = cartaoIdAsLong,
                    tipo = tipo,
                    dataInicio = dataFaturaAjustada // A recorrência começará no mês ajustado
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
                    anoInicio = anoBase, // Passamos o ano ajustado
                    mesInicio = mesBase  // Passamos o mês ajustado
                )
            } else {
                val transacao = Transacao(
                    valor = valorNumerico,
                    data = data, // Mantém o dia exato em que a compra ocorreu
                    categoriaId = catId,
                    tipo = tipo,
                    descricao = nomeFinal,
                    formaPagamento = formaPagamento,
                    cartaoId = cartaoIdAsLong,
                    anoMes = anoMesFormatado, // Debita na fatura do mês ajustado
                    numeroParcela = 1,
                    totalParcelas = 1,
                    grupoParcelamentoId = null
                )
                repository.salvarTransacao(transacao)
            }

            aoSalvarComSucesso()
        }
    }
}