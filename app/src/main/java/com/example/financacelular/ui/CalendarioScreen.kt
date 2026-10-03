package com.example.financacelular.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Mesmas cores do cartão principal da home
private val RoxoCalendario = Color(0xFF7B5CF5)
private val AzulCalendario = Color(0xFF3B82F6)
private val AmareloAgenda = Color(0xFFF59E0B)
private val LocalePtBrCalendario: Locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()

/** Resumo do dia selecionado: faixa compacta com data, pendências e totais de ganhos/gastos. */
@Composable
private fun ResumoDiaCompacto(
    rotulo: String,
    titulo: String,
    pendentes: Int,
    ganhos: String,
    gastos: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(RoxoCalendario, AzulCalendario)))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        rotulo,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        titulo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (pendentes == 1) "1 pendente" else "$pendentes pendentes",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ValorGlassCompacto(Icons.AutoMirrored.Filled.TrendingUp, ganhos, Modifier.weight(1f))
                ValorGlassCompacto(Icons.AutoMirrored.Filled.TrendingDown, gastos, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ValorGlassCompacto(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    valor: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            valor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarioScreen(
    viewModel: CalendarioViewModel = viewModel(),
    acionarNovaAgendaExterno: Boolean = false,
    aoNovaAgendaAcionada: () -> Unit = {}
) {
    val transacoesCalendario by viewModel.transacoesCalendario.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    val listaAfazeres by viewModel.afazeres.collectAsState()
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(LocalePtBrCalendario) }

    var mesAnoSelecionado by remember { mutableStateOf(YearMonth.now()) }
    var dataSelecionada by remember { mutableStateOf(LocalDate.now()) }
    var abaAtiva by remember { mutableStateOf(0) }

    var mostrarSheetNovoAfazer by remember { mutableStateOf(false) }
    var textoNovoAfazer by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hoje = remember { LocalDate.now() }

    val nomeMesAno = remember(mesAnoSelecionado) {
        mesAnoSelecionado.month.getDisplayName(TextStyle.FULL, LocalePtBrCalendario)
            .replaceFirstChar { it.uppercase() } + " " + mesAnoSelecionado.year
    }

    val transacoesDoDia = transacoesCalendario.filter { it.transacao.data == dataSelecionada }
    val afazeresDoDia = listaAfazeres.filter { it.data == dataSelecionada }

    val gastoDoDia = transacoesDoDia
        .filter { it.transacao.tipo == TipoTransacao.DESPESA }
        .sumOf { it.transacao.valor }
    val ganhoDoDia = transacoesDoDia
        .filter { it.transacao.tipo == TipoTransacao.RECEITA }
        .sumOf { it.transacao.valor }
    val afazeresPendentes = afazeresDoDia.count { !it.concluido }

    val tituloDia = remember(dataSelecionada) {
        dataSelecionada.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", LocalePtBrCalendario))
            .replaceFirstChar { it.uppercase() }
    }

    LaunchedEffect(acionarNovaAgendaExterno) {
        if (acionarNovaAgendaExterno) {
            abaAtiva = 1
            textoNovoAfazer = ""
            mostrarSheetNovoAfazer = true
            aoNovaAgendaAcionada()
        }
    }

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
                CabecalhoDePagina("Calendário", RoxoCalendario, null) // tela principal: sem seta de voltar
                Spacer(modifier = Modifier.height(16.dp))
                MesSelectorCard(
                    nomeMes = nomeMesAno,
                    onMesAnterior = { mesAnoSelecionado = mesAnoSelecionado.minusMonths(1) },
                    onMesSeguinte = { mesAnoSelecionado = mesAnoSelecionado.plusMonths(1) }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ---------------- RESUMO DO DIA SELECIONADO (compacto) ----------------
                ResumoDiaCompacto(
                    rotulo = if (dataSelecionada == hoje) "HOJE" else "DIA SELECIONADO",
                    titulo = tituloDia,
                    pendentes = afazeresPendentes,
                    ganhos = formatoMoeda.format(ganhoDoDia),
                    gastos = formatoMoeda.format(gastoDoDia)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ---------------- GRADE DO MÊS ----------------
                Card(
                    shape = RoundedCornerShape(MaterialTheme.dimens.cardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = MaterialTheme.dimens.paddingMedium)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            val diasSemana = listOf("D", "S", "T", "Q", "Q", "S", "S")
                            diasSemana.forEachIndexed { indice, dia ->
                                Text(
                                    text = dia,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (indice == 0) Coral.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
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
                                            val ehHoje = dataAtualGrid == hoje
                                            val temDespesa = transacoesCalendario.any { it.transacao.data == dataAtualGrid && it.transacao.tipo == TipoTransacao.DESPESA }
                                            val temGanho = transacoesCalendario.any { it.transacao.data == dataAtualGrid && it.transacao.tipo == TipoTransacao.RECEITA }
                                            val temAfazer = listaAfazeres.any { it.data == dataAtualGrid && !it.concluido }

                                            val fundoDia by animateColorAsState(
                                                targetValue = if (isSelecionado) RoxoCalendario else Color.Transparent,
                                                animationSpec = tween(180),
                                                label = "fundoDia"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .padding(2.dp)
                                                    .clip(CircleShape)
                                                    .background(fundoDia)
                                                    .then(
                                                        if (ehHoje && !isSelecionado)
                                                            Modifier.border(1.5.dp, RoxoCalendario, CircleShape)
                                                        else Modifier
                                                    )
                                                    .clickable { dataSelecionada = dataAtualGrid },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = diaMesReal.toString(),
                                                        color = when {
                                                            isSelecionado -> Color.White
                                                            ehHoje -> RoxoCalendario
                                                            else -> MaterialTheme.colorScheme.onSurface
                                                        },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelecionado || ehHoje) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.height(4.dp)
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
                                                                Box(modifier = Modifier.size(4.dp).background(if (isSelecionado) Color.White else AmareloAgenda, CircleShape))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                // ---------------- ABAS ----------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(4.dp)
                ) {
                    TabPill(
                        texto = "Financeiro (${transacoesDoDia.size})",
                        selecionado = abaAtiva == 0,
                        modifier = Modifier.weight(1f),
                        cor = RoxoCalendario
                    ) { abaAtiva = 0 }
                    TabPill(
                        texto = "Agenda (${afazeresDoDia.size})",
                        selecionado = abaAtiva == 1,
                        modifier = Modifier.weight(1f),
                        cor = RoxoCalendario
                    ) { abaAtiva = 1 }
                }
                Spacer(modifier = Modifier.height(20.dp))
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
                        EstadoVazioCalendario(
                            icone = Icons.AutoMirrored.Filled.TrendingUp,
                            mensagem = "Nenhum lançamento financeiro neste dia."
                        )
                    }
                }
                items(transacoesDoDia) { itemCalendario ->
                    val t = itemCalendario.transacao
                    val categoria = categorias.find { it.id == t.categoriaId }
                    val cor = if (itemCalendario.ehFaturaNaoPaga) {
                        AmareloAgenda
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
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(cor.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    iconeParaCategoria(categoria?.nome ?: ""),
                                    contentDescription = null,
                                    tint = cor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    descricaoFinal,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    categoria?.nome ?: "Sem categoria",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                (if (t.tipo == TipoTransacao.DESPESA) "- " else "+ ") + formatoMoeda.format(t.valor),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = cor,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else {
                if (afazeresDoDia.isEmpty()) {
                    item {
                        EstadoVazioCalendario(
                            icone = Icons.Filled.EventAvailable,
                            mensagem = "Nenhum afazer cadastrado para este dia."
                        )
                    }
                }
                items(afazeresDoDia) { afazer ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (afazer.concluido)
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.alternarConcluido(afazer) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (afazer.concluido) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                    contentDescription = if (afazer.concluido) "Marcar como pendente" else "Concluir",
                                    tint = if (afazer.concluido) Verde else RoxoCalendario,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = afazer.titulo,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (afazer.concluido) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (afazer.concluido) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { viewModel.excluirAfazer(afazer) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Excluir",
                                    tint = Coral,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------- SHEET: NOVO AFAZER ----------------
    if (mostrarSheetNovoAfazer) {
        ModalBottomSheet(
            onDismissRequest = { mostrarSheetNovoAfazer = false },
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
                        "Adicionar Afazer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { mostrarSheetNovoAfazer = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Agendado para: ${dataSelecionada.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoxoCalendario,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = textoNovoAfazer,
                    onValueChange = { textoNovoAfazer = it },
                    label = { Text("Descreva a tarefa...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoxoCalendario,
                        focusedLabelColor = RoxoCalendario,
                        cursorColor = RoxoCalendario
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = {
                        if (textoNovoAfazer.isNotBlank()) {
                            viewModel.inserirAfazer(dataSelecionada, textoNovoAfazer.trim())
                            textoNovoAfazer = ""
                            mostrarSheetNovoAfazer = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                        .height(52.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoxoCalendario, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Salvar na Agenda", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun EstadoVazioCalendario(icone: androidx.compose.ui.graphics.vector.ImageVector, mensagem: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                mensagem,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TabPill(
    texto: String,
    selecionado: Boolean,
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selecionado) cor else Color.Transparent,
        animationSpec = tween(200),
        label = "fundoTabPill"
    )
    val textColor = if (selecionado) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}