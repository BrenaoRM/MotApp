package com.example.financacelular.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CloudOff
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
import android.annotation.SuppressLint
import android.net.Uri
import android.os.PowerManager
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
    OpcaoDeTema(TemaPreferencia.ESCURO, "Escuro")
)

internal fun acessoAsNotificacoesLiberado(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** "Exibir sobre outros apps": liberação do Android que deixa o popup de transação aparecer por cima de outros apps. */
internal fun popupSobreOutrosAppsLiberado(context: Context): Boolean = Settings.canDrawOverlays(context)

/** Sem a economia de bateria o sistema não derruba o leitor de notificações. Um toque num diálogo do sistema. */
@SuppressLint("BatteryLife")
internal fun pedirExcecaoDeBateria(context: Context) {
    val energia = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (energia.isIgnoringBatteryOptimizations(context.packageName)) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        )
    }
}

internal tailrec fun Context.encontrarActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.encontrarActivity()
    else -> throw IllegalStateException("Nenhuma Activity encontrada no contexto")
}

@Composable
fun ConfiguracoesScreen(
    viewModel: ConfiguracoesViewModel = viewModel(),
    aoAbrirCategorias: () -> Unit = {},
    aoAbrirPermissoes: () -> Unit = {},
    aoVoltar: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context.encontrarActivity() }
    val mensagem = LocalMensagem.current

    // O acesso às notificações é uma liberação do Android: reavaliada ao voltar da tela do sistema
    var acessoLiberado by remember { mutableStateOf(acessoAsNotificacoesLiberado(context)) }
    DisposableEffect(activity) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) {
                acessoLiberado = acessoAsNotificacoesLiberado(context)
            }
        }
        val ciclo = (activity as? LifecycleOwner)?.lifecycle
        ciclo?.addObserver(observador)
        onDispose { ciclo?.removeObserver(observador) }
    }

    // Resultado do backup também vira aviso no topo (mensagens de progresso terminam em "...")
    LaunchedEffect(viewModel.statusBackupMessage) {
        val texto = viewModel.statusBackupMessage
        if (texto.isNotEmpty() && !texto.endsWith("...")) mensagem(texto, erro = viewModel.statusBackupErro)
    }
    var confirmarRestauracao by remember { mutableStateOf(false) }
    var confirmarDesconexao by remember { mutableStateOf(false) }
    var mostrarBancos by remember { mutableStateOf(false) }
    if (confirmarDesconexao) {
        ConfirmarAcaoSheet(
            titulo = "Desconectar o Google Drive?",
            texto = "Seus dados continuam neste aparelho e o backup fica no seu Drive. O app para de enviar backups e perde a permissão dessa conta.",
            textoConfirmar = "Desconectar",
            onConfirmar = { viewModel.desconectarGoogle() },
            onDismiss = { confirmarDesconexao = false },
            icone = Icons.Outlined.CloudOff
        )
    }
    EscolhaBackupSheet(viewModel)
    if (confirmarRestauracao) {
        ConfirmarAcaoSheet(
            titulo = "Restaurar backup?",
            texto = "Os dados do backup do Google Drive substituem os dados atuais deste aparelho. Essa ação não pode ser desfeita.",
            textoConfirmar = "Restaurar",
            onConfirmar = { viewModel.restaurarBackupDaNuvem(activity) },
            onDismiss = { confirmarRestauracao = false }
        )
    }

    if (mostrarBancos) FolhaBancosMonitorados(viewModel) { mostrarBancos = false }

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
                DivisorDeLinha()
                LinhaDePagina(
                    inicio = { IconeDeLinha(Icons.Outlined.Shield) },
                    titulo = "Permissões",
                    valor = "",
                    fim = {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Tamanho.iconeGrande)
                        )
                    },
                    onClick = aoAbrirPermissoes
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
                        else abrirAcessoAsNotificacoes(context)
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
                                if (logado) viewModel.emailUtilizador else "Desconectado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (logado) {
                                Text(
                                    "Último backup: ${viewModel.ultimaDataBackup}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BotaoSecundario(
                                texto = "Trocar de conta",
                                onClick = { viewModel.sincronizarOuEntrarGoogle(activity) },
                                modifier = Modifier.weight(1f)
                            )
                            BotaoSecundario(
                                texto = "Desconectar",
                                onClick = { confirmarDesconexao = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
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

/**
 * Conta nova que já tem backup enquanto este aparelho tem dados: o usuário escolhe qual vale.
 * Fechar o sheet cancela e nada muda. Usado nas Configurações e na tela de boas-vindas.
 */
@Composable
fun EscolhaBackupSheet(viewModel: ConfiguracoesViewModel) {
    val escolha = viewModel.escolhaBackup ?: return
    FolhaApp(onDismiss = viewModel::cancelarEscolhaBackup) {
        CabecalhoDeFolha(
            "Qual backup manter?",
            aoFechar = viewModel::cancelarEscolhaBackup,
            subtitulo = escolha.conta
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Essa conta já tem um backup e este aparelho já tem dados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        BotaoPrincipal(
            texto = "Manter os dados do aparelho",
            onClick = viewModel::manterDadosDoAparelho,
            modifier = Modifier.fillMaxWidth(),
            cor = CorConfiguracoes,
            corConteudo = Color.White
        )
        Text(
            "O backup da conta é substituído.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )
        BotaoSecundario(
            texto = "Restaurar o backup da conta",
            onClick = viewModel::restaurarBackupDaContaNova,
            modifier = Modifier.fillMaxWidth(),
            cor = CorConfiguracoes
        )
        Text(
            "Os dados deste aparelho são substituídos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
    }
}

/** Ícone neutro de 44dp no início de uma linha (o mesmo de Categorias), sem cor de destaque. */
@Composable
internal fun IconeDeLinha(icone: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icone, contentDescription = null, modifier = Modifier.size(Tamanho.icone))
    }
}
