package com.example.financacelular.deteccao

import android.content.SharedPreferences

data class Banco(val pacote: String, val nome: String)

/** Apps de banco cujas notificações podem ser lidas. Nenhum vem ligado: a pessoa escolhe em Configurações. */
object BancosMonitorados {
    // Pacotes conferidos na Play Store em 2026-11-18.
    val todos: List<Banco> = listOf(
        Banco("br.com.intermedium", "Inter"),
        Banco("com.nu.production", "Nubank"),
        Banco("com.itau", "Itaú"),
        Banco("com.bradesco", "Bradesco"),
        Banco("com.c6bank.app", "C6 Bank"),
        Banco("com.santander.app", "Santander"),
        Banco("br.com.bb.android", "Banco do Brasil"),
        Banco("br.com.gabba.Caixa", "Caixa"),
        Banco("com.picpay", "PicPay"),
        Banco("com.mercadopago.wallet", "Mercado Pago")
    )

    private const val CHAVE_ATIVOS = "deteccao_bancos_ativos"

    fun porPacote(pacote: String): Banco? = todos.firstOrNull { it.pacote == pacote }

    fun ativados(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(CHAVE_ATIVOS, emptySet()).orEmpty().toSet()

    fun definirAtivo(prefs: SharedPreferences, pacote: String, ativo: Boolean) {
        val novos = if (ativo) ativados(prefs) + pacote else ativados(prefs) - pacote
        prefs.edit().putStringSet(CHAVE_ATIVOS, novos).apply()
    }
}
