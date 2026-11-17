package com.example.financacelular.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.CoresCategoria
import com.example.financacelular.ui.theme.CoresCategoriaGanhos
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.util.Locale

private val AlturaGrafico = 130.dp

private val LocalePtBr: Locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()
private val MesesAbreviados = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

private fun formatarEixoY(valor: Double): String {
    if (valor == 0.0) return "0"
    return when {
        valor >= 1_000_000 -> String.format(LocalePtBr, "%.1fM", valor / 1_000_000)
        valor >= 1_000 -> String.format(LocalePtBr, "%.1fk", valor / 1_000)
        else -> String.format(LocalePtBr, "%.0f", valor)
    }
}

/** "2026-10" -> "Out" (se o formato for outro, cai para os dois últimos caracteres). */
private fun rotuloMes(anoMes: String): String {
    val mes = anoMes.takeLast(2).toIntOrNull()
    return if (mes != null && mes in 1..12) MesesAbreviados[mes - 1] else anoMes.takeLast(2)
}

/** "2026-10-03" -> "03/10" (sem depender do tipo exato da data). */
private fun formatarDataCurta(texto: String): String {
    val partes = texto.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}" else texto
}

/** "R$ 141,02" com o "R$" pequeno e os centavos menores e esmaecidos (como nas colunas da Home). */
@Composable
private fun moedaCompacta(texto: String): AnnotatedString {
    val corTexto = MaterialTheme.colorScheme.onSurface
    val numero = texto.replace("R$", "").trim()
    val posVirgula = numero.lastIndexOf(',')
    return buildAnnotatedString {
        withStyle(SpanStyle(fontSize = 11.sp, color = corTexto.copy(alpha = 0.6f))) { append("R$ ") }
        if (posVirgula > 0) {
            append(numero.substring(0, posVirgula))
            withStyle(SpanStyle(fontSize = 12.sp, color = corTexto.copy(alpha = 0.55f))) { append(numero.substring(posVirgula)) }
        } else {
            append(numero)
        }
    }
}

