package com.example.financacelular.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Savings
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
import com.example.financacelular.ui.theme.Verde

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
        viewModel.pedidoAutorizacaoDrive?.let { launcher.launch(it) }
    }

    LaunchedEffect(viewModel.completouBoasVindas) {
        if (viewModel.completouBoasVindas) {
            aoConcluir()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Savings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(50.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Bem-vindo ao MotApp",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Proteja as suas finanças. O aplicativo sincroniza automaticamente com o seu Google Drive e recupera dados anteriores se já os tiver.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = nomeInput,
            onValueChange = { nomeInput = it },
            label = { Text("Seu Nome") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (viewModel.statusBackupMessage.isNotBlank()) {
            Text(
                text = viewModel.statusBackupMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(
            onClick = {
                if (nomeInput.isNotBlank()) {
                    viewModel.definirNomeUtilizador(nomeInput)
                }
                activity?.let { viewModel.sincronizarOuEntrarGoogle(it) }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Verde)
        ) {
            Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Entrar com Google e Sincronizar", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = {
                if (nomeInput.isNotBlank()) {
                    viewModel.definirNomeUtilizador(nomeInput)
                }
                viewModel.marcarBoasVindasComoConcluida()
            }
        ) {
            Text("Entrar sem fazer login (Apenas Local)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}