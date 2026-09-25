package com.example.financacelular.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class InterNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        val packageName = sbn?.packageName ?: return
        
        // Verifica se a notificação é do aplicativo do Banco Inter (pacote oficial)
        if (packageName == "br.com.bancointer") {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
            
            // Exemplo de texto comum de compra do Inter: "Compra aprovada de R$ 45,90 em Padaria Sao Jose"
            analisarNotificacaoCompra(title, text)
        }
    }

    private fun analisarNotificacaoCompra(titulo: String, texto: String) {
        val textoCompleto = "$titulo $texto"
        
        // Verifica se é uma notificação de compra aprovada
        if (textoCompleto.contains("compra", ignoreCase = true) || 
            textoCompleto.contains("aprovada", ignoreCase = true)) {
            
            // Regex para extrair o valor monetário (ex: R$ 45,90 ou 45.90)
            val regexValor = Regex("R\\$\\s*([0-9.,]+)")
            val matchValor = regexValor.find(textoCompleto)
            
            val valorStr = matchValor?.groupValues?.get(1)?.replace(".", "")?.replace(",", ".") ?: return
            val valorDouble = valorStr.toDoubleOrNull() ?: return

            // Tenta extrair o estabelecimento (geralmente após "em" ou "no")
            val estabelecimento = extrairEstabelecimento(textoCompleto)

            // Salva automaticamente no banco de dados como Cartão de Crédito
            scope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val repository = FinancaRepository.getInstance(db)
                
                // Busca uma categoria padrão (ex: Outros ou Geral com ID 6 ou similar)
                val categorias = db.categoriaDao().listarPorTipo(TipoTransacao.DESPESA)
                
                val transacao = Transacao(
                    valor = valorDouble,
                    data = LocalDate.now(),
                    categoriaId = 6L, // ID padrão de categoria genérica, ajuste se necessário
                    tipo = TipoTransacao.DESPESA,
                    descricao = "Inter: $estabelecimento",
                    formaPagamento = FormaPagamento.CARTAO_CREDITO,
                    cartaoId = 1L,
                    anoMes = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now()),
                    numeroParcela = 1,
                    totalParcelas = 1
                )
                
                repository.salvarTransacao(transacao)
                Log.d("InterListener", "Compra de R$ $valorDouble em $estabelecimento salva com sucesso!")
            }
        }
    }

    private fun extrairEstabelecimento(texto: String): String {
        // Procura por palavras como "em" ou "no" seguidas pelo local
        val regexLocal = Regex("(?:em|no)\\s+([A-Za-z0-9\\s]+)")
        val match = regexLocal.find(texto)
        return match?.groupValues?.get(1)?.trim() ?: "Estabelecimento Inter"
    }
}