@Composable
fun AnaliseScreen(
    viewModel: AnaliseViewModel = viewModel(),
    aoAbrirConfiguracoes: () -> Unit = {}
) {
    val despesasPorCategoria by viewModel.despesasPorCategoria.collectAsState()
    val receitasPorCategoria by viewModel.receitasPorCategoria.collectAsState()
    val evolucaoMensal by viewModel.evolucaoMensal.collectAsState()
    val topDespesas by viewModel.topDespesas.collectAsState()
    val aportes by viewModel.aportes.collectAsState()
    val periodo by viewModel.periodo.collectAsState()
    val totalDespesas by viewModel.totalDespesasPeriodo.collectAsState()
    val totalReceitas by viewModel.totalReceitasPeriodo.collectAsState()
    val serie by viewModel.serieDoPeriodo.collectAsState()

    val formato = remember { NumberFormat.getCurrencyInstance(LocalePtBr) }
    val saldo = totalReceitas - totalDespesas
    val saldoPositivo = saldo >= 0
    val nomePeriodo = periodo.rotulo()

    val mediaDiaria = if (totalDespesas > 0) totalDespesas / periodo.dias else 0.0
    val maiorCategoriaDespesa = despesasPorCategoria.maxByOrNull { it.total }

    var abaDistribuicao by rememberSaveable { mutableIntStateOf(0) }
    var folhaPeriodoAberta by rememberSaveable { mutableStateOf(false) }
    var graficosProntos by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        graficosProntos = true
    }

    if (folhaPeriodoAberta) {
        FolhaPeriodo(
            periodo = periodo,
            aoEscolher = {
                viewModel.definirPeriodo(it)
                folhaPeriodoAberta = false
            },
            aoFechar = { folhaPeriodoAberta = false }
        )
    }

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
        // ---------------- CABEÇALHO + PERÍODO ----------------
        item {
            // tocar no período abre a folha de escolha
            CabecalhoPadrao(
                titulo = periodo.tituloCurto(),
                subtitulo = periodo.subtituloCurto(),
                aoAbrirConfiguracoes = aoAbrirConfiguracoes,
                aoAnterior = { viewModel.periodoAnterior() },
                aoSeguinte = { viewModel.periodoSeguinte() },
                aoTocarTitulo = { folhaPeriodoAberta = true }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ---------------- CARTÃO PRINCIPAL (mesmo desenho do saldo da Home) ----------------
        item {
            CartaoHeroDePagina(
                cor = if (saldoPositivo) RoxoHero else CorHeroAlerta
            ) {
                Text(
                    if (periodo.ehMesUnico) "Saldo do mês" else "Saldo do período",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                ValorDoHero(formato.format(saldo))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    ColunaFluxo(
                        icone = Icons.Outlined.ArrowUpward,
                        corMarca = Verde,
                        valorFormatado = formato.format(totalReceitas),
                        pontos = serie.entradas,
                        visivel = true,
                        descricao = "Entradas em $nomePeriodo: ${formato.format(totalReceitas)}",
                        modifier = Modifier.weight(1f).padding(end = 14.dp)
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    ColunaFluxo(
                        icone = Icons.Outlined.ArrowDownward,
                        corMarca = Coral,
                        valorFormatado = formato.format(totalDespesas),
                        pontos = serie.saidas,
                        visivel = true,
                        descricao = "Saídas em $nomePeriodo: ${formato.format(totalDespesas)}",
                        modifier = Modifier.weight(1f).padding(start = 14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    RodapeDoHero("Média por dia", Modifier.weight(1f)) {
                        Text(
                            moedaCompacta(formato.format(mediaDiaria)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    RodapeDoHero("Maior gasto", Modifier.weight(1f)) {
                        if (maiorCategoriaDespesa == null || totalDespesas <= 0.0) {
                            Text("Nenhum", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(CoresCategoria[0], CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    maiorCategoriaDespesa.nomeCategoria,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "${(maiorCategoriaDespesa.total / totalDespesas * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ---------------- DISTRIBUIÇÃO ----------------
        item {
            TituloDeSecao("Distribuição")
            Spacer(modifier = Modifier.height(12.dp))
            if (graficosProntos) {
                CartaoAnalise(
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 150f)
                    )
                ) {
                    SeletorQuadrado(
                        opcoes = listOf("Gastos", "Ganhos"),
                        indiceSelecionado = abaDistribuicao,
                        aoSelecionar = { abaDistribuicao = it }
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    val gastos = abaDistribuicao == 0
                    val dados = if (gastos) despesasPorCategoria else receitasPorCategoria
                    val total = if (gastos) totalDespesas else totalReceitas
                    val paletaCores = if (gastos) CoresCategoria else CoresCategoriaGanhos

                    if (dados.isEmpty()) {
                        EstadoVazio(Icons.Outlined.PieChart, "Nenhum registro para este gráfico no período.")
                    } else {
                        val maiorFatia = dados.maxByOrNull { it.total }
                        val descricaoRosca = (if (gastos) "Distribuição de gastos" else "Distribuição de ganhos") +
                            ": total ${formato.format(total)}" +
                            (maiorFatia?.let { ", maior categoria ${it.nomeCategoria}, ${formato.format(it.total)}" } ?: "")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Rosca(
                                dados = dados,
                                total = total,
                                paleta = paletaCores,
                                modifier = Modifier
                                    .size(132.dp)
                                    .semantics(mergeDescendants = true) { contentDescription = descricaoRosca }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (gastos) "Total gasto" else "Total ganho",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formato.format(total),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    if (dados.size == 1) "1 categoria" else "${dados.size} categorias",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        dados.forEachIndexed { index, item ->
                            val fracao = if (total > 0) (item.total / total).toFloat() else 0f
                            LinhaDeCategoria(
                                nome = item.nomeCategoria,
                                valor = formato.format(item.total),
                                fracao = fracao,
                                cor = paletaCores[index % paletaCores.size]
                            )
                        }
                    }
                }
            } else {
                PlaceholderGrafico(altura = 300.dp)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ---------------- MAIORES GASTOS ----------------
        item {
            TituloDeSecao("Maiores gastos")
            Spacer(modifier = Modifier.height(12.dp))
            if (topDespesas.isEmpty()) {
                EstadoVazio(Icons.AutoMirrored.Outlined.TrendingDown, "Nenhuma despesa registrada neste período.")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                ) {
                    GrupoDeLista(topDespesas) { despesa ->
                        LinhaDeLista(
                            icone = iconeParaCategoria(despesa.nomeCategoria, despesa.icone),
                            cor = Coral,
                            titulo = despesa.descricao,
                            subtitulo = "${despesa.nomeCategoria} • ${formatarDataCurta(despesa.data)}",
                            valor = "- ${formato.format(despesa.valor)}",
                            corValor = MaterialTheme.cores.textoGasto
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ---------------- FLUXO DE CAIXA (sempre os últimos 6 meses) ----------------
        item {
            TituloDeSecao("Fluxo de caixa", "6 meses")
            Spacer(modifier = Modifier.height(12.dp))
            if (graficosProntos) {
                CartaoAnalise {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendaPonto(Verde, "Ganhos")
                        LegendaPonto(Coral, "Gastos")
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    if (evolucaoMensal.isEmpty()) {
                        EstadoVazio(Icons.Outlined.BarChart, "Ainda não há dados suficientes para exibir o histórico.")
                    } else {
                        val maiorValor = evolucaoMensal.maxOfOrNull { maxOf(it.totalReceitas, it.totalDespesas) }?.coerceAtLeast(1.0) ?: 1.0
                        GraficoBase(
                            maiorValor = maiorValor,
                            rotulos = evolucaoMensal.map { rotuloMes(it.anoMes) },
                            descricao = "Fluxo de caixa dos últimos ${evolucaoMensal.size} meses: ganhos somam ${formato.format(evolucaoMensal.sumOf { it.totalReceitas })} e gastos somam ${formato.format(evolucaoMensal.sumOf { it.totalDespesas })}"
                        ) {
                            evolucaoMensal.forEachIndexed { indice, item ->
                                // O mês atual (último) em destaque; os anteriores esmaecidos
                                val opacidade = if (indice == evolucaoMensal.lastIndex) 1f else 0.5f
                                val fracaoReceita = (item.totalReceitas / maiorValor).toFloat()
                                val fracaoDespesa = (item.totalDespesas / maiorValor).toFloat()
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .height((AlturaGrafico * fracaoReceita).coerceAtLeast(3.dp))
                                            .background(Verde.copy(alpha = opacidade), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .height((AlturaGrafico * fracaoDespesa).coerceAtLeast(3.dp))
                                            .background(Coral.copy(alpha = opacidade), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                PlaceholderGrafico(altura = 250.dp)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ---------------- APORTES (segue o período da tela) ----------------
        item {
            TituloDeSecao("Aportes", aportes?.granularidade?.texto)
            Spacer(modifier = Modifier.height(12.dp))
            val dadosAportes = aportes
            if (graficosProntos && dadosAportes != null) {
                CartaoAportes(dados = dadosAportes, periodo = periodo, formato = formato)
            } else {
                PlaceholderGrafico(altura = 250.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Componentes locais
// ---------------------------------------------------------------------------

/** Título de seção em texto simples, como na Home; [lateral] é um complemento discreto à direita. */
/** Alternador preto e branco de cantos quase retos: a opção ativa vira a cor do texto (preto no claro, branco no escuro). */
@Composable
internal fun SeletorQuadrado(
    opcoes: List<String>,
    indiceSelecionado: Int,
    aoSelecionar: (Int) -> Unit
) {
    val forma = RoundedCornerShape(12.dp)
    val formaAtiva = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(forma)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(4.dp)
    ) {
        opcoes.forEachIndexed { indice, texto ->
            val ativo = indice == indiceSelecionado
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(formaAtiva)
                    .background(if (ativo) MaterialTheme.colorScheme.background else Color.Transparent)
                    .then(if (ativo) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, formaAtiva) else Modifier)
                    .clickable(role = Role.Tab, onClick = { aoSelecionar(indice) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    texto,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (ativo) FontWeight.Bold else FontWeight.Medium,
                    color = if (ativo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TituloDeSecao(titulo: String, lateral: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (lateral != null) {
            Text(
                lateral,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Rótulo pequeno em cima e valor embaixo, no rodapé do cartão principal. */
@Composable
private fun RodapeDoHero(
    rotulo: String,
    modifier: Modifier = Modifier,
    conteudo: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            rotulo,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        conteudo()
    }
}

/** Rosca fina com as categorias; o total aparece ao lado. */
@Composable
private fun Rosca(
    dados: List<ItemGastoCategoria>,
    total: Double,
    paleta: List<Color>,
    modifier: Modifier = Modifier
) {
    val corTrilha = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Canvas(modifier = modifier) {
        val larguraTraco = 14.dp.toPx()
        val raio = (size.minDimension - larguraTraco) / 2
        val topLeft = Offset((size.width - raio * 2) / 2, (size.height - raio * 2) / 2)
        val tamanho = Size(raio * 2, raio * 2)
        drawArc(
            color = corTrilha,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = larguraTraco),
            topLeft = topLeft,
            size = tamanho
        )
        val folga = if (dados.size > 1) 2f else 0f
        var anguloInicial = -90f
        dados.forEachIndexed { index, item ->
            val fracao = if (total > 0) (item.total / total).toFloat() else 0f
            val varredura = fracao * 360f
            drawArc(
                color = paleta[index % paleta.size],
                startAngle = anguloInicial + folga / 2,
                sweepAngle = (varredura - folga).coerceAtLeast(0.5f),
                useCenter = false,
                style = Stroke(width = larguraTraco),
                topLeft = topLeft,
                size = tamanho
            )
            anguloInicial += varredura
        }
    }
}

/** Categoria com ponto de cor, valor, percentual e uma barra fina proporcional. */
@Composable
private fun LinhaDeCategoria(
    nome: String,
    valor: String,
    fracao: Float,
    cor: Color
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(cor, CircleShape))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                nome,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                valor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "${(fracao * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fracao.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(cor)
            )
        }
    }
}

@Composable
internal fun CartaoAnalise(
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(MaterialTheme.dimens.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.dimens.paddingMedium),
            content = conteudo
        )
    }
}

@Composable
private fun LegendaPonto(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(cor, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Eixo Y, grade tracejada e rótulos dos meses. As barras são desenhadas pelo [barras]. */
@Composable
private fun GraficoBase(
    maiorValor: Double,
    rotulos: List<String>,
    descricao: String,
    barras: @Composable RowScope.() -> Unit
) {
    val corGrade = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val corEixo = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = descricao },
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .height(AlturaGrafico)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(formatarEixoY(maiorValor), style = MaterialTheme.typography.labelSmall, color = corEixo)
            Text(formatarEixoY(maiorValor / 2), style = MaterialTheme.typography.labelSmall, color = corEixo)
            Text("0", style = MaterialTheme.typography.labelSmall, color = corEixo)
        }
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth().height(AlturaGrafico)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val espessura = 1.dp.toPx()
                    val tracejado = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    listOf(0f, size.height / 2, size.height).forEach { y ->
                        drawLine(corGrade, Offset(0f, y), Offset(size.width, y), strokeWidth = espessura, pathEffect = tracejado)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                    content = barras
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rotulos.forEach { rotulo ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            rotulo,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = corEixo
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderGrafico(altura: Dp) {
    Card(
        shape = RoundedCornerShape(MaterialTheme.dimens.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(altura)
    ) {}
}