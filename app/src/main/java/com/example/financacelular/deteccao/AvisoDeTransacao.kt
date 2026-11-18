package com.example.financacelular.deteccao

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.financacelular.MainActivity
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao

private const val EXTRA_VALOR = "det_valor"
private const val EXTRA_TIPO = "det_tipo"
private const val EXTRA_DESCRICAO = "det_descricao"
private const val EXTRA_FORMA = "det_forma"
private const val CANAL = "transacao_detectada"

fun TransacaoDetectada.paraExtras(): Map<String, Any?> = mapOf(
    EXTRA_VALOR to valor,
    EXTRA_TIPO to tipo.name,
    EXTRA_DESCRICAO to descricao,
    EXTRA_FORMA to formaPagamento.name
)

fun extrasParaDetectada(extras: Map<String, Any?>): TransacaoDetectada? {
    val valor = extras[EXTRA_VALOR] as? Double ?: return null
    val tipo = (extras[EXTRA_TIPO] as? String)?.let { runCatching { TipoTransacao.valueOf(it) }.getOrNull() }
        ?: return null
    val forma = (extras[EXTRA_FORMA] as? String)?.let { runCatching { FormaPagamento.valueOf(it) }.getOrNull() }
        ?: return null
    return TransacaoDetectada(valor, tipo, extras[EXTRA_DESCRICAO] as? String, forma)
}

@Suppress("DEPRECATION")
fun Intent.paraDetectada(): TransacaoDetectada? {
    val pacote = extras ?: return null
    return extrasParaDetectada(pacote.keySet().associateWith { pacote.get(it) })
}

/** Avisa que uma transação foi detectada. Tocar na notificação abre Nova Transação já preenchida. */
@SuppressLint("MissingPermission") // a permissão é verificada logo abaixo; o lint não enxerga
fun avisarTransacao(context: Context, detectada: TransacaoDetectada, nomeBanco: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) return
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

    val gerenciador = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    gerenciador.createNotificationChannel(
        NotificationChannel(CANAL, "Transações detectadas", NotificationManager.IMPORTANCE_HIGH)
    )

    // Um id por aviso: o requestCode único impede que um aviso novo troque os dados de um antigo
    val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    val abrir = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        detectada.paraExtras().forEach { (chave, valor) ->
            when (valor) {
                is Double -> putExtra(chave, valor)
                is String -> putExtra(chave, valor)
            }
        }
    }
    val pendingIntent = PendingIntent.getActivity(
        context, id, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val rotulo = if (detectada.tipo == TipoTransacao.DESPESA) "Gasto" else "Ganho"
    val notificacao = NotificationCompat.Builder(context, CANAL)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("$rotulo de R$ ${detectada.valorParaCampo()}")
        .setContentText("${detectada.descricao ?: nomeBanco} · Toque para registrar")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    gerenciador.notify(id, notificacao)
}
