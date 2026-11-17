package com.example.financacelular.ui

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.AzulCartao
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.R
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.dimens
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Mostra uma mensagem curta ao usuário (Snackbar); provido pelo AppNavigation. */
val LocalMensagem = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Sheet padrão do app (anatomia de "Padrões de UI"). Use um por vez. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaApp(
    onDismiss: () -> Unit,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
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
    subtitulo: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitulo != null) {
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(Espaco.md))
        IconButton(
            onClick = aoFechar,
            modifier = Modifier
                .size(Tamanho.toque)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Fechar", modifier = Modifier.size(Tamanho.icone))
        }
    }
}

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
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = cor, contentColor = conteudo),
        elevation = null
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(Tamanho.icone))
            Spacer(modifier = Modifier.width(Espaco.sm))
        }
        Text(texto, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BotaoSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.onSurface,
    icone: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(Tamanho.controle),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = cor)
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(Tamanho.icone))
            Spacer(modifier = Modifier.width(Espaco.sm))
        }
        Text(texto, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    icone: ImageVector = Icons.Filled.DeleteOutline,
    cor: Color = Coral,
    corConteudoBotao: Color = Color.White,
    textoCancelar: String = "Cancelar",
    resumo: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(cor.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(28.dp))
        }

        Spacer(modifier = Modifier.height(Espaco.lg))

        Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
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
    icone: ImageVector = Icons.Filled.DeleteOutline,
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

