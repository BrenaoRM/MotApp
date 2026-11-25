package com.example.financacelular.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.Tamanho

@Composable
fun BotaoPrincipal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.primary,
    corConteudo: Color = Color.Unspecified,
    icone: ImageVector? = null,
    enabled: Boolean = true
) {
    val conteudo = if (corConteudo != Color.Unspecified) corConteudo else if (cor.luminance() > 0.5f) Color.Black else Color.White
    Button(
        onClick = onClick,
        modifier = modifier.height(Tamanho.controle),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = cor, contentColor = conteudo),
        elevation = null
    ) { ConteudoDoBotao(texto, icone) }
}

@Composable
fun BotaoSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.onSurface,
    icone: ImageVector? = null,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(Tamanho.controle),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = cor)
    ) { ConteudoDoBotao(texto, icone) }
}

/** Ícone opcional + texto, o miolo comum de [BotaoPrincipal] e [BotaoSecundario]. */
@Composable
private fun ConteudoDoBotao(texto: String, icone: ImageVector?) {
    if (icone != null) {
        Icon(icone, contentDescription = null, modifier = Modifier.size(Tamanho.icone))
        Spacer(modifier = Modifier.width(Espaco.sm))
    }
    Text(texto, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
