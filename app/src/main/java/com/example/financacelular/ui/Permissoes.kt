package com.example.financacelular.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

enum class GrupoPermissao(val titulo: String) {
    AVISOS("Avisos"),
    DETECCAO("Detectar transações"),
    AGENDA("Agenda"),
    ATUALIZACOES("Atualizações")
}

enum class EstadoPermissao { LIBERADA, PENDENTE, BLOQUEADA }

/**
 * Tudo que o usuário pode liberar para o app. Só entra o que o app usa de verdade.
 * [permissoesRuntime] vazio = liberação feita numa tela do sistema (sem pedido em diálogo).
 */
enum class Permissao(
    val titulo: String,
    val paraQue: String,
    val icone: ImageVector,
    val grupo: GrupoPermissao,
    val permissoesRuntime: List<String> = emptyList()
) {
    NOTIFICACOES(
        "Notificações", "Lembretes de fatura e transações",
        Icons.Outlined.Notifications, GrupoPermissao.AVISOS,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    ),
    LEITOR_NOTIFICACOES(
        "Ler avisos do banco", "Detectar compras",
        Icons.Outlined.NotificationsActive, GrupoPermissao.DETECCAO
    ),
    SOBRE_OUTROS_APPS(
        "Exibir sobre outros apps", "Popup da transação",
        Icons.Outlined.Layers, GrupoPermissao.DETECCAO
    ),
    BATERIA(
        "Bateria sem restrição", "Detecção não para",
        Icons.Outlined.BatteryChargingFull, GrupoPermissao.DETECCAO
    ),
    CALENDARIO(
        "Calendário", "Salvar no Google Agenda",
        Icons.Outlined.CalendarMonth, GrupoPermissao.AGENDA,
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    ),
    INSTALAR_ATUALIZACOES(
        "Instalar atualizações", "Atualizar pelo app",
        Icons.Outlined.SystemUpdate, GrupoPermissao.ATUALIZACOES
    )
}

private const val PREFS = "financacelular_prefs"

/** Regra do estado de um pedido do sistema. "Bloqueada" = o Android não mostra mais o diálogo. */
internal fun estadoDePedido(liberada: Boolean, jaPedida: Boolean, podePedirDeNovo: Boolean): EstadoPermissao = when {
    liberada -> EstadoPermissao.LIBERADA
    jaPedida && !podePedirDeNovo -> EstadoPermissao.BLOQUEADA
    else -> EstadoPermissao.PENDENTE
}

/** O Android só devolve "não mostrar de novo" depois do primeiro pedido, então guardamos que já pedimos. */
fun Permissao.marcarPedida(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("permissao_pedida_$name", true).apply()
}

private fun Permissao.jaPedida(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("permissao_pedida_$name", false)

private fun Permissao.runtimeLiberado(context: Context): Boolean = permissoesRuntime.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun Permissao.podePedirDeNovo(context: Context): Boolean = permissoesRuntime.any {
    ActivityCompat.shouldShowRequestPermissionRationale(context.encontrarActivity(), it)
}

fun Permissao.estado(context: Context): EstadoPermissao = when (this) {
    Permissao.NOTIFICACOES -> {
        val avisosLigados = NotificationManagerCompat.from(context).areNotificationsEnabled()
        when {
            avisosLigados -> EstadoPermissao.LIBERADA
            permissoesRuntime.isEmpty() -> EstadoPermissao.PENDENTE
            // Permissão dada mas avisos desligados nas configurações do app: só lá se resolve
            runtimeLiberado(context) -> EstadoPermissao.BLOQUEADA
            else -> estadoDePedido(false, jaPedida(context), podePedirDeNovo(context))
        }
    }
    Permissao.CALENDARIO ->
        estadoDePedido(runtimeLiberado(context), jaPedida(context), podePedirDeNovo(context))
    Permissao.LEITOR_NOTIFICACOES ->
        if (acessoAsNotificacoesLiberado(context)) EstadoPermissao.LIBERADA else EstadoPermissao.PENDENTE
    Permissao.SOBRE_OUTROS_APPS ->
        if (popupSobreOutrosAppsLiberado(context)) EstadoPermissao.LIBERADA else EstadoPermissao.PENDENTE
    Permissao.BATERIA ->
        if ((context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName))
            EstadoPermissao.LIBERADA else EstadoPermissao.PENDENTE
    Permissao.INSTALAR_ATUALIZACOES ->
        if (context.packageManager.canRequestPackageInstalls()) EstadoPermissao.LIBERADA else EstadoPermissao.PENDENTE
}

private fun Context.abrir(vararg intents: Intent) {
    for (intent in intents) if (runCatching { startActivity(intent) }.isSuccess) return
}

private fun Context.detalhesDoApp() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

/** Abre a tela do sistema em que a pessoa liga ou desliga esta permissão. */
fun Permissao.abrirConfiguracoes(context: Context) {
    val pacote = Uri.parse("package:${context.packageName}")
    when (this) {
        Permissao.NOTIFICACOES -> context.abrir(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            context.detalhesDoApp()
        )
        Permissao.LEITOR_NOTIFICACOES -> context.abrir(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                android.content.ComponentName(context, com.example.financacelular.deteccao.NotificacaoListener::class.java).flattenToString()
            ),
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            context.detalhesDoApp()
        )
        Permissao.SOBRE_OUTROS_APPS -> context.abrir(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pacote),
            context.detalhesDoApp()
        )
        Permissao.BATERIA -> context.abrir(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            context.detalhesDoApp()
        )
        Permissao.CALENDARIO -> context.abrir(context.detalhesDoApp())
        Permissao.INSTALAR_ATUALIZACOES -> context.abrir(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pacote),
            context.detalhesDoApp()
        )
    }
}

/** Pedido que não é diálogo de permissão: o do sistema para bateria, ou a tela especial das demais. */
fun Permissao.pedirNaTelaDoSistema(context: Context) = when (this) {
    Permissao.BATERIA -> pedirExcecaoDeBateria(context)
    else -> abrirConfiguracoes(context)
}

/** Estados atuais, recalculados ao voltar das configurações do sistema. [recalcular] força a releitura. */
class EstadosPermissoes internal constructor(
    private val context: Context,
    val versao: Int,
    val recalcular: () -> Unit
) {
    operator fun get(permissao: Permissao): EstadoPermissao = permissao.estado(context)
    val pendentes: Int get() = Permissao.entries.count { get(it) != EstadoPermissao.LIBERADA }
}

@Composable
fun rememberEstadosPermissoes(): EstadosPermissoes {
    val context = LocalContext.current
    var versao by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) versao++
        }
        val ciclo = (context.encontrarActivity() as? LifecycleOwner)?.lifecycle
        ciclo?.addObserver(observador)
        onDispose { ciclo?.removeObserver(observador) }
    }
    return EstadosPermissoes(context, versao) { versao++ }
}

/**
 * Pede o diálogo do sistema de [permissao] e chama [aoConcluir] ao terminar (ou na hora, se não há o que pedir).
 * Use em contexto, quando a pessoa liga uma função que depende da permissão.
 */
@Composable
fun rememberPedirPermissao(permissao: Permissao, aoConcluir: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val concluir by rememberUpdatedState(aoConcluir)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { concluir() }
    return {
        if (permissao.permissoesRuntime.isNotEmpty() && permissao.estado(context) == EstadoPermissao.PENDENTE) {
            permissao.marcarPedida(context)
            launcher.launch(permissao.permissoesRuntime.toTypedArray())
        } else concluir()
    }
}
