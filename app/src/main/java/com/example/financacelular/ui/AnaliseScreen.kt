package com.example.financacelular.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.example.financacelular.ui.theme.Tamanho
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.unit.IntOffset
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
    estadoLista: LazyListState = rememberLazyListState()
) {
    val despesasPorCategoria by viewModel.despesasPorCategoria.collectAsStateWithLifecycle()
    val receitasPorCategoria by viewModel.receitasPorCategoria.collectAsStateWithLifecycle()
    val fluxo by viewModel.fluxo.collectAsStateWithLifecycle()
    val topDespesas by viewModel.topDespesas.collectAsStateWithLifecycle()
    val aportes by viewModel.aportes.collectAsStateWithLifecycle()
    val periodo by viewModel.periodo.collectAsStateWithLifecycle()
    val totalDespesas by viewModel.totalDespesasPeriodo.collectAsStateWithLifecycle()
    val totalReceitas by viewModel.totalReceitasPeriodo.collectAsStateWithLifecycle()
    val serie by viewModel.serieDoPeriodo.collectAsStateWithLifecycle()

    val formatoReal = remember { NumberFormat.getCurrencyInstance(LocalePtBr) }
    val valoresVisiveis = ValoresVisiveis.visivel
    val formato = ValoresVisiveis.formato(formatoReal)
    val saldo = (totalReceitas - totalDespesas).arredondadoACentavos()
    val saldoPositivo = saldo >= 0
    val nomePeriodo = periodo.rotulo()

    val mediaDiaria = if (totalDespesas > 0) totalDespesas / periodo.dias else 0.0
    val maiorCategoriaDespesa = despesasPorCategoria.maxByOrNull { it.total }

    var abaDistribuicao by rememberSaveable { mutableIntStateOf(0) }
    var graficosProntos by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        graficosProntos = true
    }

    LazyColumn(
        state = estadoLista,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen),
        contentPadding = PaddingValues(bottom = espacoParaBarraFlutuante())
    ) {
        espacoDoCabecalho()
        // O cabeçalho (logo e período) é desenhado em AppNavigation e sobe junto com esta lista
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ValorDoHero(formato.format(saldo), Modifier.weight(1f, fill = false))
                    IconButton(onClick = { ValoresVisiveis.alternar() }, modifier = Modifier.size(Tamanho.toque)) {
                        Icon(
                            if (valoresVisiveis) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (valoresVisiveis) "Ocultar valores" else "Mostrar valores",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.size(Tamanho.icone)
                        )
                    }
                }
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
                        visivel = valoresVisiveis,
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
                        visivel = valoresVisiveis,
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

        // ---------------- FLUXO DE CAIXA (segue o período da tela) ----------------
        item {
            val dadosFluxo = fluxo
            TituloDeSecao("Fluxo de caixa")
            Spacer(modifier = Modifier.height(12.dp))
            if (graficosProntos && dadosFluxo != null) {
                CartaoFluxo(dados = dadosFluxo, periodo = periodo, formato = formato)
            } else {
                PlaceholderGrafico(altura = 250.dp)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ---------------- APORTES (segue o período da tela) ----------------
        item {
            TituloDeSecao("Aportes")
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(forma)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(4.dp)
    ) {
        // A pílula ativa desliza de uma opção para a outra
        val larguraOpcao = maxWidth / opcoes.size
        val deslocamento by animateDpAsState(larguraOpcao * indiceSelecionado, tween(280, easing = FastOutSlowInEasing), label = "deslizeSeletor")
        Box(
            modifier = Modifier
                .offset { IntOffset(deslocamento.roundToPx(), 0) }
                .width(larguraOpcao)
                .fillMaxHeight()
                .clip(formaAtiva)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline, formaAtiva)
        )
        Row(modifier = Modifier.fillMaxSize()) {
            opcoes.forEachIndexed { indice, texto ->
                val ativo = indice == indiceSelecionado
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(formaAtiva)
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