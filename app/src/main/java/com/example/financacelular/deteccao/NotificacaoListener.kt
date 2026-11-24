package com.example.financacelular.deteccao

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.RestauroBanco
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Lê as notificações dos apps de banco que a pessoa ligou em Configurações e avisa quando
 * parecem uma transação. Só o corpo do texto é usado e nada dele é gravado ou registrado em log.
 */
class NotificacaoListener : NotificationListenerService() {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val filtro = FiltroDeRepeticao()

    private fun repositorio() = FinancaRepository.getInstance(AppDatabase.getInstance(applicationContext))

    // Abre o banco e monta o popup escondido já na conexão do serviço: o primeiro aviso não espera nada carregar
    override fun onListenerConnected() {
        // Restauro pendente: não abre o banco antes da troca do arquivo
        if (RestauroBanco.temPendente(applicationContext)) return
        escopo.launch { runCatching { repositorio().listarTransacoesDoDia(LocalDate.now()) } }
        PopupDeTransacao.aquecer(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pacote = sbn.packageName
        if (pacote == packageName) return
        // Restauro pendente: ignora a notificação em vez de abrir o banco antes da troca do arquivo
        if (RestauroBanco.temPendente(applicationContext)) return

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
                // Só os lançamentos de hoje: jaLancada não olha outros dias, e ler tudo atrasava o popup
                val hoje = LocalDate.now()
                if (!jaLancada(detectada, hoje, repositorio().listarTransacoesDoDia(hoje))) {
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
