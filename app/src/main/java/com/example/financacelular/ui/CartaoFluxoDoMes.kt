package com.example.financacelular.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.financacelular.ui.theme.AmareloInvestimento
import com.example.financacelular.ui.theme.cores
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Minigráfico de área para uma série acumulada (tema AMOLED).
 * Lista vazia ou sem valor positivo desenha só um traço neutro na base.
 */
@Composable
fun MiniGraficoArea(
    pontos: List<Float>,
    cor: Color,
    modifier: Modifier = Modifier
) {
    val progresso = remember { Animatable(0f) }
    LaunchedEffect(pontos) {
        progresso.snapTo(0f)
        progresso.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    val corBase = MaterialTheme.colorScheme.outline

    Canvas(modifier) {
        val traco = 1.5.dp.toPx()
        val topo = traco
        val base = size.height - traco / 2
        val maximo = pontos.maxOrNull() ?: 0f

        if (maximo <= 0f) {
            drawLine(corBase, Offset(0f, base), Offset(size.width, base), traco, StrokeCap.Round)
            return@Canvas
        }

        fun y(v: Float) = base - (v / maximo) * (base - topo)

        val linha = Path()
        if (pontos.size == 1) {
            linha.moveTo(0f, y(pontos[0]))
            linha.lineTo(size.width, y(pontos[0]))
        } else {
            val passo = size.width / (pontos.size - 1)
            linha.moveTo(0f, y(pontos[0]))
            for (i in 1 until pontos.size) {
                val x0 = (i - 1) * passo
                val x1 = i * passo
                val xm = (x0 + x1) / 2
                linha.cubicTo(xm, y(pontos[i - 1]), xm, y(pontos[i]), x1, y(pontos[i]))
            }
        }
        val area = Path().apply {
            addPath(linha)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        clipRect(right = size.width * progresso.value + 8.dp.toPx()) {
            drawPath(
                area,
                Brush.verticalGradient(listOf(cor.copy(alpha = 0.30f), cor.copy(alpha = 0.04f)))
            )
            drawPath(linha, cor, style = Stroke(traco, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Ponto final com halo: "onde estamos hoje"
        if (progresso.value >= 1f) {
            val centro = Offset(size.width, y(pontos.last()))
            drawCircle(cor.copy(alpha = 0.25f), radius = 5.5.dp.toPx(), center = centro)
            drawCircle(cor, radius = 3.dp.toPx(), center = centro)
        }
    }
}

/** Uma coluna do cartão: ícone, valor (centavos esmaecidos) e minigráfico. */
@Composable
fun ColunaFluxo(
    icone: ImageVector,
    corMarca: Color,
    valorFormatado: String,
    pontos: List<Float>,
    visivel: Boolean,
    descricao: String,
    modifier: Modifier = Modifier
) {
    val textoValor = if (visivel) valorFormatado.replace("R$", "").trim() else "••••"
    val posVirgula = textoValor.lastIndexOf(',')
    val corTexto = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = if (visivel) descricao else "Valor oculto"
        }
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(corMarca.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = corMarca, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 11.sp, color = corTexto.copy(alpha = 0.6f))) { append("R$ ") }
                if (posVirgula > 0) {
                    append(textoValor.substring(0, posVirgula))
                    withStyle(SpanStyle(color = corTexto.copy(alpha = 0.55f), fontSize = 12.sp)) {
                        append(textoValor.substring(posVirgula))
                    }
                } else {
                    append(textoValor)
                }
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = corTexto,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.height(10.dp))
        MiniGraficoArea(
            pontos = if (visivel) pontos else emptyList(),
            cor = corMarca,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(end = 4.dp)
        )
    }
}

/**
 * Card Investido: valor do mês com o botão "Investimentos ›", variação sobre o mês anterior sob o valor
 * e barras dos 6 meses que terminam no mês selecionado (último item de [aportes]). Só o botão
 * "Investimentos ›" leva à tela de investimentos ([onClick]); o resto do card não é tocável.
 */
@Composable
fun CartaoInvestido(
    aportes: List<TotalMensalInvestimento>,
    valorFormatado: String,
    visivel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (aportes.size < 2) return
    val forma = RoundedCornerShape(20.dp)
    val corTexto = MaterialTheme.colorScheme.onSurface
    val corNeutra = corTexto.copy(alpha = 0.13f)
    val ptBr = remember { Locale.Builder().setLanguage("pt").setRegion("BR").build() }

    val meses = aportes.map { YearMonth.parse(it.anoMes) }
    val rotulos = meses.map { it.month.getDisplayName(TextStyle.SHORT, ptBr).take(3).lowercase() }
    val nomeMes = meses.last().month.getDisplayName(TextStyle.FULL, ptBr)
    val atual = aportes.last().total
    val anterior = aportes[aportes.size - 2].total
    val variacao = if (anterior > 0.0) ((atual - anterior) / anterior * 100).roundToInt() else null
    val maximo = aportes.maxOf { it.total }

    val textoValor = if (visivel) valorFormatado.replace("R$", "").trim() else "••••"
    val posVirgula = textoValor.lastIndexOf(',')
    val descricao = if (!visivel) "Investido em $nomeMes, valor oculto" else buildString {
        append("Investido em $nomeMes: $valorFormatado")
        if (variacao != null) append(", ${if (variacao >= 0) "mais" else "menos"} ${kotlin.math.abs(variacao)}% que ${rotulos[rotulos.size - 2]}")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, forma)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 12.sp, color = corTexto.copy(alpha = 0.6f))) { append("R$ ") }
                    if (posVirgula > 0) {
                        append(textoValor.substring(0, posVirgula))
                        withStyle(SpanStyle(color = corTexto.copy(alpha = 0.55f), fontSize = 15.sp)) {
                            append(textoValor.substring(posVirgula))
                        }
                    } else {
                        append(textoValor)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = descricao },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = corTexto,
                maxLines = 1,
                softWrap = false
            )
            BotaoExtratoHero(onClick = onClick, texto = "Investimentos")
        }
        if (visivel && variacao != null) {
            val cor = if (variacao >= 0) MaterialTheme.cores.textoGanho else MaterialTheme.cores.textoGasto
            // A variação já está na descrição do valor; evita ler duas vezes
            Row(modifier = Modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (variacao >= 0) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    tint = cor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "${kotlin.math.abs(variacao)}% vs. ${rotulos[rotulos.size - 2]}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cor,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            aportes.forEachIndexed { indice, aporte ->
                // Valores ocultos: barras de altura igual, para não revelar a proporção
                val altura = when {
                    !visivel -> 16.dp
                    maximo > 0.0 -> (48.dp * (aporte.total / maximo).toFloat()).coerceAtLeast(4.dp)
                    else -> 4.dp
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(altura)
                        .background(
                            if (visivel && indice == aportes.lastIndex) AmareloInvestimento else corNeutra,
                            RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
                        )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rotulos.forEachIndexed { indice, rotulo ->
                val ehAtual = indice == rotulos.lastIndex
                Text(
                    rotulo,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (ehAtual) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (ehAtual) corTexto else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Botão pequeno "Extrato ›" do hero (área de toque de 48dp, visual de 32dp); [texto] troca o rótulo. */
@Composable
fun BotaoExtratoHero(onClick: () -> Unit, modifier: Modifier = Modifier, texto: String = "Extrato") {
    val corTexto = MaterialTheme.colorScheme.onSurface
    val forma = RoundedCornerShape(50)
    // O efeito de toque fica só na pílula (clip antes do clickable); o toque continua com 48dp de altura.
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .clip(forma)
            .background(corTexto.copy(alpha = 0.08f))
            .border(1.dp, MaterialTheme.colorScheme.outline, forma)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(texto, color = corTexto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = corTexto, modifier = Modifier.size(16.dp))
    }
}
