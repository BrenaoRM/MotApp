package com.example.financacelular.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.AzulAgenda
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Verde
import com.example.financacelular.ui.theme.dimens

/** Altura da ilha; mantenha em sincronia com `ALTURA_BARRA_FLUTUANTE` em InsetsPadrao.kt. */
private val AlturaIlha = 66.dp

/**
 * Barra de navegação principal: ilha flutuante de cantos arredondados, sem indicador
 * circular. O item selecionado tem ícone preenchido, cor forte e um traço no topo.
 * O botão central abre os atalhos (Gasto/Ganho ou Nova Agenda) logo acima da barra.
 */
@Composable
internal fun BarraInferiorPrincipal(
    estaSelecionado: (DestinoPrincipal) -> Boolean,
    expandido: Boolean,
    emAgenda: Boolean,
    aoSelecionar: (DestinoPrincipal) -> Unit,
    aoAlternarAcoes: () -> Unit,
    aoGasto: () -> Unit,
    aoGanho: () -> Unit,
    aoNovaAgenda: () -> Unit
) {
    val forma = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(
            visible = expandido,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 },
            exit = fadeOut(tween(150))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (emAgenda) {
                    BotaoPrincipal(
                        texto = "Nova Agenda",
                        onClick = aoNovaAgenda,
                        modifier = Modifier.weight(1f),
                        cor = AzulAgenda,
                        icone = Icons.Filled.CalendarMonth
                    )
                } else {
                    BotaoPrincipal(
                        texto = "Gasto",
                        onClick = aoGasto,
                        modifier = Modifier.weight(1f),
                        cor = Coral,
                        icone = Icons.AutoMirrored.Filled.TrendingDown
                    )
                    BotaoPrincipal(
                        texto = "Ganho",
                        onClick = aoGanho,
                        modifier = Modifier.weight(1f),
                        cor = Verde,
                        icone = Icons.AutoMirrored.Filled.TrendingUp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AlturaIlha)
                .shadow(elevation = 12.dp, shape = forma)
                .clip(forma)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, forma)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DestinoPrincipal.entries.forEachIndexed { indice, destino ->
                if (indice == 2) BotaoCentral(expandido = expandido, onClick = aoAlternarAcoes)
                ItemBarra(
                    destino = destino,
                    selecionado = estaSelecionado(destino),
                    onClick = { aoSelecionar(destino) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.ItemBarra(
    destino: DestinoPrincipal,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    val fonte = remember { MutableInteractionSource() }
    val pressionado by fonte.collectIsPressedAsState()
    val cor by animateColorAsState(
        targetValue = if (selecionado) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "corItemBarra"
    )
    val alfa by animateFloatAsState(
        targetValue = if (pressionado) 0.6f else 1f,
        animationSpec = tween(100),
        label = "alfaItemBarra"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selecionado,
                interactionSource = fonte,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .graphicsLayer { this.alpha = alfa },
        contentAlignment = Alignment.Center
    ) {
        if (selecionado) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(24.dp)
                    .height(3.dp)
                    .background(RoxoHero, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (selecionado) destino.iconeSelecionado else destino.icone,
                contentDescription = null,
                tint = cor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                destino.rotulo,
                style = MaterialTheme.typography.labelMedium,
                color = cor,
                fontWeight = if (selecionado) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BotaoCentral(expandido: Boolean, onClick: () -> Unit) {
    val giro by animateFloatAsState(
        targetValue = if (expandido) 45f else 0f,
        animationSpec = tween(200),
        label = "giroBotaoCentral"
    )
    Box(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RoxoHero)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = if (expandido) "Fechar ações" else "Nova ação",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = giro }
            )
        }
    }
}