/** Seletor de opções exclusivas com indicador deslizante (como Gasto/Ganho da Nova Transação). */
@Composable
fun SeletorSegmentado(
    opcoes: List<String>,
    indiceSelecionado: Int,
    aoSelecionar: (Int) -> Unit,
    cor: Color,
    modifier: Modifier = Modifier
) {
    val posicao by animateFloatAsState(
        targetValue = indiceSelecionado.toFloat(),
        animationSpec = tween(250),
        label = "posicaoSeletor"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Tamanho.toque + Espaco.xs * 2)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(Espaco.xs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(1f / opcoes.size)
                .fillMaxHeight()
                .graphicsLayer { translationX = posicao * size.width }
                .background(cor, RoundedCornerShape(50))
        )
        Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
            opcoes.forEachIndexed { indice, texto ->
                val selecionado = indice == indiceSelecionado
                val corTexto by animateColorAsState(
                    targetValue = if (selecionado) (if (cor.luminance() > 0.5f) Color.Black else Color.White) else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "corOpcaoSeletor"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .selectable(selected = selecionado, role = Role.Tab, onClick = { aoSelecionar(indice) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        texto,
                        fontWeight = FontWeight.Bold,
                        color = corTexto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Opção de [SeletorQuadradoIcones]: ícone, descrição para leitor de tela e cor quando ativa. */
data class OpcaoDeIcone(val icone: ImageVector, val descricao: String, val cor: Color)

/**
 * Versão compacta do `SeletorQuadrado` (Análise) só com ícones: mesma trilha cinza de cantos 12dp e
 * bloco ativo com borda fina. A trilha tem 40dp de altura, mas o toque ocupa os 48dp.
 */
@Composable
fun SeletorQuadradoIcones(
    opcoes: List<OpcaoDeIcone>,
    indiceSelecionado: Int,
    aoSelecionar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val formaAtiva = RoundedCornerShape(10.dp)
    Box(modifier = modifier.height(Tamanho.toque)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        )
        Row(modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp).selectableGroup()) {
            opcoes.forEachIndexed { indice, opcao ->
                val ativo = indice == indiceSelecionado
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    // O efeito de toque fica só no bloco visível (40x32dp); o toque continua com 48dp de altura.
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(formaAtiva)
                            .background(if (ativo) MaterialTheme.colorScheme.background else Color.Transparent)
                            .then(if (ativo) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, formaAtiva) else Modifier)
                            .selectable(selected = ativo, role = Role.Tab, onClick = { aoSelecionar(indice) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            opcao.icone,
                            contentDescription = opcao.descricao,
                            tint = if (ativo) opcao.cor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

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
            imageVector = Icons.Filled.CreditCard,
            contentDescription = null,
            tint = AzulCartao,
            modifier = Modifier.size(11.dp)
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

/** Segunda cor do degradê do cartão principal: a cor da página, um pouco mais escura. */
fun gradienteDaCor(cor: Color): Color = lerp(cor, Color.Black, 0.28f)

/** Cor usada no cartão principal quando algo está estourado/negativo (igual à home). */
val CorHeroAlerta = Color(0xFFA85560)

/** "Novembro": o mês por extenso, com a inicial maiúscula. */
fun YearMonth.nomeDoMes(): String =
    month.getDisplayName(TextStyle.FULL, Locale.Builder().setLanguage("pt").setRegion("BR").build())
        .replaceFirstChar { it.uppercase() }

/**
 * Cabeçalho padrão de TODAS as telas: uma linha, sem molduras, com três posições fixas.
 * Esquerda: a logo ([aoAbrirConfiguracoes], nas abas) ou a seta de voltar ([aoVoltar], nas telas internas).
 * Centro: [titulo] grande (o mês quando a tela tem mês; senão o nome da tela) e [subtitulo] pequeno em violeta
 * (o ano; nas telas internas com mês, "Nome da tela · ano"). [aoTocarTitulo] torna o título clicável e mostra um ▾.
 * Direita: as setas de mês, só quando [aoAnterior] e [aoSeguinte] existem. Ações extras vão para a ilha de baixo.
 */
@Composable
fun CabecalhoPadrao(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    aoVoltar: (() -> Unit)? = null,
    aoAbrirConfiguracoes: (() -> Unit)? = null,
    aoAnterior: (() -> Unit)? = null,
    aoSeguinte: (() -> Unit)? = null,
    aoTocarTitulo: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Tamanho.toque),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (aoAbrirConfiguracoes != null) {
            BotaoLogoConfiguracoes(onClick = aoAbrirConfiguracoes)
        } else if (aoVoltar != null) {
            IconeDeCabecalho(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", Alignment.CenterStart, MaterialTheme.colorScheme.onSurface, aoVoltar)
        }
        Box(modifier = Modifier.weight(1f)) {
            Column(
                // O recuo interno evita que o canto arredondado do toque corte a 1ª letra do ano;
                // o deslocamento negativo mantém o texto alinhado à margem da tela.
                modifier = if (aoTocarTitulo != null) {
                    Modifier
                        .offset(x = (-8).dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Escolher período", role = Role.Button, onClick = aoTocarTitulo)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else Modifier
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        titulo,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (aoTocarTitulo != null) {
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (subtitulo != null) {
                    Text(
                        subtitulo,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoxoHero,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (aoAnterior != null && aoSeguinte != null) {
            val corSeta = MaterialTheme.colorScheme.onSurfaceVariant
            IconeDeCabecalho(Icons.Filled.ChevronLeft, "Mês anterior", Alignment.CenterEnd, corSeta, aoAnterior)
            IconeDeCabecalho(Icons.Filled.ChevronRight, "Próximo mês", Alignment.CenterEnd, corSeta, aoSeguinte)
        }
    }
}

/** Ícone solto do cabeçalho (voltar, setas de mês): sem círculo nem borda, com toque de 48dp. */
@Composable
private fun IconeDeCabecalho(
    icone: ImageVector,
    descricao: String,
    alinhamento: Alignment,
    cor: Color,
    onClick: () -> Unit
) {
    // Toque de 48dp, mas o efeito de toque é um círculo de 40dp centrado no ícone; o deslocamento
    // encosta o ícone na borda (esquerda ou direita) como antes.
    val folga = (Tamanho.toque - Tamanho.iconeGrande) / 2
    Box(
        modifier = Modifier
            .offset(x = if (alinhamento == Alignment.CenterStart) -folga else folga)
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icone, contentDescription = descricao, tint = cor, modifier = Modifier.size(Tamanho.iconeGrande))
    }
}

/** Logo do app sem moldura, com uma engrenagem pequena no canto; abre as Configurações. Toque de 48dp. */
@Composable
private fun BotaoLogoConfiguracoes(onClick: () -> Unit) {
    // O efeito de toque cobre só a logo (36x34dp); o toque continua com 48dp.
    Box(
        modifier = Modifier
            .offset(x = -(Tamanho.toque - 36.dp) / 2)
            .minimumInteractiveComponentSize()
            .size(width = 36.dp, height = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Ícone adaptativo: o desenho ocupa só a zona central, por isso o primeiro plano é ampliado
            Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))) {
                Image(
                    painterResource(R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Configurações",
                    modifier = Modifier.fillMaxSize().scale(1.3f)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Cartão principal das páginas, no mesmo estilo do cartão do saldo da home: fundo e borda do tema,
 * com um brilho suave na [cor] da página vindo do canto inferior direito.
 */
@Composable
fun CartaoHeroDePagina(
    cor: Color,
    modifier: Modifier = Modifier,
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            content = conteudo
        )
    }
}

/** Selo translúcido (ex.: "3 ativos", "No limite") para usar dentro do cartão principal. */
@Composable
fun PilulaGlass(texto: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** Mini indicador translúcido para o cartão principal (mesmo visual da home). */
@Composable
fun IndicadorGlassDePagina(
    titulo: String,
    valor: String,
    icone: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                titulo,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

/** Indicador do cartão principal das páginas: rótulo pequeno e valor, num bloco discreto. */
@Composable
fun IndicadorDePagina(
    titulo: String,
    valor: String,
    icone: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                titulo,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
