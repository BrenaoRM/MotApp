package com.example.financacelular.ui

import androidx.compose.runtime.DisposableEffect
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.example.financacelular.ui.theme.dimens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.financacelular.data.AtualizacaoDisponivel
import com.example.financacelular.data.AtualizacaoService
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.AmareloInvestimento
import com.example.financacelular.ui.theme.AuroraIndigo
import com.example.financacelular.ui.theme.AzulAgenda
import com.example.financacelular.ui.theme.AzulAgendaClaro
import com.example.financacelular.ui.theme.AzulCartao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.RoxoMeta
import com.example.financacelular.ui.theme.RoxoMetaEscuro
import com.example.financacelular.ui.theme.Verde
import kotlinx.coroutines.launch

private fun tomClaro(cor: Color, mistura: Float = 0.35f) = lerp(cor, Color.White, mistura)

private enum class ModoBarraInferior {
    PADRAO, EXTRATO, CARTAO, PARCELADOS, RECORRENTES, INVESTIMENTO, META, ORCAMENTO
}

@Composable
private fun IconeCircular(icone: ImageVector) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(Color.White.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

private enum class DestinoPrincipal(val rota: String, val titulo: String, val icone: ImageVector) {
    INICIO("inicio", "Início", Icons.Filled.Home),
    ANALISE("analise", "Análise", Icons.Filled.BarChart),
    CALENDARIO("calendario", "Calendário", Icons.Filled.CalendarMonth),
    CONTAS("contas", "Contas", Icons.Filled.AccountBalance)
}

private const val ROTA_NOVA_TRANSACAO_BASE = "nova_transacao"
private const val ROTA_NOVA_TRANSACAO = "nova_transacao?tipo={tipo}&parcelado={parcelado}&recorrente={recorrente}"
private const val ROTA_ORCAMENTO = "orcamento"
private const val ROTA_METAS = "metas"
private const val ROTA_RECORRENTES = "recorrentes"
private const val ROTA_CARTAO = "cartao"
private const val ROTA_EXTRATO = "extrato"
private const val ROTA_INVESTIMENTO = "investimento"
private const val ROTA_CONFIGURACOES = "configuracoes"
private const val ROTA_PARCELADOS = "parcelados"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(configuracoesViewModel: ConfiguracoesViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val mensagem: (String) -> Unit = remember(snackbarHostState, scope) {
        { texto ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(texto)
            }
            Unit
        }
    }

    if (!configuracoesViewModel.completouBoasVindas) {
        CompositionLocalProvider(LocalMensagem provides mensagem) {
            Box(modifier = Modifier.fillMaxSize()) {
                WelcomeScreen(
                    viewModel = configuracoesViewModel,
                    aoConcluir = {
                        configuracoesViewModel.marcarBoasVindasComoConcluida()
                    }
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }
        }
        return
    }

    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
    var acionarNovoInvestimento by rememberSaveable { mutableStateOf(false) }
    var acionarNovaAgenda by rememberSaveable { mutableStateOf(false) }
    var acionarNovaMeta by rememberSaveable { mutableStateOf(false) }
    var acionarGuardarValorMeta by rememberSaveable { mutableStateOf(false) }
    var acionarNovoLimiteOrcamento by rememberSaveable { mutableStateOf(false) }
    var textoPesquisaExtrato by rememberSaveable { mutableStateOf("") }
    // ViewModel do Cartão exposto à barra inferior (evita buscá-lo na entrada atual, que pode ser outra durante a transição)
    var cartaoViewModelBarra by remember { mutableStateOf<CartaoViewModel?>(null) }
    var atualizacaoDisponivel by remember { mutableStateOf<AtualizacaoDisponivel?>(null) }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val containerWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val espacoFabCentral = if (containerWidthDp < 350.dp) 32.dp else 48.dp

    BackHandler(enabled = isFabExpanded) {
        isFabExpanded = false
    }

    LaunchedEffect(Unit) {
        val versaoAtual = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
        atualizacaoDisponivel = AtualizacaoService.verificarAtualizacao(context, versaoAtual)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(currentDestination?.route) {
        isFabExpanded = false
        if (currentDestination?.route != ROTA_EXTRATO) {
            textoPesquisaExtrato = ""
        }
    }

    val emTelaDeFormulario = currentDestination?.route?.startsWith(ROTA_NOVA_TRANSACAO_BASE) == true
    val emTelaInvestimento = currentDestination?.route == ROTA_INVESTIMENTO
    val emTelaCalendario = currentDestination?.route == DestinoPrincipal.CALENDARIO.rota
    val emTelaMetas = currentDestination?.route == ROTA_METAS
    val emTelaExtrato = currentDestination?.route == ROTA_EXTRATO
    val emTelaCartao = currentDestination?.route == ROTA_CARTAO
    val emTelaParcelados = currentDestination?.route == ROTA_PARCELADOS
    val emTelaRecorrentes = currentDestination?.route == ROTA_RECORRENTES
    val emTelaOrcamento = currentDestination?.route == ROTA_ORCAMENTO

    val modoBarraInferior = when {
        emTelaExtrato -> ModoBarraInferior.EXTRATO
        emTelaCartao -> ModoBarraInferior.CARTAO
        emTelaParcelados -> ModoBarraInferior.PARCELADOS
        emTelaRecorrentes -> ModoBarraInferior.RECORRENTES
        emTelaInvestimento -> ModoBarraInferior.INVESTIMENTO
        emTelaMetas -> ModoBarraInferior.META
        emTelaOrcamento -> ModoBarraInferior.ORCAMENTO
        else -> ModoBarraInferior.PADRAO
    }

    val corFundoTema = MaterialTheme.colorScheme.background
    val corBaseDegrade by animateColorAsState(
        targetValue = if (isFabExpanded) lerp(corFundoTema, Color.Black, 0.4f) else corFundoTema,
        animationSpec = tween(300),
        label = "corBaseDegrade"
    )

    CompositionLocalProvider(LocalMensagem provides mensagem) {
    @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!emTelaDeFormulario) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.25f to corBaseDegrade,
                                    1.0f to corBaseDegrade
                                )
                            )
                            .navigationBarsPadding()
                            .height(75.dp)
                    )

                    AnimatedContent(
                        targetState = modoBarraInferior,
                        transitionSpec = {
                            (fadeIn(tween(300)) + slideInVertically { it / 2 })
                                .togetherWith(fadeOut(tween(200)) + slideOutVertically { it / 2 })
                        },
                        label = "bottomBarTransform"
                    ) { modo ->
                        when (modo) {
                            ModoBarraInferior.INVESTIMENTO -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BotaoPrincipal(
                                        texto = "Novo Aporte",
                                        onClick = { acionarNovoInvestimento = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        cor = AmareloInvestimento,
                                        corConteudo = Color.White,
                                        icone = Icons.Filled.Add
                                    )
                                }
                            }
                            ModoBarraInferior.EXTRATO -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedTextField(
                                        value = textoPesquisaExtrato,
                                        onValueChange = { textoPesquisaExtrato = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(elevation = 12.dp, shape = RoundedCornerShape(20.dp))
                                            .heightIn(min = 52.dp),
                                        placeholder = { Text("Pesquisar no extrato...") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Search,
                                                contentDescription = "Pesquisar",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = {
                                            if (textoPesquisaExtrato.isNotEmpty()) {
                                                IconButton(onClick = { textoPesquisaExtrato = "" }) {
                                                    Icon(
                                                        Icons.Filled.Clear,
                                                        contentDescription = "Limpar",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(20.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                                    )
                                }
                            }
                            ModoBarraInferior.CARTAO -> {
                                // Ver cartaoViewModelBarra: não usar a entrada atual aqui (pode não ser a do Cartão durante a transição)
                                val cartaoViewModel = cartaoViewModelBarra
                                if (cartaoViewModel != null) {
                                    val faturaPaga by cartaoViewModel.faturaPaga.collectAsState()
                                    val podePagar by cartaoViewModel.podePagarFatura.collectAsState()
                                    val naPaginaAdicionar by cartaoViewModel.naPaginaAdicionar.collectAsState()

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .navigationBarsPadding()
                                            .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (naPaginaAdicionar) {
                                            BotaoPrincipal(
                                                texto = "Adicionar Cartão",
                                                onClick = { cartaoViewModel.solicitarAdicionarCartao() },
                                                modifier = Modifier.fillMaxWidth(),
                                                cor = AzulCartao,
                                                corConteudo = Color.White,
                                                icone = Icons.Filled.Add
                                            )
                                        } else {
                                            BotaoPrincipal(
                                                texto = if (!faturaPaga) "Pagar Fatura" else "Cancelar Pagamento da Fatura",
                                                onClick = {
                                                    if (!faturaPaga) {
                                                        cartaoViewModel.pagarFatura {
                                                            mensagem("Fatura paga e descontada do saldo!")
                                                        }
                                                    } else {
                                                        cartaoViewModel.cancelarPagamentoFatura {
                                                            mensagem("Pagamento cancelado com sucesso.")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                cor = if (!faturaPaga) Verde else Coral,
                                                corConteudo = Color.White,
                                                icone = if (!faturaPaga) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.Undo,
                                                enabled = podePagar
                                            )
                                        }
                                    }
                                }
                            }
                            ModoBarraInferior.PARCELADOS -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BotaoPrincipal(
                                        texto = "Novo Parcelamento",
                                        onClick = {
                                            navController.navigate("nova_transacao?tipo=DESPESA&parcelado=true") {
                                                launchSingleTop = true
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        icone = Icons.Filled.Add
                                    )
                                }
                            }
                            ModoBarraInferior.RECORRENTES -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BotaoPrincipal(
                                        texto = "Nova Recorrência",
                                        onClick = {
                                            navController.navigate("nova_transacao?tipo=DESPESA&recorrente=true") {
                                                launchSingleTop = true
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        icone = Icons.Filled.Add
                                    )
                                }
                            }
                            ModoBarraInferior.META -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .height(52.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(
                                                    Brush.linearGradient(colors = listOf(tomClaro(RoxoMeta), RoxoMeta))
                                                )
                                                .clickable { acionarNovaMeta = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconeCircular(Icons.Filled.Flag)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("Nova Meta", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            }
                                        }
                                        Spacer(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(1.dp)
                                                .background(Color.White.copy(alpha = 0.25f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(
                                                    Brush.linearGradient(colors = listOf(RoxoMeta, RoxoMetaEscuro))
                                                )
                                                .clickable { acionarGuardarValorMeta = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconeCircular(Icons.Filled.Savings)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("Guardar valor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            ModoBarraInferior.ORCAMENTO -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BotaoPrincipal(
                                        texto = "Definir Limite",
                                        onClick = { acionarNovoLimiteOrcamento = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        icone = Icons.Filled.PieChart
                                    )
                                }
                            }
                            ModoBarraInferior.PADRAO -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NavigationBar(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                                            .clip(RoundedCornerShape(20.dp))
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .height(50.dp),
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 0.dp,
                                        windowInsets = WindowInsets(0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            DestinoPrincipal.entries.forEachIndexed { index, destino ->
                                                if (index == 2) {
                                                    Spacer(modifier = Modifier.width(espacoFabCentral))
                                                }
                                                val selecionado = currentDestination?.hierarchy?.any { it.route == destino.rota } == true
                                                val indicatorScaleX by animateFloatAsState(
                                                    targetValue = if (selecionado) 1.35f else 0f,
                                                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                                                    label = "indicatorScaleX"
                                                )
                                                val indicatorScaleY by animateFloatAsState(
                                                    targetValue = if (selecionado) 1.15f else 0f,
                                                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 450f),
                                                    label = "indicatorScaleY"
                                                )
                                                val indicatorAlpha by animateFloatAsState(
                                                    targetValue = if (selecionado) 1f else 0f,
                                                    animationSpec = tween(80),
                                                    label = "indicatorAlpha"
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            isFabExpanded = false
                                                            if (!selecionado) {
                                                                navController.navigate(destino.rota) {
                                                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                                    launchSingleTop = true
                                                                    restoreState = true
                                                                }
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (indicatorAlpha > 0f) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .graphicsLayer {
                                                                    scaleX = indicatorScaleX
                                                                    scaleY = indicatorScaleY
                                                                    alpha = indicatorAlpha
                                                                }
                                                                .shadow(elevation = 3.dp, shape = CircleShape)
                                                                .background(
                                                                    brush = Brush.linearGradient(
                                                                        colors = listOf(RoxoMeta, AuroraIndigo, AzulAgenda)
                                                                    ),
                                                                    shape = CircleShape
                                                                )
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = destino.icone,
                                                        contentDescription = destino.titulo,
                                                        tint = if (selecionado)
                                                            Color.White
                                                        else
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val screenWidth = with(density) { windowInfo.containerSize.width.toDp() }
                                    val liquidSpring = spring<Dp>(
                                        dampingRatio = 0.7f,
                                        stiffness = 100f
                                    )
                                    val transition = updateTransition(targetState = isFabExpanded, label = "fabTransition")

                                    val fabWidth by transition.animateDp(
                                        transitionSpec = { liquidSpring },
                                        label = "fabWidth"
                                    ) { expanded ->
                                        if (expanded) (screenWidth - (MaterialTheme.dimens.paddingScreen * 2 + 30.dp)) else 52.dp
                                    }
                                    val fabHeight by transition.animateDp(
                                        transitionSpec = { liquidSpring },
                                        label = "fabHeight"
                                    ) { expanded -> if (expanded) 60.dp else 52.dp }

                                    val fabOffset by transition.animateDp(
                                        transitionSpec = { liquidSpring },
                                        label = "fabOffset"
                                    ) { expanded -> if (expanded) (-76).dp else 0.dp }

                                    val fabElevation by transition.animateDp(
                                        transitionSpec = { liquidSpring },
                                        label = "fabElevation"
                                    ) { expanded -> if (expanded) 12.dp else 6.dp }

                                    val contentAlpha by transition.animateFloat(
                                        transitionSpec = { tween(300) },
                                        label = "contentAlpha"
                                    ) { expanded -> if (expanded) 1f else 0f }

                                    val iconAlpha by transition.animateFloat(
                                        transitionSpec = { tween(200) },
                                        label = "iconAlpha"
                                    ) { expanded -> if (expanded) 0f else 1f }

                                    val corPadrao = MaterialTheme.colorScheme.primary
                                    val corPadraoClara = Color(0xFF5CDBCF)

                                    val gradienteCor1 by animateColorAsState(
                                        targetValue = when {
                                            emTelaCalendario -> AzulAgenda
                                            else -> corPadrao
                                        },
                                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                        label = "gradiente1"
                                    )

                                    val gradienteCor2 by animateColorAsState(
                                        targetValue = when {
                                            emTelaCalendario -> AzulAgendaClaro
                                            else -> corPadraoClara
                                        },
                                        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                                        label = "gradiente2"
                                    )

                                    val rotacaoIcone by animateFloatAsState(
                                        targetValue = if (emTelaCalendario) 180f else 0f,
                                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 150f),
                                        label = "rotacaoIcone"
                                    )

                                    Surface(
                                        modifier = Modifier
                                            .offset(y = fabOffset)
                                            .size(width = fabWidth, height = fabHeight),
                                        shape = CircleShape,
                                        shadowElevation = fabElevation,
                                        color = MaterialTheme.colorScheme.surface,
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (isFabExpanded) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer {
                                                            alpha = contentAlpha
                                                            scaleX = 0.8f + (0.2f * contentAlpha)
                                                            scaleY = 0.8f + (0.2f * contentAlpha)
                                                        }
                                                ) {
                                                    if (emTelaCalendario) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(
                                                                    Brush.linearGradient(
                                                                        colors = listOf(tomClaro(AzulAgenda), AzulAgenda)
                                                                    )
                                                                )
                                                                .clickable {
                                                                    isFabExpanded = false
                                                                    acionarNovaAgenda = true
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                IconeCircular(Icons.Filled.CalendarMonth)
                                                                Spacer(modifier = Modifier.width(10.dp))
                                                                Text("Nova Agenda", color = Color.White, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                                .background(
                                                                    Brush.linearGradient(
                                                                        colors = listOf(tomClaro(Coral), Coral)
                                                                    )
                                                                )
                                                                .clickable {
                                                                    isFabExpanded = false
                                                                    navController.navigate("nova_transacao?tipo=DESPESA") {
                                                                        launchSingleTop = true
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                IconeCircular(Icons.AutoMirrored.Filled.TrendingDown)
                                                                Spacer(modifier = Modifier.width(10.dp))
                                                                Text("Gasto", color = Color.White, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                        Spacer(
                                                            modifier = Modifier
                                                                .fillMaxHeight()
                                                                .width(1.dp)
                                                                .background(Color.White.copy(alpha = 0.25f))
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                                .background(
                                                                    Brush.linearGradient(
                                                                        colors = listOf(tomClaro(Verde), Verde)
                                                                    )
                                                                )
                                                                .clickable {
                                                                    isFabExpanded = false
                                                                    navController.navigate("nova_transacao?tipo=RECEITA") {
                                                                        launchSingleTop = true
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                IconeCircular(Icons.AutoMirrored.Filled.TrendingUp)
                                                                Spacer(modifier = Modifier.width(10.dp))
                                                                Text("Ganho", color = Color.White, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer {
                                                            alpha = iconAlpha
                                                        }
                                                        .background(Brush.linearGradient(colors = listOf(gradienteCor1, gradienteCor2)))
                                                        .clickable { isFabExpanded = true },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Add,
                                                        contentDescription = "Nova Ação",
                                                        tint = Color.White,
                                                        modifier = Modifier.graphicsLayer { rotationZ = rotacaoIcone }
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
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            NavHost(
                navController = navController,
                startDestination = DestinoPrincipal.INICIO.rota,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)) },
                exitTransition = { fadeOut(tween(300)) },
                popEnterTransition = { fadeIn(tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)) },
                popExitTransition = { fadeOut(tween(300)) }
            ) {
                composable(DestinoPrincipal.INICIO.rota) {
                    DashboardScreen(
                        aoAbrirConfiguracoes = { navController.navigate(ROTA_CONFIGURACOES) },
                        aoAbrirExtrato = { navController.navigate(ROTA_EXTRATO) },
                        aoAbrirInvestimento = { navController.navigate(ROTA_INVESTIMENTO) },
                        aoAbrirCartao = { navController.navigate(ROTA_CARTAO) },
                        aoAbrirAssinaturas = { navController.navigate(ROTA_RECORRENTES) },
                        aoAbrirParcelados = { navController.navigate(ROTA_PARCELADOS) },
                        aoAbrirOrcamento = { navController.navigate(ROTA_ORCAMENTO) },
                        aoAbrirMetas = { navController.navigate(ROTA_METAS) }
                    )
                }
                composable(DestinoPrincipal.ANALISE.rota) {
                    AnaliseScreen()
                }
                composable(DestinoPrincipal.CALENDARIO.rota) {
                    CalendarioScreen(
                        acionarNovaAgendaExterno = acionarNovaAgenda,
                        aoNovaAgendaAcionada = { acionarNovaAgenda = false }
                    )
                }
                composable(DestinoPrincipal.CONTAS.rota) {
                    ContasScreen(
                        aoVoltar = { navController.popBackStack() }
                    )
                }
                composable(ROTA_CONFIGURACOES) {
                    ConfiguracoesScreen(
                        viewModel = configuracoesViewModel,
                        aoVoltar = { navController.popBackStack() }
                    )
                }
                composable(
                    route = ROTA_NOVA_TRANSACAO,
                    arguments = listOf(
                        navArgument("tipo") { type = NavType.StringType; defaultValue = "DESPESA" },
                        navArgument("parcelado") { type = NavType.BoolType; defaultValue = false },
                        navArgument("recorrente") { type = NavType.BoolType; defaultValue = false }
                    )
                ) { backStackEntry ->
                    val tipoTexto = backStackEntry.arguments?.getString("tipo") ?: "DESPESA"
                    val isParcelado = backStackEntry.arguments?.getBoolean("parcelado") ?: false
                    val isRecorrente = backStackEntry.arguments?.getBoolean("recorrente") ?: false
                    NovaTransacaoScreen(
                        tipoInicial = TipoTransacao.valueOf(tipoTexto),
                        isParceladoInicial = isParcelado,
                        isRecorrenteInicial = isRecorrente,
                        aoSalvar = { navController.popBackStack() },
                        aoFechar = { navController.popBackStack() }
                    )
                }
                composable(ROTA_ORCAMENTO) {
                    OrcamentoScreen(
                        acionarNovoLimiteExterno = acionarNovoLimiteOrcamento,
                        aoNovoLimiteAcionado = { acionarNovoLimiteOrcamento = false },
                        aoVoltar = { navController.popBackStack() }
                    )
                }
                composable(ROTA_METAS) {
                    MetaScreen(
                        acionarNovaMetaExterna = acionarNovaMeta,
                        aoMetaAcionada = { acionarNovaMeta = false },
                        acionarGuardarValorExterno = acionarGuardarValorMeta,
                        aoGuardarValorAcionado = { acionarGuardarValorMeta = false },
                        aoVoltar = { navController.popBackStack() }
                    )
                }
                composable(ROTA_RECORRENTES) {
                    RecorrenteScreen(aoVoltar = { navController.popBackStack() })
                }
                composable(ROTA_CARTAO) { backStackEntry ->
                    val cartaoViewModel: CartaoViewModel = viewModel(backStackEntry)
                    SideEffect { cartaoViewModelBarra = cartaoViewModel }
                    DisposableEffect(cartaoViewModel) {
                        onDispose { if (cartaoViewModelBarra === cartaoViewModel) cartaoViewModelBarra = null }
                    }
                    CartaoScreen(
                        viewModel = cartaoViewModel,
                        aoVoltar = { navController.popBackStack() }
                    )
                }
                composable(ROTA_EXTRATO) {
                    ExtratoScreen(
                        textoPesquisa = textoPesquisaExtrato,
                        aoVoltar = { navController.popBackStack() }
                    )
                }
                composable(ROTA_INVESTIMENTO) {
                    InvestimentoScreen(
                        acionarNovoAporteExterno = acionarNovoInvestimento,
                        aoAporteAcionado = { acionarNovoInvestimento = false },
                        aoVoltar = { navController.popBackStack() }
                    )
                }
                composable(ROTA_PARCELADOS) {
                    ParceladosScreen(aoVoltar = { navController.popBackStack() })
                }
            }

            AnimatedVisibility(
                visible = isFabExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isFabExpanded = false }
                )
            }
        }
    }
    }

    atualizacaoDisponivel?.let { atualizacao ->
        AtualizacaoSheet(
            atualizacao = atualizacao,
            aoAbrirNavegador = {
                val urlParaAbrir = atualizacao.urlApk.ifBlank { atualizacao.urlPagina }
                context.startActivity(Intent(Intent.ACTION_VIEW, urlParaAbrir.toUri()))
                atualizacaoDisponivel = null
            },
            aoFechar = { atualizacaoDisponivel = null }
        )
    }
}