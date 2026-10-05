package com.example.financacelular.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.AfazerEntity
import com.example.financacelular.data.CalendarService
import com.example.financacelular.data.EventoGoogleAgenda
import com.example.financacelular.data.ResultadoCalendario
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
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
    val context = LocalContext.current
    val transacoesCalendario by viewModel.transacoesCalendario.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    val listaAfazeres by viewModel.afazeres.collectAsState()
    val eventosGoogle by viewModel.eventosGoogle.collectAsState()
    val diasComEventosGoogle by viewModel.diasComEventosGoogle.collectAsState()
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(LocalePtBrCalendario) }

    var mesAnoSelecionado by remember { mutableStateOf(YearMonth.now()) }
    var dataSelecionada by remember { mutableStateOf(LocalDate.now()) }
    var abaAtiva by remember { mutableStateOf(0) }

    var mostrarSheetNovoAfazer by remember { mutableStateOf(false) }
    var textoNovoAfazer by remember { mutableStateOf("") }
    var sincronizarGoogle by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hoje = remember { LocalDate.now() }

    // Salva o afazer (e sincroniza com o Google Agenda, se pedido) e avisa o resultado ao usuário
    val salvarAfazer: (NovoItemAgenda, Boolean) -> Unit = { novo, sincronizar ->
        viewModel.inserirAfazerESincronizar(
            data = novo.data,
            titulo = novo.titulo,
            sincronizarGoogle = sincronizar,
            hora = novo.hora,
            diaInteiro = novo.diaInteiro,
            horaFim = novo.horaFim
        ) { resultado ->
            val mensagem = when (resultado) {
                null -> "Afazer salvo com sucesso!"
                ResultadoCalendario.SUCESSO -> "Salvo no Google Agenda!"
                ResultadoCalendario.SEM_PERMISSAO -> "Salvo só no app: sem permissão para usar o Google Agenda."
                ResultadoCalendario.SEM_CALENDARIO -> "Salvo só no app: conecte sua conta Google (a mesma do Drive) para usar o Google Agenda."
                ResultadoCalendario.ERRO -> "Salvo só no app: não foi possível criar o evento no Google Agenda."
            }
            Toast.makeText(context, mensagem, Toast.LENGTH_SHORT).show()
        }
    }

    // Afazer aguardando o usuário responder ao pedido de permissão do calendário
    var afazerPendente by remember { mutableStateOf<NovoItemAgenda?>(null) }

    // Opções do novo item quando ele é salvo no Google Agenda
    var diaInteiroNovo by remember { mutableStateOf(false) }
    var horaNovo by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var horaFimNovo by remember { mutableStateOf(LocalTime.of(10, 0)) }
    // Data do novo item: começa no dia selecionado e pode ser trocada no seletor
    var dataNovo by remember(dataSelecionada, mostrarSheetNovoAfazer) { mutableStateOf(dataSelecionada) }

    // Item aberto para edição e item aguardando confirmação de exclusão
    var itemEmEdicao by remember { mutableStateOf<ItemAgenda?>(null) }
    var itemParaExcluir by remember { mutableStateOf<ItemAgenda?>(null) }

    val launcherPermissaoCalendario = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        val leitura = resultado[Manifest.permission.READ_CALENDAR] == true
        val escrita = resultado[Manifest.permission.WRITE_CALENDAR] == true
        if (leitura) {
            viewModel.carregarEventosGoogle(dataSelecionada)
            viewModel.carregarDiasComEventos(mesAnoSelecionado)
        }
        afazerPendente?.let { novo ->
            salvarAfazer(novo, leitura && escrita)
        }
        afazerPendente = null
    }

    // Pede a permissão do calendário na primeira vez que a tela abre
    LaunchedEffect(Unit) {
        if (!CalendarService.temPermissaoLeitura(context)) {
            launcherPermissaoCalendario.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    // Atualiza os eventos do Google Agenda quando a data for alterada
    LaunchedEffect(dataSelecionada) {
        viewModel.carregarEventosGoogle(dataSelecionada)
    }

    // Atualiza os pontinhos do calendário quando o mês for alterado
    LaunchedEffect(mesAnoSelecionado) {
        viewModel.carregarDiasComEventos(mesAnoSelecionado)
    }

    val nomeMesAno = remember(mesAnoSelecionado) {
        mesAnoSelecionado.month.getDisplayName(TextStyle.FULL, LocalePtBrCalendario)
            .replaceFirstChar { it.uppercase() } + " " + mesAnoSelecionado.year
    }

    val transacoesDoDia = transacoesCalendario.filter { it.transacao.data == dataSelecionada }
    val afazeresDoDia = listaAfazeres.filter { it.data == dataSelecionada }

    // Evento criado pelo app já aparece como afazer: esconde a cópia vinda do Google Agenda
    val eventosGoogleVisiveis = eventosGoogle.filterNot { evento ->
        evento.descricao == CalendarService.DESCRICAO_EVENTO_APP &&
                afazeresDoDia.any { it.titulo == evento.titulo }
    }

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
                CabecalhoDePagina("Calendário", RoxoCalendario, null)
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
                                            val temAgenda = temAfazer || dataAtualGrid in diasComEventosGoogle

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
                                                            if (temAgenda) {
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
                        texto = "Agenda (${afazeresDoDia.size + eventosGoogleVisiveis.size})",
                        selecionado = abaAtiva == 1,
                        modifier = Modifier.weight(1f),
                        cor = RoxoCalendario
                    ) { abaAtiva = 1 }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (abaAtiva == 0) "Lançamentos Financeiros" else "Afazeres e Eventos",
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
                // ---------------- LISTA UNIFICADA: EVENTOS DO GOOGLE AGENDA + AFAZERES ----------------
                if (afazeresDoDia.isEmpty() && eventosGoogleVisiveis.isEmpty()) {
                    item {
                        EstadoVazioCalendario(
                            icone = Icons.Filled.EventAvailable,
                            mensagem = "Nenhum afazer ou evento cadastrado para este dia."
                        )
                    }
                }

                items(eventosGoogleVisiveis) { evento ->
                    CartaoAgenda(
                        titulo = evento.titulo,
                        horario = textoHorarioEvento(evento) + if (evento.editavel) "" else " · Somente leitura",
                        descricao = evento.descricao?.takeIf { it != CalendarService.DESCRICAO_EVENTO_APP },
                        onClick = {
                            if (evento.editavel) {
                                itemEmEdicao = ItemAgenda.Evento(evento)
                            } else {
                                val aviso = if (evento.recorrente) {
                                    "Evento recorrente: edite pelo app Google Agenda."
                                } else {
                                    "Este calendário é somente leitura."
                                }
                                Toast.makeText(context, aviso, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                items(afazeresDoDia) { afazer ->
                    CartaoAgenda(
                        titulo = afazer.titulo,
                        horario = "Afazer",
                        descricao = null,
                        concluido = afazer.concluido,
                        onClick = { itemEmEdicao = ItemAgenda.Afazer(afazer) },
                        onClickIcone = { viewModel.alternarConcluido(afazer) }
                    )
                }
            }
        }
    }

    // ---------------- SHEET: EDITAR ITEM ----------------
    itemEmEdicao?.let { item ->
        FolhaEdicaoAgenda(
            item = item,
            onDismiss = { itemEmEdicao = null },
            onSalvar = { titulo, descricao, data, hora, horaFim, diaInteiro ->
                when (item) {
                    is ItemAgenda.Evento -> viewModel.atualizarEvento(
                        evento = item.evento,
                        titulo = titulo,
                        descricao = descricao,
                        data = data,
                        hora = hora,
                        horaFim = horaFim,
                        diaInteiro = diaInteiro
                    ) { resultado ->
                        Toast.makeText(context, mensagemEdicao(resultado, "Evento atualizado!"), Toast.LENGTH_SHORT).show()
                    }
                    is ItemAgenda.Afazer -> {
                        viewModel.atualizarAfazer(item.afazer, titulo, data)
                        Toast.makeText(context, "Afazer atualizado!", Toast.LENGTH_SHORT).show()
                    }
                }
                itemEmEdicao = null
            },
            onExcluir = {
                itemParaExcluir = item
                itemEmEdicao = null
            }
        )
    }

    // ---------------- SHEET: CONFIRMAR EXCLUSÃO ----------------
    itemParaExcluir?.let { item ->
        FolhaConfirmarExclusao(
            item = item,
            onDismiss = { itemParaExcluir = null },
            onConfirmar = {
                when (item) {
                    is ItemAgenda.Evento -> viewModel.excluirEvento(item.evento) { resultado ->
                        Toast.makeText(context, mensagemEdicao(resultado, "Evento excluído."), Toast.LENGTH_SHORT).show()
                    }
                    is ItemAgenda.Afazer -> viewModel.excluirAfazer(item.afazer)
                }
                itemParaExcluir = null
            }
        )
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
                    .verticalScroll(rememberScrollState())
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
                Spacer(modifier = Modifier.height(16.dp))
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
                Spacer(modifier = Modifier.height(16.dp))

                // Switch para opção de Sincronização com o Google Agenda
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = RoxoCalendario,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Salvar no Google Agenda",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Switch(
                        checked = sincronizarGoogle,
                        onCheckedChange = { sincronizarGoogle = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = RoxoCalendario
                        )
                    )
                }

                // Horário ou dia inteiro (só vale para o que vai para o Google Agenda)
                if (sincronizarGoogle) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dia inteiro", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = diaInteiroNovo,
                            onCheckedChange = { diaInteiroNovo = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RoxoCalendario
                            )
                        )
                    }

                }

                Spacer(modifier = Modifier.height(8.dp))
                SeletorDataHora(
                    data = dataNovo,
                    hora = horaNovo,
                    horaFim = horaFimNovo,
                    diaInteiro = diaInteiroNovo,
                    mostrarHorario = sincronizarGoogle,
                    onData = { dataNovo = it },
                    onHorario = { inicio, fim -> horaNovo = inicio; horaFimNovo = fim }
                )

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (textoNovoAfazer.isNotBlank()) {
                            val novo = NovoItemAgenda(
                                titulo = textoNovoAfazer.trim(),
                                data = dataNovo,
                                hora = horaNovo,
                                horaFim = horaFimNovo,
                                diaInteiro = diaInteiroNovo
                            )

                            if (sincronizarGoogle && !CalendarService.temPermissaoEscrita(context)) {
                                // Pede a permissão e salva o item assim que o usuário responder
                                afazerPendente = novo
                                launcherPermissaoCalendario.launch(
                                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                                )
                            } else {
                                salvarAfazer(novo, sincronizarGoogle)
                            }

                            textoNovoAfazer = ""
                            diaInteiroNovo = false
                            horaNovo = LocalTime.of(9, 0)
                            horaFimNovo = LocalTime.of(10, 0)
                            mostrarSheetNovoAfazer = false
                        }
                    },
                    enabled = textoNovoAfazer.isNotBlank() &&
                            !(sincronizarGoogle && !diaInteiroNovo && horaFimNovo == horaNovo),
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

