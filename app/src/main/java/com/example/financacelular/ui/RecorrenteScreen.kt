package com.example.financacelular.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun RecorrenteScreen(viewModel: RecorrenteViewModel = viewModel()) {
    val recorrentes by viewModel.recorrentes.collectAsState(initial = emptyList())
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    val receitasRecorrentes = recorrentes.filter { it.tipo == TipoTransacao.RECEITA }
    val despesasRecorrentes = recorrentes.filter { it.tipo == TipoTransacao.DESPESA }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = espacoParaBarraFlutuante())
    ) {
        item {
            Text("Recorrentes e Assinaturas", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Gerencie suas receitas fixas e cobranças recorrentes. Toque em um item para ver detalhes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (recorrentes.isEmpty()) {
            item {
                Text(
                    "Nenhum item recorrente cadastrado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (receitasRecorrentes.isNotEmpty()) {
            item {
                Text(
                    "Ganhos Recorrentes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Verde
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(receitasRecorrentes) { item ->
                ItemRecorrenteCard(
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
                    color = Coral
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(despesasRecorrentes) { item ->
                ItemRecorrenteCard(
                    item = item,
                    formatoMoeda = formatoMoeda,
                    corTema = Coral,
                    subtitulo = "Cobrança fixa recorrente",
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
    corTema: androidx.compose.ui.graphics.Color,
    subtitulo: String,
    onCancelar: () -> Unit
) {
    var expandido by remember { mutableStateOf(false) }

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

    // Calcula quantos meses se passaram desde a criação + 1 mês atual/inicial
    val mesesCobrados = remember(item.dataCriacao) {
        val mesesPassados = ChronoUnit.MONTHS.between(item.dataCriacao, hoje)
        maxOf(1, mesesPassados + 1)
    }

    val totalJaPago = item.valor * mesesCobrados

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { expandido = !expandido }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(corTema.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.tipo == TipoTransacao.RECEITA) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = null,
                        tint = corTema
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatoMoeda.format(item.valor),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = corTema
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Icon(
                        imageVector = if (expandido) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Expandir detalhes",
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
                                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dia do vencimento:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Todo dia ${item.diaDoMes}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(tempoAtivoTexto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Verde)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- LINHA DE TOTAL ACUMULADO/PAGO ---
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
                            color = corTema
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onCancelar,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancelar Recorrência", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}