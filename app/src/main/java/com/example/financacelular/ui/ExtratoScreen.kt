package com.example.financacelular.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtratoScreen(viewModel: ExtratoViewModel = viewModel()) {
    val transacoes by viewModel.todasTransacoes.collectAsState()
    val futuros by viewModel.futurosLancamentos.collectAsState()
    val categorias by viewModel.categorias.collectAsState()
    var filtroTipo by remember { mutableStateOf<TipoTransacao?>(null) }
    var filtroCategoria by remember { mutableStateOf<Categoria?>(null) }
    var menuCategoriaExpandido by remember { mutableStateOf(false) }
    var busca by remember { mutableStateOf("") }
    var apenasEsteMes by remember { mutableStateOf(false) }
    var transacaoEmEdicao by remember { mutableStateOf<Transacao?>(null) }
    val formato = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val mesAtual = remember { LocalDate.now().let { "%04d-%02d".format(it.year, it.monthValue) } }
    val formatoDataFiltro = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    // --- Filtros (tipo, categoria, mês, período) consolidados em um único bottom sheet ---
    var mostrarFiltros by remember { mutableStateOf(false) }
    var dataInicioFiltro by remember { mutableStateOf<LocalDate?>(null) }
    var dataFimFiltro by remember { mutableStateOf<LocalDate?>(null) }
    var mostrarDatePickerInicio by remember { mutableStateOf(false) }
    var mostrarDatePickerFim by remember { mutableStateOf(false) }

    val algumFiltroAtivo = filtroTipo != null || filtroCategoria != null || apenasEsteMes ||
            dataInicioFiltro != null || dataFimFiltro != null

    fun passaPeriodo(data: LocalDate): Boolean {
        val inicio = dataInicioFiltro
        val fim = dataFimFiltro
        val depoisDoInicio = inicio == null || !data.isBefore(inicio)
        val antesDoFim = fim == null || !data.isAfter(fim)
        return depoisDoInicio && antesDoFim
    }

    val transacoesFiltradas = transacoes.filter { t ->
        val categoria = categorias.find { it.id == t.categoriaId }
        val passaTipo = filtroTipo == null || t.tipo == filtroTipo
        val passaCategoria = filtroCategoria == null || t.categoriaId == filtroCategoria?.id
        val passaBusca = busca.isBlank() ||
                (t.descricao?.contains(busca, ignoreCase = true) == true) ||
                (categoria?.nome?.contains(busca, ignoreCase = true) == true)
        val passaMes = !apenasEsteMes || t.data.toString().startsWith(mesAtual)
        passaTipo && passaCategoria && passaBusca && passaMes && passaPeriodo(t.data)
    }.sortedByDescending { it.data }

    val futurosFiltrados = futuros.filter { passaPeriodo(it.data) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Extrato", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

                // Botão único de filtros (mesmo estilo do Dashboard)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable { mostrarFiltros = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = "Filtros",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            if (algumFiltroAtivo) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .background(MaterialTheme.colorScheme.error, CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Filtros",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                label = { Text("Buscar por descrição ou categoria") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text("Lançamentos Realizados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (transacoesFiltradas.isEmpty()) {
                Text(
                    "Nenhuma transação realizada encontrada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        itemsIndexed(transacoesFiltradas) { index, transacao ->
            val diaMudou = index > 0 && transacoesFiltradas[index - 1].data != transacao.data
            if (diaMudou) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
            val categoria = categorias.find { it.id == transacao.categoriaId }
            val cor = if (transacao.tipo == TipoTransacao.DESPESA) Coral else Verde
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Desabilita clique se for uma transação projetada (id negativo)
                    .clickable(enabled = transacao.id >= 0) { transacaoEmEdicao = transacao }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(cor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        iconeParaCategoria(categoria?.nome ?: ""),
                        contentDescription = null,
                        tint = cor
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transacao.descricao?.takeIf { it.isNotBlank() }
                            ?: (categoria?.nome ?: "Transação"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${categoria?.nome ?: "Sem categoria"}, ${transacao.data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    (if (transacao.tipo == TipoTransacao.DESPESA) "- " else "+ ") +
                            formato.format(transacao.valor),
                    color = cor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (futurosFiltrados.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Futuros Lançamentos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(futurosFiltrados) { index, transacao ->
                val diaMudou = index > 0 && futurosFiltrados[index - 1].data != transacao.data
                if (diaMudou) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
                val categoria = categorias.find { it.id == transacao.categoriaId }
                val cor = if (transacao.tipo == TipoTransacao.DESPESA) Coral else Verde
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = transacao.id >= 0) { transacaoEmEdicao = transacao }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(cor.copy(alpha = 0.10f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            iconeParaCategoria(categoria?.nome ?: ""),
                            contentDescription = null,
                            tint = cor.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            transacao.descricao?.takeIf { it.isNotBlank() }
                                ?: (categoria?.nome ?: "Transação Futura"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${categoria?.nome ?: "Sem categoria"}, ${transacao.data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} (Previsto)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        (if (transacao.tipo == TipoTransacao.DESPESA) "- " else "+ ") +
                                formato.format(transacao.valor),
                        color = cor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Modal de Edição (Estilo Premium - Bottom Sheet)
    transacaoEmEdicao?.let { transacao ->
        BottomSheetEditarTransacao(
            transacao = transacao,
            categorias = categorias,
            onDismiss = { transacaoEmEdicao = null },
            onSalvar = {
                viewModel.atualizar(it)
                transacaoEmEdicao = null
            },
            onExcluir = {
                viewModel.excluir(transacao)
                transacaoEmEdicao = null
            }
        )
    }

    // --- BOTTOM SHEET: FILTROS (tipo, categoria, mês e período) ---
    if (mostrarFiltros) {
        val sheetStateFiltros = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { mostrarFiltros = false },
            sheetState = sheetStateFiltros,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
            ) {
                Text(
                    text = "Filtros",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text("Tipo de movimentação", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    FilterChip(selected = filtroTipo == null, onClick = { filtroTipo = null }, label = { Text("Todas") })
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = filtroTipo == TipoTransacao.DESPESA,
                        onClick = { filtroTipo = TipoTransacao.DESPESA },
                        label = { Text("Despesas") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = filtroTipo == TipoTransacao.RECEITA,
                        onClick = { filtroTipo = TipoTransacao.RECEITA },
                        label = { Text("Receitas") }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("Período rápido", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                FilterChip(
                    selected = apenasEsteMes,
                    onClick = { apenasEsteMes = !apenasEsteMes },
                    label = { Text("Este mês") }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text("Categoria", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = menuCategoriaExpandido,
                    onExpandedChange = { menuCategoriaExpandido = it }
                ) {
                    OutlinedTextField(
                        value = filtroCategoria?.nome ?: "Todas as categorias",
                        onValueChange = {},
                        readOnly = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuCategoriaExpandido) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = menuCategoriaExpandido,
                        onDismissRequest = { menuCategoriaExpandido = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Todas as categorias") },
                            onClick = { filtroCategoria = null; menuCategoriaExpandido = false }
                        )
                        categorias.forEach { categoria ->
                            DropdownMenuItem(
                                text = { Text(categoria.nome) },
                                onClick = { filtroCategoria = categoria; menuCategoriaExpandido = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("Período (de / até)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                // Campo: data inicial
                val interactionInicio = remember { MutableInteractionSource() }
                LaunchedEffect(interactionInicio) {
                    interactionInicio.interactions.collect {
                        if (it is PressInteraction.Release) mostrarDatePickerInicio = true
                    }
                }
                OutlinedTextField(
                    value = dataInicioFiltro?.format(formatoDataFiltro) ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("De") },
                    placeholder = { Text("Data inicial") },
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = "Selecionar data inicial", tint = MaterialTheme.colorScheme.primary) },
                    interactionSource = interactionInicio,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Campo: data final
                val interactionFim = remember { MutableInteractionSource() }
                LaunchedEffect(interactionFim) {
                    interactionFim.interactions.collect {
                        if (it is PressInteraction.Release) mostrarDatePickerFim = true
                    }
                }
                OutlinedTextField(
                    value = dataFimFiltro?.format(formatoDataFiltro) ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Até") },
                    placeholder = { Text("Data final") },
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = "Selecionar data final", tint = MaterialTheme.colorScheme.primary) },
                    interactionSource = interactionFim,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            filtroTipo = null
                            filtroCategoria = null
                            apenasEsteMes = false
                            dataInicioFiltro = null
                            dataFimFiltro = null
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Limpar filtros", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { mostrarFiltros = false },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Aplicar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Calendário para a data inicial do filtro
    if (mostrarDatePickerInicio) {
        val estadoPicker = rememberDatePickerState(
            initialSelectedDateMillis = (dataInicioFiltro ?: LocalDate.now())
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { mostrarDatePickerInicio = false },
            confirmButton = {
                TextButton(onClick = {
                    estadoPicker.selectedDateMillis?.let { millis ->
                        dataInicioFiltro = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    mostrarDatePickerInicio = false
                }) { Text("Confirmar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePickerInicio = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = estadoPicker)
        }
    }

    // Calendário para a data final do filtro
    if (mostrarDatePickerFim) {
        val estadoPicker = rememberDatePickerState(
            initialSelectedDateMillis = (dataFimFiltro ?: LocalDate.now())
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { mostrarDatePickerFim = false },
            confirmButton = {
                TextButton(onClick = {
                    estadoPicker.selectedDateMillis?.let { millis ->
                        dataFimFiltro = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    mostrarDatePickerFim = false
                }) { Text("Confirmar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePickerFim = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = estadoPicker)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetEditarTransacao(
    transacao: Transacao,
    categorias: List<Categoria>,
    onDismiss: () -> Unit,
    onSalvar: (Transacao) -> Unit,
    onExcluir: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var valor by remember { mutableStateOf(transacao.valor.toString()) }
    var descricao by remember { mutableStateOf(transacao.descricao ?: "") }
    var dataSelecionada by remember { mutableStateOf(transacao.data) }
    var categoriaSelecionada by remember {
        mutableStateOf(categorias.find { it.id == transacao.categoriaId })
    }

    var menuExpandido by remember { mutableStateOf(false) }
    var mostrarDatePicker by remember { mutableStateOf(false) }

    val categoriasDoTipo = categorias.filter { it.tipo == transacao.tipo }
    val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Editar Lançamento",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Campo de Valor
            OutlinedTextField(
                value = valor,
                onValueChange = { valor = it },
                label = { Text("Valor (R$)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Campo de Categoria
            ExposedDropdownMenuBox(
                expanded = menuExpandido,
                onExpandedChange = { menuExpandido = it }
            ) {
                OutlinedTextField(
                    value = categoriaSelecionada?.nome ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoria") },
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpandido) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = menuExpandido,
                    onDismissRequest = { menuExpandido = false }
                ) {
                    categoriasDoTipo.forEach { categoria ->
                        DropdownMenuItem(
                            text = { Text(categoria.nome) },
                            onClick = {
                                categoriaSelecionada = categoria
                                menuExpandido = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Campo de Descrição
            OutlinedTextField(
                value = descricao,
                onValueChange = { descricao = it },
                label = { Text("Descrição (Opcional)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // NOVO: Campo de Data (Abre o calendário ao tocar)
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
                label = { Text("Data do Lançamento") },
                shape = RoundedCornerShape(12.dp),
                trailingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = "Selecionar Data", tint = MaterialTheme.colorScheme.primary) },
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Botões de Ação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onExcluir,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val valorDouble = valor.replace(",", ".").toDoubleOrNull()
                        val categoria = categoriaSelecionada
                        if (valorDouble != null && categoria != null) {
                            // Atualiza o anoMes caso o utilizador tenha alterado o mês do lançamento
                            val novoAnoMes = String.format("%04d-%02d", dataSelecionada.year, dataSelecionada.monthValue)

                            onSalvar(
                                transacao.copy(
                                    valor = valorDouble,
                                    categoriaId = categoria.id,
                                    descricao = descricao.ifBlank { null },
                                    data = dataSelecionada,
                                    anoMes = novoAnoMes
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Salvar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Modal do Calendário (DatePicker)
    if (mostrarDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dataSelecionada.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { mostrarDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dataSelecionada = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
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