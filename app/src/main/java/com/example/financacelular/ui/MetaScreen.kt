package com.example.financacelular.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.Meta
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.util.Locale

private val RoxoMeta = Color(0xFF8B5CF6)

private val ValoresRapidos = listOf(50, 100, 200, 500)

private fun valorParaTexto(valor: Double): String =
    if (valor % 1.0 == 0.0) valor.toLong().toString() else "%.2f".format(Locale.US, valor).replace(".", ",")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaScreen(
    viewModel: MetaViewModel = viewModel(),
    acionarNovaMetaExterna: Boolean = false,
    aoMetaAcionada: () -> Unit = {},
    acionarGuardarValorExterno: Boolean = false,
    aoGuardarValorAcionado: () -> Unit = {},
    aoVoltar: (() -> Unit)? = null
) {
    val metas by viewModel.metas.collectAsState()
    val formato = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build()) }

    var mostrarSheetNovaMeta by remember { mutableStateOf(false) }
    var metaParaEditar by remember { mutableStateOf<Meta?>(null) }
    var metaParaAdicionarValor by remember { mutableStateOf<Meta?>(null) }
    var mostrarSheetGuardarValor by remember { mutableStateOf(false) }

    val sheetStateCriarOuEditar = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetStateAdicionarValor = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var novoNome by remember { mutableStateOf("") }
    var novoValorAlvo by remember { mutableStateOf("") }

    val totalAcumulado = metas.sumOf { it.valorAtual }
    val totalAlvo = metas.sumOf { it.valorAlvo }

    LaunchedEffect(acionarNovaMetaExterna) {
        if (acionarNovaMetaExterna) {
            metaParaEditar = null
            novoNome = ""
            novoValorAlvo = ""
            mostrarSheetNovaMeta = true
            aoMetaAcionada()
        }
    }

    LaunchedEffect(acionarGuardarValorExterno) {
        if (acionarGuardarValorExterno) {
            if (metas.isEmpty()) {
                // Sem metas ainda: leva direto para criar a primeira
                metaParaEditar = null
                novoNome = ""
                novoValorAlvo = ""
                mostrarSheetNovaMeta = true
            } else {
                metaParaAdicionarValor = metas.firstOrNull { it.valorAtual < it.valorAlvo } ?: metas.first()
                mostrarSheetGuardarValor = true
            }
            aoGuardarValorAcionado()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = MaterialTheme.dimens.paddingScreen),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = espacoParaBarraFlutuante()
            )
        ) {
            item {
                CabecalhoDePagina("Metas", RoxoMeta, aoVoltar)
                Spacer(modifier = Modifier.height(16.dp))

                val metasConcluidas = metas.count { it.valorAlvo > 0 && it.valorAtual >= it.valorAlvo }
                CartaoHeroDePagina(RoxoMeta, gradienteDaCor(RoxoMeta)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "TOTAL ACUMULADO",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f)
                        )
                        PilulaGlass(if (metas.size == 1) "1 meta" else "${metas.size} metas")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        formato.format(totalAcumulado),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IndicadorGlassDePagina("META TOTAL", formato.format(totalAlvo), Icons.Filled.Flag, Modifier.weight(1f))
                        IndicadorGlassDePagina("CONCLUÍDAS", "$metasConcluidas de ${metas.size}", Icons.Filled.CheckCircle, Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Seus Objetivos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (metas.size == 1) "1 meta" else "${metas.size} metas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (metas.isEmpty()) {
                item {
                    Text(
                        "Nenhuma meta cadastrada ainda. Toque em 'Nova Meta' abaixo para criar o seu primeiro objetivo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(metas) { meta ->
                    val progresso = if (meta.valorAlvo > 0)
                        (meta.valorAtual / meta.valorAlvo).toFloat().coerceIn(0f, 1f) else 0f
                    val percentual = (progresso * 100).toInt()

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                metaParaEditar = meta
                                novoNome = meta.nome
                                novoValorAlvo = meta.valorAlvo.toString()
                                mostrarSheetNovaMeta = true
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(RoxoMeta.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Flag, contentDescription = null, tint = RoxoMeta, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        meta.nome,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${formato.format(meta.valorAtual)} / ${formato.format(meta.valorAlvo)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$percentual%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = RoxoMeta, maxLines = 1)
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            LinearProgressIndicator(
                                progress = { progresso },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = RoxoMeta,
                                trackColor = RoxoMeta.copy(alpha = 0.12f),
                            )

                        }
                    }
                }
            }
        }

        // --- Bottom Sheet para Criar/Editar Meta ---
        if (mostrarSheetNovaMeta) {
            ModalBottomSheet(
                onDismissRequest = { mostrarSheetNovaMeta = false },
                sheetState = sheetStateCriarOuEditar,
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
                            text = if (metaParaEditar == null) "Nova Meta" else "Editar Meta",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { mostrarSheetNovaMeta = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = novoNome,
                        onValueChange = { novoNome = it },
                        label = { Text("Nome da Meta (ex: Viagem de Férias)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = novoValorAlvo,
                        onValueChange = { novoValorAlvo = it },
                        label = { Text("Valor Alvo (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (metaParaEditar != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.excluir(metaParaEditar!!)
                                    mostrarSheetNovaMeta = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Excluir", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                val valorDouble = novoValorAlvo.replace(",", ".").toDoubleOrNull() ?: 0.0
                                if (novoNome.isNotBlank() && valorDouble > 0) {
                                    if (metaParaEditar == null) {
                                        viewModel.criar(novoNome, valorDouble)
                                    } else {
                                        viewModel.atualizar(metaParaEditar!!.copy(nome = novoNome, valorAlvo = valorDouble))
                                    }
                                    mostrarSheetNovaMeta = false
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                                .height(52.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoxoMeta, contentColor = Color.White),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                        ) {
                            Text(
                                if (metaParaEditar == null) "Criar Meta" else "Salvar Meta",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // --- Bottom Sheet "Guardar valor": escolhe a meta, valores rápidos e prévia do progresso ---
        if (mostrarSheetGuardarValor) {
            var valorInput by remember { mutableStateOf("") }
            val metaAtual = metaParaAdicionarValor
            val valorDigitado = valorInput.replace(",", ".").toDoubleOrNull() ?: 0.0

            ModalBottomSheet(
                onDismissRequest = { mostrarSheetGuardarValor = false },
                sheetState = sheetStateAdicionarValor,
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
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.dimens.paddingScreen),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Guardar valor",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { mostrarSheetGuardarValor = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "PARA QUAL META?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MaterialTheme.dimens.paddingScreen)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Seletor de meta (cartões com mini-progresso)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = MaterialTheme.dimens.paddingScreen),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(metas) { m ->
                            val selecionada = m == metaAtual
                            val prog = if (m.valorAlvo > 0) (m.valorAtual / m.valorAlvo).toFloat().coerceIn(0f, 1f) else 0f
                            Column(
                                modifier = Modifier
                                    .width(150.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selecionada) RoxoMeta.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                    .border(
                                        width = if (selecionada) 1.5.dp else 0.dp,
                                        color = if (selecionada) RoxoMeta else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { metaParaAdicionarValor = m }
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Flag, contentDescription = null, tint = RoxoMeta, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        m.nome,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { prog },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = RoxoMeta,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "${(prog * 100).toInt()}% · ${formato.format(m.valorAtual)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.dimens.paddingScreen)
                    ) {
                        Text(
                            "QUANTO VOCÊ QUER GUARDAR?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Valores rápidos: cada toque soma ao valor digitado
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ValoresRapidos.forEach { rapido ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(RoxoMeta.copy(alpha = 0.12f))
                                        .clickable { valorInput = valorParaTexto(valorDigitado + rapido) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "+$rapido",
                                        color = RoxoMeta,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = valorInput,
                            onValueChange = { valorInput = it },
                            label = { Text("Valor a guardar (R$)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Prévia: como a meta fica depois do aporte
                        if (metaAtual != null && valorDigitado > 0 && metaAtual.valorAlvo > 0) {
                            val novoTotal = metaAtual.valorAtual + valorDigitado
                            val progAntes = (metaAtual.valorAtual / metaAtual.valorAlvo).toFloat().coerceIn(0f, 1f)
                            val progDepois = (novoTotal / metaAtual.valorAlvo).toFloat().coerceIn(0f, 1f)
                            val progAnimado by animateFloatAsState(targetValue = progDepois, label = "previaProgresso")

                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { progAnimado },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = RoxoMeta,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (novoTotal >= metaAtual.valorAlvo) {
                                    "Meta alcançada! ${formato.format(novoTotal)} / ${formato.format(metaAtual.valorAlvo)}"
                                } else {
                                    "${(progAntes * 100).toInt()}% → ${(progDepois * 100).toInt()}%  ·  ${formato.format(novoTotal)} / ${formato.format(metaAtual.valorAlvo)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = RoxoMeta,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (metaAtual != null && valorDigitado > 0) {
                                    viewModel.adicionarValor(metaAtual, valorDigitado)
                                    mostrarSheetGuardarValor = false
                                }
                            },
                            enabled = metaAtual != null && valorDigitado > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                                .height(52.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoxoMeta,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                        ) {
                            Text(
                                text = if (valorDigitado > 0) "Guardar ${formato.format(valorDigitado)}" else "Confirmar Aporte",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}