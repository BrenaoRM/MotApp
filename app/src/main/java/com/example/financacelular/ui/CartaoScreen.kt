package com.example.financacelular.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.Transacao
import com.example.financacelular.ui.theme.Verde
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartaoScreen(viewModel: CartaoViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { FinancaRepository.getInstance(AppDatabase.getInstance(context)) }

    var mesSelecionado by remember { mutableStateOf(YearMonth.now()) }
    val anoMesStr = remember(mesSelecionado) { mesSelecionado.format(DateTimeFormatter.ofPattern("yyyy-MM")) }
    val nomeMesAno = remember(mesSelecionado) {
        mesSelecionado.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .replaceFirstChar { it.uppercase() } + " / " + mesSelecionado.year
    }
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    var transacoesFatura by remember { mutableStateOf<List<Transacao>>(emptyList()) }
    var faturaPaga by remember { mutableStateOf(false) }

    val cartao by viewModel.cartao.collectAsState()
    var mostrarConfig by remember { mutableStateOf(false) }

    LaunchedEffect(mesSelecionado) {
        repository.listarTransacoesFatura(1L, anoMesStr).collect { lista ->
            transacoesFatura = lista
        }
    }

    LaunchedEffect(mesSelecionado) {
        repository.verificarFaturaPaga(1L, anoMesStr).collect { paga ->
            faturaPaga = paga
        }
    }

    val valorTotalFatura = transacoesFatura.sumOf { it.valor }
    val diaVencimentoSalvo = cartao?.diaVencimento ?: 25 // Padrão se não configurado

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
                Text("Fatura do Cartão", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { mostrarConfig = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Configurar Fatura", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { mesSelecionado = mesSelecionado.minusMonths(1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior")
                }
                Text(nomeMesAno, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { mesSelecionado = mesSelecionado.plusMonths(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Mês seguinte")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (faturaPaga) Verde.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (faturaPaga) "FATURA PAGA NESTE MÊS" else "VALOR DA FATURA",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (faturaPaga) Verde else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Vencimento dia $diaVencimentoSalvo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        formatoMoeda.format(valorTotalFatura),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (faturaPaga) Verde else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!faturaPaga) {
                        Button(
                            onClick = {
                                if (valorTotalFatura > 0.0) {
                                    scope.launch {
                                        repository.pagarFatura(1L, anoMesStr, valorTotalFatura)
                                        Toast.makeText(context, "Fatura paga e descontada do saldo!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Não há valor pendente na fatura.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pagar Fatura", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    repository.cancelarPagamentoFatura(anoMesStr)
                                    Toast.makeText(context, "Pagamento cancelado com sucesso.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cancelar Pagamento da Fatura", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Lançamentos na Fatura", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(transacoesFatura) { transacao ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(transacao.descricao ?: "Compra no Cartão", fontWeight = FontWeight.Bold)
                        Text(transacao.data.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        formatoMoeda.format(transacao.valor),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (mostrarConfig) {
        var diaFechamento by remember { mutableStateOf(cartao?.diaFechamento?.toString() ?: "") }
        var diaVencimento by remember { mutableStateOf(cartao?.diaVencimento?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { mostrarConfig = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("Configuração do Cartão", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "As compras feitas a partir do dia de fechamento entram na fatura do mês seguinte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = diaFechamento,
                        onValueChange = { diaFechamento = it.filter { char -> char.isDigit() } },
                        label = { Text("Dia de Fechamento (ex: 18)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = diaVencimento,
                        onValueChange = { diaVencimento = it.filter { char -> char.isDigit() } },
                        label = { Text("Dia de Vencimento (ex: 25)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fechamentoInt = diaFechamento.toIntOrNull()?.coerceIn(1, 31)
                        val vencimentoInt = diaVencimento.toIntOrNull()?.coerceIn(1, 31)

                        if (fechamentoInt != null && vencimentoInt != null) {
                            viewModel.atualizarDatasCartao(fechamentoInt, vencimentoInt)
                            mostrarConfig = false
                            Toast.makeText(context, "Datas atualizadas com sucesso!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Por favor, insira dias válidos de 1 a 31.", Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Salvar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfig = false }) {
                    Text("Cancelar", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}