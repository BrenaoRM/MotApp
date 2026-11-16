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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financacelular.ui.theme.AmareloInvestimento
import com.example.financacelular.ui.theme.Tamanho

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

/** Linha "Investido": valor e seta; leva à tela de investimentos. */
@Composable
fun LinhaInvestido(
    valorFormatado: String,
    visivel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forma = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Tamanho.controle)
            .clip(forma)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, forma)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(8.dp).background(AmareloInvestimento, CircleShape))
        Text(
            "Investido",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (visivel) valorFormatado else "R$ ••••",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Botão pequeno "Extrato ›" do hero (área de toque de 48dp, visual de 32dp). */
@Composable
fun BotaoExtratoHero(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val corTexto = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .heightIn(min = Tamanho.toque)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(corTexto.copy(alpha = 0.08f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Extrato", color = corTexto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = corTexto, modifier = Modifier.size(16.dp))
        }
    }
}
