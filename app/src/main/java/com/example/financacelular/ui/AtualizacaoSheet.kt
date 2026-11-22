package com.example.financacelular.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financacelular.data.ApkDownloader
import com.example.financacelular.data.AtualizacaoDisponivel
import com.example.financacelular.data.StatusInstalacao
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.cores
import kotlinx.coroutines.launch

/** Aviso de nova versão. Usa o FolhaApp; não fecha durante o download. */
@Composable
fun AtualizacaoSheet(
    atualizacao: AtualizacaoDisponivel,
    aoAbrirNavegador: () -> Unit,
    aoFechar: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var baixando by remember { mutableStateOf(false) }
    var progresso by remember { mutableFloatStateOf(0f) }
    var erroDownload by remember { mutableStateOf<String?>(null) }

    val erroInstalacao by StatusInstalacao.erro.collectAsStateWithLifecycle()
    LaunchedEffect(erroInstalacao) {
        erroInstalacao?.let {
            baixando = false
            erroDownload = it.ifBlank { null }
            StatusInstalacao.erro.value = null
        }
    }

    BackHandler(enabled = baixando) {}

    FolhaApp(
        onDismiss = { if (!baixando) aoFechar() },
        bloquearFechamento = baixando
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(RoxoHero.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = RoxoHero,
                modifier = Modifier.size(Tamanho.icone)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Atualização disponível",
            style = MaterialTheme.typography.titleLarge,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Versão ${atualizacao.versao}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            atualizacao.notas,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .heightIn(max = 140.dp)
                .verticalScroll(rememberScrollState())
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (baixando) {
            LinearProgressIndicator(
                progress = { progresso },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = RoxoHero,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (progresso >= 1f) "Instalando…" else "Baixando… ${(progresso * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            if (erroDownload != null) {
                Text(
                    erroDownload.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.cores.textoGasto
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (atualizacao.urlApk.isNotBlank()) {
                BotaoPrincipal(
                    texto = "Atualizar",
                    onClick = {
                        erroDownload = null
                        baixando = true
                        scope.launch {
                            ApkDownloader.baixarEInstalarApk(
                                context = context,
                                urlApk = atualizacao.urlApk,
                                onProgresso = { progresso = it },
                                onError = { mensagem ->
                                    baixando = false
                                    erroDownload = mensagem
                                },
                                onAguardandoUsuario = { precisaPermissao ->
                                    baixando = false
                                    progresso = 0f
                                    if (precisaPermissao) {
                                        erroDownload = "Libere a instalação e toque em Atualizar de novo"
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    cor = RoxoHero,
                    corConteudo = androidx.compose.ui.graphics.Color.White
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            BotaoSecundario(
                texto = "Baixar pelo navegador",
                onClick = aoAbrirNavegador,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = aoFechar,
                modifier = Modifier.fillMaxWidth().heightIn(min = Tamanho.controle)
            ) {
                Text(
                    "Agora não",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
