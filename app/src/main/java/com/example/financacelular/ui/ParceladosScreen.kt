package com.example.financacelular.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TealParcelados = Color(0xFF2EC4B6)

@Composable
fun ParceladosScreen(
    viewModel: ParceladosViewModel = viewModel(),
    aoVoltar: (() -> Unit)? = null
) {
    val comprasAgrupadas by viewModel.comprasAgrupadas.collectAsState()
    val formatoMoedaResumo = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build()) }
    val totalRestante = comprasAgrupadas.sumOf { it.parcelasRestantes * it.valorParcela }
    val parcelasPagas = comprasAgrupadas.sumOf { it.parcelasPagas }
    val parcelasTotais = comprasAgrupadas.sumOf { it.totalParcelas }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = espacoParaBarraFlutuante()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CabecalhoDePagina("Parcelamentos", TealParcelados, aoVoltar)
            Spacer(modifier = Modifier.height(16.dp))

            CartaoHeroDePagina(TealParcelados, gradienteDaCor(TealParcelados)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RESTANTE A PAGAR",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                    PilulaGlass(if (comprasAgrupadas.size == 1) "1 compra" else "${comprasAgrupadas.size} compras")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    formatoMoedaResumo.format(totalRestante),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IndicadorGlassDePagina("PARCELAS PAGAS", "$parcelasPagas de $parcelasTotais", Icons.Filled.CheckCircle, Modifier.weight(1f))
                    IndicadorGlassDePagina("EM ANDAMENTO", "${comprasAgrupadas.count { it.parcelasRestantes > 0 }}", Icons.Filled.ShoppingCart, Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (comprasAgrupadas.isEmpty()) {
                Text(
                    "Nenhuma compra parcelada em andamento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(comprasAgrupadas) { compra ->
            ItemCompraParcelada(compra = compra, viewModel = viewModel)
        }
    }
}

@Composable
fun ItemCompraParcelada(compra: CompraParceladaAgrupada, viewModel: ParceladosViewModel) {
    var expandido by remember { mutableStateOf(false) }
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build()) }

    val progresso = if (compra.totalParcelas > 0) compra.parcelasPagas.toFloat() / compra.totalParcelas else 0f
    val progressoAnimado by animateFloatAsState(targetValue = progresso, label = "ProgressoParcelas")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
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
                        .background(TealParcelados.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = TealParcelados, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(compra.descricaoBase, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${compra.totalParcelas}x de ${formatoMoeda.format(compra.valorParcela)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatoMoeda.format(compra.valorTotal),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = if (expandido) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Expandir detalhes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progressoAnimado },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (compra.parcelasRestantes == 0) Verde else TealParcelados,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "${compra.parcelasPagas}/${compra.totalParcelas} pagas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expandido) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Data da compra:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(compra.dataCompra.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total já pago:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatoMoeda.format(compra.parcelasPagas * compra.valorParcela), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Verde)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Restante a pagar:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatoMoeda.format(compra.parcelasRestantes * compra.valorParcela), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Coral)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (compra.parcelasRestantes > 0) {
                        OutlinedButton(
                            onClick = { viewModel.cancelarParcelasRestantes(compra.transacoesPendentes) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancelar ${compra.parcelasRestantes} parcelas restantes")
                        }
                    } else {
                        Text(
                            "Compra totalmente quitada! 🎉",
                            style = MaterialTheme.typography.titleSmall,
                            color = Verde,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}