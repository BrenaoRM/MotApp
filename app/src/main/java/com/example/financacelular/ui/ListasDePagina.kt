package com.example.financacelular.ui

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.AzulCartao
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.Tamanho

/** Bloco único de linhas (cartão sem borda) com divisórias finas entre elas. */
@Composable
fun <T> GrupoDeLista(
    itens: List<T>,
    modifier: Modifier = Modifier,
    linha: @Composable (T) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            itens.forEachIndexed { indice, item ->
                if (indice > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 74.dp, end = Espaco.lg),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
                linha(item)
            }
        }
    }
}

/** Linha com ícone em quadrado colorido, título/subtítulo e valor à direita (visual do Extrato). */
@Composable
fun LinhaDeLista(
    icone: ImageVector,
    cor: Color,
    titulo: String,
    subtitulo: String?,
    valor: String,
    corValor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    descricaoAcessivel: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (descricaoAcessivel != null) contentDescription = descricaoAcessivel
            }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Espaco.lg, vertical = Espaco.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(cor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(Tamanho.iconeGrande))
        }
        Spacer(modifier = Modifier.width(Espaco.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitulo != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            valor,
            color = corValor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun EstadoVazio(
    icone: ImageVector,
    mensagem: String,
    modifier: Modifier = Modifier,
    textoAcao: String? = null,
    aoAcao: (() -> Unit)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Espaco.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(primary.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = primary, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(Espaco.lg))
        Text(
            mensagem,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (textoAcao != null && aoAcao != null) {
            Spacer(modifier = Modifier.height(Espaco.lg))
            BotaoSecundario(textoAcao, aoAcao, cor = primary)
        }
    }
}

/** Etiqueta pequena com o nome do cartão (extrato, recorrentes e parcelados). */
@Composable
fun EtiquetaCartao(nome: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AzulCartao.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.CreditCard,
            contentDescription = null,
            tint = AzulCartao,
            modifier = Modifier.size(Tamanho.iconePequeno)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = nome,
            style = MaterialTheme.typography.labelSmall,
            color = AzulCartao,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Um único cartão (borda 1dp, raio 20) para as linhas da página, como as Categorias da Início. */
@Composable
fun CartaoUnicoDeLinhas(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(content = conteudo)
    }
}

/** Divisória entre linhas do [CartaoUnicoDeLinhas], recuada para alinhar com o texto. */
@Composable
fun DivisorDeLinha() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp, end = 14.dp),
        color = MaterialTheme.colorScheme.outline
    )
}

/**
 * Linha do [CartaoUnicoDeLinhas]: [inicio] de 44dp (anel ou ícone solto), [titulo] e [valor] na primeira
 * linha, [subtitulo] embaixo. [valorSecundario] aparece esmaecido depois do valor ("/ R$ 1.000").
 */
@Composable
fun LinhaDePagina(
    inicio: @Composable () -> Unit,
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    valorSecundario: String? = null,
    subtitulo: String? = null,
    corSubtitulo: Color = Color.Unspecified,
    corValor: Color = Color.Unspecified,
    fim: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val corNeutra = MaterialTheme.colorScheme.onSurface
    val muda = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) { inicio() }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    titulo,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (valor.isNotEmpty() || valorSecundario != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    buildAnnotatedString {
                        append(valor)
                        if (valorSecundario != null) {
                            withStyle(SpanStyle(color = muda, fontWeight = FontWeight.Medium, fontSize = 12.sp)) {
                                append(" / $valorSecundario")
                            }
                        }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (corValor != Color.Unspecified) corValor else corNeutra,
                    maxLines = 1
                )
                }
            }
            if (subtitulo != null) {
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (corSubtitulo != Color.Unspecified) corSubtitulo else muda,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (fim != null) {
            Spacer(modifier = Modifier.width(8.dp))
            fim()
        }
    }
}

/** Linha "rótulo ... valor" dos detalhes em sheet, com divisória embaixo. */
@Composable
fun LinhaDeDetalhe(rotulo: String, valor: String, modifier: Modifier = Modifier, corValor: Color = Color.Unspecified) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(rotulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                valor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (corValor != Color.Unspecified) corValor else MaterialTheme.colorScheme.onSurface
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
