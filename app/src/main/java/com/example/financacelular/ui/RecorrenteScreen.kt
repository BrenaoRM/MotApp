package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.LilasRecorrente
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun RecorrenteScreen(
    viewModel: RecorrenteViewModel = viewModel(),
    aoVoltar: (() -> Unit)? = null
) {
    val recorrentesCarregados by viewModel.recorrentes.collectAsState()
    val recorrentes = recorrentesCarregados.orEmpty()
    val cartoes by viewModel.cartoes.collectAsState()
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build()) }

    val receitasRecorrentes = recorrentes.filter { it.tipo == TipoTransacao.RECEITA }
    val despesasRecorrentes = recorrentes.filter { it.tipo == TipoTransacao.DESPESA }
    val totalCobrancas = despesasRecorrentes.sumOf { it.valor }
    val totalGanhosFixos = receitasRecorrentes.sumOf { it.valor }

    // Assinaturas antigas sem cartão gravado são cobradas no primeiro cartão (mesma regra do app)
    fun nomeDoCartao(item: DespesaRecorrente): String? {
        if (item.tipo == TipoTransacao.RECEITA) return null
        return (cartoes.find { it.id == item.cartaoId } ?: cartoes.firstOrNull())?.nome
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen),
        contentPadding = PaddingValues(top = 16.dp, bottom = espacoParaBarraFlutuante())
    ) {
        item {
            CabecalhoPadrao("Recorrentes", aoVoltar = aoVoltar)
            Spacer(modifier = Modifier.height(16.dp))

            CartaoHeroDePagina(LilasRecorrente) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "COBRANÇAS POR MÊS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    PilulaDePagina(if (recorrentes.size == 1) "1 item" else "${recorrentes.size} itens")
                }
                Spacer(modifier = Modifier.height(6.dp))
                ValorDoHero(formatoMoeda.format(totalCobrancas))
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IndicadorDePagina("GANHOS FIXOS", formatoMoeda.format(totalGanhosFixos), Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f))
                    IndicadorDePagina("ASSINATURAS", "${despesasRecorrentes.size}", Icons.Filled.Repeat, Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (recorrentesCarregados?.isEmpty() == true) {
            item(key = "vazio") {
                EstadoVazio(
                    icone = Icons.Filled.Repeat,
                    mensagem = "Nenhum item recorrente cadastrado.",
                    modifier = Modifier.animateItem()
                )
            }
        }

        if (receitasRecorrentes.isNotEmpty()) {
            item {
                Text(
                    "Ganhos Recorrentes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.cores.textoGanho
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(receitasRecorrentes, key = { it.id }) { item ->
                ItemRecorrenteCard(
                    modifier = Modifier.animateItem(),
                    item = item,
                    formatoMoeda = formatoMoeda,
                    corTema = Verde,
                    subtitulo = "Receita fixa mensal",
                    onCancelar = { viewModel.excluir(item) }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (despesasRecorrentes.isNotEmpty()) {
            item {
                Text(
                    "Cobranças e Assinaturas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.cores.textoGasto
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(despesasRecorrentes, key = { it.id }) { item ->
                ItemRecorrenteCard(
                    modifier = Modifier.animateItem(),
                    item = item,
                    formatoMoeda = formatoMoeda,
                    corTema = Coral,
                    subtitulo = "Cobrança fixa recorrente",
                    cartaoNome = nomeDoCartao(item),
                    onCancelar = { viewModel.excluir(item) }
                )
            }
        }
    }
}

@Composable
fun ItemRecorrenteCard(
    item: DespesaRecorrente,
    formatoMoeda: NumberFormat,
    corTema: Color,
    subtitulo: String,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
    cartaoNome: String? = null
) {
    var expandido by rememberSaveable { mutableStateOf(false) }
    var confirmarExclusao by rememberSaveable { mutableStateOf(false) }
    val corTexto = if (item.tipo == TipoTransacao.RECEITA) MaterialTheme.cores.textoGanho else MaterialTheme.cores.textoGasto

    val hoje = remember { LocalDate.now() }

    val tempoAtivoTexto = remember(item.dataCriacao) {
        val dias = ChronoUnit.DAYS.between(item.dataCriacao, hoje)
        val meses = ChronoUnit.MONTHS.between(item.dataCriacao, hoje)
        val anos = ChronoUnit.YEARS.between(item.dataCriacao, hoje)

        when {
            anos > 0 -> if (anos == 1L) "Ativo há 1 ano" else "Ativo há $anos anos"
            meses > 0 -> if (meses == 1L) "Ativo há 1 mês" else "Ativo há $meses meses"
            dias > 0 -> if (dias == 1L) "Ativo há 1 dia" else "Ativo há $dias dias"
            else -> "Ativo hoje"
        }
    }

    val mesesCobrados = remember(item.dataCriacao) {
        val mesesPassados = ChronoUnit.MONTHS.between(item.dataCriacao, hoje)
        maxOf(1, mesesPassados + 1)
    }

    val totalJaPago = item.valor * mesesCobrados

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button) { expandido = !expandido }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(corTema.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.tipo == TipoTransacao.RECEITA) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = corTema,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.nome,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (cartaoNome != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        EtiquetaCartao(nome = cartaoNome)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatoMoeda.format(item.valor),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = corTexto,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Icon(
                        imageVector = if (expandido) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expandido) "Recolher detalhes" else "Expandir detalhes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expandido,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = LilasRecorrente)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dia do vencimento:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Todo dia ${item.diaDoMes}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(tempoAtivoTexto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.cores.textoGanho)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (item.tipo == TipoTransacao.DESPESA) "Total já gasto (estimado):" else "Total já recebido (estimado):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatoMoeda.format(totalJaPago),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = corTexto
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (confirmarExclusao) {
                        ConfirmarAcaoSheet(
                            titulo = "Cancelar recorrência?",
                            texto = "\"${item.nome}\" deixará de ser lançado nos próximos meses.",
                            textoConfirmar = "Cancelar recorrência",
                            onConfirmar = onCancelar,
                            onDismiss = { confirmarExclusao = false }
                        )
                    }

                    BotaoSecundario(
                        texto = "Cancelar Recorrência",
                        onClick = { confirmarExclusao = true },
                        modifier = Modifier.fillMaxWidth(),
                        cor = MaterialTheme.cores.textoGasto,
                        icone = Icons.Filled.DeleteOutline
                    )
                }
            }
        }
    }
}