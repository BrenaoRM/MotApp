package com.example.financacelular.ui

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

private val AzulFatura = Color(0xFF5B8DEF)

/** "2026-10-03" -> "03/10/2026" (sem depender do tipo exato da data). */
private fun formatarDataFatura(texto: String): String {
    val partes = texto.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else texto
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartaoScreen(
    viewModel: CartaoViewModel = viewModel(),
    aoVoltar: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val mesSelecionado by viewModel.mesSelecionado.collectAsState()
    val transacoesFatura by viewModel.transacoesFatura.collectAsState()
    val faturaPaga by viewModel.faturaPaga.collectAsState()
    val cartao by viewModel.cartao.collectAsState()

    val nomeMesAno = remember(mesSelecionado) {
        mesSelecionado.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"))
            .replaceFirstChar { it.uppercase() } + " / " + mesSelecionado.year
    }
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")) }
    var mostrarConfig by remember { mutableStateOf(false) }
    val sheetStateConfig = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val valorTotalFatura = transacoesFatura.sumOf { it.valor }
    val diaVencimentoSalvo = cartao?.diaVencimento ?: 25

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen),
        contentPadding = PaddingValues(top = 16.dp, bottom = espacoParaBarraFlutuante())
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CabecalhoDePagina(
                    titulo = "Fatura do Cartão",
                    corIcone = AzulFatura,
                    aoVoltar = aoVoltar,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), CircleShape)
                        .clickable { mostrarConfig = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Configurar Fatura",
                        tint = AzulFatura,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MesSelectorCard(
                nomeMes = nomeMesAno,
                onMesAnterior = { viewModel.mesAnterior() },
                onMesSeguinte = { viewModel.mesSeguinte() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            CartaoVirtualHero(
                valorTotal = valorTotalFatura,
                faturaPaga = faturaPaga,
                diaVencimento = diaVencimentoSalvo,
                quantidadeItens = transacoesFatura.size,
                formatoMoeda = formatoMoeda
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Lançamentos na Fatura",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (transacoesFatura.size == 1) "1 item" else "${transacoesFatura.size} itens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (transacoesFatura.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Nenhum lançamento na fatura deste mês.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }

        items(transacoesFatura) { transacao ->
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
                            .background(AzulFatura.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CreditCard,
                            contentDescription = null,
                            tint = AzulFatura,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            transacao.descricao ?: "Compra no Cartão",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatarDataFatura(transacao.data.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        formatoMoeda.format(transacao.valor),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Coral,
                        maxLines = 1
                    )
                }
            }
        }
    }

    if (mostrarConfig) {
        var diaFechamento by remember { mutableStateOf(cartao?.diaFechamento?.toString() ?: "") }
        var diaVencimento by remember { mutableStateOf(cartao?.diaVencimento?.toString() ?: "") }

        ModalBottomSheet(
            onDismissRequest = { mostrarConfig = false },
            sheetState = sheetStateConfig,
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
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Configuração do Cartão",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { mostrarConfig = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "As compras feitas a partir do dia de fechamento entram na fatura do mês seguinte.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = diaFechamento,
                    onValueChange = { diaFechamento = it.filter { char -> char.isDigit() } },
                    label = { Text("Dia de Fechamento (ex: 18)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AzulFatura, focusedLabelColor = AzulFatura),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = diaVencimento,
                    onValueChange = { diaVencimento = it.filter { char -> char.isDigit() } },
                    label = { Text("Dia de Vencimento (ex: 25)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AzulFatura, focusedLabelColor = AzulFatura),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(28.dp))

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                        .height(52.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AzulFatura, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Salvar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun CartaoVirtualHero(
    valorTotal: Double,
    faturaPaga: Boolean,
    diaVencimento: Int,
    quantidadeItens: Int,
    formatoMoeda: NumberFormat
) {
    val animProgress by animateFloatAsState(
        targetValue = if (faturaPaga) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "transicaoFaturaColor"
    )

    // Azul enquanto está em aberto; vira verde quando a fatura é paga
    val corInicial = lerp(AzulFatura, Verde, animProgress)
    val corFinal = lerp(gradienteDaCor(AzulFatura), gradienteDaCor(Verde), animProgress)

    CartaoHeroDePagina(corInicio = corInicial, corFim = corFinal) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (faturaPaga) "VALOR PAGO" else "VALOR DA FATURA",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
            PilulaGlass(if (faturaPaga) "PAGA" else "EM ABERTO")
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = formatoMoeda.format(valorTotal),
            style = MaterialTheme.typography.displayMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IndicadorGlassDePagina("VENCIMENTO", "Dia $diaVencimento", Icons.Filled.CalendarToday, Modifier.weight(1f))
            IndicadorGlassDePagina(
                "LANÇAMENTOS",
                if (quantidadeItens == 1) "1 item" else "$quantidadeItens itens",
                Icons.AutoMirrored.Filled.ReceiptLong,
                Modifier.weight(1f)
            )
        }
    }
}