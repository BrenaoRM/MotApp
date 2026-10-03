package com.example.financacelular.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.TemaPreferencia
import com.example.financacelular.ui.theme.dimens

private val CorConfiguracoes = Color(0xFF64748B)

private data class OpcaoDeTema(
    val tema: TemaPreferencia,
    val rotulo: String,
    val icone: ImageVector
)

private val OpcoesDeTema = listOf(
    OpcaoDeTema(TemaPreferencia.SISTEMA, "Sistema", Icons.Filled.SettingsBrightness),
    OpcaoDeTema(TemaPreferencia.CLARO, "Claro", Icons.Filled.LightMode),
    OpcaoDeTema(TemaPreferencia.ESCURO, "Escuro", Icons.Filled.DarkMode)
)

private tailrec fun Context.encontrarActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.encontrarActivity()
    else -> throw IllegalStateException("Nenhuma Activity encontrada no contexto")
}

@Composable
fun ConfiguracoesScreen(
    viewModel: ConfiguracoesViewModel = viewModel(),
    aoVoltar: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context.encontrarActivity() }

    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { resultado ->
        viewModel.aoReceberResultadoAutorizacaoDrive(activity, resultado.data)
    }

    LaunchedEffect(viewModel.pedidoAutorizacaoDrive) {
        viewModel.pedidoAutorizacaoDrive?.let {
            authorizationLauncher.launch(it)
            viewModel.limparPedidoAutorizacao()
        }
    }

    val logado = viewModel.estaLogadoGoogle
    val nomeDoTemaAtual = OpcoesDeTema.firstOrNull { it.tema == viewModel.temaAtual }?.rotulo ?: "Sistema"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen),
        contentPadding = PaddingValues(top = 16.dp, bottom = espacoParaBarraFlutuante())
    ) {
        item {
            CabecalhoDePagina("Configurações", CorConfiguracoes, aoVoltar)
            Spacer(modifier = Modifier.height(16.dp))

            CartaoHeroDePagina(CorConfiguracoes, gradienteDaCor(CorConfiguracoes)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "BACKUP NA NUVEM",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                    PilulaGlass(if (logado) "CONECTADO" else "DESCONECTADO")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Google Drive",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IndicadorGlassDePagina("ÚLTIMO BACKUP", viewModel.ultimaDataBackup, Icons.Filled.History, Modifier.weight(1.5f))
                    IndicadorGlassDePagina("TEMA", nomeDoTemaAtual, Icons.Filled.Palette, Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Seção: Aparência ---
        item {
            Text(
                text = "Aparência",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconeDeConfiguracao(Icons.Filled.Palette)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Tema do Aplicativo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Escolha como o app deve aparecer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OpcoesDeTema.forEach { opcao ->
                            OpcaoDeTemaItem(
                                opcao = opcao,
                                selecionada = viewModel.temaAtual == opcao.tema,
                                aoSelecionar = { viewModel.definirTema(opcao.tema) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Seção: Google Drive / Backup ---
        item {
            Text(
                text = "Dados e Nuvem",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconeDeConfiguracao(Icons.Filled.CloudSync)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Google Drive",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (logado) "Conta: ${viewModel.emailUtilizador}" else "Proteja os seus dados na nuvem",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!logado) {
                        Button(
                            onClick = { viewModel.sincronizarOuEntrarGoogle(activity) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CorConfiguracoes, contentColor = Color.White),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Entrar com a Conta Google", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.realizarBackupNaNuvem(activity) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CorConfiguracoes, contentColor = Color.White),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fazer Backup Agora", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.restaurarBackupDaNuvem(activity) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CorConfiguracoes),
                            border = BorderStroke(1.dp, CorConfiguracoes.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restaurar Backup do Google Drive", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (viewModel.statusBackupMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CorConfiguracoes.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = CorConfiguracoes,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = viewModel.statusBackupMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Caixinha de ícone no mesmo estilo dos itens das outras páginas. */
@Composable
private fun IconeDeConfiguracao(icone: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(CorConfiguracoes.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icone, contentDescription = null, tint = CorConfiguracoes, modifier = Modifier.size(22.dp))
    }
}

/** Opção de tema (Sistema / Claro / Escuro) no estilo dos chips de seleção do app. */
@Composable
private fun OpcaoDeTemaItem(
    opcao: OpcaoDeTema,
    selecionada: Boolean,
    aoSelecionar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(forma)
            .background(
                if (selecionada) CorConfiguracoes.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                width = if (selecionada) 1.5.dp else 0.dp,
                color = if (selecionada) CorConfiguracoes else Color.Transparent,
                shape = forma
            )
            .selectable(selected = selecionada, onClick = aoSelecionar, role = Role.RadioButton)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = opcao.icone,
            contentDescription = null,
            tint = if (selecionada) CorConfiguracoes else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            opcao.rotulo,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}