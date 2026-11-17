package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.example.financacelular.ui.theme.AzulCartao
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.TealParcelados
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.ui.theme.AmareloInvestimento
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Guarda os dados do aporte aguardando confirmação de exclusão. */
private data class AporteParaExcluir(
    val data: LocalDate,
    val valor: Double,
    val nomeAtivo: String,
    val confirmar: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestimentoScreen(
    viewModel: InvestimentoViewModel = viewModel(),
    acionarNovoAporteExterno: Boolean = false,
    aoAporteAcionado: () -> Unit = {},
    aoVoltar: (() -> Unit)? = null
) {
    val mensagem = LocalMensagem.current
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build()) }
    val ativosCarregados by viewModel.investimentosAgrupados.collectAsState()
    val listaAtivosAgrupados = ativosCarregados ?: emptyList()
    val patrimonioTotal = listaAtivosAgrupados.sumOf { it.valorTotal }

    var categoriaFiltro by remember { mutableStateOf("Todas") }
    var ativoDetalheChave by remember { mutableStateOf<String?>(null) }
    var mostrarFiltro by remember { mutableStateOf(false) }

    var mostrarSheetNovoAporte by remember { mutableStateOf(false) }
    var ativoParaAporteOuResgate by remember { mutableStateOf<InvestimentoAgrupado?>(null) }
    var mostrarSheetResgate by remember { mutableStateOf(false) }
    var valorResgateInput by remember { mutableStateOf("") }

    val sheetStateAporte = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetStateResgate = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var aporteParaExcluir by remember { mutableStateOf<AporteParaExcluir?>(null) }

    var novoNome by remember { mutableStateOf("") }
    var novaCategoria by remember { mutableStateOf("Renda Fixa") }
    var novoValor by remember { mutableStateOf("") }
    var menuExpandido by remember { mutableStateOf(false) }
    var dataSelecionada by remember { mutableStateOf(LocalDate.now()) }
    var mostrarDatePicker by remember { mutableStateOf(false) }
    val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val categoriasSugestoes = listOf("Renda Fixa", "Ações", "FIIs", "Tesouro Direto", "Criptomoedas", "Previdência", "Outros")

    // Tipos de ativo (categorias) do maior para o menor, com o total de cada um
    val tiposOrdenados = remember(listaAtivosAgrupados) {
        listaAtivosAgrupados.groupBy { it.categoria }
            .map { (categoria, ativos) -> categoria to ativos.sumOf { it.valorTotal } }
            .sortedByDescending { it.second }
    }
    val corOutros = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val segmentos = remember(tiposOrdenados, corOutros) { montarSegmentos(tiposOrdenados, corOutros) }
    val corPorCategoria = remember(segmentos) {
        segmentos.flatMap { segmento -> segmento.categorias.map { it to segmento.cor } }.toMap()
    }

    val ativosFiltrados = remember(listaAtivosAgrupados, categoriaFiltro) {
        if (categoriaFiltro == "Todas") listaAtivosAgrupados
        else listaAtivosAgrupados.filter { it.categoria.equals(categoriaFiltro, ignoreCase = true) }
    }

    // O detalhe é buscado na lista atual, assim ele acompanha aportes excluídos ou resgatados
    val ativoDetalhe = ativoDetalheChave?.let { chave -> listaAtivosAgrupados.find { it.chave() == chave } }

    // Se o tipo filtrado deixa de existir (ex.: último aporte excluído), volta para "Todas"
    LaunchedEffect(tiposOrdenados) {
        if (categoriaFiltro != "Todas" && tiposOrdenados.none { it.first.equals(categoriaFiltro, ignoreCase = true) }) {
            categoriaFiltro = "Todas"
        }
    }

    LaunchedEffect(acionarNovoAporteExterno) {
        if (acionarNovoAporteExterno) {
            novoNome = ""
            novaCategoria = "Renda Fixa"
            novoValor = ""
            dataSelecionada = LocalDate.now()
            mostrarSheetNovoAporte = true
            aoAporteAcionado()
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
                CabecalhoPadrao("Investimentos", aoVoltar = aoVoltar)
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                CartaoHeroDePagina(AmareloInvestimento) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "PATRIMÔNIO TOTAL",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        PilulaDePagina(if (listaAtivosAgrupados.size == 1) "1 ativo" else "${listaAtivosAgrupados.size} ativos")
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    ValorDoHero(formatoMoeda.format(patrimonioTotal))

                    if (segmentos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                contentDescription = "Composição do patrimônio: " +
                                    segmentos.joinToString(", ") { "${it.rotulo} ${percentualInteiro(it.valor, patrimonioTotal)}%" }
                            }
                        ) {
                            BarraComposicao(segmentos, categoriaFiltro)
                            Spacer(modifier = Modifier.height(10.dp))
                            LegendaComposicao(segmentos, categoriaFiltro, patrimonioTotal)
                        }
                    }
                }
                if (categoriaFiltro != "Todas") {
                    Spacer(modifier = Modifier.height(12.dp))
                    ChipFiltroAtivo(
                        categoria = categoriaFiltro,
                        subtotal = formatoMoeda.format(ativosFiltrados.sumOf { it.valorTotal }),
                        onRemover = { categoriaFiltro = "Todas" }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = Tamanho.toque),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Seus ativos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (tiposOrdenados.size > 1) {
                        BotaoFiltroTipo(ativo = categoriaFiltro != "Todas", onClick = { mostrarFiltro = true })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (ativosCarregados != null && ativosFiltrados.isEmpty()) {
                    EstadoVazio(
                        icone = Icons.Outlined.Savings,
                        mensagem = "Nenhum investimento registrado nesta categoria."
                    )
                } else if (ativosFiltrados.isNotEmpty()) {
                    // Um único cartão com todos os ativos, como as Categorias da Início
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            ativosFiltrados.forEachIndexed { indice, ativo ->
                                if (indice > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 70.dp, end = 14.dp),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                LinhaAtivo(
                                    ativo = ativo,
                                    valor = formatoMoeda.format(ativo.valorTotal),
                                    onClick = { ativoDetalheChave = ativo.chave() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------------- SHEET: FILTRAR POR TIPO ----------------
        if (mostrarFiltro) {
            FolhaApp(onDismiss = { mostrarFiltro = false }) {
                CabecalhoDeFolha(titulo = "Filtrar por tipo", aoFechar = { mostrarFiltro = false })
                Spacer(modifier = Modifier.height(Espaco.md))
                OpcaoFiltro(
                    rotulo = "Todos os tipos",
                    detalhe = if (listaAtivosAgrupados.size == 1) "1 ativo" else "${listaAtivosAgrupados.size} ativos",
                    cor = MaterialTheme.colorScheme.onSurface,
                    selecionado = categoriaFiltro == "Todas",
                    onClick = { categoriaFiltro = "Todas"; mostrarFiltro = false }
                )
                tiposOrdenados.forEach { (categoria, total) ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    OpcaoFiltro(
                        rotulo = categoria,
                        detalhe = "${percentualInteiro(total, patrimonioTotal)}%",
                        cor = corPorCategoria[categoria] ?: corOutros,
                        selecionado = categoriaFiltro.equals(categoria, ignoreCase = true),
                        onClick = { categoriaFiltro = categoria; mostrarFiltro = false }
                    )
                }
                Spacer(modifier = Modifier.height(Espaco.lg))
            }
        }

        // ---------------- SHEET: DETALHE DO ATIVO ----------------
        ativoDetalhe?.let { ativo ->
            val fecharDetalhe = { ativoDetalheChave = null; aporteParaExcluir = null }
            FolhaApp(onDismiss = fecharDetalhe) {
                AnimatedContent(
                    targetState = aporteParaExcluir,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) using SizeTransform() },
                    label = "confirmarExclusaoAporte"
                ) { pendente ->
                    if (pendente != null) {
                        ConfirmarAcaoConteudo(
                            titulo = "Excluir aporte?",
                            texto = "Esta ação não pode ser desfeita e o valor será removido do patrimônio.",
                            textoConfirmar = "Excluir",
                            onConfirmar = {
                                pendente.confirmar()
                                mensagem("Aporte removido!")
                                aporteParaExcluir = null
                            },
                            onCancelar = { aporteParaExcluir = null },
                            resumo = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            pendente.nomeAtivo,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "Aporte de ${pendente.data.format(formatoData)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        formatoMoeda.format(pendente.valor),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AmareloInvestimento
                                    )
                                }
                            }
                        )
                    } else {
                        Column {
                            CabecalhoDeFolha(
                                titulo = ativo.nome,
                                aoFechar = fecharDetalhe,
                                subtitulo = "${ativo.categoria} · " + if (ativo.aportes.size == 1) "1 aporte" else "${ativo.aportes.size} aportes"
                            )
                            Spacer(modifier = Modifier.height(Espaco.lg))
                            ValorDoHero(formatoMoeda.format(ativo.valorTotal))
                            Text(
                                "${(ativo.percentualDoTotal * 100).toInt()}% da carteira",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(Espaco.lg))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Espaco.md)
                            ) {
                                BotaoPrincipal(
                                    texto = "Aporte",
                                    onClick = {
                                        novoNome = ativo.nome
                                        novaCategoria = ativo.categoria
                                        novoValor = ""
                                        dataSelecionada = LocalDate.now()
                                        ativoDetalheChave = null
                                        mostrarSheetNovoAporte = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    cor = AmareloInvestimento,
                                    corConteudo = Color.White,
                                )
                                BotaoSecundario(
                                    texto = "Resgatar",
                                    onClick = {
                                        ativoParaAporteOuResgate = ativo
                                        valorResgateInput = ""
                                        ativoDetalheChave = null
                                        mostrarSheetResgate = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    cor = MaterialTheme.cores.textoGasto,
                                )
                            }

                            Spacer(modifier = Modifier.height(Espaco.lg))
                            Text("Histórico de aportes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ativo.aportes.forEach { aporte ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Aporte de ${aporte.data.format(formatoData)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    formatoMoeda.format(aporte.valorInvestido),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = {
                                                        aporteParaExcluir = AporteParaExcluir(
                                                            data = aporte.data,
                                                            valor = aporte.valorInvestido,
                                                            nomeAtivo = ativo.nome,
                                                            confirmar = { viewModel.deletarAporte(aporte) }
                                                        )
                                                    },
                                                    modifier = Modifier.size(Tamanho.toque)
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.DeleteOutline,
                                                        contentDescription = "Excluir aporte",
                                                        tint = MaterialTheme.cores.textoGasto,
                                                        modifier = Modifier.size(Tamanho.icone)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(Espaco.lg))
                        }
                    }
                }
            }
        }

        if (mostrarSheetResgate && ativoParaAporteOuResgate != null) {
            val ativo = ativoParaAporteOuResgate!!
            val valorTotalAtivo = ativo.valorTotal

            ModalBottomSheet(
                onDismissRequest = { mostrarSheetResgate = false },
                sheetState = sheetStateResgate,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                contentWindowInsets = { WindowInsets.statusBars }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Resgatar de ${ativo.nome}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("Saldo disponível: ${formatoMoeda.format(valorTotalAtivo)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = { mostrarSheetResgate = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Fechar")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.25 to "25%", 0.50 to "50%", 0.75 to "75%", 1.0 to "Total").forEach { (perc, label) ->
                            OutlinedButton(
                                onClick = {
                                    val valorCalc = valorTotalAtivo * perc
                                    valorResgateInput = String.format(Locale.ROOT, "%.2f", valorCalc)
                                },
                                modifier = Modifier.weight(1f).heightIn(min = Tamanho.toque),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = valorResgateInput,
                        onValueChange = { valorResgateInput = it },
                        label = { Text("Valor a retirar (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    BotaoPrincipal(
                        texto = "Confirmar Resgate",
                        onClick = {
                            val valorResgate = valorResgateInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (valorResgate > 0) {
                                if (valorResgate > valorTotalAtivo) {
                                    mensagem("Valor superior ao saldo do ativo!")
                                } else {
                                    viewModel.resgatarDoAtivo(ativo.aportes, valorResgate)
                                    mostrarSheetResgate = false
                                    mensagem("Resgate efetuado com sucesso!")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        cor = Coral,
                        corConteudo = Color.White,
                    )
                }
            }
        }

        if (mostrarSheetNovoAporte) {
            ModalBottomSheet(
                onDismissRequest = { mostrarSheetNovoAporte = false },
                sheetState = sheetStateAporte,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                contentWindowInsets = { WindowInsets.statusBars }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Novo Aporte", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { mostrarSheetNovoAporte = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Fechar")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = novoNome,
                        onValueChange = { novoNome = it },
                        label = { Text("Nome do Ativo (ex: CDB Porquinho)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ExposedDropdownMenuBox(
                        expanded = menuExpandido,
                        onExpandedChange = { menuExpandido = it }
                    ) {
                        OutlinedTextField(
                            value = novaCategoria,
                            onValueChange = { novaCategoria = it },
                            label = { Text("Categoria") },
                            shape = RoundedCornerShape(14.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpandido) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                                .fillMaxWidth(),
                            singleLine = true
                        )
                        val categoriasFiltradas = categoriasSugestoes.filter { it.contains(novaCategoria, ignoreCase = true) }
                        if (categoriasFiltradas.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = menuExpandido,
                                onDismissRequest = { menuExpandido = false }
                            ) {
                                categoriasFiltradas.forEach { sugestao ->
                                    DropdownMenuItem(
                                        text = { Text(sugestao) },
                                        onClick = {
                                            novaCategoria = sugestao
                                            menuExpandido = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = novoValor,
                        onValueChange = { novoValor = it },
                        label = { Text("Valor Investido (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val interactionSource = remember { MutableInteractionSource() }
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                mostrarDatePicker = true
                            }
                        }
                    }

                    OutlinedTextField(
                        value = dataSelecionada.format(formatoData),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Data do Aporte") },
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = "Selecionar Data", tint = AmareloInvestimento) },
                        interactionSource = interactionSource,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    BotaoPrincipal(
                        texto = "Salvar Aporte",
                        onClick = {
                            val valorDouble = novoValor.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (novoNome.isNotBlank() && valorDouble > 0) {
                                viewModel.inserirAporte(
                                    nome = novoNome,
                                    categoria = novaCategoria.ifBlank { "Geral" },
                                    valor = valorDouble,
                                    dataAporte = dataSelecionada
                                )
                                mostrarSheetNovoAporte = false
                                mensagem("Aporte guardado com sucesso!")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        cor = AmareloInvestimento,
                        corConteudo = Color.White,
                    )
                }
            }
        }

        if (mostrarDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = dataSelecionada.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { mostrarDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            dataSelecionada = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        mostrarDatePicker = false
                    }) { Text("Confirmar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDatePicker = false }) { Text("Cancelar") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}


// ---------------------------------------------------------------------------
// Componentes locais
// ---------------------------------------------------------------------------

/** Faixa da barra de composição: um ou mais tipos de ativo (vários quando agrupados em "Outros"). */
private data class SegmentoComposicao(
    val categorias: List<String>,
    val rotulo: String,
    val valor: Double,
    val cor: Color
) {
    fun casa(filtro: String) = filtro == "Todas" || categorias.any { it.equals(filtro, ignoreCase = true) }
}

private val PaletaComposicao = listOf(AmareloInvestimento, RoxoHero, TealParcelados, AzulCartao)

/** Até 4 faixas: se houver mais tipos, os menores se juntam em "Outros". */
private fun montarSegmentos(tipos: List<Pair<String, Double>>, corOutros: Color): List<SegmentoComposicao> {
    val comValor = tipos.filter { it.second > 0.0 }
    if (comValor.size <= PaletaComposicao.size) {
        return comValor.mapIndexed { i, (categoria, valor) ->
            SegmentoComposicao(listOf(categoria), categoria, valor, PaletaComposicao[i])
        }
    }
    val principais = comValor.take(PaletaComposicao.size - 1)
    val resto = comValor.drop(PaletaComposicao.size - 1)
    return principais.mapIndexed { i, (categoria, valor) ->
        SegmentoComposicao(listOf(categoria), categoria, valor, PaletaComposicao[i])
    } + SegmentoComposicao(resto.map { it.first }, "Outros", resto.sumOf { it.second }, corOutros)
}

private fun percentualInteiro(parte: Double, total: Double) = if (total > 0.0) (parte / total * 100).toInt() else 0

/** Chave estável do ativo (o agrupamento ignora maiúsculas e espaços nas pontas). */
private fun InvestimentoAgrupado.chave() = nome.trim().lowercase()

/** Barra fina de composição do patrimônio; com filtro ativo, os outros tipos esmaecem. */
@Composable
private fun BarraComposicao(segmentos: List<SegmentoComposicao>, filtro: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        segmentos.forEach { segmento ->
            val alfa by animateFloatAsState(
                targetValue = if (segmento.casa(filtro)) 1f else 0.25f,
                animationSpec = tween(200),
                label = "alfaSegmento"
            )
            Box(
                modifier = Modifier
                    .weight(segmento.valor.toFloat())
                    .fillMaxHeight()
                    .alpha(alfa)
                    .background(segmento.cor, RoundedCornerShape(4.dp))
            )
        }
    }
}

/** Legenda em texto da barra: bolinha, tipo e percentual. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendaComposicao(segmentos: List<SegmentoComposicao>, filtro: String, total: Double) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        segmentos.forEach { segmento ->
            Row(
                modifier = Modifier.alpha(if (segmento.casa(filtro)) 1f else 0.4f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(segmento.cor, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    segmento.rotulo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "${percentualInteiro(segmento.valor, total)}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

/** Botão de filtro ao lado do título da lista: bloco de 40dp (toque de 48dp), âmbar quando há filtro. */
@Composable
private fun BotaoFiltroTipo(ativo: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(forma)
            .background(
                if (ativo) AmareloInvestimento.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
            .then(if (ativo) Modifier.border(1.dp, AmareloInvestimento.copy(alpha = 0.5f), forma) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.FilterList,
            contentDescription = if (ativo) "Filtrar por tipo, filtro ativo" else "Filtrar por tipo",
            tint = if (ativo) AmareloInvestimento else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Tamanho.icone)
        )
    }
}

/** Chip do filtro ativo: tipo, subtotal e X; tocar remove o filtro. */
@Composable
private fun ChipFiltroAtivo(categoria: String, subtotal: String, onRemover: () -> Unit) {
    val forma = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .clip(forma)
            .background(AmareloInvestimento.copy(alpha = 0.16f))
            .border(1.dp, AmareloInvestimento.copy(alpha = 0.5f), forma)
            .clickable(role = Role.Button, onClickLabel = "Remover filtro", onClick = onRemover)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            categoria,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            "· $subtotal",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(Tamanho.iconePequeno))
    }
}

/** Linha do cartão de ativos: ícone, nome, valor, tipo e aportes, barra fina e percentual da carteira. */
@Composable
private fun LinhaAtivo(ativo: InvestimentoAgrupado, valor: String, onClick: () -> Unit) {
    val fracao = ativo.percentualDoTotal.coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(AmareloInvestimento.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Savings, contentDescription = null, tint = AmareloInvestimento, modifier = Modifier.size(Tamanho.icone))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ativo.nome,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    valor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                "${ativo.categoria} · " + if (ativo.aportes.size == 1) "1 aporte" else "${ativo.aportes.size} aportes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fracao)
                            .fillMaxHeight()
                            .background(AmareloInvestimento)
                    )
                }
                Text(
                    "${(fracao * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 38.dp)
                )
            }
        }
    }
}

/** Opção do sheet de filtro: bolinha na cor do tipo, nome, detalhe e marca de selecionado. */
@Composable
private fun OpcaoFiltro(
    rotulo: String,
    detalhe: String,
    cor: Color,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tamanho.controle)
            .selectable(selected = selecionado, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(cor, CircleShape))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            rotulo,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            detalhe,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (selecionado) {
                Icon(Icons.Outlined.Check, contentDescription = "Selecionado", tint = AmareloInvestimento, modifier = Modifier.size(Tamanho.icone))
            }
        }
    }
}
