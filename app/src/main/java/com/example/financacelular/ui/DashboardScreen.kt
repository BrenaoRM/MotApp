package com.example.financacelular.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
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
import com.example.financacelular.ui.theme.AzulAgenda
import com.example.financacelular.ui.theme.AzulCartao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.LilasRecorrente
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.RoxoMeta
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.TealParcelados
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.VerdeOrcamento
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
    val corHero2 by animateColorAsState(
        targetValue = if (saldo >= 0) AzulAgenda else CorHeroAlerta,
        animationSpec = tween(600),
        label = "corHero2"
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
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(Tamanho.toque)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(role = Role.Button, onClick = aoAbrirConfiguracoes),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Configurações",
                            tint = MaterialTheme.colorScheme.primary,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MaterialTheme.dimens.cardCornerRadius))
                        .background(Brush.linearGradient(colors = listOf(corHero1, corHero2)))
                ) {
                    // Círculos decorativos
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 70.dp, y = (-70).dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .align(Alignment.BottomStart)
                            .offset(x = (-50).dp, y = 60.dp)
                            .background(Color.White.copy(alpha = 0.06f), CircleShape)
                    )

                    Column(modifier = Modifier.padding(MaterialTheme.dimens.paddingMedium)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PROJEÇÃO DO MÊS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            IconButton(
                                onClick = { saldoVisivel = !saldoVisivel },
                                modifier = Modifier.size(Tamanho.toque)
                            ) {
                                Icon(
                                    if (saldoVisivel) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (saldoVisivel) "Ocultar valores" else "Mostrar valores",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            SeloSaude(saudavel = saldo >= 0)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (saldoVisivel) formatoMoeda.format(saldo) else "R$ ••••••",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IndicadorGlass(
                                "ENTRADAS", if (saldoVisivel) formatoMoeda.format(totalReceitas) else "R$ ••••",
                                Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f)
                            )
                            IndicadorGlass(
                                "SAÍDAS", if (saldoVisivel) formatoMoeda.format(totalDespesas) else "R$ ••••",
                                Icons.AutoMirrored.Filled.TrendingDown, Modifier.weight(1f)
                            )
                            IndicadorGlass(
                                "INVESTIDO", if (saldoVisivel) formatoMoeda.format(totalInvestido) else "R$ ••••",
                                Icons.Filled.Savings, Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = aoAbrirExtrato,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ver Extrato Completo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }

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
                    AtalhoRapidoCard("Fatura", Icons.Filled.CreditCard, AzulCartao, aoAbrirCartao)
                    AtalhoRapidoCard("Assinaturas", Icons.Filled.Repeat, LilasRecorrente, aoAbrirAssinaturas)
                    AtalhoRapidoCard("Parcelados", Icons.AutoMirrored.Filled.ReceiptLong, TealParcelados, aoAbrirParcelados)
                    AtalhoRapidoCard("Investir", Icons.Filled.Savings, AmareloInvestimento, aoAbrirInvestimento)
                    AtalhoRapidoCard("Metas", Icons.Filled.Flag, RoxoMeta, aoAbrirMetas)
                    AtalhoRapidoCard("Orçamento", Icons.Filled.PieChart, VerdeOrcamento, aoAbrirOrcamento)
                }

                Spacer(modifier = Modifier.height(28.dp))

                // ---------------- RESUMO POR CATEGORIA ----------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Resumo por Categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .heightIn(min = Tamanho.toque)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (filtrosAtivos) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                            .clickable(role = Role.Button) { mostrarFiltros = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val corFiltro = if (filtrosAtivos) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = null,
                                tint = corFiltro,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (filtrosAtivos) "Filtrado" else "Filtrar",
                                color = corFiltro,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
        colors = CardDefaults.cardColors(containerColor = cor.copy(alpha = 0.10f)),
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
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(cor, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                titulo,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SeloSaude(saudavel: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (saudavel) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            if (saudavel) "Saudável" else "Atenção",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun IndicadorGlass(
    titulo: String,
    valor: String,
    icone: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                titulo,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}