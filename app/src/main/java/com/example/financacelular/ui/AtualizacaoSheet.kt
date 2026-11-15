package com.example.financacelular.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financacelular.data.ApkDownloader
import com.example.financacelular.data.AtualizacaoDisponivel
import com.example.financacelular.ui.theme.cores
import com.example.financacelular.ui.theme.dimens
import kotlinx.coroutines.launch

/** Aviso de nova versão. Sheet de baixo, como os demais avisos do app; não fecha durante o download. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtualizacaoSheet(
    atualizacao: AtualizacaoDisponivel,
    aoAbrirNavegador: () -> Unit,
    aoFechar: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var baixando by remember { mutableStateOf(false) }
    var progresso by remember { mutableStateOf(0f) }
    var erroDownload by remember { mutableStateOf<String?>(null) }

    val baixandoAtual by rememberUpdatedState(baixando)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !baixandoAtual }
    )

    BackHandler(enabled = baixando) {}

    ModalBottomSheet(
        onDismissRequest = { if (!baixando) aoFechar() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        contentWindowInsets = { WindowInsets.statusBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MaterialTheme.dimens.paddingScreen, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Nova atualização disponível",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Versão ${atualizacao.versao}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                atualizacao.notas,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (baixando) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { progresso },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A baixar atualização... ${(progresso * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                if (erroDownload != null) {
                    Text(
                        erroDownload.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.cores.textoGasto
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                // Opção 1: Atualizar diretamente no app
                if (atualizacao.urlApk.isNotBlank()) {
                    BotaoPrincipal(
                        texto = "Atualizar no App",
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
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icone = Icons.Filled.Download
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Opção 2: Alternativa via Navegador
                BotaoSecundario(
                    texto = "Baixar pelo Navegador",
                    onClick = aoAbrirNavegador,
                    modifier = Modifier.fillMaxWidth(),
                    icone = Icons.Filled.OpenInBrowser
                )

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(onClick = aoFechar) {
                    Text("Agora não", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
