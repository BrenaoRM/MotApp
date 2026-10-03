package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.util.Locale
import java.time.format.TextStyle as JavaTextStyle

private val VerdeOrcamento = Color(0xFF5FB36B)

/**
 * Converte qualquer entrada numérica (usando vírgula ou ponto) para Double com segurança.
 */
private fun converterTextoParaDouble(texto: String): Double {
    val limpo = texto.trim()
    if (limpo.isEmpty()) return 0.0

    val temVirgula = limpo.contains(",")
    val temPonto = limpo.contains(".")

    val textoFormatado = when {
        // Se contiver ponto e vírgula, identifica qual é o último para saber qual é o decimal
        temVirgula && temPonto -> {
            val ultimoPonto = limpo.lastIndexOf('.')
            val ultimaVirgula = limpo.lastIndexOf(',')
            if (ultimaVirgula > ultimoPonto) {
                // Formato BR: 1.500,50 -> remove ponto de milhar, troca vírgula decimal por ponto
                limpo.replace(".", "").replace(",", ".")
            } else {
                // Formato US: 1,500.50 -> remove vírgula de milhar
                limpo.replace(",", "")
            }
        }
        // Se contiver apenas vírgula: 500,50 -> troca por ponto
        temVirgula -> limpo.replace(",", ".")
        // Se contiver apenas ponto: 500.50 ou 1500 -> mantém o ponto
        else -> limpo
    }

    return textoFormatado.toDoubleOrNull() ?: 0.0
}

