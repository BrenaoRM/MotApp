package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.AmareloInvestimento
import com.example.financacelular.ui.theme.AzulCartao
import com.example.financacelular.ui.theme.LilasRecorrente
import com.example.financacelular.ui.theme.RoxoMeta
import com.example.financacelular.ui.theme.TealParcelados
import com.example.financacelular.ui.theme.VerdeOrcamento
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

private fun saudacaoPorHorario(): String {
    val hora = java.time.LocalTime.now().hour
    return when {
        hora < 12 -> "Bom dia"
        hora < 18 -> "Boa tarde"
        else -> "Boa noite"
    }
}

private fun formatarDataIso(texto: String): String {
    val partes = texto.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else texto
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    aoAbrirConfiguracoes: () -> Unit = {},
    aoAbrirExtrato: () -> Unit = {},
    aoAbrirInvestimento: () -> Unit = {},
    aoAbrirCartao: () -> Unit = {},
    aoAbrirAssinaturas: () -> Unit = {},
    aoAbrirParcelados: () -> Unit = {},
    aoAbrirOrcamento: () -> Unit = {},
    aoAbrirMetas: () -> Unit = {}
) {
    val totalDespesas by viewModel.totalDespesas.collectAsState()
    val totalReceitas by viewModel.totalReceitas.collectAsState()
    val mesSelecionado by viewModel.mesSelecionado.collectAsState()
    val resumoDoMes by viewModel.resumoDoMes.collectAsState()
    val totalInvestido by viewModel.totalInvestidoDoMes.collectAsState()
    val serieDoMes by viewModel.serieDoMes.collectAsState()

    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build()) }
    val saldo = totalReceitas - totalDespesas
    var saldoVisivel by rememberSaveable { mutableStateOf(true) }

    var mostrarFiltros by rememberSaveable { mutableStateOf(false) }
    var filtroTipo by rememberSaveable { mutableStateOf<TipoTransacao?>(null) }
    var ordemFiltro by rememberSaveable { mutableStateOf("VALOR") }
    val filtrosAtivos = filtroTipo != null || ordemFiltro != "VALOR"

    // Guarda só a chave (categoriaId-tipo); o resumo é buscado no resumo do mês atual
    var chaveCategoriaDetalhe by rememberSaveable { mutableStateOf<String?>(null) }
    val categoriaDetalheSelecionada = chaveCategoriaDetalhe?.let { chave ->
        resumoDoMes.find { "${it.categoriaId}-${it.tipo}" == chave }
    }

    val alertaFaturaPendente by viewModel.alertaFaturaPendente.collectAsState()
    val nomeUsuario = viewModel.nomeUtilizador
    val saudacao = remember { saudacaoPorHorario() }

    val nomeMes = remember(mesSelecionado) {
        mesSelecionado.month.getDisplayName(TextStyle.FULL, Locale.Builder().setLanguage("pt").setRegion("BR").build())
            .replaceFirstChar { it.uppercase() } + " " + mesSelecionado.year
    }

    val resumosFiltrados = remember(resumoDoMes, filtroTipo, ordemFiltro) {
        resumoDoMes.filter {
            filtroTipo == null || it.tipo == filtroTipo
        }.sortedWith(
            if (ordemFiltro == "VALOR") compareByDescending { it.total }
            else compareBy { it.titulo }
        )
    }

    // Total por tipo (entradas / saídas) para mostrar a participação de cada categoria
    val totaisPorTipo = remember(resumoDoMes) {
        resumoDoMes.groupBy { it.tipo }.mapValues { (_, lista) -> lista.sumOf { it.total } }
    }

    // O cartão principal muda de cor quando o saldo fica negativo
    val corHero1 by animateColorAsState(
        targetValue = if (saldo >= 0) RoxoHero else CorHeroAlerta,
        animationSpec = tween(600),
        label = "corHero1"
    )

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
                // ---------------- CABEÇALHO ----------------
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            saudacao,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            nomeUsuario,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    // Engrenagem sem fundo, discreta
                    Box(
                        modifier = Modifier
                            .size(Tamanho.toque)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = aoAbrirConfiguracoes),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Configurações",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (alertaFaturaPendente) {
                    FaixaFaturaVencendo(
                        aoVerFatura = {
                            viewModel.marcarAlertaComoExibido()
                            aoAbrirCartao()
                        },
                        aoDispensar = { viewModel.marcarAlertaComoExibido() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                MesSelectorCard(
                    nomeMes = nomeMes,
                    onMesAnterior = { viewModel.mesAnterior() },
                    onMesSeguinte = { viewModel.mesSeguinte() }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ---------------- CARTÃO PRINCIPAL (PROJEÇÃO) ----------------
                // Mesmo layout em todos os temas; só as cores mudam.
                val formaHero = RoundedCornerShape(MaterialTheme.dimens.cardCornerRadius)
                val temaClaro = MaterialTheme.colorScheme.background.luminance() > 0.5f
                val intensidadeBrilho = if (temaClaro) 0.45f else 0.75f
                val corTexto = MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(formaHero)
                        .background(MaterialTheme.colorScheme.surface)
                        .drawBehind {
                            // Brilho violeta vindo do canto inferior direito
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(
                                        corHero1.copy(alpha = intensidadeBrilho),
                                        corHero1.copy(alpha = intensidadeBrilho / 3),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.92f, size.height * 1.05f),
                                    radius = size.width * 0.95f
                                )
                            )
                        }
                        .border(1.dp, MaterialTheme.colorScheme.outline, formaHero)
                ) {
                    Column(modifier = Modifier.padding(MaterialTheme.dimens.paddingMedium)) {
                        val textoSaldo = if (saldoVisivel) formatoMoeda.format(saldo) else "R$ ••••••"
                        val posVirgula = textoSaldo.lastIndexOf(',')
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            // Valor + olho ocupam o espaço que sobra; o botão Extrato fica fixo à direita
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    // Os centavos ficam menores e esmaecidos
                                    if (posVirgula > 0) buildAnnotatedString {
                                        append(textoSaldo.substring(0, posVirgula))
                                        withStyle(SpanStyle(color = corTexto.copy(alpha = 0.55f), fontSize = 20.sp)) {
                                            append(textoSaldo.substring(posVirgula))
                                        }
                                    } else AnnotatedString(textoSaldo),
                                    style = MaterialTheme.typography.displayMedium,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = corTexto,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                IconButton(
                                    onClick = { saldoVisivel = !saldoVisivel },
                                    modifier = Modifier.size(Tamanho.toque)
                                ) {
                                    Icon(
                                        if (saldoVisivel) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = if (saldoVisivel) "Ocultar valores" else "Mostrar valores",
                                        tint = corTexto.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            BotaoExtratoHero(onClick = aoAbrirExtrato)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            ColunaFluxo(
                                icone = Icons.Filled.ArrowUpward,
                                corMarca = Verde,
                                valorFormatado = formatoMoeda.format(totalReceitas),
                                pontos = serieDoMes.entradas,
                                visivel = saldoVisivel,
                                descricao = "Entradas em $nomeMes: ${formatoMoeda.format(totalReceitas)}",
                                modifier = Modifier.weight(1f).padding(end = 14.dp)
                            )
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                            ColunaFluxo(
                                icone = Icons.Filled.ArrowDownward,
                                corMarca = Coral,
                                valorFormatado = formatoMoeda.format(totalDespesas),
                                pontos = serieDoMes.saidas,
                                visivel = saldoVisivel,
                                descricao = "Saídas em $nomeMes: ${formatoMoeda.format(totalDespesas)}",
                                modifier = Modifier.weight(1f).padding(start = 14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                LinhaInvestido(
                    valorFormatado = formatoMoeda.format(totalInvestido),
                    visivel = saldoVisivel,
                    onClick = aoAbrirInvestimento
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ---------------- ACESSO RÁPIDO ----------------
                Text("Acesso rápido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AtalhoRapidoCard("Fatura", Icons.Outlined.CreditCard, AzulCartao, aoAbrirCartao)
                    AtalhoRapidoCard("Assinaturas", Icons.Outlined.Repeat, LilasRecorrente, aoAbrirAssinaturas)
                    AtalhoRapidoCard("Parcelados", Icons.AutoMirrored.Outlined.ReceiptLong, TealParcelados, aoAbrirParcelados)
                    AtalhoRapidoCard("Investir", Icons.Outlined.Savings, AmareloInvestimento, aoAbrirInvestimento)
                    AtalhoRapidoCard("Metas", Icons.Outlined.Flag, RoxoMeta, aoAbrirMetas)
                    AtalhoRapidoCard("Orçamento", Icons.Outlined.PieChart, VerdeOrcamento, aoAbrirOrcamento)
                }

                Spacer(modifier = Modifier.height(28.dp))

                // ---------------- RESUMO POR CATEGORIA ----------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Resumo por Categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    // Só um ícone discreto; fica na cor de destaque quando há filtro ativo
                    IconButton(
                        onClick = { mostrarFiltros = true },
                        modifier = Modifier.size(Tamanho.toque)
                    ) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = if (filtrosAtivos) "Filtros ativos" else "Filtrar",
                            tint = if (filtrosAtivos) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Tamanho.icone)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (resumosFiltrados.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Nenhuma movimentação encontrada com esses filtros.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }

            // A chave usa o id da categoria: o título não é único (várias categorias podem aparecer como
            // "Sem categoria" enquanto as categorias ainda carregam, ou ter o mesmo nome) e chave repetida derruba o app
            items(resumosFiltrados, key = { "${it.categoriaId}-${it.tipo}" }) { resumo ->
                val nomeCategoria = resumo.titulo
                val cor = if (resumo.tipo == TipoTransacao.DESPESA) Coral else Verde
                val corTexto = if (resumo.tipo == TipoTransacao.DESPESA) MaterialTheme.cores.textoGasto else MaterialTheme.cores.textoGanho
                val sinal = if (resumo.tipo == TipoTransacao.DESPESA) "- " else "+ "
                val totalDoTipo = totaisPorTipo[resumo.tipo] ?: 0.0
                val fracao = if (totalDoTipo > 0.0) (resumo.total / totalDoTipo).toFloat().coerceIn(0f, 1f) else 0f
                val percentual = (fracao * 100).toInt()
                val rotuloTipo = if (resumo.tipo == TipoTransacao.DESPESA) "das saídas" else "das entradas"

                Card(
                    onClick = { chaveCategoriaDetalhe = "${resumo.categoriaId}-${resumo.tipo}" },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(cor.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    iconeParaCategoria(nomeCategoria),
                                    contentDescription = null,
                                    tint = cor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    nomeCategoria,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    "$percentual% $rotuloTipo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "$sinal${formatoMoeda.format(resumo.total)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = corTexto,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { fracao },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = cor,
                            trackColor = cor.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }

        // ---------------- SHEET: DETALHES DA CATEGORIA ----------------
        categoriaDetalheSelecionada?.let { resumo ->
            FolhaApp(onDismiss = { chaveCategoriaDetalhe = null }) {
                CabecalhoDeFolha(
                    titulo = resumo.titulo,
                    aoFechar = { chaveCategoriaDetalhe = null },
                    subtitulo = if (resumo.transacoes.size == 1) "1 lançamento" else "${resumo.transacoes.size} lançamentos"
                )
                Spacer(modifier = Modifier.height(Espaco.lg))

                if (resumo.transacoes.isEmpty()) {
                    Text(
                        text = "Nenhum lançamento encontrado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Espaco.xxl)
                    )
                } else {
                    GrupoDeLista(itens = resumo.transacoes) { transacao ->
                        val ehGasto = transacao.tipo == TipoTransacao.DESPESA
                        LinhaDeLista(
                            icone = if (ehGasto) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                            cor = if (ehGasto) Coral else Verde,
                            titulo = transacao.descricao ?: resumo.titulo,
                            subtitulo = formatarDataIso(transacao.data.toString()),
                            valor = (if (ehGasto) "- " else "+ ") + formatoMoeda.format(transacao.valor),
                            corValor = if (ehGasto) MaterialTheme.cores.textoGasto else MaterialTheme.cores.textoGanho
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Espaco.lg))
            }
        }

        // ---------------- SHEET: FILTROS ----------------
        if (mostrarFiltros) {
            FolhaApp(onDismiss = { mostrarFiltros = false }) {
                CabecalhoDeFolha(titulo = "Opções de visualização", aoFechar = { mostrarFiltros = false })
                Spacer(modifier = Modifier.height(24.dp))
                Text("EXIBIR TIPO DE MOVIMENTAÇÃO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                SeletorSegmentado(
                    opcoes = listOf("Tudo", "Ganhos", "Gastos"),
                    indiceSelecionado = when (filtroTipo) {
                        null -> 0
                        TipoTransacao.RECEITA -> 1
                        TipoTransacao.DESPESA -> 2
                    },
                    aoSelecionar = { indice ->
                        filtroTipo = when (indice) {
                            1 -> TipoTransacao.RECEITA
                            2 -> TipoTransacao.DESPESA
                            else -> null
                        }
                    },
                    cor = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("ORDENAR CATEGORIAS POR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                SeletorSegmentado(
                    opcoes = listOf("Maior valor", "A-Z"),
                    indiceSelecionado = if (ordemFiltro == "VALOR") 0 else 1,
                    aoSelecionar = { indice -> ordemFiltro = if (indice == 0) "VALOR" else "NOME" },
                    cor = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Espaco.md)
                ) {
                    BotaoSecundario(
                        texto = "Limpar filtros",
                        onClick = {
                            filtroTipo = null
                            ordemFiltro = "VALOR"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    BotaoPrincipal(
                        texto = "Concluir",
                        onClick = { mostrarFiltros = false },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(Espaco.lg))
            }
        }
    }
}

/** Faixa no topo do Dashboard avisando que a fatura do cartão vence nos próximos dias. */
@Composable
private fun FaixaFaturaVencendo(aoVerFatura: () -> Unit, aoDispensar: () -> Unit) {
    val cor = MaterialTheme.cores.alerta
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cor.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = Espaco.lg, top = Espaco.sm, bottom = Espaco.sm, end = Espaco.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(cor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = cor, modifier = Modifier.size(Tamanho.icone))
            }
            Spacer(modifier = Modifier.width(Espaco.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Fatura vencendo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Pague nos próximos dias para evitar juros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = aoVerFatura,
                modifier = Modifier.heightIn(min = Tamanho.toque)
            ) {
                Text("Ver fatura", fontWeight = FontWeight.Bold, color = cor)
            }
            IconButton(onClick = aoDispensar, modifier = Modifier.size(Tamanho.toque)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dispensar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Tamanho.icone)
                )
            }
        }
    }
}

@Composable
private fun AtalhoRapidoCard(titulo: String, icone: ImageVector, cor: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .width(104.dp)
            .height(92.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Ícone na cor do atalho sobre um fundo suave da própria cor
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(cor.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(20.dp))
            }
            Text(
                titulo,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
