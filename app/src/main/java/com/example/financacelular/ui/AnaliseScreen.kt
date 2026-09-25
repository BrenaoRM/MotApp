package com.example.financacelular.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

private val AmareloInvestimento = Color(0xFFF2A93B)

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

private fun formatarEixoY(valor: Double): String {
    if (valor == 0.0) return "0"
    return when {
        valor >= 1_000_000 -> String.format(Locale("pt", "BR"), "%.1fM", valor / 1_000_000)
        valor >= 1_000 -> String.format(Locale("pt", "BR"), "%.1fk", valor / 1_000)
        else -> String.format(Locale("pt", "BR"), "%.0f", valor)
    }
}

@Composable
fun AnaliseScreen(viewModel: AnaliseViewModel = viewModel()) {
    val despesasPorCategoria by viewModel.despesasPorCategoria.collectAsState()
    val receitasPorCategoria by viewModel.receitasPorCategoria.collectAsState()
    val evolucaoMensal by viewModel.evolucaoMensal.collectAsState()
    val topDespesas by viewModel.topDespesas.collectAsState()
    val evolucaoInvestimentos by viewModel.evolucaoInvestimentos.collectAsState()
    val mesSelecionado by viewModel.mesSelecionado.collectAsState()
    val totalDespesasMes by viewModel.totalDespesasMes.collectAsState()
    val totalReceitasMes by viewModel.totalReceitasMes.collectAsState()
    val formato = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    // --- CÁLCULO DO SALDO E COR ---
    val saldoMes = totalReceitasMes - totalDespesasMes
    val corSaldo = if (saldoMes >= 0) Verde else Coral
    // ------------------------------

    val nomeMes = remember(mesSelecionado) {
        mesSelecionado.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .replaceFirstChar { it.uppercase() } + " " + mesSelecionado.year
    }

    val diasNoMes = mesSelecionado.lengthOfMonth()
    val mediaDiaria = if (totalDespesasMes > 0) totalDespesasMes / diasNoMes else 0.0
    val maiorCategoriaDespesa = despesasPorCategoria.maxByOrNull { it.total }

    val pagerState = rememberPagerState(pageCount = { 2 })

    var graficosProntos by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        graficosProntos = true
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = espacoParaBarraFlutuante()
        )
    ) {
        item {
            Text("Análise Financeira", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(nomeMes, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.mesAnterior() }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior")
                    }
                    IconButton(onClick = { viewModel.mesSeguinte() }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo mês")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Resumo do Período", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- LINHA 1: GANHOS E GASTOS ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total Ganho", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(formato.format(totalReceitasMes), style = MaterialTheme.typography.titleMedium, color = Verde, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total Gasto", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(formato.format(totalDespesasMes), style = MaterialTheme.typography.titleMedium, color = Coral, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- LINHA 2: SALDO DO MÊS E MÉDIA DIÁRIA ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Saldo do Mês", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(formato.format(saldoMes), style = MaterialTheme.typography.titleMedium, color = corSaldo, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Média Diária", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(formato.format(mediaDiaria), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Maior foco de despesa:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(maiorCategoriaDespesa?.nomeCategoria ?: "Nenhuma", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Top 5 Maiores Gastos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (topDespesas.isEmpty()) {
            item {
                Text(
                    "Nenhuma despesa registrada neste mês.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            items(topDespesas) { despesa ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
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
                                .background(Coral.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(iconeParaCategoria(despesa.nomeCategoria), contentDescription = null, tint = Coral)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(despesa.descricao, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${despesa.nomeCategoria} • ${despesa.data}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "- ${formato.format(despesa.valor)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = Coral,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        item {
            if (graficosProntos) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = 0.9f,
                                stiffness = 150f
                            )
                        )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (pagerState.currentPage == 0) "Distribuição de Gastos" else "Distribuição de Ganhos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(8.dp).background(if (pagerState.currentPage == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                Box(modifier = Modifier.size(8.dp).background(if (pagerState.currentPage == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))

                        HorizontalPager(
                            state = pagerState,
                            verticalAlignment = Alignment.Top
                        ) { page ->
                            val dados = if (page == 0) despesasPorCategoria else receitasPorCategoria
                            val total = if (page == 0) totalDespesasMes else totalReceitasMes
                            val paletaCores = if (page == 0) CoresCategoria else CoresCategoriaGanhos

                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (dados.isEmpty()) {
                                    Text(
                                        "Nenhum registro para este gráfico no mês.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                                        Canvas(modifier = Modifier.size(180.dp)) {
                                            val larguraTraco = 28.dp.toPx()
                                            val raio = (size.minDimension - larguraTraco) / 2
                                            var anguloInicial = -90f
                                            dados.forEachIndexed { index, item ->
                                                val fracao = if (total > 0) (item.total / total).toFloat() else 0f
                                                val varredura = fracao * 360f
                                                drawArc(
                                                    color = paletaCores[index % paletaCores.size],
                                                    startAngle = anguloInicial,
                                                    sweepAngle = varredura,
                                                    useCenter = false,
                                                    style = Stroke(width = larguraTraco),
                                                    topLeft = Offset(
                                                        (size.width - raio * 2) / 2,
                                                        (size.height - raio * 2) / 2
                                                    ),
                                                    size = Size(raio * 2, raio * 2)
                                                )
                                                anguloInicial += varredura
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(formato.format(total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    dados.forEachIndexed { index, item ->
                                        val cor = paletaCores[index % paletaCores.size]
                                        val percentual = if (total > 0) (item.total / total * 100).toInt() else 0
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(12.dp).background(cor, CircleShape))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(item.nomeCategoria, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text("${formato.format(item.total)} ($percentual%)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } else {
                PlaceholderGrafico(altura = 260.dp)
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            if (graficosProntos) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Fluxo de Caixa (Últimos 6 Meses)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Verde, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ganhos", style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Coral, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gastos", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        if (evolucaoMensal.isEmpty()) {
                            Text(
                                "Ainda não há dados suficientes para exibir o histórico.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val maiorValor = evolucaoMensal.maxOfOrNull { maxOf(it.totalReceitas, it.totalDespesas) }?.coerceAtLeast(1.0) ?: 1.0

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier
                                        .height(120.dp)
                                        .padding(end = 8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(formatarEixoY(maiorValor), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatarEixoY(maiorValor / 2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val strokeW = 1.dp.toPx()
                                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                            val lineColor = Color.Gray.copy(alpha = 0.3f)

                                            drawLine(lineColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = strokeW, pathEffect = dashEffect)
                                            drawLine(lineColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = strokeW, pathEffect = dashEffect)
                                            drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = strokeW, pathEffect = dashEffect)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            evolucaoMensal.forEach { item ->
                                                val fracaoReceita = (item.totalReceitas / maiorValor).toFloat()
                                                val fracaoDespesa = (item.totalDespesas / maiorValor).toFloat()
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.Bottom
                                                ) {
                                                    Box(modifier = Modifier.width(8.dp).height((120 * fracaoReceita).dp.coerceAtLeast(2.dp)).background(Verde, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Box(modifier = Modifier.width(8.dp).height((120 * fracaoDespesa).dp.coerceAtLeast(2.dp)).background(Coral, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        evolucaoMensal.forEach { item ->
                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                Text(item.anoMes.takeLast(2), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } else {
                PlaceholderGrafico(altura = 230.dp)
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            if (graficosProntos) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Aportes (Últimos 6 Meses)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(AmareloInvestimento, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Valor Investido", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        if (evolucaoInvestimentos.isEmpty()) {
                            Text(
                                "Ainda não há dados suficientes para exibir o histórico de aportes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val maiorInv = evolucaoInvestimentos.maxOfOrNull { it.total }?.coerceAtLeast(1.0) ?: 1.0

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier
                                        .height(120.dp)
                                        .padding(end = 8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(formatarEixoY(maiorInv), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatarEixoY(maiorInv / 2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val strokeW = 1.dp.toPx()
                                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                            val lineColor = Color.Gray.copy(alpha = 0.3f)

                                            drawLine(lineColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = strokeW, pathEffect = dashEffect)
                                            drawLine(lineColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = strokeW, pathEffect = dashEffect)
                                            drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = strokeW, pathEffect = dashEffect)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            evolucaoInvestimentos.forEach { item ->
                                                val fracao = (item.total / maiorInv).toFloat()
                                                Box(
                                                    modifier = Modifier.weight(1f),
                                                    contentAlignment = Alignment.BottomCenter
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(16.dp)
                                                            .height((120 * fracao).dp.coerceAtLeast(2.dp))
                                                            .background(AmareloInvestimento, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        evolucaoInvestimentos.forEach { item ->
                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                Text(item.anoMes.takeLast(2), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                PlaceholderGrafico(altura = 230.dp)
            }
        }
    }
}

@Composable
private fun PlaceholderGrafico(altura: Dp) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(altura)
    ) {}
}