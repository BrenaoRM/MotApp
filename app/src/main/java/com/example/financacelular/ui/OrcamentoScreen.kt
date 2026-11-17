package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.financacelular.ui.theme.cores
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.VerdeOrcamento
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.util.Locale
import java.time.format.TextStyle as JavaTextStyle

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

    // Limite sem centavos quando é inteiro ("R$ 1.000"), para a linha da categoria não ficar longa
    val formatoLimiteInteiro = remember {
        NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
    }
    fun formatoLimite(limite: Double): String =
        if (limite % 1.0 == 0.0) formatoLimiteInteiro.format(limite) else formatoMoeda.format(limite)

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
                CabecalhoPadrao(
                    titulo = mesSelecionado.nomeDoMes(),
                    subtitulo = "Orçamento · ${mesSelecionado.year}",
                    aoVoltar = aoVoltar,
                    aoAnterior = { viewModel.mesAnterior() },
                    aoSeguinte = { viewModel.mesSeguinte() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                val progressoGeral = if (totalLimite > 0.0) (totalGasto / totalLimite).toFloat() else 0f
                CartaoHeroDePagina(
                    cor = if (ultrapassouGeral) CorHeroAlerta else VerdeOrcamento
                ) {
                    Text(
                        "Gasto no mês",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ValorDoHero(formatoMoeda.format(totalGasto))
                    Spacer(modifier = Modifier.height(if (temAlgumLimite) 14.dp else 10.dp))
                    if (temAlgumLimite) {
                        BarraDoHero(progressoGeral, if (ultrapassouGeral) Coral else VerdeOrcamento)
                        Spacer(modifier = Modifier.height(10.dp))
                        val destaque = SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            buildAnnotatedString {
                                if (ultrapassouGeral) {
                                    append("Excedido em ")
                                    withStyle(destaque) { append(formatoMoeda.format(totalGasto - totalLimite)) }
                                    append(" do limite de ${formatoMoeda.format(totalLimite)}")
                                } else {
                                    append("Disponível ")
                                    withStyle(destaque) { append(formatoMoeda.format(totalLimite - totalGasto)) }
                                    append(" de ${formatoMoeda.format(totalLimite)}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Nenhum limite definido",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Categorias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (itens.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
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
            } else {
                item {
                    CartaoUnicoDeLinhas {
                        itens.forEachIndexed { indice, item ->
                            if (indice > 0) DivisorDeLinha()
                            val limiteVal = item.limite ?: 0.0
                            val temOrcamento = limiteVal > 0.0
                            val progresso = if (temOrcamento) (item.gasto / limiteVal).toFloat().coerceIn(0f, 1f) else 0f
                            val ultrapassou = temOrcamento && item.gasto > limiteVal
                            val corAnel = if (ultrapassou) Coral else VerdeOrcamento
                            val percentualInt = if (temOrcamento) ((item.gasto / limiteVal) * 100).toInt() else 0
                            LinhaDePagina(
                                inicio = {
                                    // O anel em volta do ícone mostra quanto do limite já foi usado
                                    AnelDeProgresso(if (ultrapassou) 1f else progresso, corAnel) {
                                        Icon(
                                            imageVector = iconeParaCategoria(item.categoria.nome),
                                            contentDescription = null,
                                            tint = if (temOrcamento) corAnel else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                titulo = item.categoria.nome,
                                valor = formatoMoeda.format(item.gasto),
                                valorSecundario = if (temOrcamento) formatoLimite(limiteVal) else null,
                                subtitulo = when {
                                    !temOrcamento -> "Sem limite · toque para definir"
                                    ultrapassou -> "Excedido em ${formatoMoeda.format(item.gasto - limiteVal)}"
                                    item.limiteHerdado -> "Disponível ${formatoMoeda.format(limiteVal - item.gasto)} · padrão do mês anterior"
                                    else -> "$percentualInt% · disponível ${formatoMoeda.format(limiteVal - item.gasto)}"
                                },
                                corSubtitulo = if (ultrapassou) MaterialTheme.cores.textoGasto else Color.Unspecified,
                                onClick = {
                                    modoNovoLimite = false
                                    categoriaEmEdicao = item
                                    valorInput = limiteParaTexto(item.limite)
                                    mostrarSheet = true
                                }
                            )
                        }
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
                                        .clickable(role = Role.Button) {
                                            categoriaEmEdicao = opcao
                                            valorInput = limiteParaTexto(opcao.limite)
                                        }
                                        .heightIn(min = Tamanho.toque)
                                        .padding(horizontal = 14.dp),
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
                    BotaoPrincipal(
                        texto = "Definir Limite",
                        onClick = {
                            val valorDouble = converterTextoParaDouble(valorInput)
                            item?.let { viewModel.definirLimite(it.categoria, valorDouble) }
                            mostrarSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        cor = VerdeOrcamento,
                        corConteudo = Color.White,
                        icone = Icons.Filled.Check
                    )
                }
            }
        }
    }
}