/** Dados de um item novo, guardados enquanto o usuário responde ao pedido de permissão. */
private data class NovoItemAgenda(
    val titulo: String,
    val data: LocalDate,
    val hora: LocalTime,
    val horaFim: LocalTime,
    val diaInteiro: Boolean
)

/** Item da aba Agenda que pode ser editado: afazer local ou evento do Google Agenda. */
private sealed interface ItemAgenda {
    data class Afazer(val afazer: AfazerEntity) : ItemAgenda
    data class Evento(val evento: EventoGoogleAgenda) : ItemAgenda
}

/** Cartão da aba Agenda no estilo do Google Agenda: usado por afazeres e eventos. */
@Composable
private fun CartaoAgenda(
    titulo: String,
    horario: String,
    descricao: String?,
    onClick: () -> Unit,
    concluido: Boolean = false,
    onClickIcone: (() -> Unit)? = null
) {
    val formaCartao = RoundedCornerShape(20.dp)
    val corIcone = if (concluido) Verde else RoxoCalendario

    Card(
        shape = formaCartao,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, RoxoCalendario.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(formaCartao)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(corIcone.copy(alpha = 0.15f))
                    .then(if (onClickIcone != null) Modifier.clickable(onClick = onClickIcone) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (concluido) Icons.Filled.Check else Icons.Filled.Event,
                    contentDescription = if (onClickIcone != null) {
                        if (concluido) "Marcar como pendente" else "Concluir"
                    } else null,
                    tint = corIcone,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (concluido) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (concluido) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = horario,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (descricao != null) {
                    Text(
                        text = descricao,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Folha para editar (ou excluir) um afazer ou um evento do Google Agenda. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaEdicaoAgenda(
    item: ItemAgenda,
    onDismiss: () -> Unit,
    onSalvar: (titulo: String, descricao: String?, data: LocalDate, hora: LocalTime, horaFim: LocalTime, diaInteiro: Boolean) -> Unit,
    onExcluir: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val evento = (item as? ItemAgenda.Evento)?.evento
    val afazer = (item as? ItemAgenda.Afazer)?.afazer

    var titulo by remember(item) { mutableStateOf(evento?.titulo ?: afazer?.titulo ?: "") }
    var descricao by remember(item) {
        mutableStateOf(evento?.descricao?.takeIf { it != CalendarService.DESCRICAO_EVENTO_APP } ?: "")
    }
    var data by remember(item) { mutableStateOf(evento?.dataInicio ?: afazer?.data ?: LocalDate.now()) }
    var hora by remember(item) {
        mutableStateOf(if (evento != null && !evento.diaInteiro) horaDeMillis(evento.horaInicioMillis) else LocalTime.of(9, 0))
    }
    var horaFim by remember(item) {
        mutableStateOf(
            if (evento != null && !evento.diaInteiro && evento.horaFimMillis > evento.horaInicioMillis) {
                horaDeMillis(evento.horaFimMillis)
            } else {
                hora.plusHours(1)
            }
        )
    }
    var diaInteiro by remember(item) { mutableStateOf(evento?.diaInteiro ?: false) }

    val coresCampo = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = RoxoCalendario,
        focusedLabelColor = RoxoCalendario,
        cursorColor = RoxoCalendario
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (evento != null) "Editar evento" else "Editar afazer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Título") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = coresCampo,
                modifier = Modifier.fillMaxWidth()
            )

            if (evento != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("Descrição") },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = coresCampo,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (evento != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dia inteiro", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = diaInteiro,
                        onCheckedChange = { diaInteiro = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = RoxoCalendario
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SeletorDataHora(
                data = data,
                hora = hora,
                horaFim = horaFim,
                diaInteiro = diaInteiro,
                mostrarHorario = evento != null,
                onData = { data = it },
                onHorario = { inicio, fim -> hora = inicio; horaFim = fim }
            )

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    val descricaoFinal = if (evento != null) {
                        // Mantém a marca interna dos eventos criados pelo app quando o campo fica vazio
                        descricao.trim().ifEmpty {
                            if (evento.descricao == CalendarService.DESCRICAO_EVENTO_APP) CalendarService.DESCRICAO_EVENTO_APP else ""
                        }
                    } else null
                    onSalvar(titulo.trim(), descricaoFinal, data, hora, horaFim, diaInteiro)
                },
                enabled = titulo.isNotBlank() && !(evento != null && !diaInteiro && horaFim == hora),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoxoCalendario, contentColor = Color.White)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar alterações", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onExcluir,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (evento != null) "Excluir evento" else "Excluir afazer",
                    color = Coral,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Folha (abre de baixo para cima) para confirmar a exclusão de um afazer ou evento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaConfirmarExclusao(
    item: ItemAgenda,
    onConfirmar: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ehEvento = item is ItemAgenda.Evento
    val tituloItem = when (item) {
        is ItemAgenda.Evento -> item.evento.titulo
        is ItemAgenda.Afazer -> item.afazer.titulo
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Coral.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = Coral,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (ehEvento) "Excluir evento?" else "Excluir afazer?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, Coral.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    tituloItem,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                if (ehEvento) {
                    "Ele também será removido do Google Agenda. Essa ação não pode ser desfeita."
                } else {
                    "Esse afazer será removido. Essa ação não pode ser desfeita."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onConfirmar,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White)
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Excluir", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Cancelar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private enum class DialogoSeletor { DATA, INICIO, FIM }

/**
 * Cartão para escolher data e horário: uma linha com a data e dois blocos grandes (Início e Fim),
 * com atalhos de duração. Se o fim for antes do início, o evento termina no dia seguinte.
 * Ao mudar o início, o fim acompanha mantendo a mesma duração.
 */
@Composable
private fun SeletorDataHora(
    data: LocalDate,
    hora: LocalTime,
    horaFim: LocalTime,
    diaInteiro: Boolean,
    mostrarHorario: Boolean,
    onData: (LocalDate) -> Unit,
    onHorario: (inicio: LocalTime, fim: LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var dialogo by remember { mutableStateOf<DialogoSeletor?>(null) }
    val formatoHora = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val textoData = remember(data) {
        data.format(DateTimeFormatter.ofPattern("EEE, dd 'de' MMM 'de' yyyy", LocalePtBrCalendario))
            .replaceFirstChar { it.uppercase() }
    }
    val duracaoMin = ((Duration.between(hora, horaFim).toMinutes() % 1440) + 1440) % 1440

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, RoxoCalendario.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { dialogo = DialogoSeletor.DATA },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(RoxoCalendario.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = RoxoCalendario, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Data",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        textoData,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Alterar",
                    style = MaterialTheme.typography.labelMedium,
                    color = RoxoCalendario,
                    fontWeight = FontWeight.Bold
                )
            }

            if (mostrarHorario && !diaInteiro) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = RoxoCalendario.copy(alpha = 0.15f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    BlocoHorario(
                        rotulo = "Início",
                        texto = hora.format(formatoHora),
                        modifier = Modifier.weight(1f)
                    ) { dialogo = DialogoSeletor.INICIO }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(18.dp)
                    )
                    BlocoHorario(
                        rotulo = "Fim",
                        texto = horaFim.format(formatoHora),
                        erro = duracaoMin == 0L,
                        modifier = Modifier.weight(1f)
                    ) { dialogo = DialogoSeletor.FIM }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30L to "30 min", 60L to "1 h", 120L to "2 h").forEach { (minutos, rotulo) ->
                        ChipDuracao(rotulo, selecionado = duracaoMin == minutos) {
                            onHorario(hora, hora.plusMinutes(minutos))
                        }
                    }
                }

                if (duracaoMin == 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "O fim precisa ser diferente do início.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Coral
                    )
                } else if (horaFim.isBefore(hora)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Termina no dia seguinte.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    when (dialogo) {
        DialogoSeletor.DATA -> DialogoData(
            inicial = data,
            onConfirmar = { onData(it); dialogo = null },
            onDismiss = { dialogo = null }
        )
        DialogoSeletor.INICIO -> DialogoHora(
            titulo = "Horário de início",
            inicial = hora,
            onConfirmar = { nova ->
                val duracao = if (duracaoMin == 0L) 60L else duracaoMin
                onHorario(nova, nova.plusMinutes(duracao))
                dialogo = null
            },
            onDismiss = { dialogo = null }
        )
        DialogoSeletor.FIM -> DialogoHora(
            titulo = "Horário de término",
            inicial = horaFim,
            onConfirmar = { onHorario(hora, it); dialogo = null },
            onDismiss = { dialogo = null }
        )
        null -> Unit
    }
}

@Composable
private fun BlocoHorario(
    rotulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    erro: Boolean = false,
    onClick: () -> Unit
) {
    val cor = if (erro) Coral else RoxoCalendario
    val forma = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(forma)
            .background(cor.copy(alpha = 0.10f))
            .border(1.dp, cor.copy(alpha = 0.25f), forma)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            rotulo,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = cor
        )
        Text(
            texto,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChipDuracao(texto: String, selecionado: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selecionado) RoxoCalendario else RoxoCalendario.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selecionado) Color.White else RoxoCalendario
        )
    }
}