private fun limiteParaTexto(limite: Double?): String =
    limite?.takeIf { it > 0.0 }?.let { lim ->
        if (lim % 1.0 == 0.0) lim.toLong().toString() else lim.toString().replace(".", ",")
    } ?: ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrcamentoScreen(
    viewModel: OrcamentoViewModel = viewModel(),
    acionarNovoLimiteExterno: Boolean = false,
    aoNovoLimiteAcionado: () -> Unit = {},
    aoVoltar: (() -> Unit)? = null
) {
    val itens by viewModel.itens.collectAsState(initial = emptyList())
    val mesSelecionado by viewModel.mesSelecionado.collectAsState()
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build()) }

    val nomeMes = remember(mesSelecionado) {
        mesSelecionado.month.getDisplayName(JavaTextStyle.FULL, Locale.Builder().setLanguage("pt").setRegion("BR").build())
            .replaceFirstChar { it.uppercase() } + " " + mesSelecionado.year
    }

    var categoriaEmEdicao by remember { mutableStateOf<ItemOrcamento?>(null) }
    var mostrarSheet by remember { mutableStateOf(false) }
    var modoNovoLimite by remember { mutableStateOf(false) }
    var valorInput by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val totalGasto = remember(itens) { itens.sumOf { it.gasto } }
    val totalLimite = remember(itens) { itens.sumOf { it.limite ?: 0.0 } }
    val temAlgumLimite = totalLimite > 0.0
    val progressoGeral = if (temAlgumLimite) (totalGasto / totalLimite).toFloat().coerceIn(0f, 1f) else 0f
    val ultrapassouGeral = temAlgumLimite && totalGasto > totalLimite

    // Botão "Definir Limite" da barra inferior: abre o sheet com seletor de categoria
    LaunchedEffect(acionarNovoLimiteExterno) {
        if (acionarNovoLimiteExterno) {
            val inicial = itens.firstOrNull { (it.limite ?: 0.0) <= 0.0 } ?: itens.firstOrNull()
            if (inicial != null) {
                modoNovoLimite = true
                categoriaEmEdicao = inicial
                valorInput = limiteParaTexto(inicial.limite)
                mostrarSheet = true
            }
            aoNovoLimiteAcionado()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = MaterialTheme.dimens.paddingScreen),
            contentPadding = PaddingValues(top = 16.dp, bottom = espacoParaBarraFlutuante())
        ) {
            item {
                CabecalhoDePagina("Orçamento Mensal", VerdeOrcamento, aoVoltar)
                Spacer(modifier = Modifier.height(16.dp))
                MesSelectorCard(
                    nomeMes = nomeMes,
                    onMesAnterior = { viewModel.mesAnterior() },
                    onMesSeguinte = { viewModel.mesSeguinte() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                CartaoHeroDePagina(
                    corInicio = if (ultrapassouGeral) CorHeroAlerta else VerdeOrcamento,
                    corFim = gradienteDaCor(if (ultrapassouGeral) CorHeroAlerta else VerdeOrcamento)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PieChart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "TOTAL ORÇADO DO MÊS",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (temAlgumLimite) {
                            PilulaGlass(if (ultrapassouGeral) "Limite excedido" else "No limite")
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (temAlgumLimite) {
                            "${formatoMoeda.format(totalGasto)} / ${formatoMoeda.format(totalLimite)}"
                        } else {
                            formatoMoeda.format(totalGasto)
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progressoGeral },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Categorias de Despesa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (itens.size == 1) "1 categoria" else "${itens.size} categorias",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (itens.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Nenhuma categoria de despesa encontrada.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(itens) { item ->
                val temOrcamento = (item.limite ?: 0.0) > 0.0
                val limiteVal = item.limite ?: 0.0
                val progresso = if (temOrcamento) (item.gasto / limiteVal).toFloat().coerceIn(0f, 1f) else 0f
                val ultrapassou = temOrcamento && item.gasto > limiteVal
                val corBarra = if (ultrapassou) Coral else VerdeOrcamento
                val percentualInt = if (temOrcamento) ((item.gasto / limiteVal) * 100).toInt() else 0
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            modoNovoLimite = false
                            categoriaEmEdicao = item
                            valorInput = item.limite?.let { lim ->
                                if (lim % 1.0 == 0.0) lim.toLong().toString() else lim.toString().replace(".", ",")
                            } ?: ""
                            mostrarSheet = true
                        }
                ) {
                    Column(modifier = Modifier.padding(MaterialTheme.dimens.paddingMedium)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        color = if (ultrapassou) Coral.copy(alpha = 0.15f) else VerdeOrcamento.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconeParaCategoria(item.categoria.nome),
                                    contentDescription = null,
                                    tint = if (ultrapassou) Coral else VerdeOrcamento,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.categoria.nome,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when {
                                        !temOrcamento -> "Toque para definir um limite"
                                        ultrapassou -> "Excedido em ${formatoMoeda.format(item.gasto - limiteVal)}"
                                        else -> "Disponível: ${formatoMoeda.format(limiteVal - item.gasto)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (ultrapassou) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (ultrapassou) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (temOrcamento) {
                                        "${formatoMoeda.format(item.gasto)} / ${formatoMoeda.format(limiteVal)}"
                                    } else {
                                        formatoMoeda.format(item.gasto)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (ultrapassou) Coral else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                if (temOrcamento && item.limiteHerdado) {
                                    Text(
                                        text = "padrão do mês anterior",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                } else if (temOrcamento) {
                                    Text(
                                        text = "$percentualInt%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (ultrapassou) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progresso },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = corBarra,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }

        if (mostrarSheet) {
            val item = categoriaEmEdicao
            ModalBottomSheet(
                onDismissRequest = { mostrarSheet = false },
                sheetState = sheetState,
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
                        .imePadding()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (modoNovoLimite) "Definir limite" else "Orçamento: ${item?.categoria?.nome.orEmpty()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { mostrarSheet = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Limite de gastos para $nomeMes. Deixe em branco (ou zero) para remover o limite deste mês.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (modoNovoLimite) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "PARA QUAL CATEGORIA?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(itens) { opcao ->
                                val selecionada = item?.categoria == opcao.categoria
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (selecionada) VerdeOrcamento.copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                        .border(
                                            width = if (selecionada) 1.5.dp else 0.dp,
                                            color = if (selecionada) VerdeOrcamento else Color.Transparent,
                                            shape = RoundedCornerShape(50)
                                        )
                                        .clickable {
                                            categoriaEmEdicao = opcao
                                            valorInput = limiteParaTexto(opcao.limite)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = iconeParaCategoria(opcao.categoria.nome),
                                        contentDescription = null,
                                        tint = VerdeOrcamento,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        opcao.categoria.nome,
                                        fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = valorInput,
                        onValueChange = { valorInput = it },
                        label = { Text("Valor Limite (R$)") },
                        placeholder = { Text("Ex: 500,00 ou 500.50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(
                        onClick = {
                            val valorDouble = converterTextoParaDouble(valorInput)
                            item?.let { viewModel.definirLimite(it.categoria, valorDouble) }
                            mostrarSheet = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                            .height(52.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VerdeOrcamento,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Definir Limite", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}