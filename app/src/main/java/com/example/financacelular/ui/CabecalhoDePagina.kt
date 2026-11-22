package com.example.financacelular.ui

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financacelular.R
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.dimens

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
            BotaoVoltarCabecalho(onClick = aoVoltar)
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
                            Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Tamanho.icone)
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
            IconeDeCabecalho(Icons.Outlined.ChevronLeft, "Mês anterior", Alignment.CenterEnd, corSeta, aoAnterior)
            IconeDeCabecalho(Icons.Outlined.ChevronRight, "Próximo mês", Alignment.CenterEnd, corSeta, aoSeguinte)
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

/** Caixa de 42x40dp (toque de 48dp) compartilhada pela logo das abas e pela seta de voltar, para ficarem no mesmo lugar. */
private fun Modifier.caixaDoBotaoDeCabecalho(onClick: () -> Unit): Modifier = this
    .offset(x = -(Tamanho.toque - 42.dp) / 2)
    .minimumInteractiveComponentSize()
    .size(width = 42.dp, height = 40.dp)
    .clip(RoundedCornerShape(10.dp))
    .clickable(role = Role.Button, onClick = onClick)

/** Seta de voltar no mesmo lugar, tamanho e alinhamento da logo das abas: mesma caixa de 42x40dp e mesmo quadrado de 36dp. */
@Composable
private fun BotaoVoltarCabecalho(onClick: () -> Unit) {
    Box(modifier = Modifier.caixaDoBotaoDeCabecalho(onClick)) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Voltar",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(Tamanho.iconeGrande)
            )
        }
    }
}

/**
 * Lista de uma tela interna. O cabeçalho é desenhado por `AppNavigation`, fora do NavHost (igual ao das abas),
 * e acompanha a rolagem deste [estado]; a lista só reserva o espaço dele com `espacoDoCabecalho()`.
 */
@Composable
fun ListaDePagina(
    estado: LazyListState,
    conteudo: LazyListScope.() -> Unit
) {
    LazyColumn(
        state = estado,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen),
        contentPadding = PaddingValues(bottom = espacoParaBarraFlutuante())
    ) {
        espacoDoCabecalho()
        conteudo()
    }
}

/** Logo do app sem moldura, com uma engrenagem pequena no canto; abre as Configurações. Toque de 48dp. */
@Composable
private fun BotaoLogoConfiguracoes(onClick: () -> Unit) {
    // O efeito de toque cobre só a logo (42x40dp); o toque continua com 48dp.
    Box(modifier = Modifier.caixaDoBotaoDeCabecalho(onClick)) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Ícone adaptativo: o desenho ocupa só a zona central, por isso o primeiro plano é ampliado
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))) {
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
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(Tamanho.iconePequeno)
                )
            }
        }
    }
}
