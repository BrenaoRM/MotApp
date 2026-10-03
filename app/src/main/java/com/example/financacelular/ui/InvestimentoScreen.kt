package com.example.financacelular.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Savings
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AmareloInvestimento = Color(0xFFF2A93B)
private val AmareloEscuroGradiente = Color(0xFFD97706)

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
    val context = LocalContext.current
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build()) }
    val listaAtivosAgrupados by viewModel.investimentosAgrupados.collectAsState()
    val patrimonioTotal = listaAtivosAgrupados.sumOf { it.valorTotal }

    var categoriaFiltro by remember { mutableStateOf("Todas") }
    var ativoExpandidoNome by remember { mutableStateOf<String?>(null) }

    var mostrarSheetNovoAporte by remember { mutableStateOf(false) }
    var ativoParaAporteOuResgate by remember { mutableStateOf<InvestimentoAgrupado?>(null) }
    var mostrarSheetResgate by remember { mutableStateOf(false) }
    var valorResgateInput by remember { mutableStateOf("") }

    val sheetStateAporte = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetStateResgate = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetStateExcluir = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var aporteParaExcluir by remember { mutableStateOf<AporteParaExcluir?>(null) }

    var novoNome by remember { mutableStateOf("") }
    var novaCategoria by remember { mutableStateOf("Renda Fixa") }
    var novoValor by remember { mutableStateOf("") }
    var menuExpandido by remember { mutableStateOf(false) }
    var dataSelecionada by remember { mutableStateOf(LocalDate.now()) }
    var mostrarDatePicker by remember { mutableStateOf(false) }
    val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val categoriasSugestoes = listOf("Renda Fixa", "Ações", "FIIs", "Tesouro Direto", "Criptomoedas", "Previdência", "Outros")

    val categoriasPresentes = remember(listaAtivosAgrupados) {
        listOf("Todas") + listaAtivosAgrupados.map { it.categoria }.distinct()
    }

    val ativosFiltrados = remember(listaAtivosAgrupados, categoriaFiltro) {
        if (categoriaFiltro == "Todas") listaAtivosAgrupados
        else listaAtivosAgrupados.filter { it.categoria.equals(categoriaFiltro, ignoreCase = true) }
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
                CabecalhoDePagina("Investimentos", AmareloInvestimento, aoVoltar)
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                // Distribuição por categoria (para a barra segmentada do card)
                val distribuicao = remember(listaAtivosAgrupados) {
                    listaAtivosAgrupados
                        .groupBy { it.categoria }
                        .map { (cat, ativos) -> cat to ativos.sumOf { it.valorTotal } }
                        .sortedByDescending { it.second }
                }
                val maiorPosicao = distribuicao.firstOrNull()

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(AmareloInvestimento, AmareloEscuroGradiente)
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        CirculosDecorativosHero()
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "PATRIMÔNIO TOTAL",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = if (listaAtivosAgrupados.size == 1) "1 ativo" else "${listaAtivosAgrupados.size} ativos",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = formatoMoeda.format(patrimonioTotal),
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 32.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (maiorPosicao != null && patrimonioTotal > 0.0) {
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Barra segmentada: cada trecho é uma categoria
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        distribuicao.forEachIndexed { i, (_, valor) ->
                                            val peso = (valor / patrimonioTotal).toFloat().coerceAtLeast(0.02f)
                                            Box(
                                                modifier = Modifier
                                                    .weight(peso)
                                                    .height(6.dp)
                                                    .background(Color.White.copy(alpha = (1f - i * 0.22f).coerceAtLeast(0.30f)))
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "${maiorPosicao.first} ${((maiorPosicao.second / patrimonioTotal) * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 130.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (categoriasPresentes.size > 2) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categoriasPresentes) { cat ->
                            FilterChip(
                                selected = categoriaFiltro == cat,
                                onClick = { categoriaFiltro = cat },
                                label = { Text(cat, fontWeight = FontWeight.SemiBold) },
                                shape = RoundedCornerShape(14.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmareloInvestimento,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Seus Ativos Consolidados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        Icons.Filled.PieChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (ativosFiltrados.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.Savings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Nenhum investimento registrado nesta categoria.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(ativosFiltrados) { ativo ->
                    val isExpandido = ativoExpandidoNome == ativo.nome
                    val percentualInt = (ativo.percentualDoTotal * 100).toInt()

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                ativoExpandidoNome = if (isExpandido) null else ativo.nome
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(AmareloInvestimento.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Savings, contentDescription = null, tint = AmareloInvestimento)
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        ativo.nome,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = AmareloInvestimento.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                ativo.categoria,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = AmareloEscuroGradiente
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (ativo.aportes.size == 1) "1 aporte" else "${ativo.aportes.size} aportes",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatoMoeda.format(ativo.valorTotal),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AmareloInvestimento,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "$percentualInt% da carteira",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (isExpandido) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { ativo.percentualDoTotal },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = AmareloInvestimento,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            AnimatedVisibility(
                                visible = isExpandido,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                novoNome = ativo.nome
                                                novaCategoria = ativo.categoria
                                                novoValor = ""
                                                dataSelecionada = LocalDate.now()
                                                mostrarSheetNovoAporte = true
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AmareloInvestimento)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Aporte", fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                ativoParaAporteOuResgate = ativo
                                                valorResgateInput = ""
                                                mostrarSheetResgate = true
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)
                                        ) {
                                            Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Resgatar", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Histórico de Aportes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.DeleteOutline,
                                                                contentDescription = "Eliminar",
                                                                tint = Coral,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        aporteParaExcluir?.let { pendente ->
            ModalBottomSheet(
                onDismissRequest = { aporteParaExcluir = null },
                sheetState = sheetStateExcluir,
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
                        .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Coral.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = Coral,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Excluir aporte?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Esta ação não pode ser desfeita e o valor será removido do patrimônio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { aporteParaExcluir = null },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Cancelar", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                pendente.confirmar()
                                aporteParaExcluir = null
                                Toast.makeText(context, "Aporte removido!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Coral,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Excluir", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
                            Icon(Icons.Filled.Close, contentDescription = "Fechar")
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
                                modifier = Modifier.weight(1f),
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

                    Button(
                        onClick = {
                            val valorResgate = valorResgateInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (valorResgate > 0) {
                                if (valorResgate > valorTotalAtivo) {
                                    Toast.makeText(context, "Valor superior ao saldo do ativo!", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.resgatarDoAtivo(ativo.aportes, valorResgate)
                                    mostrarSheetResgate = false
                                    Toast.makeText(context, "Resgate efetuado com sucesso!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                            .height(52.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Coral,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Confirmar Resgate",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
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
                            Icon(Icons.Filled.Close, contentDescription = "Fechar")
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
                        trailingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = "Selecionar Data", tint = AmareloInvestimento) },
                        interactionSource = interactionSource,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
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
                                Toast.makeText(context, "Aporte guardado com sucesso!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                            .height(52.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmareloInvestimento,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Salvar Aporte",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        if (mostrarDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = dataSelecionada.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { mostrarDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            dataSelecionada = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
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