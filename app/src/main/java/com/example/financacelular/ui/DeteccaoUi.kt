package com.example.financacelular.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.financacelular.R
import com.example.financacelular.deteccao.BancosMonitorados
import com.example.financacelular.ui.theme.CorConfiguracoes
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.dimens

/** O acesso às notificações é uma liberação do Android: reavaliado ao voltar da tela do sistema. */
@Composable
fun rememberAcessoNotificacoes(): Boolean {
    val context = LocalContext.current
    var liberado by remember { mutableStateOf(acessoAsNotificacoesLiberado(context)) }
    DisposableEffect(context) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) liberado = acessoAsNotificacoesLiberado(context)
        }
        val ciclo = (context.encontrarActivity() as? LifecycleOwner)?.lifecycle
        ciclo?.addObserver(observador)
        onDispose { ciclo?.removeObserver(observador) }
    }
    return liberado
}

fun abrirAcessoAsNotificacoes(context: android.content.Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

/** Sheet para escolher os bancos que a detecção acompanha (Configurações e onboarding usam o mesmo). */
@Composable
fun FolhaBancosMonitorados(viewModel: ConfiguracoesViewModel, aoFechar: () -> Unit) {
    val context = LocalContext.current
    FolhaApp(onDismiss = aoFechar) {
        CabecalhoDeFolha("Bancos monitorados", aoFechar = aoFechar)
        Spacer(modifier = Modifier.height(8.dp))
        BancosMonitorados.todos.forEach { banco ->
            val ativo = banco.pacote in viewModel.bancosAtivos
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tamanho.toque)
                    .clickable(role = Role.Switch) {
                        viewModel.alternarBanco(banco.pacote, !ativo)
                        if (!ativo) pedirExcecaoDeBateria(context)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    banco.nome,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (ativo) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Ativo",
                        tint = CorConfiguracoes,
                        modifier = Modifier.size(Tamanho.iconeGrande)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Passo depois da boas-vindas (só na primeira abertura): explica a detecção e leva à liberação do acesso.
 * "Liberar acesso" abre a tela do sistema; ao voltar com o acesso liberado o sheet de bancos abre sozinho.
 */
@Composable
fun PassoDeteccaoScreen(viewModel: ConfiguracoesViewModel) {
    val context = LocalContext.current
    val acesso = rememberAcessoNotificacoes()
    var mostrarBancos by remember { mutableStateOf(false) }
    val nomesAtivos = BancosMonitorados.todos.filter { it.pacote in viewModel.bancosAtivos }.map { it.nome }
    val pronto = acesso && nomesAtivos.isNotEmpty()

    LaunchedEffect(acesso) { if (acesso && nomesAtivos.isEmpty()) mostrarBancos = true }
    if (mostrarBancos) FolhaBancosMonitorados(viewModel) { mostrarBancos = false }

    val corBrilho = RoxoHero
    val intensidadeBrilho = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) 0.45f else 0.75f
    val formaLogo = RoundedCornerShape(21.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            corBrilho.copy(alpha = intensidadeBrilho),
                            corBrilho.copy(alpha = intensidadeBrilho / 3),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.92f, size.height * 1.05f),
                        radius = size.width * 0.95f
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = MaterialTheme.dimens.paddingScreen + Espaco.xs, vertical = Espaco.xl)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(formaLogo)
                        .border(1.dp, MaterialTheme.colorScheme.outline, formaLogo)
                ) {
                    Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize())
                    Image(
                        painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().scale(1.3f)
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    "Lance seus gastos\nsem digitar",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(Espaco.sm))
                Text(
                    "O MotApp avisa quando seu banco notificar uma compra e já preenche o lançamento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Espaco.xl))
                listOf(
                    "Lê só o valor e o nome do estabelecimento",
                    "Nada do texto é guardado ou enviado",
                    "Você escolhe quais bancos acompanhar"
                ).forEach { linha ->
                    Row(
                        modifier = Modifier.padding(bottom = Espaco.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(6.dp).background(RoxoHero, CircleShape))
                        Spacer(modifier = Modifier.width(Espaco.md))
                        Text(
                            linha,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (pronto) {
                    Spacer(modifier = Modifier.height(Espaco.sm))
                    Text(
                        "Acompanhando: " + nomesAtivos.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            BotaoPrincipal(
                texto = when {
                    pronto -> "Concluir"
                    acesso -> "Escolher bancos"
                    else -> "Liberar acesso"
                },
                onClick = {
                    when {
                        pronto -> viewModel.concluirPassoDeteccao()
                        acesso -> mostrarBancos = true
                        else -> abrirAcessoAsNotificacoes(context)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                cor = RoxoHero
            )
            if (!pronto) {
                Spacer(modifier = Modifier.height(10.dp))
                BotaoSecundario(
                    texto = "Agora não",
                    onClick = { viewModel.concluirPassoDeteccao() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Cartão do Início enquanto a detecção estiver desligada (para quem já usava o app). Some ao ligar ou no X. */
@Composable
fun CartaoDicaDeteccao(aoLigar: () -> Unit, aoDispensar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Bolt,
                contentDescription = null,
                tint = RoxoHero,
                modifier = Modifier.size(Tamanho.iconeGrande)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Detectar gastos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Lance pelas notificações do banco",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BotaoPrincipal(
                texto = "Ligar",
                onClick = aoLigar,
                modifier = Modifier.height(40.dp),
                cor = RoxoHero
            )
            Box(
                modifier = Modifier
                    .size(Tamanho.toque)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = aoDispensar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Dispensar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Tamanho.icone)
                )
            }
        }
    }
}
