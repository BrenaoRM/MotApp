package com.example.financacelular.ui

import com.example.financacelular.data.somaEmReais
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.LilasRecorrente
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.cores
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun RecorrenteScreen(
    viewModel: RecorrenteViewModel = viewModel(),
    estadoLista: LazyListState
) {
    val recorrentesCarregados by viewModel.recorrentes.collectAsStateWithLifecycle()
    val recorrentes = recorrentesCarregados.orEmpty()
    val cartoes by viewModel.cartoes.collectAsStateWithLifecycle()
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(LocalePtBr) }

    val receitasRecorrentes = recorrentes.filter { it.tipo == TipoTransacao.RECEITA }
    val despesasRecorrentes = recorrentes.filter { it.tipo == TipoTransacao.DESPESA }
    val totalCobrancas = despesasRecorrentes.somaEmReais { it.valorCentavos }
    val totalGanhosFixos = receitasRecorrentes.somaEmReais { it.valorCentavos }

    // A lista mostra um tipo por vez, como as Categorias da Início; o padrão é Cobranças
    var tipoLista by rememberSaveable { mutableStateOf(TipoTransacao.DESPESA) }
    val itensDoTipo = if (tipoLista == TipoTransacao.DESPESA) despesasRecorrentes else receitasRecorrentes

    // Detalhe em sheet; guarda só o id e busca na lista atual
    var detalheId by remember { mutableStateOf<Long?>(null) }
    var confirmandoCancelamento by remember { mutableStateOf(false) }
    val itemDetalhe = detalheId?.let { id -> recorrentes.find { it.id == id } }

    // Assinaturas antigas sem cartão gravado são cobradas no primeiro cartão (mesma regra do app)
    fun nomeDoCartao(item: DespesaRecorrente): String? {
        if (item.tipo == TipoTransacao.RECEITA) return null
        if (item.formaPagamento == FormaPagamento.DEBITO) return "Débito"
        return (cartoes.find { it.id == item.cartaoId } ?: cartoes.firstOrNull())?.nome
    }

    ListaDePagina(estadoLista) {
        item {
            CartaoHeroDePagina(LilasRecorrente) {
                Text(
                    "Cobranças por mês",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                ValorDoHero(formatoMoeda.format(totalCobrancas))
                if (totalGanhosFixos > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ArrowUpward,
                            contentDescription = null,
                            tint = MaterialTheme.cores.textoGanho,
                            modifier = Modifier.size(Tamanho.iconePequeno)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                                    append(formatoMoeda.format(totalGanhosFixos))
                                }
                                append(" de ganhos fixos")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Tamanho.toque),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recorrências",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (recorrentes.isNotEmpty()) {
                    SeletorQuadradoIcones(
                        opcoes = listOf(
                            OpcaoDeIcone(Icons.Outlined.ArrowDownward, "Cobranças", MaterialTheme.cores.textoGasto),
                            OpcaoDeIcone(Icons.Outlined.ArrowUpward, "Ganhos", MaterialTheme.cores.textoGanho)
                        ),
                        indiceSelecionado = if (tipoLista == TipoTransacao.DESPESA) 0 else 1,
                        aoSelecionar = { tipoLista = if (it == 0) TipoTransacao.DESPESA else TipoTransacao.RECEITA }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recorrentesCarregados?.isEmpty() == true) {
            item(key = "vazio") {
                EstadoVazio(
                    icone = Icons.Outlined.Repeat,
                    mensagem = "Nenhum item recorrente cadastrado.",
                    modifier = Modifier.animateItem()
                )
            }
        } else if (recorrentes.isNotEmpty()) {
            item(key = "recorrencias") {
                CartaoUnicoDeLinhas {
                    if (itensDoTipo.isEmpty()) {
                        Text(
                            if (tipoLista == TipoTransacao.DESPESA) "Nenhuma cobrança recorrente" else "Nenhum ganho recorrente",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 22.dp)
                        )
                    }
                    itensDoTipo.forEachIndexed { indice, item ->
                        if (indice > 0) DivisorDeLinha()
                        val cartao = nomeDoCartao(item)
                        LinhaDePagina(
                            inicio = {
                                Icon(
                                    Icons.Outlined.Repeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(Tamanho.icone)
                                )
                            },
                            titulo = item.nome,
                            valor = formatoMoeda.format(item.valor),
                            subtitulo = "Todo dia ${item.diaDoMes}" + (cartao?.let { " · $it" } ?: ""),
                            onClick = {
                                confirmandoCancelamento = false
                                detalheId = item.id
                            }
                        )
                    }
                }
            }
        }
    }

    // ---------------- SHEET: DETALHE DA RECORRÊNCIA (a confirmação troca o conteúdo do mesmo sheet) ----------------
    itemDetalhe?.let { item ->
        val fechar = { detalheId = null; confirmandoCancelamento = false }
        FolhaApp(onDismiss = fechar) {
            AnimatedContent(
                targetState = confirmandoCancelamento,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) using SizeTransform() },
                label = "confirmarCancelamentoRecorrencia"
            ) { confirmando ->
                if (confirmando) {
                    ConfirmarAcaoConteudo(
                        titulo = "Cancelar recorrência?",
                        texto = "\"${item.nome}\" deixará de ser lançado nos próximos meses.",
                        textoConfirmar = "Cancelar recorrência",
                        onConfirmar = {
                            viewModel.excluir(item)
                            fechar()
                        },
                        onCancelar = { confirmandoCancelamento = false }
                    )
                } else {
                    DetalheDaRecorrencia(
                        item = item,
                        cartaoNome = nomeDoCartao(item),
                        formatoMoeda = formatoMoeda,
                        aoFechar = fechar,
                        aoCancelar = { confirmandoCancelamento = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetalheDaRecorrencia(
    item: DespesaRecorrente,
    cartaoNome: String?,
    formatoMoeda: NumberFormat,
    aoFechar: () -> Unit,
    aoCancelar: () -> Unit
) {
    val ehReceita = item.tipo == TipoTransacao.RECEITA
    val hoje = remember { LocalDate.now() }

    val tempoAtivo = remember(item.dataCriacao) {
        val dias = ChronoUnit.DAYS.between(item.dataCriacao, hoje)
        val meses = ChronoUnit.MONTHS.between(item.dataCriacao, hoje)
        val anos = ChronoUnit.YEARS.between(item.dataCriacao, hoje)
        when {
            anos > 0 -> if (anos == 1L) "1 ano" else "$anos anos"
            meses > 0 -> if (meses == 1L) "1 mês" else "$meses meses"
            dias > 0 -> if (dias == 1L) "1 dia" else "$dias dias"
            else -> "hoje"
        }
    }
    val mesesCobrados = remember(item.dataCriacao) {
        maxOf(1, ChronoUnit.MONTHS.between(item.dataCriacao, hoje) + 1)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        CabecalhoDeFolha(
            titulo = item.nome,
            aoFechar = aoFechar,
            subtitulo = if (ehReceita) "Receita fixa mensal" else "Cobrança mensal"
        )
        Spacer(modifier = Modifier.height(Espaco.lg))
        ValorDoHero(formatoMoeda.format(item.valor))
        Text(
            "por mês",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Espaco.md))

        LinhaDeDetalhe("Dia do mês", "Todo dia ${item.diaDoMes}")
        LinhaDeDetalhe("Ativa há", tempoAtivo)
        LinhaDeDetalhe(
            if (ehReceita) "Total já recebido (estimado)" else "Total já pago (estimado)",
            formatoMoeda.format(item.valor * mesesCobrados)
        )
        if (cartaoNome != null) {
            LinhaDeDetalhe(if (item.formaPagamento == FormaPagamento.DEBITO) "Pagamento" else "Cartão", cartaoNome)
        }

        Spacer(modifier = Modifier.height(Espaco.xl))
        BotaoSecundario(
            texto = "Cancelar recorrência",
            onClick = aoCancelar,
            modifier = Modifier.fillMaxWidth(),
            cor = MaterialTheme.cores.textoGasto,
        )
        Spacer(modifier = Modifier.height(Espaco.lg))
    }
}
