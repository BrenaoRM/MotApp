package com.example.financacelular.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

// Mesmas cores do cartão principal da home
private val RoxoAnalise = Color(0xFF7B5CF5)
private val AzulAnalise = Color(0xFF3B82F6)
private val AmareloInvestimento = Color(0xFFF2A93B)
private val AlturaGrafico = 130.dp

private val CoresCategoria = listOf(
    Coral,
    Color(0xFF5B8DEF),
    Color(0xFFB07CE8),
    Color(0xFFF2A93B),
    Verde,
    Color(0xFF2EC4B6)
)
private val CoresCategoriaGanhos = listOf(
    Verde,
    Color(0xFF2EC4B6),
    Color(0xFF81C784),
    Color(0xFF00B4D8),
    Color(0xFF4CAF50),
    Color(0xFF009688)
)

private val LocalePtBr: Locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()
private val MesesAbreviados = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

private fun formatarEixoY(valor: Double): String {
    if (valor == 0.0) return "0"
    return when {
        valor >= 1_000_000 -> String.format(LocalePtBr, "%.1fM", valor / 1_000_000)
        valor >= 1_000 -> String.format(LocalePtBr, "%.1fk", valor / 1_000)
        else -> String.format(LocalePtBr, "%.0f", valor)
    }
}

/** "2026-10" -> "Out" (se o formato for outro, cai para os dois últimos caracteres). */
private fun rotuloMes(anoMes: String): String {
    val mes = anoMes.takeLast(2).toIntOrNull()
    return if (mes != null && mes in 1..12) MesesAbreviados[mes - 1] else anoMes.takeLast(2)
}

/** "2026-10-03" -> "03/10/2026" (sem depender do tipo exato da data). */
private fun formatarDataAnalise(texto: String): String {
    val partes = texto.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else texto
}

