package com.example.financacelular.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Verde

private val AmareloInvestimento = Color(0xFFF2A93B)
private val AmareloClaro = Color(0xFFFFC75F)

private val AuroraVioleta = Color(0xFF8B5CF6)
private val AuroraIndigo = Color(0xFF6366F1)
private val AuroraAzul = Color(0xFF3B82F6)

/** Gera uma variação mais clara de [cor], usada como topo dos gradientes. */
private fun tomClaro(cor: Color, mistura: Float = 0.35f) = lerp(cor, Color.White, mistura)

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
private const val ROTA_NOVA_TRANSACAO = "nova_transacao/{tipo}"
private const val ROTA_ORCAMENTO = "orcamento"
private const val ROTA_METAS = "metas"
private const val ROTA_RECORRENTES = "recorrentes"
private const val ROTA_CARTAO = "cartao"
private const val ROTA_EXPORTAR = "exportar"
private const val ROTA_EXTRATO = "extrato"
private const val ROTA_INVESTIMENTO = "investimento"
private const val ROTA_CATEGORIAS = "categorias"
private const val ROTA_MAIS = "mais"
private const val ROTA_CONFIGURACOES = "configuracoes"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(configuracoesViewModel: ConfiguracoesViewModel) {
    val navController = rememberNavController()

    var isFabExpanded by remember { mutableStateOf(false) }
    var acionarNovoInvestimento by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val emTelaDeFormulario = currentDestination?.route?.startsWith(ROTA_NOVA_TRANSACAO_BASE) == true
    val emTelaInvestimento = currentDestination?.route == ROTA_INVESTIMENTO

    Scaffold(
        bottomBar = {
            if (!emTelaDeFormulario) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
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
                                    Spacer(modifier = Modifier.width(48.dp))
                                }

                                val selecionado = currentDestination?.hierarchy?.any { it.route == destino.rota } == true

                                val indicatorScaleX by animateFloatAsState(
                                    targetValue = if (selecionado) 1.35f else 0f,
                                    animationSpec = spring(dampingRatio = 0.3f, stiffness = 500f),
                                    label = "indicatorScaleX"
                                )

                                val indicatorScaleY by animateFloatAsState(
                                    targetValue = if (selecionado) 1.15f else 0f,
                                    animationSpec = spring(dampingRatio = 0.35f, stiffness = 450f),
                                    label = "indicatorScaleY"
                                )

                                val indicatorAlpha by animateFloatAsState(
                                    targetValue = if (selecionado) 1f else 0f,
                                    animationSpec = tween(80),
                                    label = "indicatorAlpha"
                                )

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (!selecionado) {
                                                navController.navigate(destino.rota) {
                                                    popUpTo(navController.graph.startDestinationId) { saveState = false }
                                                    launchSingleTop = true
                                                    restoreState = false
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
                                                        colors = listOf(AuroraVioleta, AuroraIndigo, AuroraAzul)
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

                    val configuration = LocalConfiguration.current
                    val screenWidth = configuration.screenWidthDp.dp

                    val liquidSpring = spring<Dp>(
                        dampingRatio = 0.7f,
                        stiffness = 100f
                    )

                    val transition = updateTransition(targetState = isFabExpanded, label = "fabTransition")

                    val fabWidth by transition.animateDp(
                        transitionSpec = { liquidSpring },
                        label = "fabWidth"
                    ) { expanded ->
                        if (expanded) {
                            if (emTelaInvestimento) (screenWidth - 80.dp) else (screenWidth - 40.dp)
                        } else 52.dp
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
                        targetValue = if (emTelaInvestimento) AmareloInvestimento else corPadrao,
                        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                        label = "gradiente1"
                    )

                    val gradienteCor2 by animateColorAsState(
                        targetValue = if (emTelaInvestimento) AmareloClaro else corPadraoClara,
                        animationSpec = tween(durationMillis = 1100, easing = LinearOutSlowInEasing),
                        label = "gradiente2"
                    )

                    val rotacaoIcone by animateFloatAsState(
                        targetValue = if (emTelaInvestimento) 180f else 0f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 150f),
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
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = contentAlpha
                                        scaleX = 0.8f + (0.2f * contentAlpha)
                                        scaleY = 0.8f + (0.2f * contentAlpha)
                                    }
                            ) {
                                if (emTelaInvestimento) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(tomClaro(AmareloInvestimento), AmareloInvestimento)
                                                )
                                            )
                                            .clickable(enabled = isFabExpanded) {
                                                isFabExpanded = false
                                                acionarNovoInvestimento = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconeCircular(Icons.Filled.Savings)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Novo Aporte", color = Color.White, fontWeight = FontWeight.Bold)
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
                                            .clickable(enabled = isFabExpanded) {
                                                isFabExpanded = false
                                                navController.navigate("nova_transacao/DESPESA")
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconeCircular(Icons.Filled.TrendingDown)
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
                                            .clickable(enabled = isFabExpanded) {
                                                isFabExpanded = false
                                                navController.navigate("nova_transacao/RECEITA")
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconeCircular(Icons.Filled.TrendingUp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Ganho", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = iconAlpha
                                        scaleX = if (isFabExpanded) 0f else 1f
                                        scaleY = if (isFabExpanded) 0f else 1f
                                    }
                                    .background(Brush.linearGradient(colors = listOf(gradienteCor1, gradienteCor2)))
                                    .clickable(enabled = !isFabExpanded) { isFabExpanded = true },
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
        } // CORREÇÃO: Faltava fechar este bloco antes de abrir o de baixo
    ) { paddingValues ->
        val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val paddingInferiorDinamico = if (emTelaDeFormulario) {
            0.dp
        } else {
            50.dp + 5.dp + navigationBarBottom
        }

        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = DestinoPrincipal.INICIO.rota,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingInferiorDinamico),
                enterTransition = { fadeIn(tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)) },
                exitTransition = { fadeOut(tween(300)) },
                popEnterTransition = { fadeIn(tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)) },
                popExitTransition = { fadeOut(tween(300)) }
            ) {
                composable(DestinoPrincipal.INICIO.rota) {
                    DashboardScreen(
                        aoAbrirMais = { navController.navigate(ROTA_MAIS) },
                        aoAbrirExtrato = { navController.navigate(ROTA_EXTRATO) },
                        aoAbrirInvestimento = { navController.navigate(ROTA_INVESTIMENTO) },
                        aoAbrirCartao = { navController.navigate(ROTA_CARTAO) },
                        aoAbrirAssinaturas = { navController.navigate(ROTA_RECORRENTES) },
                        aoAbrirParcelados = { navController.navigate("parcelados") },
                        aoAbrirOrcamento = { navController.navigate(ROTA_ORCAMENTO) },
                        aoAbrirMetas = { navController.navigate(ROTA_METAS) }
                    )
                }
                composable(DestinoPrincipal.ANALISE.rota) { AnaliseScreen() }
                composable(DestinoPrincipal.CALENDARIO.rota) { CalendarioScreen() }
                composable(DestinoPrincipal.CONTAS.rota) {
                    ContasScreen()
                }
                composable(ROTA_MAIS) { MaisScreen(aoNavegar = { rota -> navController.navigate(rota) }) }
                composable(ROTA_CONFIGURACOES) { ConfiguracoesScreen(viewModel = configuracoesViewModel) }
                composable(
                    route = ROTA_NOVA_TRANSACAO,
                    arguments = listOf(navArgument("tipo") { type = NavType.StringType; defaultValue = "DESPESA" })
                ) { backStackEntry ->
                    val tipoTexto = backStackEntry.arguments?.getString("tipo") ?: "DESPESA"
                    NovaTransacaoScreen(
                        tipoInicial = TipoTransacao.valueOf(tipoTexto),
                        aoSalvar = { navController.popBackStack() },
                        aoFechar = { navController.popBackStack() }
                    )
                }
                composable(ROTA_ORCAMENTO) { OrcamentoScreen() }
                composable(ROTA_METAS) { MetaScreen() }
                composable(ROTA_RECORRENTES) { RecorrenteScreen() }
                composable(ROTA_CARTAO) { CartaoScreen() }
                composable(ROTA_EXPORTAR) { ExportarScreen() }
                composable(ROTA_EXTRATO) { ExtratoScreen() }

                composable(ROTA_INVESTIMENTO) {
                    InvestimentoScreen(
                        acionarNovoAporteExterno = acionarNovoInvestimento,
                        aoAporteAcionado = { acionarNovoInvestimento = false }
                    )
                }

                composable(ROTA_CATEGORIAS) { CategoriasScreen() }
                composable("parcelados") { ParceladosScreen() }
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