package com.example.financacelular.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

private val AmareloInvestimento = Color(0xFFF2A93B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    investimentoViewModel: InvestimentoViewModel = viewModel(),
    aoAbrirMais: () -> Unit = {},
    aoAbrirExtrato: () -> Unit = {},
    aoAbrirInvestimento: () -> Unit = {},
    aoAbrirCartao: () -> Unit = {},
    aoAbrirAssinaturas: () -> Unit = {},
    aoAbrirParcelados: () -> Unit = {},
    aoAbrirOrcamento: () -> Unit = {},
    aoAbrirMetas: () -> Unit = {}
) {
    val totalDespesas by viewModel.totalDespesas.collectAsState()
    val totalReceitas by viewModel.totalReceitas.collectAsState()
    val mesSelecionado by viewModel.mesSelecionado.collectAsState()
    val resumoDoMes by viewModel.resumoDoMes.collectAsState()
    val totalInvestido by viewModel.totalInvestidoDoMes.collectAsState()
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val saldo = totalReceitas - totalDespesas
    var saldoVisivel by remember { mutableStateOf(true) }

    var mostrarFiltros by remember { mutableStateOf(false) }
    var filtroTipo by remember { mutableStateOf<TipoTransacao?>(null) }
    var ordemFiltro by remember { mutableStateOf("VALOR") }

    var categoriaDetalheSelecionada by remember { mutableStateOf<ResumoMovimentacao?>(null) }

    val sheetStateDetalhes = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetStateFiltros = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val alertaFaturaPendente by viewModel.alertaFaturaPendente.collectAsState()

    val nomeMes = remember(mesSelecionado) {
        mesSelecionado.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .replaceFirstChar { it.uppercase() } + " " + mesSelecionado.year
    }

    val resumosFiltrados = remember(resumoDoMes, filtroTipo, ordemFiltro) {
        resumoDoMes.filter {
            filtroTipo == null || it.tipo == filtroTipo
        }.sortedWith(
            if (ordemFiltro == "VALOR") compareByDescending { it.total }
            else compareBy { it.titulo }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = 22.dp
            )
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        nomeMes,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.mesAnterior() }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior")
                    }
                    IconButton(onClick = { viewModel.mesSeguinte() }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo mês")
                    }
                    IconButton(onClick = aoAbrirMais) {
                        Icon(Icons.Filled.Settings, contentDescription = "Mais opções")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PROJEÇÃO DO MÊS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { saldoVisivel = !saldoVisivel }, modifier = Modifier.size(20.dp)) {
                                Icon(
                                    if (saldoVisivel) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "Ocultar valor",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            SeloSaude(saudavel = saldo >= 0)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (saldoVisivel) formatoMoeda.format(saldo) else "R$ ••••••",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LinhaIndicador(
                                "ENTRADAS", formatoMoeda.format(totalReceitas), Verde,
                                Icons.Filled.TrendingUp, Modifier.weight(1f)
                            )
                            LinhaIndicador(
                                "SAÍDAS", formatoMoeda.format(totalDespesas), MaterialTheme.colorScheme.onSurface,
                                Icons.Filled.TrendingDown, Modifier.weight(1f)
                            )
                            LinhaIndicador(
                                "INVESTIDO", formatoMoeda.format(totalInvestido), AmareloInvestimento,
                                Icons.Filled.Savings, Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = aoAbrirExtrato,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ver Extrato Completo", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AtalhoRapidoCard("Fatura", Icons.Filled.CreditCard, Color(0xFF5B8DEF), aoAbrirCartao)
                    AtalhoRapidoCard("Assinaturas", Icons.Filled.Repeat, Color(0xFFB07CE8), aoAbrirAssinaturas)
                    AtalhoRapidoCard("Parcelados", Icons.Filled.ReceiptLong, Color(0xFF2EC4B6), aoAbrirParcelados)
                    AtalhoRapidoCard("Investir", Icons.Filled.Savings, AmareloInvestimento, aoAbrirInvestimento)
                    AtalhoRapidoCard("Orçamento", Icons.Filled.PieChart, Color(0xFFF2A93B), aoAbrirOrcamento)
                    AtalhoRapidoCard("Metas", Icons.Filled.Flag, Verde, aoAbrirMetas)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Resumo do mês por Categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    // Botão de filtro modernizado
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { mostrarFiltros = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = "Filtros",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Filtrar",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (resumosFiltrados.isEmpty()) {
                    Text(
                        "Nenhuma movimentação encontrada com esses filtros.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(resumosFiltrados) { resumo ->
                val nomeCategoria = resumo.titulo
                val cor = if (resumo.tipo == TipoTransacao.DESPESA) Coral else Verde
                val sinal = if (resumo.tipo == TipoTransacao.DESPESA) "- " else "+ "
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { categoriaDetalheSelecionada = resumo }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(cor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                iconeParaCategoria(nomeCategoria),
                                contentDescription = null,
                                tint = cor
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                nomeCategoria,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Toque para ver os lançamentos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "$sinal${formatoMoeda.format(resumo.total)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = cor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- BOTTOM SHEET: DETALHES DA CATEGORIA ---
        categoriaDetalheSelecionada?.let { resumo ->
            val corCategoria = if (resumo.tipo == TipoTransacao.DESPESA) Coral else Verde

            ModalBottomSheet(
                onDismissRequest = { categoriaDetalheSelecionada = null },
                sheetState = sheetStateDetalhes,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(corCategoria.copy(alpha = 0.1f))
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(corCategoria, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        iconeParaCategoria(resumo.titulo),
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = resumo.titulo,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${resumo.transacoes.size} lançamentos",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { categoriaDetalheSelecionada = null },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Fechar")
                            }
                        }
                    }

                    if (resumo.transacoes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum lançamento encontrado.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(resumo.transacoes) { transacao ->
                                val corItem = if (transacao.tipo == TipoTransacao.DESPESA) Coral else Verde
                                val sinalItem = if (transacao.tipo == TipoTransacao.DESPESA) "- " else "+ "

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = transacao.descricao ?: resumo.titulo,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = transacao.data.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "$sinalItem${formatoMoeda.format(transacao.valor)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = corItem
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- BOTTOM SHEET: FILTROS MODERNOS ---
        if (mostrarFiltros) {
            ModalBottomSheet(
                onDismissRequest = { mostrarFiltros = false },
                sheetState = sheetStateFiltros,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
                ) {
                    Text(
                        text = "Opções de Visualização",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Exibir tipo de movimentação", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Segmented Button Customizado (Estilo Pílula)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SegmentedButton(
                            text = "Tudo",
                            selected = filtroTipo == null,
                            onClick = { filtroTipo = null },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedButton(
                            text = "Só Ganhos",
                            selected = filtroTipo == TipoTransacao.RECEITA,
                            onClick = { filtroTipo = TipoTransacao.RECEITA },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedButton(
                            text = "Só Gastos",
                            selected = filtroTipo == TipoTransacao.DESPESA,
                            onClick = { filtroTipo = TipoTransacao.DESPESA },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Ordenar categorias por", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SegmentedButton(
                            text = "Maior Valor",
                            selected = ordemFiltro == "VALOR",
                            onClick = { ordemFiltro = "VALOR" },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedButton(
                            text = "Ordem A-Z",
                            selected = ordemFiltro == "NOME",
                            onClick = { ordemFiltro = "NOME" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { mostrarFiltros = false },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Aplicar Filtros", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        // Pop-up de Alerta de Fatura
        if (alertaFaturaPendente) {
            AlertDialog(
                onDismissRequest = { /* Não faz nada ao clicar fora */ },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text("Fatura Vencendo! ⚠️", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text("Sua fatura do cartão de crédito vence nos próximos dias. Não se esqueça de realizar o pagamento para evitar juros.", style = MaterialTheme.typography.bodyMedium)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.marcarAlertaComoExibido()
                            aoAbrirCartao()
                        }
                    ) { Text("Ver Fatura", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.marcarAlertaComoExibido()
                        }
                    ) { Text("Lembrar depois") }
                }
            )
        }
    }
}

// Componente para o Botão Segmentado (Estilo Pílula)
@Composable
private fun SegmentedButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AtalhoRapidoCard(titulo: String, icone: ImageVector, cor: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .width(100.dp)
            .height(85.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(cor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(18.dp))
            }
            Text(titulo, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SeloSaude(saudavel: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (saudavel) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (saudavel) Verde else Coral,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            if (saudavel) "Saudável" else "Atenção",
            style = MaterialTheme.typography.labelLarge,
            color = if (saudavel) Verde else Coral
        )
    }
}

@Composable
private fun LinhaIndicador(
    titulo: String,
    valor: String,
    cor: Color,
    icone: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                titulo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            color = cor,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}