package com.example.financacelular.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

private val AmareloInvestimento = Color(0xFFF2A93B)
private val HeroInicio = Color(0xFF7B5CF5)
private val HeroFim = Color(0xFF3B82F6)
private val HeroNegativoInicio = Color(0xFFA85560)
private val HeroNegativoFim = Color(0xFFA85560)

private val ConsumirExcedenteDeScroll = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

private fun saudacaoPorHorario(): String {
    val hora = java.time.LocalTime.now().hour
    return when {
        hora < 12 -> "Bom dia"
        hora < 18 -> "Boa tarde"
        else -> "Boa noite"
    }
}

private fun formatarDataIso(texto: String): String {
    val partes = texto.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else texto
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    aoAbrirConfiguracoes: () -> Unit = {},
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

    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build()) }
    val saldo = totalReceitas - totalDespesas
    var saldoVisivel by remember { mutableStateOf(true) }

    var mostrarFiltros by remember { mutableStateOf(false) }
    var filtroTipo by remember { mutableStateOf<TipoTransacao?>(null) }
    var ordemFiltro by remember { mutableStateOf("VALOR") }
    val filtrosAtivos = filtroTipo != null || ordemFiltro != "VALOR"

    var categoriaDetalheSelecionada by remember { mutableStateOf<ResumoMovimentacao?>(null) }
    val sheetStateDetalhes = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetStateFiltros = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val alertaFaturaPendente by viewModel.alertaFaturaPendente.collectAsState()
    val nomeUsuario = viewModel.nomeUtilizador
    val saudacao = remember { saudacaoPorHorario() }

    val nomeMes = remember(mesSelecionado) {
        mesSelecionado.month.getDisplayName(TextStyle.FULL, Locale.Builder().setLanguage("pt").setRegion("BR").build())
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

    // Total por tipo (entradas / saídas) para mostrar a participação de cada categoria
    val totaisPorTipo = remember(resumoDoMes) {
        resumoDoMes.groupBy { it.tipo }.mapValues { (_, lista) -> lista.sumOf { it.total } }
    }

    // O cartão principal muda de cor quando o saldo fica negativo
    val corHero1 by animateColorAsState(
        targetValue = if (saldo >= 0) HeroInicio else HeroNegativoInicio,
        animationSpec = tween(600),
        label = "corHero1"
    )
    val corHero2 by animateColorAsState(
        targetValue = if (saldo >= 0) HeroFim else HeroNegativoFim,
        animationSpec = tween(600),
        label = "corHero2"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = MaterialTheme.dimens.paddingScreen),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = espacoParaBarraFlutuante()
            )
        ) {
            item {
                // ---------------- CABEÇALHO ----------------
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            saudacao,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            nomeUsuario,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), CircleShape)
                            .clickable(onClick = aoAbrirConfiguracoes),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Configurações",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                MesSelectorCard(
                    nomeMes = nomeMes,
                    onMesAnterior = { viewModel.mesAnterior() },
                    onMesSeguinte = { viewModel.mesSeguinte() }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ---------------- CARTÃO PRINCIPAL (PROJEÇÃO) ----------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MaterialTheme.dimens.cardCornerRadius))
                        .background(Brush.linearGradient(colors = listOf(corHero1, corHero2)))
                ) {
                    // Círculos decorativos
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 70.dp, y = (-70).dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .align(Alignment.BottomStart)
                            .offset(x = (-50).dp, y = 60.dp)
                            .background(Color.White.copy(alpha = 0.06f), CircleShape)
                    )

                    Column(modifier = Modifier.padding(MaterialTheme.dimens.paddingMedium)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PROJEÇÃO DO MÊS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            IconButton(
                                onClick = { saldoVisivel = !saldoVisivel },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (saldoVisivel) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "Ocultar valor",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            SeloSaude(saudavel = saldo >= 0)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (saldoVisivel) formatoMoeda.format(saldo) else "R$ ••••••",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IndicadorGlass(
                                "ENTRADAS", formatoMoeda.format(totalReceitas),
                                Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f)
                            )
                            IndicadorGlass(
                                "SAÍDAS", formatoMoeda.format(totalDespesas),
                                Icons.AutoMirrored.Filled.TrendingDown, Modifier.weight(1f)
                            )
                            IndicadorGlass(
                                "INVESTIDO", formatoMoeda.format(totalInvestido),
                                Icons.Filled.Savings, Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = aoAbrirExtrato,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ver Extrato Completo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ---------------- ACESSO RÁPIDO ----------------
                Text("Acesso rápido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AtalhoRapidoCard("Fatura", Icons.Filled.CreditCard, Color(0xFF5B8DEF), aoAbrirCartao)
                    AtalhoRapidoCard("Assinaturas", Icons.Filled.Repeat, Color(0xFFB07CE8), aoAbrirAssinaturas)
                    AtalhoRapidoCard("Parcelados", Icons.AutoMirrored.Filled.ReceiptLong, Color(0xFF2EC4B6), aoAbrirParcelados)
                    AtalhoRapidoCard("Investir", Icons.Filled.Savings, AmareloInvestimento, aoAbrirInvestimento)
                    AtalhoRapidoCard("Metas", Icons.Filled.Flag, Color(0xFF8B5CF6), aoAbrirMetas)
                    AtalhoRapidoCard("Orçamento", Icons.Filled.PieChart, Color(0xFF5FB36B), aoAbrirOrcamento)
                }

                Spacer(modifier = Modifier.height(28.dp))

                // ---------------- RESUMO POR CATEGORIA ----------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Resumo por Categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (filtrosAtivos) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                            .clickable { mostrarFiltros = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val corFiltro = if (filtrosAtivos) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = "Filtros",
                                tint = corFiltro,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (filtrosAtivos) "Filtrado" else "Filtrar",
                                color = corFiltro,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (resumosFiltrados.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Nenhuma movimentação encontrada com esses filtros.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }

            // A chave usa o id da categoria: o título não é único (várias categorias podem aparecer como
            // "Sem categoria" enquanto as categorias ainda carregam, ou ter o mesmo nome) e chave repetida derruba o app
            items(resumosFiltrados, key = { "${it.categoriaId}-${it.tipo}" }) { resumo ->
                val nomeCategoria = resumo.titulo
                val cor = if (resumo.tipo == TipoTransacao.DESPESA) Coral else Verde
                val sinal = if (resumo.tipo == TipoTransacao.DESPESA) "- " else "+ "
                val totalDoTipo = totaisPorTipo[resumo.tipo] ?: 0.0
                val fracao = if (totalDoTipo > 0.0) (resumo.total / totalDoTipo).toFloat().coerceIn(0f, 1f) else 0f
                val percentual = (fracao * 100).toInt()
                val rotuloTipo = if (resumo.tipo == TipoTransacao.DESPESA) "das saídas" else "das entradas"

                Card(
                    onClick = { categoriaDetalheSelecionada = resumo },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(cor.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    iconeParaCategoria(nomeCategoria),
                                    contentDescription = null,
                                    tint = cor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    nomeCategoria,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    "$percentual% $rotuloTipo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "$sinal${formatoMoeda.format(resumo.total)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = cor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { fracao },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = cor,
                            trackColor = cor.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }

        // ---------------- SHEET: DETALHES DA CATEGORIA ----------------
        categoriaDetalheSelecionada?.let { resumo ->
            val corCategoria = if (resumo.tipo == TipoTransacao.DESPESA) Coral else Verde
            ModalBottomSheet(
                onDismissRequest = { categoriaDetalheSelecionada = null },
                sheetState = sheetStateDetalhes,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                contentWindowInsets = { WindowInsets.statusBars }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(corCategoria.copy(alpha = 0.16f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(corCategoria, RoundedCornerShape(16.dp)),
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
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (resumo.transacoes.size == 1) "1 lançamento" else "${resumo.transacoes.size} lançamentos",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { categoriaDetalheSelecionada = null },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .nestedScroll(ConsumirExcedenteDeScroll),
                            contentPadding = PaddingValues(
                                start = MaterialTheme.dimens.paddingScreen,
                                end = MaterialTheme.dimens.paddingScreen,
                                top = 4.dp,
                                bottom = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(resumo.transacoes) { transacao ->
                                val corItem = if (transacao.tipo == TipoTransacao.DESPESA) Coral else Verde
                                val sinalItem = if (transacao.tipo == TipoTransacao.DESPESA) "- " else "+ "
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(corItem.copy(alpha = 0.14f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (transacao.tipo == TipoTransacao.DESPESA) Icons.AutoMirrored.Filled.TrendingDown
                                            else Icons.AutoMirrored.Filled.TrendingUp,
                                            contentDescription = null,
                                            tint = corItem,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = transacao.descricao ?: resumo.titulo,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = formatarDataIso(transacao.data.toString()),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$sinalItem${formatoMoeda.format(transacao.valor)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = corItem,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------------- SHEET: FILTROS ----------------
        if (mostrarFiltros) {
            ModalBottomSheet(
                onDismissRequest = { mostrarFiltros = false },
                sheetState = sheetStateFiltros,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                contentWindowInsets = { WindowInsets.statusBars }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.dimens.paddingScreen)
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "Opções de Visualização",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("EXIBIR TIPO DE MOVIMENTAÇÃO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
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
                    Text("ORDENAR CATEGORIAS POR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                            .height(52.dp),
                        shape = RoundedCornerShape(20.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                    ) {
                        Text("Aplicar Filtros", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        if (alertaFaturaPendente) {
            AlertDialog(
                onDismissRequest = { viewModel.marcarAlertaComoExibido() },
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

@Composable
private fun SegmentedButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "fundoSegmento"
    )
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
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
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun AtalhoRapidoCard(titulo: String, icone: ImageVector, cor: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cor.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, cor.copy(alpha = 0.2f)),
        modifier = Modifier
            .width(104.dp)
            .height(92.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(cor, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                titulo,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SeloSaude(saudavel: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (saudavel) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            if (saudavel) "Saudável" else "Atenção",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun IndicadorGlass(
    titulo: String,
    valor: String,
    icone: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                titulo,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}