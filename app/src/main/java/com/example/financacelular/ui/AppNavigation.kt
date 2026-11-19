package com.example.financacelular.ui

import androidx.compose.runtime.DisposableEffect
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.financacelular.ui.theme.dimens
import kotlinx.coroutines.launch

/** Altura do cabeçalho; as telas com cabeçalho a usam como espaço no topo da lista para ele subir junto com elas. */
val LocalEspacoCabecalho = compositionLocalOf { 0.dp }

/** Primeiro item das listas das abas: reserva exatamente a altura do cabeçalho que fica sobre a lista. */
fun LazyListScope.espacoDoCabecalho() {
    item(key = "espaco_cabecalho", contentType = "espaco_cabecalho") {
        Spacer(Modifier.height(LocalEspacoCabecalho.current))
    }
}

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

// Troca de tela: a saída é curta e a entrada começa depois dela (total de 300 ms)
private const val TEMPO_SAIDA = 90
private const val TEMPO_ENTRADA = 210

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
private val ROTAS_INTERNAS_COM_CABECALHO = setOf(ROTA_CARTAO, ROTA_RECORRENTES, ROTA_PARCELADOS, ROTA_METAS, ROTA_ORCAMENTO)

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
    // Cabeçalho fixo das abas: o ViewModel da Análise e a folha de período ficam aqui, fora das telas
    val analiseViewModel: AnaliseViewModel = viewModel()
    val mesGlobal by MesGlobal.mes.collectAsState()
    val periodoAnalise by analiseViewModel.periodo.collectAsState()
    var folhaPeriodoAberta by rememberSaveable { mutableStateOf(false) }
    // Uma lista por aba: o cabeçalho é derivado da rolagem real da lista da aba atual
    var alturaCabecalhoPx by remember { mutableFloatStateOf(0f) }
    val listaInicio = rememberLazyListState()
    val listaAnalise = rememberLazyListState()
    val listaAgenda = rememberLazyListState()
    val listaCartao = rememberLazyListState()
    val listaRecorrentes = rememberLazyListState()
    val listaParcelados = rememberLazyListState()
    val listaMetas = rememberLazyListState()
    val listaOrcamento = rememberLazyListState()
    val escopoLista = rememberCoroutineScope()
    // Abre uma tela interna sempre no topo (a lista fica aqui, então guardaria a rolagem da visita anterior)
    fun abrirInterna(rota: String, lista: LazyListState) {
        escopoLista.launch { lista.scrollToItem(0) }
        navController.navigate(rota)
    }

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
    val abaAtual = DestinoPrincipal.values().firstOrNull { it.rota == currentDestination?.route }
    // Telas com o cabeçalho fixo fora do NavHost: as 4 abas e as 5 internas (Cartões, Recorrentes, Parcelados, Metas, Orçamento)
    val rotaAtual = currentDestination?.route
    val comCabecalhoFixo = abaAtual != null || rotaAtual in ROTAS_INTERNAS_COM_CABECALHO
    // Última rota com cabeçalho: mantém o conteúdo certo enquanto ele some indo para Configurações etc.
    var rotaDoCabecalho by remember { mutableStateOf(DestinoPrincipal.INICIO.rota) }
    if (comCabecalhoFixo && rotaAtual != null) rotaDoCabecalho = rotaAtual
    val listaDaAba: LazyListState? = when (rotaDoCabecalho) {
        DestinoPrincipal.INICIO.rota -> listaInicio
        DestinoPrincipal.ANALISE.rota -> listaAnalise
        DestinoPrincipal.CALENDARIO.rota -> listaAgenda
        ROTA_CARTAO -> listaCartao
        ROTA_RECORRENTES -> listaRecorrentes
        ROTA_PARCELADOS -> listaParcelados
        ROTA_METAS -> listaMetas
        ROTA_ORCAMENTO -> listaOrcamento
        else -> null // Contas não rola
    }
    val deslocamentoCabecalho = remember { Animatable(0f) }
    LaunchedEffect(listaDaAba) {
        fun alvo(): Float {
            val lista = listaDaAba ?: return 0f
            return if (lista.firstVisibleItemIndex == 0) {
                lista.firstVisibleItemScrollOffset.toFloat().coerceAtMost(alturaCabecalhoPx)
            } else {
                alturaCabecalhoPx
            }
        }
        // Troca de aba: o cabeçalho desliza até a posição da lista nova, no ritmo do fade/scale do NavHost
        deslocamentoCabecalho.animateTo(alvo(), tween(TEMPO_ENTRADA, TEMPO_SAIDA, FastOutSlowInEasing))
        // Depois acompanha a rolagem real, quadro a quadro
        snapshotFlow { alvo() }.collect { deslocamentoCabecalho.snapTo(it) }
    }

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
            CompositionLocalProvider(LocalEspacoCabecalho provides with(LocalDensity.current) { alturaCabecalhoPx.toDp() }) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = DestinoPrincipal.INICIO.rota,
                    modifier = Modifier.fillMaxSize(),
                    // Em sequência: a tela antiga some e só então a nova aparece, para os textos nunca ficarem um sobre o outro
                    enterTransition = { fadeIn(tween(TEMPO_ENTRADA, TEMPO_SAIDA, FastOutSlowInEasing)) + scaleIn(initialScale = 0.97f, animationSpec = tween(TEMPO_ENTRADA, TEMPO_SAIDA, FastOutSlowInEasing)) },
                    exitTransition = { fadeOut(tween(TEMPO_SAIDA)) },
                    popEnterTransition = { fadeIn(tween(TEMPO_ENTRADA, TEMPO_SAIDA, FastOutSlowInEasing)) + scaleIn(initialScale = 0.97f, animationSpec = tween(TEMPO_ENTRADA, TEMPO_SAIDA, FastOutSlowInEasing)) },
                    popExitTransition = { fadeOut(tween(TEMPO_SAIDA)) }
                ) {
                    composable(DestinoPrincipal.INICIO.rota) {
                        DashboardScreen(
                            estadoLista = listaInicio,
                            aoAbrirExtrato = { navController.navigate(ROTA_EXTRATO) },
                            aoAbrirInvestimento = { navController.navigate(ROTA_INVESTIMENTO) },
                            aoAbrirCartao = { abrirInterna(ROTA_CARTAO, listaCartao) },
                            aoAbrirAssinaturas = { abrirInterna(ROTA_RECORRENTES, listaRecorrentes) },
                            aoAbrirParcelados = { abrirInterna(ROTA_PARCELADOS, listaParcelados) },
                            aoAbrirOrcamento = { abrirInterna(ROTA_ORCAMENTO, listaOrcamento) },
                            aoAbrirMetas = { abrirInterna(ROTA_METAS, listaMetas) }
                        )
                    }
                    composable(DestinoPrincipal.ANALISE.rota) {
                        AnaliseScreen(viewModel = analiseViewModel, estadoLista = listaAnalise)
                    }
                    composable(DestinoPrincipal.CALENDARIO.rota) {
                        CalendarioScreen(
                            estadoLista = listaAgenda,
                            acionarNovaAgendaExterno = acionarNovaAgenda,
                            aoNovaAgendaAcionada = { acionarNovaAgenda = false }
                        )
                    }
                    composable(DestinoPrincipal.CONTAS.rota) {
                        ContasScreen()
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
                            estadoLista = listaOrcamento
                        )
                    }
                    composable(ROTA_METAS) {
                        MetaScreen(
                            acionarNovaMetaExterna = acionarNovaMeta,
                            aoMetaAcionada = { acionarNovaMeta = false },
                            acionarGuardarValorExterno = acionarGuardarValorMeta,
                            aoGuardarValorAcionado = { acionarGuardarValorMeta = false },
                            estadoLista = listaMetas
                        )
                    }
                    composable(ROTA_RECORRENTES) {
                        RecorrenteScreen(estadoLista = listaRecorrentes)
                    }
                    composable(ROTA_CARTAO) { backStackEntry ->
                        val cartaoViewModel: CartaoViewModel = viewModel(backStackEntry)
                        SideEffect { cartaoViewModelBarra = cartaoViewModel }
                        DisposableEffect(cartaoViewModel) {
                            onDispose { if (cartaoViewModelBarra === cartaoViewModel) cartaoViewModelBarra = null }
                        }
                        CartaoScreen(
                            viewModel = cartaoViewModel,
                            estadoLista = listaCartao
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
                        ParceladosScreen(estadoLista = listaParcelados)
                    }
                    composable(ROTA_CATEGORIAS) {
                        CategoriasScreen(
                            acionarNovaExterna = acionarNovaCategoria,
                            aoNovaAcionada = { acionarNovaCategoria = false },
                            aoVoltar = { navController.popBackStack() }
                        )
                    }
                }

            // Cabeçalho das telas com cabeçalho fixo: fica fora do NavHost, então não transita com a tela; o conteúdo troca
            // com um fade curto e ele desliza até a posição da lista da tela nova e depois acompanha a rolagem dela
            AnimatedVisibility(
                visible = comCabecalhoFixo,
                enter = fadeIn(tween(TEMPO_ENTRADA, TEMPO_SAIDA, FastOutSlowInEasing)),
                exit = fadeOut(tween(TEMPO_SAIDA))
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = -deslocamentoCabecalho.value
                            // Esmaece enquanto sobe, para não cortar o texto de repente nem encostar nos cards
                            alpha = if (alturaCabecalhoPx > 0f) (1f - deslocamentoCabecalho.value / alturaCabecalhoPx * 1.4f).coerceIn(0f, 1f) else 1f
                        }
                        .onSizeChanged { if (it.height > alturaCabecalhoPx) alturaCabecalhoPx = it.height.toFloat() }
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(horizontal = MaterialTheme.dimens.paddingScreen)
                        .padding(top = 16.dp, bottom = 16.dp)
                ) {
                    val voltar: () -> Unit = { navController.popBackStack() }
                    Crossfade(targetState = rotaDoCabecalho, animationSpec = tween(TEMPO_SAIDA + 60)) { rota ->
                        when (rota) {
                            DestinoPrincipal.ANALISE.rota -> CabecalhoPadrao(
                                titulo = periodoAnalise.tituloCurto(),
                                subtitulo = periodoAnalise.subtituloCurto(),
                                aoAbrirConfiguracoes = { navController.navigate(ROTA_CONFIGURACOES) },
                                aoAnterior = { analiseViewModel.periodoAnterior() },
                                aoSeguinte = { analiseViewModel.periodoSeguinte() },
                                aoTocarTitulo = { folhaPeriodoAberta = true }
                            )
                            DestinoPrincipal.CONTAS.rota -> CabecalhoPadrao(
                                titulo = "Contas",
                                aoAbrirConfiguracoes = { navController.navigate(ROTA_CONFIGURACOES) }
                            )
                            ROTA_CARTAO -> CabecalhoPadrao(
                                titulo = mesGlobal.nomeDoMes(),
                                subtitulo = "Cartões · ${mesGlobal.year}",
                                aoVoltar = voltar,
                                aoAnterior = { MesGlobal.anterior() },
                                aoSeguinte = { MesGlobal.seguinte() }
                            )
                            ROTA_ORCAMENTO -> CabecalhoPadrao(
                                titulo = mesGlobal.nomeDoMes(),
                                subtitulo = "Orçamento · ${mesGlobal.year}",
                                aoVoltar = voltar,
                                aoAnterior = { MesGlobal.anterior() },
                                aoSeguinte = { MesGlobal.seguinte() }
                            )
                            ROTA_RECORRENTES -> CabecalhoPadrao("Recorrentes", aoVoltar = voltar)
                            ROTA_PARCELADOS -> CabecalhoPadrao("Parcelamentos", aoVoltar = voltar)
                            ROTA_METAS -> CabecalhoPadrao("Metas", aoVoltar = voltar)
                            else -> CabecalhoPadrao(
                                titulo = mesGlobal.nomeDoMes(),
                                subtitulo = mesGlobal.year.toString(),
                                aoAbrirConfiguracoes = { navController.navigate(ROTA_CONFIGURACOES) },
                                aoAnterior = { MesGlobal.anterior() },
                                aoSeguinte = { MesGlobal.seguinte() }
                            )
                        }
                    }
                }
            }
            // Tampa a faixa da barra de status para o conteúdo não passar por baixo dela
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background))
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

    if (folhaPeriodoAberta) {
        FolhaPeriodo(
            periodo = periodoAnalise,
            aoEscolher = {
                analiseViewModel.definirPeriodo(it)
                folhaPeriodoAberta = false
            },
            aoFechar = { folhaPeriodoAberta = false }
        )
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