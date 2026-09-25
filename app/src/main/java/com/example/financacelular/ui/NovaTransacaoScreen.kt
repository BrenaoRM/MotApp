package com.example.financacelular.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaTransacaoScreen(
    viewModel: NovaTransacaoViewModel = viewModel(),
    tipoInicial: TipoTransacao = TipoTransacao.DESPESA,
    aoSalvar: () -> Unit = {},
    aoFechar: () -> Unit = {}
) {
    LaunchedEffect(tipoInicial) {
        viewModel.definirTipoInicial(tipoInicial)
    }
    val categorias by viewModel.categorias.collectAsState()
    var categoriaMenuExpandido by remember { mutableStateOf(false) }
    var mostrarDatePicker by remember { mutableStateOf(false) }
    var textoCategoria by remember { mutableStateOf("") }

    LaunchedEffect(viewModel.tipo) {
        textoCategoria = ""
        viewModel.onCategoriaChange(null)
    }
    val categoriasFiltradas = categorias.filter { it.tipo == viewModel.tipo }
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val corTema = if (viewModel.tipo == TipoTransacao.DESPESA) Coral else Verde

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Topo personalizado
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = aoFechar) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (viewModel.tipo == TipoTransacao.DESPESA) "Nova Despesa" else "Nova Receita",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = corTema
                    )
                }

                // Conteúdo estático sem rolagem desnecessária
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "VALOR",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = viewModel.valor,
                                onValueChange = viewModel::onValorChange,
                                placeholder = {
                                    Text(
                                        "0,00",
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                },
                                textStyle = MaterialTheme.typography.displaySmall.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = corTema
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExposedDropdownMenuBox(
                                expanded = categoriaMenuExpandido,
                                onExpandedChange = { categoriaMenuExpandido = it }
                            ) {
                                OutlinedTextField(
                                    value = textoCategoria,
                                    onValueChange = {
                                        textoCategoria = it
                                        categoriaMenuExpandido = true
                                        viewModel.onCategoriaChange(null)
                                    },
                                    label = { Text("Categoria") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoriaMenuExpandido)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                                        .fillMaxWidth()
                                )
                                val categoriasParaMostrar = categoriasFiltradas.filter {
                                    it.nome.contains(textoCategoria, ignoreCase = true)
                                }
                                ExposedDropdownMenu(
                                    expanded = categoriaMenuExpandido,
                                    onDismissRequest = { categoriaMenuExpandido = false }
                                ) {
                                    categoriasParaMostrar.forEach { categoria ->
                                        DropdownMenuItem(
                                            text = { Text(categoria.nome) },
                                            onClick = {
                                                viewModel.onCategoriaChange(categoria)
                                                textoCategoria = categoria.nome
                                                categoriaMenuExpandido = false
                                            },
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    viewModel.excluirCategoria(categoria) {
                                                        Toast.makeText(context, "Categoria em uso não pode ser excluída.", Toast.LENGTH_LONG).show()
                                                    }
                                                }) {
                                                    Icon(Icons.Filled.Close, contentDescription = "Excluir")
                                                }
                                            }
                                        )
                                    }
                                    val existeExata = categoriasFiltradas.any { it.nome.equals(textoCategoria, ignoreCase = true) }
                                    if (textoCategoria.isNotBlank() && !existeExata) {
                                        DropdownMenuItem(
                                            text = { Text("Salvar como nova: \"$textoCategoria\"") },
                                            onClick = { categoriaMenuExpandido = false },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Add, contentDescription = "Adicionar", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = viewModel.data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Data") },
                                trailingIcon = {
                                    IconButton(onClick = { mostrarDatePicker = true }) {
                                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Escolher data")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = viewModel.descricao,
                                onValueChange = viewModel::onDescricaoChange,
                                label = { Text(if (viewModel.ehRecorrente || viewModel.ehParcelado) "Nome (Ex: Salário, Netflix) *" else "Descrição (opcional)") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (viewModel.tipo == TipoTransacao.DESPESA) {
                                Text("Forma de Pagamento", style = MaterialTheme.typography.titleSmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = viewModel.formaPagamento == FormaPagamento.DEBITO,
                                        onClick = { viewModel.onFormaPagamentoChange(FormaPagamento.DEBITO) },
                                        label = { Text("Débito") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = viewModel.formaPagamento == FormaPagamento.CARTAO_CREDITO,
                                        onClick = { viewModel.onFormaPagamentoChange(FormaPagamento.CARTAO_CREDITO) },
                                        label = { Text("Crédito") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (viewModel.formaPagamento == FormaPagamento.CARTAO_CREDITO) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Compra Parcelada?", style = MaterialTheme.typography.titleSmall)
                                            Text("Dividir em várias vezes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = viewModel.ehParcelado,
                                            onCheckedChange = viewModel::onParceladoChange
                                        )
                                    }
                                    if (viewModel.ehParcelado) {
                                        OutlinedTextField(
                                            value = viewModel.numeroParcelas,
                                            onValueChange = viewModel::onNumeroParcelasChange,
                                            label = { Text("Número de Parcelas (ex: 10)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Repetir todo mês (Assinatura)", style = MaterialTheme.typography.titleSmall)
                                            Text("Cobrança recorrente no cartão", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = viewModel.ehRecorrente,
                                            onCheckedChange = viewModel::onRecorrenteChange
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Receita Recorrente?", style = MaterialTheme.typography.titleSmall)
                                        Text("Repetir todo mês (ex: Salário)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = viewModel.ehRecorrente,
                                        onCheckedChange = viewModel::onRecorrenteChange
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Container do Botão Salvar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .height(50.dp)
                        .clickable {
                            viewModel.salvar(nomeCategoriaDigitada = textoCategoria) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "Transação salva com sucesso!", Toast.LENGTH_SHORT).show()
                                aoSalvar()
                            }
                        },
                    color = corTema,
                    tonalElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Salvar",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (mostrarDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = viewModel.data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { mostrarDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val novaData = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            viewModel.onDataChange(novaData)
                        }
                        mostrarDatePicker = false
                    }) { Text("OK") }
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