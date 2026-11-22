package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.CalendarService
import com.example.financacelular.data.ResultadoCalendario
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.AmareloAgenda
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter


/** Resumo do dia selecionado: no mesmo desenho do cartão principal da Análise, só o essencial. */
@Composable
private fun ResumoDiaCompacto(
    titulo: String,
    pendentes: Int,
    saldo: String,
    ganhos: String,
    gastos: String
) {
    CartaoHeroDePagina(cor = RoxoHero) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (pendentes > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                PilulaDePagina(if (pendentes == 1) "1 pendente" else "$pendentes pendentes")
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        ValorDoHero(saldo)
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            ValorDoDia(Icons.Outlined.ArrowUpward, Verde, ganhos, Modifier.weight(1f).padding(end = 14.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline)
            )
            ValorDoDia(Icons.Outlined.ArrowDownward, Coral, gastos, Modifier.weight(1f).padding(start = 14.dp))
        }
    }
}

@Composable
private fun ValorDoDia(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    cor: Color,
    valor: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(24.dp).background(cor.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(Tamanho.iconePequeno))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            valor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarioScreen(
    viewModel: CalendarioViewModel = viewModel(),
    acionarNovaAgendaExterno: Boolean = false,
    aoNovaAgendaAcionada: () -> Unit = {},
    estadoLista: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val mensagem = LocalMensagem.current
    val transacoesCalendario by viewModel.transacoesCalendario.collectAsStateWithLifecycle()
    val categorias by viewModel.categorias.collectAsStateWithLifecycle()
    val afazeresCarregados by viewModel.afazeres.collectAsStateWithLifecycle()
    val listaAfazeres = afazeresCarregados.orEmpty()
    val eventosGoogle by viewModel.eventosGoogle.collectAsStateWithLifecycle()
    val diasComEventosGoogle by viewModel.diasComEventosGoogle.collectAsStateWithLifecycle()
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(LocalePtBr) }

    val mesAnoSelecionado by MesGlobal.mes.collectAsStateWithLifecycle()
    var dataSelecionada by remember { mutableStateOf(LocalDate.now()) }
    // Mês trocado (setas ou outra tela): o dia selecionado acompanha (hoje, se estiver no mês; senão dia 1)
    LaunchedEffect(mesAnoSelecionado) {
        if (java.time.YearMonth.from(dataSelecionada) != mesAnoSelecionado) {
            val agora = LocalDate.now()
            dataSelecionada = if (java.time.YearMonth.from(agora) == mesAnoSelecionado) agora else mesAnoSelecionado.atDay(1)
        }
    }
    var abaAtiva by rememberSaveable { mutableIntStateOf(0) }

    var mostrarSheetNovoAfazer by rememberSaveable { mutableStateOf(false) }
    var textoNovoAfazer by rememberSaveable { mutableStateOf("") }
    // Só começa ligado se o app já tem permissão; ao ligar sem permissão, ela é pedida
    var sincronizarGoogle by rememberSaveable { mutableStateOf(CalendarService.temPermissaoEscrita(context)) }

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
            val (texto, erro) = avisoAfazerSalvo(resultado)
            mensagem(texto, erro)
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
    var escolhendoDataNovo by remember(mostrarSheetNovoAfazer) { mutableStateOf(false) }

    // Item aberto para edição (a confirmação de exclusão acontece dentro da própria folha)
    var itemEmEdicao by remember { mutableStateOf<ItemAgenda?>(null) }

    val launcherPermissaoCalendario = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        Permissao.CALENDARIO.marcarPedida(context)
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

    // Atualiza os eventos do Google Agenda quando a data for alterada
    LaunchedEffect(dataSelecionada) {
        viewModel.carregarEventosGoogle(dataSelecionada)
    }

    // Atualiza os pontinhos do calendário quando o mês for alterado
    LaunchedEffect(mesAnoSelecionado) {
        viewModel.carregarDiasComEventos(mesAnoSelecionado)
    }

    // Agrupa uma vez por data (a grade do mês consulta isto para cada dia)
    val itensPorData = remember(transacoesCalendario) { transacoesCalendario.orEmpty().groupBy { it.transacao.data } }
    val formatoDataCompleta = remember { DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", LocalePtBr) }
    val transacoesDoDia = itensPorData[dataSelecionada].orEmpty()
    val afazeresDoDia = listaAfazeres.filter { it.data == dataSelecionada }

    // Evento criado pelo app já aparece como afazer: esconde a cópia vinda do Google Agenda
    val eventosGoogleVisiveis = eventosGoogle.filterNot { evento ->
        evento.descricao == CalendarService.DESCRICAO_EVENTO_APP &&
                afazeresDoDia.any { it.titulo == evento.titulo }
    }

    // Pagamentos de fatura e faturas em aberto não entram nas somas: o valor já está nas compras do cartão
    val gastoDoDia = transacoesDoDia
        .filter { it.contaNosTotais && it.transacao.tipo == TipoTransacao.DESPESA }
        .sumOf { it.transacao.valor }
    val ganhoDoDia = transacoesDoDia
        .filter { it.contaNosTotais && it.transacao.tipo == TipoTransacao.RECEITA }
        .sumOf { it.transacao.valor }
    val afazeresPendentes = afazeresDoDia.count { !it.concluido }

    val tituloDia = remember(dataSelecionada) {
        dataSelecionada.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", LocalePtBr))
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
            state = estadoLista,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.dimens.paddingScreen),
            contentPadding = PaddingValues(bottom = espacoParaBarraFlutuante())
        ) {
            espacoDoCabecalho()
            item {
                // ---------------- RESUMO DO DIA SELECIONADO (compacto) ----------------
                ResumoDiaCompacto(
                    titulo = tituloDia,
                    pendentes = afazeresPendentes,
                    saldo = formatoMoeda.format(ganhoDoDia - gastoDoDia),
                    ganhos = formatoMoeda.format(ganhoDoDia),
                    gastos = formatoMoeda.format(gastoDoDia)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ---------------- GRADE DO MÊS ----------------
                Card(
                    shape = RoundedCornerShape(MaterialTheme.dimens.cardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                    color = if (indice == 0) MaterialTheme.cores.textoGasto else MaterialTheme.colorScheme.onSurfaceVariant
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
                                            val itensDoDia = itensPorData[dataAtualGrid].orEmpty()
                                            val temDespesa = itensDoDia.any { it.contaNosTotais && it.transacao.tipo == TipoTransacao.DESPESA }
                                            val temGanho = itensDoDia.any { it.contaNosTotais && it.transacao.tipo == TipoTransacao.RECEITA }
                                            val temFaturaAberta = itensDoDia.any { it.ehFaturaNaoPaga }
                                            val temAfazer = listaAfazeres.any { it.data == dataAtualGrid && !it.concluido }
                                            val temAgenda = temAfazer || dataAtualGrid in diasComEventosGoogle

                                            val fundoDia by animateColorAsState(
                                                targetValue = if (isSelecionado) RoxoHero else Color.Transparent,
                                                animationSpec = tween(180),
                                                label = "fundoDia"
                                            )

                                            val quantidadeDoDia = if (abaAtiva == 0) {
                                                itensDoDia.size.takeIf { it > 0 }?.let { if (it == 1) "1 lançamento" else "$it lançamentos" }
                                            } else {
                                                val afazeresDoDiaGrade = listaAfazeres.count { it.data == dataAtualGrid && !it.concluido }
                                                when {
                                                    afazeresDoDiaGrade > 0 -> if (afazeresDoDiaGrade == 1) "1 afazer" else "$afazeresDoDiaGrade afazeres"
                                                    temAgenda -> "com eventos"
                                                    else -> null
                                                }
                                            }
                                            val descricaoDia = dataAtualGrid.format(formatoDataCompleta) +
                                                (quantidadeDoDia?.let { ", $it" } ?: "")

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .padding(2.dp)
                                                    .clip(CircleShape)
                                                    .background(fundoDia)
                                                    .then(
                                                        if (ehHoje && !isSelecionado)
                                                            Modifier.border(1.5.dp, RoxoHero, CircleShape)
                                                        else Modifier
                                                    )
                                                    .semantics {
                                                        contentDescription = descricaoDia
                                                        selected = isSelecionado
                                                    }
                                                    .clickable(role = Role.Button) { dataSelecionada = dataAtualGrid },
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
                                                            ehHoje -> RoxoHero
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
                                                            if (temFaturaAberta) {
                                                                Box(modifier = Modifier.size(4.dp).background(if (isSelecionado) Color.White else AmareloAgenda, CircleShape))
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
                SeletorQuadrado(
                    opcoes = listOf(
                        "Financeiro (${transacoesDoDia.size})",
                        "Agenda (${afazeresDoDia.size + eventosGoogleVisiveis.size})"
                    ),
                    indiceSelecionado = abaAtiva,
                    aoSelecionar = { abaAtiva = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (abaAtiva == 0) {
                if (transacoesCalendario != null && transacoesDoDia.isEmpty()) {
                    item {
                        EstadoVazio(
                            icone = Icons.AutoMirrored.Outlined.TrendingUp,
                            mensagem = "Nenhum lançamento financeiro neste dia."
                        )
                    }
                }
                if (transacoesDoDia.isNotEmpty()) {
                    item {
                        GrupoDeLista(transacoesDoDia) { itemCalendario ->
                            val t = itemCalendario.transacao
                            val categoria = categorias.find { it.id == t.categoriaId }
                            val nomeCategoria = itemCalendario.categoriaNome ?: categoria?.nome
                            val cor = when {
                                itemCalendario.ehFaturaNaoPaga -> AmareloAgenda
                                itemCalendario.ehPagamentoFatura -> MaterialTheme.colorScheme.onSurfaceVariant
                                t.tipo == TipoTransacao.DESPESA -> Coral
                                else -> Verde
                            }
                            val corValor = when {
                                itemCalendario.ehFaturaNaoPaga -> MaterialTheme.cores.alerta
                                itemCalendario.ehPagamentoFatura -> MaterialTheme.colorScheme.onSurfaceVariant
                                t.tipo == TipoTransacao.DESPESA -> MaterialTheme.cores.textoGasto
                                else -> MaterialTheme.cores.textoGanho
                            }
                            val prefixoValor = when {
                                itemCalendario.ehFaturaNaoPaga || itemCalendario.ehPagamentoFatura -> ""
                                t.tipo == TipoTransacao.DESPESA -> "- "
                                else -> "+ "
                            }
                            LinhaDeLista(
                                icone = iconeParaCategoria(nomeCategoria ?: "", categoria?.icone),
                                cor = cor,
                                titulo = itemCalendario.titulo,
                                subtitulo = itemCalendario.subtitulo ?: nomeCategoria ?: "Sem categoria",
                                valor = prefixoValor + formatoMoeda.format(t.valor),
                                corValor = corValor
                            )
                        }
                    }
                }
            } else {
                // ---------------- LISTA UNIFICADA: EVENTOS DO GOOGLE AGENDA + AFAZERES ----------------
                if (afazeresCarregados != null && afazeresDoDia.isEmpty() && eventosGoogleVisiveis.isEmpty()) {
                    item {
                        EstadoVazio(
                            icone = Icons.Outlined.EventAvailable,
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
                                    "Edite pelo app Google Agenda"
                                } else {
                                    "Calendário só de leitura"
                                }
                                mensagem(aviso, erro = true)
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

    // ---------------- SHEET: EDITAR ITEM (a exclusão é confirmada dentro dele) ----------------
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
                        val (texto, erro) = mensagemEdicao(resultado, "Evento atualizado")
                        mensagem(texto, erro)
                    }
                    is ItemAgenda.Afazer -> {
                        viewModel.atualizarAfazer(item.afazer, titulo, data)
                        mensagem("Afazer atualizado")
                    }
                }
                itemEmEdicao = null
            },
            onExcluir = {
                when (item) {
                    is ItemAgenda.Evento -> viewModel.excluirEvento(item.evento) { resultado ->
                        val (texto, erro) = mensagemEdicao(resultado, "Evento excluído")
                        mensagem(texto, erro)
                    }
                    is ItemAgenda.Afazer -> viewModel.excluirAfazer(item.afazer)
                }
                itemEmEdicao = null
            }
        )
    }

    // ---------------- SHEET: NOVO AFAZER ----------------
    if (mostrarSheetNovoAfazer) {
        FolhaApp(onDismiss = { mostrarSheetNovoAfazer = false; escolhendoDataNovo = false }) {
          TrocaPorData(
            escolhendo = escolhendoDataNovo,
            data = dataNovo,
            aoEscolher = { dataNovo = it },
            aoVoltar = { escolhendoDataNovo = false }
          ) {
          Column(Modifier.fillMaxWidth()) {
            CabecalhoDeFolha(titulo = "Adicionar Afazer", aoFechar = { mostrarSheetNovoAfazer = false; escolhendoDataNovo = false })
            Spacer(modifier = Modifier.height(16.dp))
            CampoApp(valor = textoNovoAfazer, aoMudar = { textoNovoAfazer = it }, rotulo = "Tarefa")
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
                        Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = RoxoHero,
                        modifier = Modifier.size(Tamanho.icone)
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
                    onCheckedChange = { ativar ->
                        sincronizarGoogle = ativar
                        if (ativar && !CalendarService.temPermissaoEscrita(context)) {
                            launcherPermissaoCalendario.launch(
                                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = RoxoHero
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
                            checkedTrackColor = RoxoHero
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
                aoEscolherData = { escolhendoDataNovo = true },
                onHorario = { inicio, fim -> horaNovo = inicio; horaFimNovo = fim }
            )

            Spacer(modifier = Modifier.height(24.dp))
            BotaoPrincipal(
                texto = "Salvar na Agenda",
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
                modifier = Modifier.fillMaxWidth(),
                cor = RoxoHero,
                corConteudo = Color.White,
                enabled = textoNovoAfazer.isNotBlank() &&
                        !(sincronizarGoogle && !diaInteiroNovo && horaFimNovo == horaNovo)
            )
          }
          }
        }
    }
}

/** Texto do aviso após salvar um afazer; `second` = erro. */
internal fun avisoAfazerSalvo(resultado: ResultadoCalendario?): Pair<String, Boolean> = when (resultado) {
    null -> "Afazer salvo" to false
    ResultadoCalendario.SUCESSO -> "Salvo no Google Agenda" to false
    ResultadoCalendario.SEM_PERMISSAO -> "Salvo só no app: sem acesso à Agenda" to true
    ResultadoCalendario.SEM_CALENDARIO -> "Salvo só no app: conecte sua conta Google" to true
    ResultadoCalendario.ERRO -> "Salvo só no app: falha na Agenda" to true
}

/** Texto do aviso após editar/excluir um evento; `second` = erro. */
internal fun mensagemEdicao(resultado: ResultadoCalendario, sucesso: String): Pair<String, Boolean> = when (resultado) {
    ResultadoCalendario.SUCESSO -> sucesso to false
    ResultadoCalendario.SEM_PERMISSAO -> "Sem acesso à Agenda" to true
    ResultadoCalendario.SEM_CALENDARIO -> "Nenhum calendário conectado" to true
    ResultadoCalendario.ERRO -> "Falha ao alterar na Agenda" to true
}
