package com.example.financacelular.deteccao

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Lê as notificações dos apps de banco que a pessoa ligou em Configurações e avisa quando
 * parecem uma transação. Só o corpo do texto é usado e nada dele é gravado ou registrado em log.
 */
class NotificacaoListener : NotificationListenerService() {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val filtro = FiltroDeRepeticao()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pacote = sbn.packageName
        if (pacote == packageName) return

        val prefs = getSharedPreferences("financacelular_prefs", Context.MODE_PRIVATE)
        if (pacote !in BancosMonitorados.ativados(prefs)) return
        val banco = BancosMonitorados.porPacote(pacote) ?: return

        // O título do banco costuma ser genérico (e muda com o idioma): só o corpo vale
        val extras = sbn.notification.extras
        val texto = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (texto.isBlank()) return

        if (filtro.jaVista(chaveDeRepeticao(pacote, texto))) return
        val detectada = lerNotificacao(texto) ?: return

        escopo.launch {
            runCatching {
                val repositorio = FinancaRepository.getInstance(AppDatabase.getInstance(applicationContext))
                val lancamentos = repositorio.listarTransacoes().first()
                if (!jaLancada(detectada, LocalDate.now(), lancamentos)) {
                    avisarTransacao(applicationContext, detectada, banco.nome)
                }
            }
        }
    }

    // O sistema pode desconectar o serviço (economia de bateria): pede para ser ligado de novo
    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, NotificacaoListener::class.java))
    }

    override fun onDestroy() {
        escopo.cancel()
        super.onDestroy()
    }
}
