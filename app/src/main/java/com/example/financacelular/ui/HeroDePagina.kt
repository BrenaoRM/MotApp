package com.example.financacelular.ui

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.dimens

/**
 * Cartão principal das páginas, no mesmo estilo do cartão do saldo da home: fundo e borda do tema,
 * com um brilho suave na [cor] da página vindo do canto inferior direito.
 */
@Composable
fun CartaoHeroDePagina(
    cor: Color,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    conteudo: @Composable ColumnScope.() -> Unit
) {
    val forma = RoundedCornerShape(MaterialTheme.dimens.cardCornerRadius)
    val temaClaro = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val intensidade = if (temaClaro) 0.45f else 0.75f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            cor.copy(alpha = intensidade),
                            cor.copy(alpha = intensidade / 3),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.92f, size.height * 1.05f),
                        radius = size.width * 0.95f
                    )
                )
            }
            .border(1.dp, MaterialTheme.colorScheme.outline, forma)
    ) {
        Column(
            modifier = Modifier.padding(padding),
            content = conteudo
        )
    }
}

/** Selo discreto (ex.: "3 ativos", "No limite") para o cartão principal das páginas. */
@Composable
fun PilulaDePagina(texto: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** Valor principal do cartão das páginas: centavos menores e esmaecidos, como na home. */
@Composable
fun ValorDoHero(texto: String, modifier: Modifier = Modifier) {
    val cor = MaterialTheme.colorScheme.onSurface
    val posVirgula = texto.lastIndexOf(',')
    Text(
        text = if (posVirgula > 0) buildAnnotatedString {
            append(texto.substring(0, posVirgula))
            withStyle(SpanStyle(color = cor.copy(alpha = 0.55f), fontSize = 20.sp)) {
                append(texto.substring(posVirgula))
            }
        } else AnnotatedString(texto),
        style = MaterialTheme.typography.displayMedium,
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        color = cor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Progresso de 0 a 1 que parte de zero ao aparecer e anima em 300ms a cada mudança. Devolve o `State` para a leitura ficar adiada (no desenho do anel). */
@Composable
private fun progressoAnimado(progresso: Float, rotulo: String): State<Float> {
    var alvo by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(progresso) { alvo = progresso.coerceIn(0f, 1f) }
    return animateFloatAsState(alvo, tween(300), label = rotulo)
}

/**
 * Anel de progresso: trilho cinza e arco na [cor] (de 0 a 1), com o [conteudo] (texto ou ícone) no centro.
 * O arco preenche em 300ms quando aparece e quando o valor muda.
 */
@Composable
fun AnelDeProgresso(
    progresso: Float,
    cor: Color,
    modifier: Modifier = Modifier,
    tamanho: Dp = 44.dp,
    conteudo: @Composable BoxScope.() -> Unit = {}
) {
    val animado by progressoAnimado(progresso, "anelProgresso")
    val corTrilho = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Box(modifier = modifier.size(tamanho), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val traco = 3.5.dp.toPx()
            val lado = size.minDimension - traco
            val canto = Offset(traco / 2, traco / 2)
            drawArc(corTrilho, 0f, 360f, false, canto, Size(lado, lado), style = Stroke(traco))
            if (animado > 0f) {
                drawArc(cor, -90f, 360f * animado, false, canto, Size(lado, lado), style = Stroke(traco, cap = StrokeCap.Round))
            }
        }
        conteudo()
    }
}

/** Barra de 8dp do cartão do topo (progresso geral da página); preenche em 300ms. */
@Composable
fun BarraDoHero(progresso: Float, cor: Color, modifier: Modifier = Modifier) {
    val animado by progressoAnimado(progresso, "barraHero")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animado)
                .fillMaxHeight()
                .background(cor, RoundedCornerShape(4.dp))
        )
    }
}
