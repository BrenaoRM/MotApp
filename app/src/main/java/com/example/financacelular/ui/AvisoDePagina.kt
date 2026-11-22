package com.example.financacelular.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.cores
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Dispara o aviso do topo: `mensagem("Cartão salvo")` ou `mensagem("Informe o valor", erro = true)`. */
class Mensageiro(private val mostrar: (String, Boolean) -> Unit) {
    operator fun invoke(texto: String, erro: Boolean = false) = mostrar(texto, erro)
}

data class AvisoAtual(val texto: String, val erro: Boolean, val id: Long)

/** Guarda o aviso visível; um aviso novo substitui o atual. */
class EstadoAviso {
    private var proximoId = 0L
    var atual: AvisoAtual? by mutableStateOf(null)
        private set

    fun mostrar(texto: String, erro: Boolean) {
        atual = AvisoAtual(texto, erro, ++proximoId)
    }

    /** Só fecha se [id] ainda for o aviso visível (o tempo de um aviso antigo não apaga o novo). */
    fun fechar(id: Long) {
        if (atual?.id == id) atual = null
    }
}

fun duracaoDoAviso(erro: Boolean): Long = if (erro) 5000L else 3000L

/** Aviso que desce do topo da tela, por cima de tudo (inclusive sheets). */
@Composable
fun AvisoNoTopo(estado: EstadoAviso) {
    val atual = estado.atual
    LaunchedEffect(atual?.id) {
        val aviso = atual ?: return@LaunchedEffect
        delay(duracaoDoAviso(aviso.erro))
        estado.fechar(aviso.id)
    }
    val transicao = updateTransition(atual, label = "aviso")
    if (atual == null && transicao.currentState == null) return

    val topo = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Popup(alignment = Alignment.TopCenter, properties = PopupProperties(focusable = false)) {
        transicao.AnimatedContent(
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 400f)) { -it } + fadeIn())
                    .togetherWith(slideOutVertically(tween(250)) { -it } + fadeOut(tween(200)))
            },
            contentKey = { it?.id }
        ) { aviso ->
            if (aviso != null) {
                CartaoAviso(aviso, onFechar = { estado.fechar(aviso.id) }, modifier = Modifier.padding(top = topo + 6.dp))
            }
        }
    }
}

@Composable
private fun CartaoAviso(aviso: AvisoAtual, onFechar: () -> Unit, modifier: Modifier = Modifier) {
    val cor = if (aviso.erro) MaterialTheme.cores.textoGasto else RoxoHero
    val borda = if (aviso.erro) lerp(MaterialTheme.colorScheme.outline, cor, 0.35f) else MaterialTheme.colorScheme.outline
    val progresso = remember(aviso.id) { Animatable(1f) }
    LaunchedEffect(aviso.id) {
        progresso.animateTo(0f, tween(duracaoDoAviso(aviso.erro).toInt(), easing = LinearEasing))
    }
    val limiteFechar = with(LocalDensity.current) { 25.dp.toPx() }
    var arrasto by remember(aviso.id) { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borda),
        shadowElevation = 6.dp,
        modifier = modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .offset { IntOffset(0, arrasto.roundToInt()) }
            .draggable(
                state = rememberDraggableState { delta -> arrasto = (arrasto + delta).coerceAtMost(0f) },
                orientation = Orientation.Vertical,
                onDragStopped = { if (arrasto < -limiteFechar) onFechar() else arrasto = 0f }
            )
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(30.dp).background(cor.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (aviso.erro) Icons.Rounded.PriorityHigh else Icons.Rounded.Check,
                        contentDescription = null,
                        tint = cor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = aviso.texto,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progresso.value)
                    .height(2.dp)
                    .background(cor.copy(alpha = 0.7f))
            )
        }
    }
}
