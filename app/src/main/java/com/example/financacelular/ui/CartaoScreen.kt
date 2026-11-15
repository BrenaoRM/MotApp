package com.example.financacelular.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.CartaoEntity
import com.example.financacelular.data.calcularVencimentoFatura
import com.example.financacelular.ui.theme.AmareloAlerta
import com.example.financacelular.ui.theme.AzulCartao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.absoluteValue

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
    val mensagem = LocalMensagem.current
    val focusManager = LocalFocusManager.current
    val listaCartoesCarregada by viewModel.listaCartoes.collectAsState()
    val listaCartoes = listaCartoesCarregada.orEmpty()
    val categorias by viewModel.categorias.collectAsState()
    val categoriaPorId = remember(categorias) { categorias.associateBy { it.id } }
    val cartaoSelecionadoId by viewModel.cartaoSelecionadoId.collectAsState()
    val mesSelecionado by viewModel.mesSelecionado.collectAsState()
    val transacoesFaturaCarregada by viewModel.transacoesFatura.collectAsState()
    val transacoesFatura = transacoesFaturaCarregada.orEmpty()
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

    var mostrarConfig by rememberSaveable { mutableStateOf(false) }
    var cartaoParaEditarId by rememberSaveable { mutableStateOf<Long?>(null) }
    val cartaoParaEditar = listaCartoes.firstOrNull { it.id == cartaoParaEditarId }
    var confirmandoExclusao by rememberSaveable { mutableStateOf(false) }

    val valorTotalFatura = transacoesFatura.sumOf { it.valor }
    val naPaginaAdicionar = listaCartoes.isEmpty() || pagerState.currentPage >= listaCartoes.size
    val cartaoDaPagina = listaCartoes.getOrNull(pagerState.currentPage)

    // Avisa o ViewModel quando o carrossel está na página "Adicionar cartão" (a barra inferior troca o botão)
    LaunchedEffect(naPaginaAdicionar, listaCartoesCarregada != null) {
        if (listaCartoesCarregada != null) viewModel.definirNaPaginaAdicionar(naPaginaAdicionar)
    }

    // Ao criar um cartão, o carrossel vai para ele (a página "Adicionar" tem key própria e não acompanha o índice)
    var qtdCartoesAnterior by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(listaCartoes.size, listaCartoesCarregada != null) {
        if (listaCartoesCarregada != null) {
            val anterior = qtdCartoesAnterior
            if (anterior != null && listaCartoes.size > anterior) pagerState.animateScrollToPage(anterior)
            qtdCartoesAnterior = listaCartoes.size
        }
    }

    // Botão "Adicionar cartão" da barra inferior pede para abrir o formulário de novo cartão
    LaunchedEffect(Unit) {
        viewModel.pedidoAdicionarCartao.collect {
            cartaoParaEditarId = null
            confirmandoExclusao = false
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
                corIcone = AzulCartao,
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
            if (listaCartoesCarregada != null) HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 4.dp),
                pageSpacing = 12.dp,
                key = { page -> listaCartoes.getOrNull(page)?.id ?: -1L }
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
                            cartaoParaEditarId = cartao.id
                            confirmandoExclusao = false
                            mostrarConfig = true
                        }
                    )
                } else {
                    CardAdicionarCartao(
                        modifier = pageModifier,
                        onClick = {
                            cartaoParaEditarId = null
                            confirmandoExclusao = false
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = AzulCartao.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (transacoesFatura.size == 1) "1 item" else "${transacoesFatura.size} itens",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AzulCartao,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        if (!naPaginaAdicionar) {
            if (transacoesFaturaCarregada?.isEmpty() == true) {
                item(key = "vazio") {
                    EstadoVazio(
                        icone = Icons.AutoMirrored.Filled.ReceiptLong,
                        mensagem = "Não há compras na fatura deste mês para este cartão.",
                        modifier = Modifier.animateItem()
                    )
                }
            }

            lancamentosPorDia.forEach { (dia, lancamentos) ->
                item(key = "dia-$dia") {
                    Column(modifier = Modifier.animateItem()) {
                        CabecalhoDoDia(
                            titulo = formatarDiaAmigavel(dia),
                            total = formatoMoeda.format(lancamentos.sumOf { it.valor })
                        )
                        GrupoDeLista(lancamentos) { transacao ->
                            val categoria = categoriaPorId[transacao.categoriaId]
                            LinhaDeLista(
                                icone = iconeParaCategoria(categoria?.nome.orEmpty()),
                                cor = AzulCartao,
                                titulo = transacao.descricao ?: "Compra no cartão",
                                subtitulo = categoria?.nome,
                                valor = formatoMoeda.format(transacao.valor),
                                corValor = MaterialTheme.cores.textoGasto
                            )
                        }
                    }
                }
            }
        }
    }

    // BOTTOM SHEET DE CONFIGURAÇÃO / CRIAÇÃO DE CARTÃO (a confirmação de exclusão troca o conteúdo dele)
    if (mostrarConfig) {
        var nomeCartao by rememberSaveable(cartaoParaEditarId) { mutableStateOf(cartaoParaEditar?.nome ?: "") }
        var diaFechamento by rememberSaveable(cartaoParaEditarId) { mutableStateOf(cartaoParaEditar?.diaFechamento?.toString() ?: "") }
        var diaVencimento by rememberSaveable(cartaoParaEditarId) { mutableStateOf(cartaoParaEditar?.diaVencimento?.toString() ?: "") }

        fun diaInvalido(valor: String): Boolean {
            val n = valor.toIntOrNull()
            return valor.isNotEmpty() && (n == null || n !in 1..31)
        }

        fun fecharConfig() {
            mostrarConfig = false
            confirmandoExclusao = false
        }

        LaunchedEffect(confirmandoExclusao) {
            if (confirmandoExclusao) focusManager.clearFocus()
        }

        FolhaApp(onDismiss = { fecharConfig() }) {
            AnimatedContent(
                targetState = confirmandoExclusao,
                transitionSpec = {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(150))) using SizeTransform()
                },
                label = "confirmarExclusaoCartao"
            ) { confirmando ->
                if (confirmando) {
                    ConfirmarAcaoConteudo(
                        titulo = "Excluir cartão?",
                        texto = "O cartão \"${cartaoParaEditar?.nome.orEmpty()}\" será excluído junto com todas as compras, parcelas e " +
                                "assinaturas lançadas nele. Os pagamentos de fatura já registrados no extrato " +
                                "são mantidos. Essa ação não pode ser desfeita.",
                        textoConfirmar = "Excluir",
                        onConfirmar = {
                            cartaoParaEditar?.let { cartao ->
                                viewModel.excluirCartao(cartao) {
                                    cartaoParaEditarId = null
                                    fecharConfig()
                                    mensagem("Cartão excluído.")
                                }
                            }
                        },
                        onCancelar = { confirmandoExclusao = false }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CabecalhoDeFolha(
                            titulo = if (cartaoParaEditar == null) "Novo cartão" else "Configurar cartão",
                            aoFechar = { fecharConfig() },
                            subtitulo = if (cartaoParaEditar == null) "Defina as datas da fatura"
                            else cartaoParaEditar.nome
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = nomeCartao,
                            onValueChange = { nomeCartao = it },
                            label = { Text("Nome do cartão") },
                            placeholder = { Text("Ex: Nubank, Inter") },
                            leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                            shape = RoundedCornerShape(14.dp),
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
                                shape = RoundedCornerShape(14.dp),
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
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        BotaoPrincipal(
                            texto = "Salvar cartão",
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
                                        fecharConfig()
                                        mensagem("Cartão salvo com sucesso!")
                                    }
                                } else {
                                    mensagem("Preencha todos os campos corretamente (dias de 1 a 31).")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            cor = AzulCartao,
                            corConteudo = Color.White,
                            icone = Icons.Filled.Check
                        )

                        if (cartaoParaEditar != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            BotaoSecundario(
                                texto = "Excluir cartão",
                                onClick = { confirmandoExclusao = true },
                                modifier = Modifier.fillMaxWidth(),
                                cor = MaterialTheme.cores.textoGasto,
                                icone = Icons.Filled.Delete
                            )
                        }
                        Spacer(modifier = Modifier.height(Espaco.sm))
                    }
                }
            }
        }
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
    val corInicial = lerp(AzulCartao, Verde, animProgress)
    val corFinal = lerp(gradienteDaCor(AzulCartao), gradienteDaCor(Verde), animProgress)
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
                        .size(Tamanho.toque)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onAbrirConfig),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
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
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Total da fatura",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Text(
                text = formatoMoeda.format(valorTotal),
                style = MaterialTheme.typography.displayMedium,
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
        color = Color.Transparent,
        border = BorderStroke(1.5.dp, AzulCartao.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .height(212.dp)
            .clip(forma)
            .clickable(role = Role.Button, onClick = onClick)
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
                    .background(AzulCartao.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = AzulCartao,
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
                if (selecionado) AzulCartao else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
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
            cor = if (dias <= 3) AmareloAlerta else AzulCartao
            icone = Icons.Filled.Schedule
            titulo = if (dias == 1) "Vence amanhã" else "Vence em $dias dias"
            detalhe = "Vencimento em $dataTexto"
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cor.copy(alpha = 0.10f),
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
