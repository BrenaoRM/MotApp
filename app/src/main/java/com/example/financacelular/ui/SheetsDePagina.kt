package com.example.financacelular.ui

import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.dimens

/**
 * Sheet padrão do app (anatomia de "Padrões de UI"). Use um por vez.
 * [bloquearFechamento] impede fechar por gesto/toque fora enquanto for true (ex.: download da atualização).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaApp(
    onDismiss: () -> Unit,
    bloquearFechamento: Boolean = false,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    // A folha é uma janela própria e ficaria por cima da tela bloqueada: some enquanto o app está bloqueado
    if (BloqueioApp.bloqueado) return
    val bloqueado by rememberUpdatedState(bloquearFechamento)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { !bloqueado }
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            // Alça compacta: a padrão do Material gasta 48dp só nela.
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outline, CircleShape)
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        contentWindowInsets = { WindowInsets.statusBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
            content = conteudo
        )
    }
}

@Composable
fun CabecalhoDeFolha(
    titulo: String,
    aoFechar: () -> Unit,
    subtitulo: String? = null,
    fecharAtivo: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitulo != null) {
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(Espaco.md))
        // X solto, sem círculo; o offset encosta o ícone na margem como a seta de voltar das telas.
        IconButton(
            onClick = aoFechar,
            enabled = fecharAtivo,
            modifier = Modifier
                .size(Tamanho.toque)
                .offset(x = 12.dp)
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Fechar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (fecharAtivo) 1f else 0.3f),
                modifier = Modifier.size(Tamanho.iconeGrande)
            )
        }
    }
}

/**
 * Corpo da confirmação (ação destrutiva ou aviso com ação), sem sheet.
 * Use direto dentro de um sheet já aberto, trocando o conteúdo em vez de abrir outro.
 */
@Composable
fun ConfirmarAcaoConteudo(
    titulo: String,
    texto: String,
    textoConfirmar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    icone: ImageVector = Icons.Outlined.DeleteOutline,
    cor: Color = Coral,
    corConteudoBotao: Color = Color.White,
    textoCancelar: String = "Cancelar",
    resumo: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(cor.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(Tamanho.icone))
        }

        Spacer(modifier = Modifier.height(Espaco.md))

        Text(titulo, style = MaterialTheme.typography.titleLarge, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (resumo != null) {
            Spacer(modifier = Modifier.height(Espaco.lg))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(horizontal = Espaco.lg, vertical = Espaco.md)) {
                    resumo()
                }
            }
        }

        Spacer(modifier = Modifier.height(Espaco.xl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Espaco.md)
        ) {
            BotaoSecundario(textoCancelar, onCancelar, Modifier.weight(1f))
            BotaoPrincipal(textoConfirmar, onConfirmar, Modifier.weight(1f), cor = cor, corConteudo = corConteudoBotao)
        }
        Spacer(modifier = Modifier.height(Espaco.sm))
    }
}

/**
 * Confirmação única para ações destrutivas (excluir, cancelar, restaurar) e avisos com ação.
 * Segue o padrão de sheet de baixo do app (ver "Excluir aporte?" em InvestimentoScreen).
 * Por padrão é destrutiva (ícone de lixeira, Coral); para avisos, passar icone/cor/textoCancelar.
 * Dentro de um sheet já aberto, use [ConfirmarAcaoConteudo].
 */
@Composable
fun ConfirmarAcaoSheet(
    titulo: String,
    texto: String,
    textoConfirmar: String,
    onConfirmar: () -> Unit,
    onDismiss: () -> Unit,
    icone: ImageVector = Icons.Outlined.DeleteOutline,
    cor: Color = Coral,
    corConteudoBotao: Color = Color.White,
    textoCancelar: String = "Cancelar",
    resumo: (@Composable () -> Unit)? = null
) {
    FolhaApp(onDismiss = onDismiss) {
        ConfirmarAcaoConteudo(
            titulo = titulo,
            texto = texto,
            textoConfirmar = textoConfirmar,
            onConfirmar = {
                onConfirmar()
                onDismiss()
            },
            onCancelar = onDismiss,
            icone = icone,
            cor = cor,
            corConteudoBotao = corConteudoBotao,
            textoCancelar = textoCancelar,
            resumo = resumo
        )
    }
}
