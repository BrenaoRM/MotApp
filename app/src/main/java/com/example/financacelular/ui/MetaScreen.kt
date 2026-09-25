package com.example.financacelular.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.Meta
import com.example.financacelular.ui.theme.Verde
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MetaScreen(viewModel: MetaViewModel = viewModel()) {
    val metas by viewModel.metas.collectAsState()
    val formato = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    var mostrarDialogoNovaMeta by remember { mutableStateOf(false) }
    var metaParaAdicionarValor by remember { mutableStateOf<Meta?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Metas", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Acompanhe os seus objetivos e sonhos financeiros",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { mostrarDialogoNovaMeta = true },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("+ Nova")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (metas.isEmpty()) {
                Text(
                    "Nenhuma meta cadastrada ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(metas) { meta ->
            val progresso = if (meta.valorAlvo > 0)
                (meta.valorAtual / meta.valorAlvo).toFloat().coerceIn(0f, 1f) else 0f
            val percentual = (progresso * 100).toInt()

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Verde.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Flag, contentDescription = null, tint = Verde, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(meta.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${formato.format(meta.valorAtual)} / ${formato.format(meta.valorAlvo)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("$percentual%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Verde)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progresso },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = Verde,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { metaParaAdicionarValor = meta }) {
                            Text("Adicionar valor")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { viewModel.excluir(meta) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Excluir")
                        }
                    }
                }
            }
        }
    }

    if (mostrarDialogoNovaMeta) {
        DialogoNovaMeta(
            onDismiss = { mostrarDialogoNovaMeta = false },
            onConfirmar = { nome, valorAlvo ->
                viewModel.criar(nome, valorAlvo)
                mostrarDialogoNovaMeta = false
            }
        )
    }

    metaParaAdicionarValor?.let { meta ->
        DialogoAdicionarValor(
            meta = meta,
            onDismiss = { metaParaAdicionarValor = null },
            onConfirmar = { valor ->
                viewModel.adicionarValor(meta, valor)
                metaParaAdicionarValor = null
            }
        )
    }
}

@Composable
private fun DialogoNovaMeta(onDismiss: () -> Unit, onConfirmar: (String, Double) -> Unit) {
    var nome by remember { mutableStateOf("") }
    var valorAlvo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova meta", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome da meta") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = valorAlvo,
                    onValueChange = { valorAlvo = it },
                    label = { Text("Valor alvo (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                valorAlvo.replace(",", ".").toDoubleOrNull()?.let { onConfirmar(nome, it) }
            }) { Text("Criar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun DialogoAdicionarValor(meta: Meta, onDismiss: () -> Unit, onConfirmar: (Double) -> Unit) {
    var valor by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar a \"${meta.nome}\"", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = valor,
                onValueChange = { valor = it },
                label = { Text("Valor (R$)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                valor.replace(",", ".").toDoubleOrNull()?.let { onConfirmar(it) }
            }) { Text("Adicionar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}