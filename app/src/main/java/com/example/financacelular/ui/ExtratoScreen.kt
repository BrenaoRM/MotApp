package com.example.financacelular.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
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

    val transacoesFiltradas = transacoes.filter { t ->
        val categoria = categorias.find { it.id == t.categoriaId }
        val passaTipo = filtroTipo == null || t.tipo == filtroTipo
        val passaCategoria = filtroCategoria == null || t.categoriaId == filtroCategoria?.id
        val passaBusca = busca.isBlank() ||
                (t.descricao?.contains(busca, ignoreCase = true) == true) ||
                (categoria?.nome?.contains(busca, ignoreCase = true) == true)
        val passaMes = !apenasEsteMes || t.data.toString().startsWith(mesAtual)
        passaTipo && passaCategoria && passaBusca && passaMes
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = 22.dp
        )
    ) {
        item {
            Text("Extrato", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                label = { Text("Buscar por descrição ou categoria") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
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
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = apenasEsteMes,
                    onClick = { apenasEsteMes = !apenasEsteMes },
                    label = { Text("Este mês") }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            ExposedDropdownMenuBox(
                expanded = menuCategoriaExpandido,
                onExpandedChange = { menuCategoriaExpandido = it }
            ) {
                OutlinedTextField(
                    value = filtroCategoria?.nome ?: "Todas as categorias",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoria") },
                    shape = RoundedCornerShape(14.dp),
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

        items(transacoesFiltradas) { transacao ->
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

        if (futuros.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Futuros Lançamentos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(futuros) { transacao ->
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