@Composable
fun AnaliseScreen(
    viewModel: AnaliseViewModel = viewModel()
) {
    val despesasPorCategoria by viewModel.despesasPorCategoria.collectAsState()
    val receitasPorCategoria by viewModel.receitasPorCategoria.collectAsState()
    val evolucaoMensal by viewModel.evolucaoMensal.collectAsState()
    val topDespesas by viewModel.topDespesas.collectAsState()
    val evolucaoInvestimentos by viewModel.evolucaoInvestimentos.collectAsState()
    val mesSelecionado by viewModel.mesSelecionado.collectAsState()
    val totalDespesasMes by viewModel.totalDespesasMes.collectAsState()
    val totalReceitasMes by viewModel.totalReceitasMes.collectAsState()

    val formato = remember { NumberFormat.getCurrencyInstance(LocalePtBr) }
    val saldoMes = totalReceitasMes - totalDespesasMes
    val saldoPositivo = saldoMes >= 0

    val nomeMes = remember(mesSelecionado) {
        mesSelecionado.month.getDisplayName(TextStyle.FULL, LocalePtBr)
            .replaceFirstChar { it.uppercase() } + " " + mesSelecionado.year
    }

    val diasNoMes = mesSelecionado.lengthOfMonth()
    val mediaDiaria = if (totalDespesasMes > 0) totalDespesasMes / diasNoMes else 0.0
    val maiorCategoriaDespesa = despesasPorCategoria.maxByOrNull { it.total }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val escopo = rememberCoroutineScope()
    var graficosProntos by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        graficosProntos = true
    }

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
        // ---------------- CABEÇALHO + MÊS ----------------
        item {
            CabecalhoDePagina("Análise", RoxoAnalise, null) // tela principal: sem seta de voltar
            Spacer(modifier = Modifier.height(16.dp))
            MesSelectorCard(
                nomeMes = nomeMes,
                onMesAnterior = { viewModel.mesAnterior() },
                onMesSeguinte = { viewModel.mesSeguinte() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ---------------- CARTÃO PRINCIPAL ----------------
        item {
            CartaoHeroDePagina(
                corInicio = if (saldoPositivo) RoxoAnalise else CorHeroAlerta,
                corFim = if (saldoPositivo) AzulAnalise else gradienteDaCor(CorHeroAlerta)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SALDO DO MÊS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                    PilulaGlass(if (saldoPositivo) "Positivo" else "Negativo")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    formato.format(saldoMes),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IndicadorGlassDePagina("GANHOS", formato.format(totalReceitasMes), Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f))
                    IndicadorGlassDePagina("GASTOS", formato.format(totalDespesasMes), Icons.AutoMirrored.Filled.TrendingDown, Modifier.weight(1f))
                    IndicadorGlassDePagina("MÉDIA/DIA", formato.format(mediaDiaria), Icons.Filled.CalendarToday, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PieChart, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Maior foco de despesa",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        maiorCategoriaDespesa?.nomeCategoria ?: "Nenhuma",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            TituloComIcone("Top 5 Maiores Gastos", Icons.AutoMirrored.Filled.TrendingDown, Coral)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ---------------- TOP 5 ----------------
        if (topDespesas.isEmpty()) {
            item {
                EstadoVazioAnalise("Nenhuma despesa registrada neste mês.")
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            val maiorDespesa = topDespesas.maxOf { it.valor }
            itemsIndexed(topDespesas) { indice, despesa ->
                val proporcao = if (maiorDespesa > 0.0) (despesa.valor / maiorDespesa).toFloat().coerceIn(0f, 1f) else 0f
                Card(
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
                                    .background(Coral.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    iconeParaCategoria(despesa.nomeCategoria),
                                    contentDescription = null,
                                    tint = Coral,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    despesa.descricao,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${indice + 1}º • ${despesa.nomeCategoria} • ${formatarDataAnalise(despesa.data)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "- ${formato.format(despesa.valor)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Coral,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { proporcao },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Coral,
                            trackColor = Coral.copy(alpha = 0.12f)
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        // ---------------- DISTRIBUIÇÃO (ROSCA) ----------------
        item {
            if (graficosProntos) {
                CartaoAnalise(
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 150f)
                    )
                ) {
                    TituloComIcone(
                        titulo = "Distribuição",
                        icone = Icons.Filled.PieChart,
                        cor = RoxoAnalise
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            repeat(2) { i ->
                                val ativa = pagerState.currentPage == i
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(if (ativa) 18.dp else 6.dp)
                                        .background(
                                            if (ativa) RoxoAnalise else MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(4.dp)
                    ) {
                        SeletorPilulaAnalise(
                            texto = "Gastos",
                            selecionado = pagerState.currentPage == 0,
                            cor = Coral,
                            modifier = Modifier.weight(1f)
                        ) { escopo.launch { pagerState.animateScrollToPage(0) } }
                        SeletorPilulaAnalise(
                            texto = "Ganhos",
                            selecionado = pagerState.currentPage == 1,
                            cor = Verde,
                            modifier = Modifier.weight(1f)
                        ) { escopo.launch { pagerState.animateScrollToPage(1) } }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalPager(
                        state = pagerState,
                        verticalAlignment = Alignment.Top
                    ) { page ->
                        val dados = if (page == 0) despesasPorCategoria else receitasPorCategoria
                        val total = if (page == 0) totalDespesasMes else totalReceitasMes
                        val paletaCores = if (page == 0) CoresCategoria else CoresCategoriaGanhos
                        val corTrilha = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (dados.isEmpty()) {
                                EstadoVazioAnalise("Nenhum registro para este gráfico no mês.")
                            } else {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                                    Canvas(modifier = Modifier.size(180.dp)) {
                                        val larguraTraco = 24.dp.toPx()
                                        val raio = (size.minDimension - larguraTraco) / 2
                                        val topLeft = Offset((size.width - raio * 2) / 2, (size.height - raio * 2) / 2)
                                        val tamanho = Size(raio * 2, raio * 2)
                                        drawArc(
                                            color = corTrilha,
                                            startAngle = 0f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            style = Stroke(width = larguraTraco),
                                            topLeft = topLeft,
                                            size = tamanho
                                        )
                                        val folga = if (dados.size > 1) 2f else 0f
                                        var anguloInicial = -90f
                                        dados.forEachIndexed { index, item ->
                                            val fracao = if (total > 0) (item.total / total).toFloat() else 0f
                                            val varredura = fracao * 360f
                                            drawArc(
                                                color = paletaCores[index % paletaCores.size],
                                                startAngle = anguloInicial + folga / 2,
                                                sweepAngle = (varredura - folga).coerceAtLeast(0.5f),
                                                useCenter = false,
                                                style = Stroke(width = larguraTraco),
                                                topLeft = topLeft,
                                                size = tamanho
                                            )
                                            anguloInicial += varredura
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            if (page == 0) "Total gasto" else "Total ganho",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            formato.format(total),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                dados.forEachIndexed { index, item ->
                                    val cor = paletaCores[index % paletaCores.size]
                                    val percentual = if (total > 0) (item.total / total * 100).toInt() else 0
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(10.dp).background(cor, CircleShape))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            item.nomeCategoria,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            formato.format(item.total),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = cor.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                "$percentual%",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = cor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                PlaceholderGrafico(altura = 300.dp)
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // ---------------- FLUXO DE CAIXA ----------------
        item {
            if (graficosProntos) {
                CartaoAnalise {
                    TituloComIcone("Fluxo de Caixa", Icons.Filled.BarChart, AzulAnalise) {
                        Text(
                            "6 meses",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendaPonto(Verde, "Ganhos")
                        LegendaPonto(Coral, "Gastos")
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    if (evolucaoMensal.isEmpty()) {
                        EstadoVazioAnalise("Ainda não há dados suficientes para exibir o histórico.")
                    } else {
                        val maiorValor = evolucaoMensal.maxOfOrNull { maxOf(it.totalReceitas, it.totalDespesas) }?.coerceAtLeast(1.0) ?: 1.0
                        GraficoBase(
                            maiorValor = maiorValor,
                            rotulos = evolucaoMensal.map { rotuloMes(it.anoMes) }
                        ) {
                            evolucaoMensal.forEach { item ->
                                val fracaoReceita = (item.totalReceitas / maiorValor).toFloat()
                                val fracaoDespesa = (item.totalDespesas / maiorValor).toFloat()
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .height((AlturaGrafico * fracaoReceita).coerceAtLeast(3.dp))
                                            .background(Verde, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .height((AlturaGrafico * fracaoDespesa).coerceAtLeast(3.dp))
                                            .background(Coral, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                PlaceholderGrafico(altura = 250.dp)
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // ---------------- APORTES ----------------
        item {
            if (graficosProntos) {
                CartaoAnalise {
                    TituloComIcone("Aportes", Icons.Filled.Savings, AmareloInvestimento) {
                        Text(
                            "6 meses",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LegendaPonto(AmareloInvestimento, "Valor investido")
                    Spacer(modifier = Modifier.height(20.dp))
                    if (evolucaoInvestimentos.isEmpty()) {
                        EstadoVazioAnalise("Ainda não há dados suficientes para exibir o histórico de aportes.")
                    } else {
                        val maiorInv = evolucaoInvestimentos.maxOfOrNull { it.total }?.coerceAtLeast(1.0) ?: 1.0
                        GraficoBase(
                            maiorValor = maiorInv,
                            rotulos = evolucaoInvestimentos.map { rotuloMes(it.anoMes) }
                        ) {
                            evolucaoInvestimentos.forEach { item ->
                                val fracao = (item.total / maiorInv).toFloat()
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height((AlturaGrafico * fracao).coerceAtLeast(3.dp))
                                            .background(AmareloInvestimento, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                PlaceholderGrafico(altura = 250.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Componentes locais
// ---------------------------------------------------------------------------

@Composable
private fun CartaoAnalise(
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(MaterialTheme.dimens.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.dimens.paddingMedium),
            content = conteudo
        )
    }
}

@Composable
private fun TituloComIcone(
    titulo: String,
    icone: ImageVector,
    cor: Color,
    lateral: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(cor.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        lateral()
    }
}

@Composable
private fun LegendaPonto(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(cor, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EstadoVazioAnalise(mensagem: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            mensagem,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
private fun SeletorPilulaAnalise(
    texto: String,
    selecionado: Boolean,
    cor: Color,
    modifier: Modifier = Modifier,
    aoClicar: () -> Unit
) {
    val fundo by animateColorAsState(
        targetValue = if (selecionado) cor else Color.Transparent,
        animationSpec = tween(200),
        label = "fundoSeletorAnalise"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(fundo)
            .clickable(onClick = aoClicar)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Medium,
            color = if (selecionado) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Eixo Y, grade tracejada e rótulos dos meses. As barras são desenhadas pelo [barras]. */
@Composable
private fun GraficoBase(
    maiorValor: Double,
    rotulos: List<String>,
    barras: @Composable RowScope.() -> Unit
) {
    val corGrade = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val corEixo = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .height(AlturaGrafico)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(formatarEixoY(maiorValor), style = MaterialTheme.typography.labelSmall, color = corEixo)
            Text(formatarEixoY(maiorValor / 2), style = MaterialTheme.typography.labelSmall, color = corEixo)
            Text("0", style = MaterialTheme.typography.labelSmall, color = corEixo)
        }
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth().height(AlturaGrafico)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val espessura = 1.dp.toPx()
                    val tracejado = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    listOf(0f, size.height / 2, size.height).forEach { y ->
                        drawLine(corGrade, Offset(0f, y), Offset(size.width, y), strokeWidth = espessura, pathEffect = tracejado)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                    content = barras
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rotulos.forEach { rotulo ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            rotulo,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = corEixo
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderGrafico(altura: Dp) {
    Card(
        shape = RoundedCornerShape(MaterialTheme.dimens.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(altura)
    ) {}
}