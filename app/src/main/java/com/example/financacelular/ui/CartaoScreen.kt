package com.example.financacelular.ui

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.CartaoEntity
import com.example.financacelular.data.calcularVencimentoFatura
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.absoluteValue

private val AzulFatura = Color(0xFF5B8DEF)
private val AmareloAlerta = Color(0xFFF5A524)
private val LocalePtBr: Locale = Locale.forLanguageTag("pt-BR")

private fun formatarDataFatura(texto: String): String {
    val partes = texto.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else texto
}

/** "sáb, 04 de out" — cai para dd/MM/yyyy se não conseguir interpretar a data. */
private fun formatarDiaAmigavel(texto: String): String =
    runCatching {
        LocalDate.parse(texto.take(10))
            .format(DateTimeFormatter.ofPattern("EEE, dd 'de' MMM", LocalePtBr))
            .replace(".", "")
            .replaceFirstChar { it.uppercase() }
    }.getOrElse { formatarDataFatura(texto) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartaoScreen(
    viewModel: CartaoViewModel = viewModel(),
    aoVoltar: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val listaCartoes by viewModel.listaCartoes.collectAsState()
    val cartaoSelecionadoId by viewModel.cartaoSelecionadoId.collectAsState()
    val mesSelecionado by viewModel.mesSelecionado.collectAsState()
    val transacoesFatura by viewModel.transacoesFatura.collectAsState()
    val faturaPaga by viewModel.faturaPaga.collectAsState()

    val pagerState = rememberPagerState(pageCount = { listaCartoes.size + 1 })

    // Sincroniza a página do Pager com o ViewModel
    LaunchedEffect(pagerState.currentPage, listaCartoes) {
        if (listaCartoes.isNotEmpty() && pagerState.currentPage < listaCartoes.size) {
            viewModel.selecionarCartao(listaCartoes[pagerState.currentPage].id)
        }
    }

    val nomeMesAno = remember(mesSelecionado) {
        mesSelecionado.month.getDisplayName(TextStyle.FULL, LocalePtBr)
            .replaceFirstChar { it.uppercase() } + " / " + mesSelecionado.year
    }
    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(LocalePtBr) }

    var mostrarConfig by remember { mutableStateOf(false) }
    var cartaoParaEditar by remember { mutableStateOf<CartaoEntity?>(null) }
    var cartaoParaExcluir by remember { mutableStateOf<CartaoEntity?>(null) }
    val sheetStateConfig = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val valorTotalFatura = transacoesFatura.sumOf { it.valor }
    val naPaginaAdicionar = listaCartoes.isEmpty() || pagerState.currentPage >= listaCartoes.size
    val cartaoDaPagina = listaCartoes.getOrNull(pagerState.currentPage)

    // Avisa o ViewModel quando o carrossel está na página "Adicionar cartão" (a barra inferior troca o botão)
    LaunchedEffect(naPaginaAdicionar) { viewModel.definirNaPaginaAdicionar(naPaginaAdicionar) }

    // Botão "Adicionar cartão" da barra inferior pede para abrir o formulário de novo cartão
    LaunchedEffect(Unit) {
        viewModel.pedidoAdicionarCartao.collect {
            cartaoParaEditar = null
            mostrarConfig = true
        }
    }

    // Agrupa os lançamentos por dia (mantém a ordem original)
    val lancamentosPorDia = remember(transacoesFatura) {
        transacoesFatura.groupBy { it.data.toString().take(10) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen),
        contentPadding = PaddingValues(top = 16.dp, bottom = espacoParaBarraFlutuante())
    ) {
        item {
            CabecalhoDePagina(
                titulo = "Cartões",
                corIcone = AzulFatura,
                aoVoltar = aoVoltar
            )
            Spacer(modifier = Modifier.height(16.dp))

            MesSelectorCard(
                nomeMes = nomeMesAno,
                onMesAnterior = { viewModel.mesAnterior() },
                onMesSeguinte = { viewModel.mesSeguinte() }
            )
            Spacer(modifier = Modifier.height(20.dp))

            // CARROSSEL DE CARTÕES
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 4.dp),
                pageSpacing = 12.dp
            ) { page ->
                // Páginas vizinhas ficam menores e mais translúcidas.
                // O offset do pager muda a cada frame do arrasto: lê dentro do graphicsLayer
                // (fase de desenho) para não recompor a tela inteira a cada frame.
                val pageModifier = Modifier.graphicsLayer {
                    val distancia = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                    val destaque = 1f - distancia
                    val escala = 0.92f + 0.08f * destaque
                    scaleX = escala
                    scaleY = escala
                    alpha = 0.6f + 0.4f * destaque
                }

                if (page < listaCartoes.size) {
                    val cartao = listaCartoes[page]
                    val isAtivo = cartao.id == cartaoSelecionadoId

                    CartaoHeroIndividual(
                        cartao = cartao,
                        valorTotal = if (isAtivo) valorTotalFatura else 0.0,
                        faturaPaga = if (isAtivo) faturaPaga else false,
                        quantidadeItens = if (isAtivo) transacoesFatura.size else 0,
                        formatoMoeda = formatoMoeda,
                        modifier = pageModifier,
                        onAbrirConfig = {
                            cartaoParaEditar = cartao
                            mostrarConfig = true
                        }
                    )
                } else {
                    CardAdicionarCartao(
                        modifier = pageModifier,
                        onClick = {
                            cartaoParaEditar = null
                            mostrarConfig = true
                        }
                    )
                }
            }

            if (listaCartoes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    IndicadorDePaginas(pagerState = pagerState)
                }
            }

            // STATUS / VENCIMENTO DA FATURA
            if (!naPaginaAdicionar && cartaoDaPagina != null) {
                Spacer(modifier = Modifier.height(16.dp))
                val vencimento = remember(mesSelecionado, cartaoDaPagina) {
                    calcularVencimentoFatura(
                        anoMes = mesSelecionado,
                        diaVencimento = cartaoDaPagina.diaVencimento,
                        diaFechamento = cartaoDaPagina.diaFechamento
                    )
                }
                BannerStatusFatura(faturaPaga = faturaPaga, vencimento = vencimento)
            }

            if (!naPaginaAdicionar) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Lançamentos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = AzulFatura.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (transacoesFatura.size == 1) "1 item" else "${transacoesFatura.size} itens",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AzulFatura,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        if (!naPaginaAdicionar) {
            if (transacoesFatura.isEmpty()) {
                item { EstadoVazioLancamentos() }
            }

            lancamentosPorDia.forEach { (dia, lancamentos) ->
                item {
                    CabecalhoDoDia(
                        titulo = formatarDiaAmigavel(dia),
                        total = formatoMoeda.format(lancamentos.sumOf { it.valor })
                    )
                }
                itemsIndexed(lancamentos) { index, transacao ->
                    LinhaLancamento(
                        descricao = transacao.descricao,
                        valor = formatoMoeda.format(transacao.valor),
                        posicao = index,
                        total = lancamentos.size
                    )
                }
            }
        }
    }

    // BOTTOM SHEET DE CONFIGURAÇÃO / CRIAÇÃO DE CARTÃO
    if (mostrarConfig) {
        var nomeCartao by remember(cartaoParaEditar) { mutableStateOf(cartaoParaEditar?.nome ?: "") }
        var diaFechamento by remember(cartaoParaEditar) { mutableStateOf(cartaoParaEditar?.diaFechamento?.toString() ?: "") }
        var diaVencimento by remember(cartaoParaEditar) { mutableStateOf(cartaoParaEditar?.diaVencimento?.toString() ?: "") }

        fun diaInvalido(valor: String): Boolean {
            val n = valor.toIntOrNull()
            return valor.isNotEmpty() && (n == null || n !in 1..31)
        }

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
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(AzulFatura.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CreditCard, contentDescription = null, tint = AzulFatura)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (cartaoParaEditar == null) "Novo cartão" else "Configurar cartão",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (cartaoParaEditar == null) "Defina as datas da fatura"
                            else cartaoParaEditar?.nome.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { mostrarConfig = false },
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            CircleShape
                        )
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = nomeCartao,
                    onValueChange = { nomeCartao = it },
                    label = { Text("Nome do cartão") },
                    placeholder = { Text("Ex: Nubank, Inter") },
                    leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = diaFechamento,
                        onValueChange = { diaFechamento = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Fechamento") },
                        placeholder = { Text("18") },
                        leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) },
                        supportingText = { Text("Dia de 1 a 31") },
                        isError = diaInvalido(diaFechamento),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = diaVencimento,
                        onValueChange = { diaVencimento = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Vencimento") },
                        placeholder = { Text("25") },
                        leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
                        supportingText = { Text("Dia de 1 a 31") },
                        isError = diaInvalido(diaVencimento),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val fechamentoInt = diaFechamento.toIntOrNull()?.takeIf { it in 1..31 }
                        val vencimentoInt = diaVencimento.toIntOrNull()?.takeIf { it in 1..31 }
                        if (nomeCartao.isNotBlank() && fechamentoInt != null && vencimentoInt != null) {
                            viewModel.criarOuAtualizarCartao(
                                cartaoId = cartaoParaEditar?.id,
                                nome = nomeCartao.trim(),
                                diaFechamento = fechamentoInt,
                                diaVencimento = vencimentoInt
                            ) {
                                mostrarConfig = false
                                Toast.makeText(context, "Cartão salvo com sucesso!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Preencha todos os campos corretamente (dias de 1 a 31).",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AzulFatura, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Salvar cartão", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                if (cartaoParaEditar != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { cartaoParaExcluir = cartaoParaEditar },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Coral.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Excluir cartão", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    // CONFIRMAÇÃO DE EXCLUSÃO DO CARTÃO
    cartaoParaExcluir?.let { cartao ->
        AlertDialog(
            onDismissRequest = { cartaoParaExcluir = null },
            title = { Text("Excluir cartão?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "O cartão \"${cartao.nome}\" será excluído junto com todas as compras, parcelas e " +
                            "assinaturas lançadas nele. Os pagamentos de fatura já registrados no extrato " +
                            "são mantidos. Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.excluirCartao(cartao) {
                            cartaoParaExcluir = null
                            cartaoParaEditar = null
                            mostrarConfig = false
                            Toast.makeText(context, "Cartão excluído.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Excluir", color = Coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { cartaoParaExcluir = null }) { Text("Cancelar") }
            }
        )
    }
}

// ───────────────────────────── CARTÃO ─────────────────────────────

@Composable
private fun CartaoHeroIndividual(
    cartao: CartaoEntity,
    valorTotal: Double,
    faturaPaga: Boolean,
    quantidadeItens: Int,
    formatoMoeda: NumberFormat,
    modifier: Modifier = Modifier,
    onAbrirConfig: () -> Unit
) {
    val animProgress by animateFloatAsState(
        targetValue = if (faturaPaga) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "transicaoFaturaColor"
    )
    val corInicial = lerp(AzulFatura, Verde, animProgress)
    val corFinal = lerp(gradienteDaCor(AzulFatura), gradienteDaCor(Verde), animProgress)
    val forma = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(212.dp)
            .clip(forma)
            .background(Brush.linearGradient(listOf(corInicial, corFinal)))
    ) {
        // Círculos decorativos translúcidos
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.width * 0.42f,
                center = Offset(size.width * 0.98f, -size.height * 0.08f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.width * 0.34f,
                center = Offset(size.width * 0.02f, size.height * 1.12f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CreditCard,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = cartao.nome.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                PilulaGlass(if (faturaPaga) "PAGA" else "EM ABERTO")
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable(onClick = onAbrirConfig),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Configurar cartão",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Total da fatura",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Text(
                text = formatoMoeda.format(valorTotal),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IndicadorGlassDePagina(
                    "VENCIMENTO",
                    "Dia ${cartao.diaVencimento}",
                    Icons.Filled.CalendarToday,
                    Modifier.weight(1f)
                )
                IndicadorGlassDePagina(
                    "LANÇAMENTOS",
                    if (quantidadeItens == 1) "1 item" else "$quantidadeItens itens",
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CardAdicionarCartao(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val forma = RoundedCornerShape(28.dp)
    Surface(
        shape = forma,
        color = AzulFatura.copy(alpha = 0.06f),
        border = BorderStroke(1.5.dp, AzulFatura.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .height(212.dp)
            .clip(forma)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(AzulFatura.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = AzulFatura,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Adicionar novo cartão",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Nubank, Inter, Mercado Pago…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IndicadorDePaginas(pagerState: PagerState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pagerState.pageCount) { index ->
            val selecionado = pagerState.currentPage == index
            val largura by animateDpAsState(if (selecionado) 24.dp else 8.dp, label = "larguraDot")
            val cor by animateColorAsState(
                if (selecionado) AzulFatura else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                label = "corDot"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(largura)
                    .clip(CircleShape)
                    .background(cor)
            )
        }
    }
}

// ───────────────────────────── FATURA ─────────────────────────────

@Composable
private fun BannerStatusFatura(faturaPaga: Boolean, vencimento: LocalDate) {
    val dias = ChronoUnit.DAYS.between(LocalDate.now(), vencimento).toInt()
    val dataTexto = vencimento.format(DateTimeFormatter.ofPattern("dd/MM"))

    val cor: Color
    val icone: ImageVector
    val titulo: String
    val detalhe: String

    when {
        faturaPaga -> {
            cor = Verde
            icone = Icons.Filled.CheckCircle
            titulo = "Fatura paga"
            detalhe = "Tudo certo por aqui"
        }
        dias < 0 -> {
            cor = Coral
            icone = Icons.Filled.Warning
            titulo = if (dias == -1) "Venceu ontem" else "Venceu há ${-dias} dias"
            detalhe = "Vencimento em $dataTexto"
        }
        dias == 0 -> {
            cor = Coral
            icone = Icons.Filled.Warning
            titulo = "Vence hoje"
            detalhe = "Vencimento em $dataTexto"
        }
        else -> {
            cor = if (dias <= 3) AmareloAlerta else AzulFatura
            icone = Icons.Filled.Schedule
            titulo = if (dias == 1) "Vence amanhã" else "Vence em $dias dias"
            detalhe = "Vencimento em $dataTexto"
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, cor.copy(alpha = 0.20f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(cor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    detalhe,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CabecalhoDoDia(titulo: String, total: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            titulo,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            total,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

/** Linhas do mesmo dia formam um único bloco: cantos grandes nas pontas, pequenos no meio. */
@Composable
private fun LinhaLancamento(
    descricao: String?,
    valor: String,
    posicao: Int,
    total: Int
) {
    val grande = 20.dp
    val pequeno = 6.dp
    val forma = RoundedCornerShape(
        topStart = if (posicao == 0) grande else pequeno,
        topEnd = if (posicao == 0) grande else pequeno,
        bottomStart = if (posicao == total - 1) grande else pequeno,
        bottomEnd = if (posicao == total - 1) grande else pequeno
    )

    Surface(
        shape = forma,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(AzulFatura.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CreditCard,
                    contentDescription = null,
                    tint = AzulFatura,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                descricao ?: "Compra no cartão",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                valor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Coral,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EstadoVazioLancamentos() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(AzulFatura.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = AzulFatura,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Nenhum lançamento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Não há compras na fatura deste mês para este cartão.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}