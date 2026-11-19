package com.example.financacelular.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.runtime.DisposableEffect
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.financacelular.deteccao.BancosMonitorados
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Check
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financacelular.data.TemaPreferencia
import com.example.financacelular.ui.theme.CorConfiguracoes
import com.example.financacelular.ui.theme.Tamanho
import com.example.financacelular.ui.theme.dimens

private data class OpcaoDeTema(
    val tema: TemaPreferencia,
    val rotulo: String
)

private val OpcoesDeTema = listOf(
    OpcaoDeTema(TemaPreferencia.SISTEMA, "Sistema"),
    OpcaoDeTema(TemaPreferencia.CLARO, "Claro"),
    OpcaoDeTema(TemaPreferencia.ESCURO, "Escuro"),
    OpcaoDeTema(TemaPreferencia.AMOLED, "AMOLED")
)

private fun acessoAsNotificacoesLiberado(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private tailrec fun Context.encontrarActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.encontrarActivity()
    else -> throw IllegalStateException("Nenhuma Activity encontrada no contexto")
}

@Composable
fun ConfiguracoesScreen(
    viewModel: ConfiguracoesViewModel = viewModel(),
    aoAbrirCategorias: () -> Unit = {},
    aoVoltar: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context.encontrarActivity() }
    val mensagem = LocalMensagem.current

    // O acesso às notificações é uma liberação do Android: reavaliada ao voltar da tela do sistema
    var acessoLiberado by remember { mutableStateOf(acessoAsNotificacoesLiberado(context)) }
    DisposableEffect(activity) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) acessoLiberado = acessoAsNotificacoesLiberado(context)
        }
        val ciclo = (activity as? LifecycleOwner)?.lifecycle
        ciclo?.addObserver(observador)
        onDispose { ciclo?.removeObserver(observador) }
    }

    // Resultado do backup também vira Snackbar (mensagens de progresso terminam em "...")
    LaunchedEffect(viewModel.statusBackupMessage) {
        val texto = viewModel.statusBackupMessage
        if (texto.isNotEmpty() && !texto.endsWith("...")) mensagem(texto)
    }
    var confirmarRestauracao by remember { mutableStateOf(false) }
    var mostrarBancos by remember { mutableStateOf(false) }
    if (confirmarRestauracao) {
        ConfirmarAcaoSheet(
            titulo = "Restaurar backup?",
            texto = "Os dados do backup do Google Drive substituem os dados atuais deste aparelho. Essa ação não pode ser desfeita.",
            textoConfirmar = "Restaurar",
            onConfirmar = { viewModel.restaurarBackupDaNuvem(activity) },
            onDismiss = { confirmarRestauracao = false }
        )
    }

    if (mostrarBancos) {
        FolhaApp(onDismiss = { mostrarBancos = false }) {
            CabecalhoDeFolha("Bancos monitorados", aoFechar = { mostrarBancos = false })
            Spacer(modifier = Modifier.height(8.dp))
            BancosMonitorados.todos.forEach { banco ->
                val ativo = banco.pacote in viewModel.bancosAtivos
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Tamanho.toque)
                        .clickable(role = Role.Switch) { viewModel.alternarBanco(banco.pacote, !ativo) },
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen),
        contentPadding = PaddingValues(top = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
    ) {
        item {
            CabecalhoPadrao("Configurações", aoVoltar = aoVoltar)
            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Seção: Geral ---
        item {
            Text("Geral", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            CartaoUnicoDeLinhas {
                LinhaDePagina(
                    inicio = { IconeDeLinha(Icons.Outlined.Category) },
                    titulo = "Categorias",
                    valor = "",
                    subtitulo = "Nome e ícone de cada categoria",
                    fim = {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Tamanho.iconeGrande)
                        )
                    },
                    onClick = aoAbrirCategorias
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Seção: Detectar transações ---
        item {
            Text("Detectar transações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            val nomesAtivos = BancosMonitorados.todos.filter { it.pacote in viewModel.bancosAtivos }.map { it.nome }
            CartaoUnicoDeLinhas {
                LinhaDePagina(
                    inicio = { IconeDeLinha(Icons.Outlined.NotificationsActive) },
                    titulo = "Bancos monitorados",
                    valor = "",
                    subtitulo = when {
                        !acessoLiberado -> "Toque para liberar o acesso às notificações"
                        nomesAtivos.isEmpty() -> "Nenhum banco"
                        nomesAtivos.size <= 2 -> nomesAtivos.joinToString(", ")
                        else -> nomesAtivos.take(2).joinToString(", ") + " +${nomesAtivos.size - 2}"
                    },
                    fim = {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Tamanho.iconeGrande)
                        )
                    },
                    onClick = {
                        if (acessoLiberado) mostrarBancos = true
                        else context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Seção: Aparência ---
        item {
            Text("Aparência", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            SeletorQuadrado(
                opcoes = OpcoesDeTema.map { it.rotulo },
                indiceSelecionado = OpcoesDeTema.indexOfFirst { it.tema == viewModel.temaAtual }.coerceAtLeast(0),
                aoSelecionar = { viewModel.definirTema(OpcoesDeTema[it].tema) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Seção: Dados e nuvem ---
        item {
            Text("Dados e nuvem", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconeDeLinha(Icons.Outlined.CloudSync)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Google Drive",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (logado) "Último backup: ${viewModel.ultimaDataBackup}" else "Desconectado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!logado) {
                        BotaoPrincipal(
                            texto = "Entrar com a Conta Google",
                            onClick = { viewModel.sincronizarOuEntrarGoogle(activity) },
                            modifier = Modifier.fillMaxWidth(),
                            cor = CorConfiguracoes,
                            corConteudo = Color.White,
                        )
                    } else {
                        BotaoPrincipal(
                            texto = "Fazer Backup Agora",
                            onClick = { viewModel.realizarBackupNaNuvem(activity) },
                            modifier = Modifier.fillMaxWidth(),
                            cor = CorConfiguracoes,
                            corConteudo = Color.White,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        BotaoSecundario(
                            texto = "Restaurar Backup do Google Drive",
                            onClick = { confirmarRestauracao = true },
                            modifier = Modifier.fillMaxWidth(),
                            cor = CorConfiguracoes,
                        )
                    }

                    if (viewModel.statusBackupMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = viewModel.statusBackupMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Ícone neutro de 44dp no início de uma linha (o mesmo de Categorias), sem cor de destaque. */
@Composable
private fun IconeDeLinha(icone: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icone, contentDescription = null, modifier = Modifier.size(Tamanho.icone))
    }
}
