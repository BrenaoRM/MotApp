package com.example.financacelular.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.Categoria
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import java.text.NumberFormat
import java.util.Locale

@Composable
fun OrcamentoScreen(viewModel: OrcamentoViewModel = viewModel()) {
    val itens by viewModel.itens.collectAsState(initial = emptyList())
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    // Estado para controlar qual categoria está sendo editada no momento
    var categoriaEmEdicao by remember { mutableStateOf<ItemOrcamento?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = espacoParaBarraFlutuante())
    ) {
        item {
            Text("Orçamento Mensal", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Toque em uma categoria para definir ou alterar o limite de gastos estabelecido.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (itens.isEmpty()) {
            item {
                Text(
                    "Nenhuma categoria de despesa encontrada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(itens) { item ->
            val limiteVal = item.limite ?: 0.0
            val progresso = if (limiteVal > 0) (item.gasto / limiteVal).toFloat().coerceIn(0f, 1f) else 0f
            val ultrapassou = item.gasto > limiteVal
            val corBarra = if (ultrapassou) Coral else Verde

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { categoriaEmEdicao = item } // Abre o diálogo ao tocar no card
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.categoria.nome, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (item.limite != null) {
                                "${formatoMoeda.format(item.gasto)} / ${formatoMoeda.format(limiteVal)}"
                            } else {
                                "${formatoMoeda.format(item.gasto)} / Definir limite"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ultrapassou) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (ultrapassou) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { progresso },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = corBarra,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }

    // Diálogo para inserir ou atualizar o valor limite do orçamento da categoria
    categoriaEmEdicao?.let { itemOrcamento ->
        DialogoDefinirLimite(
            categoria = itemOrcamento.categoria,
            limiteAtual = itemOrcamento.limite,
            onDismiss = { categoriaEmEdicao = null },
            onSalvar = { novoValor ->
                viewModel.definirLimite(itemOrcamento.categoria, novoValor)
                categoriaEmEdicao = null
            }
        )
    }
}

@Composable
private fun DialogoDefinirLimite(
    categoria: Categoria,
    limiteAtual: Double?,
    onDismiss: () -> Unit,
    onSalvar: (Double) -> Unit
) {
    var valorInput by remember { mutableStateOf(limiteAtual?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Orçamento: ${categoria.nome}") },
        text = {
            Column {
                Text(
                    "Informe o valor limite de gastos para esta categoria no mês atual:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = valorInput,
                    onValueChange = { valorInput = it },
                    label = { Text("Valor Limite (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val valorDouble = valorInput.replace(",", ".").toDoubleOrNull()
                    if (valorDouble != null) {
                        onSalvar(valorDouble)
                    }
                }
            ) {
                Text("Salvar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}