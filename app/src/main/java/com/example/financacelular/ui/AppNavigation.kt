package com.example.financacelular.ui

import androidx.compose.runtime.DisposableEffect
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.example.financacelular.deteccao.PreenchimentoPendente
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
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
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde
import kotlinx.coroutines.launch

private enum class ModoBarraInferior {
    PADRAO, EXTRATO, CARTAO, PARCELADOS, RECORRENTES, INVESTIMENTO, META, ORCAMENTO, CATEGORIAS
}

internal enum class DestinoPrincipal(
    val rota: String,
    val titulo: String,
    val rotulo: String,
    val icone: ImageVector,
    val iconeSelecionado: ImageVector
) {
    INICIO("inicio", "Início", "Início", Icons.Outlined.Home, Icons.Filled.Home),
    ANALISE("analise", "Análise", "Análise", Icons.Outlined.Analytics, Icons.Filled.Analytics),
    CALENDARIO("calendario", "Calendário", "Agenda", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    CONTAS("contas", "Contas", "Contas", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet)
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
private const val ROTA_CATEGORIAS = "categorias"

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
    var acionarNovaCategoria by rememberSaveable { mutableStateOf(false) }
    var textoPesquisaExtrato by rememberSaveable { mutableStateOf("") }
    var acionarFiltrosExtrato by rememberSaveable { mutableStateOf(false) }
    // ViewModel do Cartão exposto à barra inferior (evita buscá-lo na entrada atual, que pode ser outra durante a transição)
    var cartaoViewModelBarra by remember { mutableStateOf<CartaoViewModel?>(null) }
    var atualizacaoDisponivel by remember { mutableStateOf<AtualizacaoDisponivel?>(null) }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

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

    // Transação detectada numa notificação de banco: abre Nova Transação, que preenche os campos
    val transacaoDetectada by PreenchimentoPendente.atual.collectAsState()
    LaunchedEffect(transacaoDetectada) {
        val detectada = transacaoDetectada ?: return@LaunchedEffect
        navController.navigate("$ROTA_NOVA_TRANSACAO_BASE?tipo=${detectada.tipo.name}") {
            launchSingleTop = true
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
    val emTelaCategorias = currentDestination?.route == ROTA_CATEGORIAS
    val emTelaConfiguracoes = currentDestination?.route == ROTA_CONFIGURACOES

    val modoBarraInferior = when {
        emTelaExtrato -> ModoBarraInferior.EXTRATO
        emTelaCartao -> ModoBarraInferior.CARTAO
        emTelaParcelados -> ModoBarraInferior.PARCELADOS
        emTelaRecorrentes -> ModoBarraInferior.RECORRENTES
        emTelaInvestimento -> ModoBarraInferior.INVESTIMENTO
        emTelaMetas -> ModoBarraInferior.META
        emTelaOrcamento -> ModoBarraInferior.ORCAMENTO
        emTelaCategorias -> ModoBarraInferior.CATEGORIAS
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
            if (!emTelaDeFormulario && !emTelaConfiguracoes) {
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
                            .height(90.dp)
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
                            ModoBarraInferior.INVESTIMENTO -> IlhaDeAcao {
                                BotaoIlha(
                                    texto = "Novo Aporte",
                                    onClick = { acionarNovoInvestimento = true },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ModoBarraInferior.EXTRATO -> IlhaDeAcao {
                                CampoBuscaIlha(
                                    valor = textoPesquisaExtrato,
                                    aoMudar = { textoPesquisaExtrato = it },
                                    placeholder = "Pesquisar no extrato...",
                                    aoBuscar = { keyboardController?.hide() },
                                    modifier = Modifier.weight(1f)
                                )
                                BotaoIlhaSuave(
                                    texto = "Filtros",
                                    onClick = { acionarFiltrosExtrato = true },
                                )
                            }
                            ModoBarraInferior.CARTAO -> {
                                // Ver cartaoViewModelBarra: não usar a entrada atual aqui (pode não ser a do Cartão durante a transição)
                                val cartaoViewModel = cartaoViewModelBarra
                                if (cartaoViewModel != null) {
                                    val faturaPaga by cartaoViewModel.faturaPaga.collectAsState()
                                    val podePagar by cartaoViewModel.podePagarFatura.collectAsState()
                                    val naPaginaAdicionar by cartaoViewModel.naPaginaAdicionar.collectAsState()

                                    IlhaDeAcao {
                                        if (naPaginaAdicionar) {
                                            BotaoIlha(
                                                texto = "Adicionar Cartão",
                                                onClick = { cartaoViewModel.solicitarAdicionarCartao() },
                                                modifier = Modifier.weight(1f),
                                            )
                                        } else {
                                            BotaoIlha(
                                                texto = if (!faturaPaga) "Pagar Fatura" else "Cancelar Pagamento",
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
                                                modifier = Modifier.weight(1f),
                                                cor = if (!faturaPaga) Verde else Coral,
                                                enabled = podePagar
                                            )
                                        }
                                    }
                                }
                            }
                            ModoBarraInferior.PARCELADOS -> IlhaDeAcao {
                                BotaoIlha(
                                    texto = "Novo Parcelamento",
                                    onClick = {
                                        navController.navigate("nova_transacao?tipo=DESPESA&parcelado=true") {
                                            launchSingleTop = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ModoBarraInferior.RECORRENTES -> IlhaDeAcao {
                                BotaoIlha(
                                    texto = "Nova Recorrência",
                                    onClick = {
                                        navController.navigate("nova_transacao?tipo=DESPESA&recorrente=true") {
                                            launchSingleTop = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ModoBarraInferior.META -> IlhaDeAcao {
                                BotaoIlha(
                                    texto = "Nova Meta",
                                    onClick = { acionarNovaMeta = true },
                                    modifier = Modifier.weight(1f),
                                )
                                BotaoIlhaSuave(
                                    texto = "Guardar valor",
                                    onClick = { acionarGuardarValorMeta = true },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ModoBarraInferior.ORCAMENTO -> IlhaDeAcao {
                                BotaoIlha(
                                    texto = "Definir Limite",
                                    onClick = { acionarNovoLimiteOrcamento = true },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ModoBarraInferior.CATEGORIAS -> IlhaDeAcao {
                                BotaoIlha(
                                    texto = "Nova Categoria",
                                    onClick = { acionarNovaCategoria = true },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ModoBarraInferior.PADRAO -> {
                                BarraInferiorPrincipal(
                                    estaSelecionado = { destino ->
                                        currentDestination?.hierarchy?.any { it.route == destino.rota } == true
                                    },
                                    expandido = isFabExpanded,
                                    emAgenda = emTelaCalendario,
                                    aoSelecionar = { destino ->
                                        isFabExpanded = false
                                        val jaSelecionado = currentDestination?.hierarchy?.any { it.route == destino.rota } == true
                                        if (!jaSelecionado) {
                                            navController.navigate(destino.rota) {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    aoAlternarAcoes = { isFabExpanded = !isFabExpanded },
                                    aoGasto = {
                                        isFabExpanded = false
                                        navController.navigate("nova_transacao?tipo=DESPESA") { launchSingleTop = true }
                                    },
                                    aoGanho = {
                                        isFabExpanded = false
                                        navController.navigate("nova_transacao?tipo=RECEITA") { launchSingleTop = true }
                                    },
                                    aoNovaAgenda = {
                                        isFabExpanded = false
                                        acionarNovaAgenda = true
                                    }
                                )
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
                    AnaliseScreen(
                        aoAbrirConfiguracoes = { navController.navigate(ROTA_CONFIGURACOES) }
                    )
                }
                composable(DestinoPrincipal.CALENDARIO.rota) {
                    CalendarioScreen(
                        acionarNovaAgendaExterno = acionarNovaAgenda,
                        aoNovaAgendaAcionada = { acionarNovaAgenda = false },
                        aoAbrirConfiguracoes = { navController.navigate(ROTA_CONFIGURACOES) }
                    )
                }
                composable(DestinoPrincipal.CONTAS.rota) {
                    ContasScreen(
                        aoAbrirConfiguracoes = { navController.navigate(ROTA_CONFIGURACOES) }
                    )
                }
                composable(ROTA_CONFIGURACOES) {
                    ConfiguracoesScreen(
                        viewModel = configuracoesViewModel,
                        aoAbrirCategorias = { navController.navigate(ROTA_CATEGORIAS) },
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
                        acionarFiltrosExterno = acionarFiltrosExtrato,
                        aoFiltrosAcionado = { acionarFiltrosExtrato = false },
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
                composable(ROTA_CATEGORIAS) {
                    CategoriasScreen(
                        acionarNovaExterna = acionarNovaCategoria,
                        aoNovaAcionada = { acionarNovaCategoria = false },
                        aoVoltar = { navController.popBackStack() }
                    )
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