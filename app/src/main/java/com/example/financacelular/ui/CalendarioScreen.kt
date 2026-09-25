package com.example.financacelular.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarioScreen(viewModel: CalendarioViewModel = viewModel()) {
    val transacoesCalendario by viewModel.transacoesCalendario.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    val listaAfazeres by viewModel.afazeres.collectAsState()

    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    var mesAnoSelecionado by remember { mutableStateOf(YearMonth.now()) }
    var dataSelecionada by remember { mutableStateOf(LocalDate.now()) }

    var abaAtiva by remember { mutableStateOf(0) }

    var mostrarSheetNovoAfazer by remember { mutableStateOf(false) }
    var textoNovoAfazer by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val nomeMesAno = remember(mesAnoSelecionado) {
        mesAnoSelecionado.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .replaceFirstChar { it.uppercase() } + " " + mesAnoSelecionado.year
    }

    val transacoesDoDia = transacoesCalendario.filter { it.transacao.data == dataSelecionada }
    val afazeresDoDia = listaAfazeres.filter { it.data == dataSelecionada }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = if (abaAtiva == 1) 100.dp else 22.dp // Margem extra para o FAB se estiver na Agenda
            )
        ) {
            item {
                Text("Calendário & Agenda", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { mesAnoSelecionado = mesAnoSelecionado.minusMonths(1) }) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior")
                            }
                            Text(nomeMesAno, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { mesAnoSelecionado = mesAnoSelecionado.plusMonths(1) }) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Mês seguinte")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            val diasSemana = listOf("D", "S", "T", "Q", "Q", "S", "S")
                            diasSemana.forEach { dia ->
                                Text(
                                    text = dia,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val diasNoMes = mesAnoSelecionado.lengthOfMonth()
                        val primeiroDiaDoMes = mesAnoSelecionado.atDay(1)
                        val deslocamentoDias = if (primeiroDiaDoMes.dayOfWeek.value == 7) 0 else primeiroDiaDoMes.dayOfWeek.value

                        Column(modifier = Modifier.fillMaxWidth()) {
                            val semanas = (0 until (diasNoMes + deslocamentoDias + 6) / 7)
                            semanas.forEach { semanaIndex ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (diaIndex in 0 until 7) {
                                        val indiceGlobal = semanaIndex * 7 + diaIndex
                                        val diaMesReal = indiceGlobal - deslocamentoDias + 1

                                        if (diaMesReal in 1..diasNoMes) {
                                            val dataAtualGrid = mesAnoSelecionado.atDay(diaMesReal)
                                            val isSelecionado = dataAtualGrid == dataSelecionada

                                            val temDespesa = transacoesCalendario.any { it.transacao.data == dataAtualGrid && it.transacao.tipo == TipoTransacao.DESPESA }
                                            val temGanho = transacoesCalendario.any { it.transacao.data == dataAtualGrid && it.transacao.tipo == TipoTransacao.RECEITA }
                                            val temAfazer = listaAfazeres.any { it.data == dataAtualGrid && !it.concluido }

                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSelecionado) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable { dataSelecionada = dataAtualGrid },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = diaMesReal.toString(),
                                                        color = if (isSelecionado) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelecionado) FontWeight.Bold else FontWeight.Medium
                                                    )

                                                    Spacer(modifier = Modifier.height(2.dp))

                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (abaAtiva == 0) {
                                                            if (temDespesa) {
                                                                Box(modifier = Modifier.size(4.dp).background(if (isSelecionado) Color.White else Coral, CircleShape))
                                                            }
                                                            if (temGanho) {
                                                                Box(modifier = Modifier.size(4.dp).background(if (isSelecionado) Color.White else Verde, CircleShape))
                                                            }
                                                        } else {
                                                            if (temAfazer) {
                                                                Box(modifier = Modifier.size(4.dp).background(if (isSelecionado) Color.White else Color(0xFFF59E0B), CircleShape))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Box(modifier = Modifier.size(46.dp)) // Espaço vazio para manter a grelha simétrica
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Menu de Abas (Segmented Button Style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(4.dp)
                ) {
                    TabPill(
                        texto = "Financeiro (${transacoesDoDia.size})",
                        selecionado = abaAtiva == 0,
                        modifier = Modifier.weight(1f)
                    ) { abaAtiva = 0 }

                    TabPill(
                        texto = "Agenda (${afazeresDoDia.size})",
                        selecionado = abaAtiva == 1,
                        modifier = Modifier.weight(1f)
                    ) { abaAtiva = 1 }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (abaAtiva == 0) "Lançamentos Financeiros" else "Afazeres do Dia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (abaAtiva == 0) {
                if (transacoesDoDia.isEmpty()) {
                    item {
                        Text(
                            "Nenhum lançamento financeiro neste dia.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                items(transacoesDoDia) { itemCalendario ->
                    val t = itemCalendario.transacao
                    val categoria = categorias.find { it.id == t.categoriaId }
                    val cor = if (itemCalendario.ehFaturaNaoPaga) {
                        Color(0xFFF59E0B)
                    } else if (t.tipo == TipoTransacao.DESPESA) {
                        Coral
                    } else {
                        Verde
                    }

                    val descricaoFinal = if (itemCalendario.ehFaturaNaoPaga) {
                        "${t.descricao ?: categoria?.nome} (Não paga)"
                    } else {
                        t.descricao ?: (categoria?.nome ?: "Transação")
                    }

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp) // Padrão Dashboard
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp), // Padrão Dashboard
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp) // Padrão Dashboard
                                    .background(cor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    iconeParaCategoria(categoria?.nome ?: ""), // Trocado a Letra pelo Ícone Real
                                    contentDescription = null,
                                    tint = cor
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    descricaoFinal,
                                    style = MaterialTheme.typography.titleSmall, // Padrão Dashboard
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    categoria?.nome ?: "Sem categoria",
                                    style = MaterialTheme.typography.bodySmall, // Padrão Dashboard
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                (if (t.tipo == TipoTransacao.DESPESA) "- " else "+ ") + formatoMoeda.format(t.valor),
                                style = MaterialTheme.typography.titleSmall, // Padrão Dashboard
                                fontWeight = FontWeight.Bold,
                                color = cor
                            )
                        }
                    }
                }
            } else {
                if (afazeresDoDia.isEmpty()) {
                    item {
                        Text(
                            "Nenhum afazer cadastrado para este dia.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                items(afazeresDoDia) { afazer ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp) // Padrão Dashboard
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp), // Padrão Dashboard
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.alternarConcluido(afazer) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (afazer.concluido) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                    contentDescription = null,
                                    tint = if (afazer.concluido) Verde else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = afazer.titulo,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall, // Padrão Dashboard
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (afazer.concluido) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (afazer.concluido) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { viewModel.excluirAfazer(afazer) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button (FAB) para Adicionar Afazer na aba Agenda
        if (abaAtiva == 1) {
            FloatingActionButton(
                onClick = { mostrarSheetNovoAfazer = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp, end = 20.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Novo Afazer")
            }
        }
    }

    // Modal Bottom Sheet Moderno para Novo Afazer
    if (mostrarSheetNovoAfazer) {
        ModalBottomSheet(
            onDismissRequest = { mostrarSheetNovoAfazer = false },
            sheetState = sheetState,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Adicionar Afazer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = { mostrarSheetNovoAfazer = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Agendado para: ${dataSelecionada.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = textoNovoAfazer,
                    onValueChange = { textoNovoAfazer = it },
                    label = { Text("Descreva a tarefa...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (textoNovoAfazer.isNotBlank()) {
                            viewModel.inserirAfazer(dataSelecionada, textoNovoAfazer.trim())
                            textoNovoAfazer = ""
                            mostrarSheetNovoAfazer = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Salvar na Agenda", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun TabPill(texto: String, selecionado: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val backgroundColor = if (selecionado) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (selecionado) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}