/** Relógio do Material 3 dentro de um diálogo arredondado, em formato 24 h. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoHora(
    titulo: String,
    inicial: LocalTime,
    onConfirmar: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val estado = rememberTimePickerState(
        initialHour = inicial.hour,
        initialMinute = inicial.minute,
        is24Hour = true
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )
                TimePicker(
                    state = estado,
                    colors = TimePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        // Mostrador e ponteiro
                        clockDialColor = RoxoCalendario.copy(alpha = 0.10f),
                        selectorColor = RoxoCalendario,
                        clockDialSelectedContentColor = Color.White,
                        clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                        // Caixas de hora e minuto
                        timeSelectorSelectedContainerColor = RoxoCalendario.copy(alpha = 0.15f),
                        timeSelectorSelectedContentColor = RoxoCalendario,
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirmar(LocalTime.of(estado.hour, estado.minute)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoxoCalendario, contentColor = Color.White)
                    ) { Text("OK", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Calendário do Material 3 (o seletor devolve a data em UTC, por isso a conversão). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoData(
    inicial: LocalDate,
    onConfirmar: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
        confirmButton = {
            Button(
                onClick = {
                    estado.selectedDateMillis?.let {
                        onConfirmar(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    } ?: onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoxoCalendario, contentColor = Color.White)
            ) { Text("OK", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    ) {
        DatePicker(
            state = estado,
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                selectedDayContainerColor = RoxoCalendario,
                selectedDayContentColor = Color.White,
                todayDateBorderColor = RoxoCalendario,
                todayContentColor = RoxoCalendario,
                selectedYearContainerColor = RoxoCalendario,
                selectedYearContentColor = Color.White,
                currentYearContentColor = RoxoCalendario
            )
        )
    }
}

private fun mensagemEdicao(resultado: ResultadoCalendario, sucesso: String): String = when (resultado) {
    ResultadoCalendario.SUCESSO -> sucesso
    ResultadoCalendario.SEM_PERMISSAO -> "Sem permissão para alterar o Google Agenda."
    ResultadoCalendario.SEM_CALENDARIO -> "Nenhum calendário da conta Google conectada."
    ResultadoCalendario.ERRO -> "Não foi possível alterar o evento no Google Agenda."
}

private fun horaDeMillis(millis: Long): LocalTime =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .withSecond(0)
        .withNano(0)

/** "Dia inteiro" ou o intervalo de horário (HH:mm – HH:mm) de um evento do Google Agenda. */
private fun textoHorarioEvento(evento: EventoGoogleAgenda): String {
    if (evento.diaInteiro) return "Dia inteiro"
    val formato = DateTimeFormatter.ofPattern("HH:mm")
    val inicio = horaDeMillis(evento.horaInicioMillis).format(formato)
    return if (evento.horaFimMillis > evento.horaInicioMillis) {
        inicio + " – " + horaDeMillis(evento.horaFimMillis).format(formato)
    } else {
        inicio
    }
}