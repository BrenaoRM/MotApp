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
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.InvestimentoEntity
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AmareloInvestimento = Color(0xFFF2A93B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestimentoScreen(
    viewModel: InvestimentoViewModel = viewModel(),
    acionarNovoAporteExterno: Boolean = false,
    aoAporteAcionado: () -> Unit = {}
) {
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val listaAtivos by viewModel.investimentos.collectAsState()

    var mostrarSheetNovoAporte by remember { mutableStateOf(false) }
    var ativoParaEditar by remember { mutableStateOf<InvestimentoEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var novoNome by remember { mutableStateOf("") }
    var novaCategoria by remember { mutableStateOf("") }
    var novoValor by remember { mutableStateOf("") }
    var menuExpandido by remember { mutableStateOf(false) }

    var dataSelecionada by remember { mutableStateOf(LocalDate.now()) }
    var mostrarDatePicker by remember { mutableStateOf(false) }
    val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    val categoriasSugestoes = listOf("Renda Fixa", "Ações", "FIIs", "Tesouro Direto", "Criptomoedas", "Previdência", "Outros")

    val patrimonioTotal = listaAtivos.sumOf { it.valorInvestido }

    LaunchedEffect(acionarNovoAporteExterno) {
        if (acionarNovoAporteExterno) {
            ativoParaEditar = null
            novoNome = ""
            novaCategoria = ""
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
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = espacoParaBarraFlutuante()
            )
        ) {
            item {
                Text("Investimentos", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Construção de patrimônio e aportes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(AmareloInvestimento.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = AmareloInvestimento, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("PATRIMÔNIO TOTAL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatoMoeda.format(patrimonioTotal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AmareloInvestimento)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Text("Seus Ativos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (listaAtivos.isEmpty()) {
                item {
                    Text(
                        "Nenhum investimento cadastrado ainda. Clique no '+' abaixo para adicionar seu primeiro aporte.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(listaAtivos) { ativo ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                ativoParaEditar = ativo
                                novoNome = ativo.nome
                                novaCategoria = ativo.categoria
                                novoValor = ativo.valorInvestido.toString()
                                dataSelecionada = ativo.data
                                mostrarSheetNovoAporte = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(AmareloInvestimento.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Savings, contentDescription = null, tint = AmareloInvestimento)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ativo.nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("${ativo.categoria} • ${ativo.data.format(formatoData)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                formatoMoeda.format(ativo.valorInvestido),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AmareloInvestimento
                            )
                        }
                    }
                }
            }
        }

        // Bottom Sheet de Adição/Edição
        if (mostrarSheetNovoAporte) {
            ModalBottomSheet(
                onDismissRequest = { mostrarSheetNovoAporte = false },
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
                        Text(
                            text = if (ativoParaEditar == null) "Novo Aporte" else "Editar Aporte",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { mostrarSheetNovoAporte = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = novoNome,
                        onValueChange = { novoNome = it },
                        label = { Text("Nome do Ativo (ex: Tesouro Selic)") },
                        shape = RoundedCornerShape(12.dp),
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
                            shape = RoundedCornerShape(12.dp),
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
                        shape = RoundedCornerShape(12.dp),
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
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = "Selecionar Data", tint = AmareloInvestimento) },
                        interactionSource = interactionSource,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (ativoParaEditar != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.deletarAporte(ativoParaEditar!!)
                                    mostrarSheetNovoAporte = false
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Excluir", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                val valorDouble = novoValor.replace(",", ".").toDoubleOrNull() ?: 0.0
                                if (novoNome.isNotBlank() && valorDouble > 0) {
                                    if (ativoParaEditar == null) {
                                        viewModel.inserirAporte(
                                            nome = novoNome,
                                            categoria = novaCategoria.ifBlank { "Geral" },
                                            valor = valorDouble,
                                            dataAporte = dataSelecionada
                                        )
                                    } else {
                                        viewModel.atualizarAporte(
                                            ativoParaEditar!!.copy(
                                                nome = novoNome,
                                                categoria = novaCategoria.ifBlank { "Geral" },
                                                valorInvestido = valorDouble,
                                                data = dataSelecionada
                                            )
                                        )
                                    }
                                    mostrarSheetNovoAporte = false
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmareloInvestimento)
                        ) {
                            Text(if (ativoParaEditar == null) "Adicionar Aporte" else "Salvar Aporte", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

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
}