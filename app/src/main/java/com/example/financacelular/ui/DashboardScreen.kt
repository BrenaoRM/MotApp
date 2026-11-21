package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.theme.AzulCartao
import com.example.financacelular.ui.theme.LilasRecorrente
import com.example.financacelular.ui.theme.RoxoMeta
import com.example.financacelular.ui.theme.TealParcelados
import com.example.financacelular.ui.theme.VerdeOrcamento
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.CoresCategoria
import com.example.financacelular.ui.theme.CoresCategoriaGanhos
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

private fun formatarDataIso(texto: String): String {
    val partes = texto.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else texto
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    aoAbrirExtrato: () -> Unit = {},
    aoAbrirInvestimento: () -> Unit = {},
    aoAbrirCartao: () -> Unit = {},
    aoAbrirAssinaturas: () -> Unit = {},
    aoAbrirParcelados: () -> Unit = {},
    aoAbrirOrcamento: () -> Unit = {},
    aoAbrirMetas: () -> Unit = {},
    mostrarDicaDeteccao: Boolean = false,
    aoLigarDeteccao: () -> Unit = {},
    aoDispensarDeteccao: () -> Unit = {},
    estadoLista: LazyListState = rememberLazyListState()
) {
    val totalDespesas by viewModel.totalDespesas.collectAsState()
    val totalReceitas by viewModel.totalReceitas.collectAsState()
    val mesSelecionado by viewModel.mesSelecionado.collectAsState()
    val resumoDoMes by viewModel.resumoDoMes.collectAsState()
    val aportesDosUltimosMeses by viewModel.aportesDosUltimosMeses.collectAsState()
    val serieDoMes by viewModel.serieDoMes.collectAsState()

    val formatoMoeda = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build()) }
    val saldo = totalReceitas - totalDespesas
    val saldoVisivel = ValoresVisiveis.visivel
    // Categorias e o detalhe delas seguem o mesmo olho do cartão do saldo
    val formatoCategorias = ValoresVisiveis.formato(formatoMoeda)

    // Categorias mostram um tipo por vez; o padrão é Gastos
    var tipoCategorias by rememberSaveable { mutableStateOf(TipoTransacao.DESPESA) }

    // Guarda só a chave (categoriaId-tipo); o resumo é buscado no resumo do mês atual
    var chaveCategoriaDetalhe by rememberSaveable { mutableStateOf<String?>(null) }
    val categoriaDetalheSelecionada = chaveCategoriaDetalhe?.let { chave ->
        resumoDoMes.find { "${it.categoriaId}-${it.tipo}" == chave }
    }

    val alertaFaturaPendente by viewModel.alertaFaturaPendente.collectAsState()

    // Mês por extenso em minúsculas ("novembro"), como o pt-BR devolve
    val mesPorExtenso = remember(mesSelecionado) {
        mesSelecionado.month.getDisplayName(TextStyle.FULL, Locale.Builder().setLanguage("pt").setRegion("BR").build())
    }
    val mesCapitalizado = mesPorExtenso.replaceFirstChar { it.uppercase() }
    val nomeMes = "$mesCapitalizado ${mesSelecionado.year}"

    // Categorias do tipo escolhido, sempre da maior para a menor
    val resumosDoTipo = remember(resumoDoMes, tipoCategorias) {
        resumoDoMes.filter { it.tipo == tipoCategorias }.sortedByDescending { it.total }
    }
    val totalDoTipo = resumosDoTipo.sumOf { it.total }

    // O cartão principal muda de cor quando o saldo fica negativo
    val corHero1 by animateColorAsState(
        targetValue = if (saldo >= 0) RoxoHero else CorHeroAlerta,
        animationSpec = tween(600),
        label = "corHero1"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = estadoLista,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.dimens.paddingScreen),
            contentPadding = PaddingValues(bottom = espacoParaBarraFlutuante())
        ) {
            espacoDoCabecalho()
            item {
                // O cabeçalho (logo e mês) é desenhado em AppNavigation e sobe junto com esta lista
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
                                    onClick = { ValoresVisiveis.alternar() },
                                    modifier = Modifier.size(Tamanho.toque)
                                ) {
                                    Icon(
                                        if (saldoVisivel) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = if (saldoVisivel) "Ocultar valores" else "Mostrar valores",
                                        tint = corTexto.copy(alpha = 0.8f),
                                        modifier = Modifier.size(Tamanho.icone)
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
                                icone = Icons.Outlined.ArrowUpward,
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
                                icone = Icons.Outlined.ArrowDownward,
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

                if (mostrarDicaDeteccao) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CartaoDicaDeteccao(aoLigar = aoLigarDeteccao, aoDispensar = aoDispensarDeteccao)
                }

                Spacer(modifier = Modifier.height(12.dp))
                CartaoInvestido(
                    aportes = aportesDosUltimosMeses,
                    valorFormatado = formatoMoeda.format(aportesDosUltimosMeses.lastOrNull()?.total ?: 0.0),
                    visivel = saldoVisivel,
                    onClick = aoAbrirInvestimento
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ---------------- ACESSO RÁPIDO ----------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AtalhoRapidoCard("Fatura", Icons.Outlined.CreditCard, AzulCartao, aoAbrirCartao)
                    AtalhoRapidoCard("Assinaturas", Icons.Outlined.Repeat, LilasRecorrente, aoAbrirAssinaturas)
                    AtalhoRapidoCard("Parcelados", Icons.AutoMirrored.Outlined.ReceiptLong, TealParcelados, aoAbrirParcelados)
                    AtalhoRapidoCard("Metas", Icons.Outlined.Flag, RoxoMeta, aoAbrirMetas)
                    AtalhoRapidoCard("Orçamento", Icons.Outlined.PieChart, VerdeOrcamento, aoAbrirOrcamento)
                }

                Spacer(modifier = Modifier.height(28.dp))

                // ---------------- CATEGORIAS ----------------
                val mesVazio = resumoDoMes.isEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = Tamanho.toque),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Categorias",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (!mesVazio) {
                        SeletorQuadradoIcones(
                            opcoes = listOf(
                                OpcaoDeIcone(Icons.Outlined.ArrowDownward, "Gastos", MaterialTheme.cores.textoGasto),
                                OpcaoDeIcone(Icons.Outlined.ArrowUpward, "Ganhos", MaterialTheme.cores.textoGanho)
                            ),
                            indiceSelecionado = if (tipoCategorias == TipoTransacao.DESPESA) 0 else 1,
                            aoSelecionar = { tipoCategorias = if (it == 0) TipoTransacao.DESPESA else TipoTransacao.RECEITA }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Um único cartão com todas as categorias do tipo escolhido
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (resumosDoTipo.isEmpty()) {
                        Text(
                            when {
                                mesVazio -> "Nada lançado em $mesPorExtenso"
                                tipoCategorias == TipoTransacao.DESPESA -> "Sem gastos em $mesPorExtenso"
                                else -> "Sem ganhos em $mesPorExtenso"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 22.dp)
                        )
                    } else {
                        val paleta = if (tipoCategorias == TipoTransacao.DESPESA) CoresCategoria else CoresCategoriaGanhos
                        Column {
                            resumosDoTipo.forEachIndexed { indice, resumo ->
                                if (indice > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 70.dp, end = 14.dp),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                LinhaCategoriaInicio(
                                    nome = resumo.titulo,
                                    iconeChave = resumo.icone,
                                    valor = formatoCategorias.format(resumo.total),
                                    fracao = if (totalDoTipo > 0.0) (resumo.total / totalDoTipo).toFloat() else 0f,
                                    cor = paleta[indice % paleta.size],
                                    onClick = { chaveCategoriaDetalhe = "${resumo.categoriaId}-${resumo.tipo}" }
                                )
                            }
                        }
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

                ValorDoHero(formatoCategorias.format(resumo.total))
                if (totalDoTipo > 0.0) {
                    Text(
                        "${(resumo.total / totalDoTipo * 100).toInt()}% " +
                            if (resumo.tipo == TipoTransacao.DESPESA) "dos gastos" else "dos ganhos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(Espaco.md))

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
                    Column {
                        resumo.transacoes.forEachIndexed { indice, transacao ->
                            if (indice > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        transacao.descricao ?: resumo.titulo,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        formatarDataIso(transacao.data.toString()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(Espaco.md))
                                Text(
                                    formatoCategorias.format(transacao.valor),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
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
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = cor, modifier = Modifier.size(Tamanho.icone))
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
                    Icons.Outlined.Close,
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
    val forma = RoundedCornerShape(16.dp)
    // Atalho compacto: ícone e nome na mesma linha
    Row(
        modifier = Modifier
            .height(Tamanho.controle)
            .clip(forma)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, forma)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ícone na cor do atalho sobre um fundo suave da própria cor
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(cor.copy(alpha = 0.16f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(Tamanho.icone))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            titulo,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Linha do cartão de categorias: ícone, nome, valor, percentual e barra fina (como na Análise). */
@Composable
private fun LinhaCategoriaInicio(
    nome: String,
    iconeChave: String?,
    valor: String,
    fracao: Float,
    cor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(cor.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconeParaCategoria(nome, iconeChave), contentDescription = null, tint = cor, modifier = Modifier.size(Tamanho.icone))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    nome,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    valor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    "${(fracao * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 38.dp)
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
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
}
