package com.example.financacelular.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import com.example.financacelular.R
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.dimens

@Composable
fun WelcomeScreen(
    viewModel: ConfiguracoesViewModel,
    aoConcluir: () -> Unit
) {
    val activity = LocalContext.current as? Activity
    var nomeInput by remember { mutableStateOf(viewModel.nomeUtilizador) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { resultado ->
        activity?.let { viewModel.aoReceberResultadoAutorizacaoDrive(it, resultado.data) }
    }

    LaunchedEffect(viewModel.pedidoAutorizacaoDrive) {
        viewModel.pedidoAutorizacaoDrive?.let {
            launcher.launch(it)
            viewModel.limparPedidoAutorizacao()
        }
    }

    EscolhaBackupSheet(viewModel)

    LaunchedEffect(viewModel.completouBoasVindas) {
        if (viewModel.completouBoasVindas) {
            aoConcluir()
        }
    }

    val corBrilho = RoxoHero
    val intensidadeBrilho = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) 0.45f else 0.75f
    val formaLogo = RoundedCornerShape(21.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                // Mesmo brilho violeta do canto inferior direito dos cartões do Início
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
                // Logo do app (a mesma do ícone)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(formaLogo)
                        .border(1.dp, MaterialTheme.colorScheme.outline, formaLogo)
                ) {
                    Image(
                        painterResource(R.drawable.ic_launcher_background),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    Image(
                        painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().scale(1.3f)
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = "Bem-vindo\nao MotApp",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(Espaco.sm))
                Text(
                    text = "Entre com o Google para guardar e recuperar seus dados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Espaco.xl + Espaco.xs))
                CampoApp(
                    valor = nomeInput,
                    aoMudar = { nomeInput = it },
                    rotulo = "Seu nome",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (viewModel.statusBackupMessage.isNotBlank()) {
                Text(
                    text = viewModel.statusBackupMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Espaco.md)
                )
            }
            Spacer(modifier = Modifier.height(Espaco.md))
            BotaoPrincipal(
                texto = "Entrar com Google",
                onClick = {
                    if (nomeInput.isNotBlank()) {
                        viewModel.definirNomeUtilizador(nomeInput)
                    }
                    activity?.let { viewModel.sincronizarOuEntrarGoogle(it) }
                },
                modifier = Modifier.fillMaxWidth(),
                cor = RoxoHero
            )
            Spacer(modifier = Modifier.height(10.dp))
            BotaoSecundario(
                texto = "Usar só neste aparelho",
                onClick = {
                    if (nomeInput.isNotBlank()) {
                        viewModel.definirNomeUtilizador(nomeInput)
                    }
                    viewModel.marcarBoasVindasComoConcluida()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
