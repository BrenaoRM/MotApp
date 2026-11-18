package com.example.financacelular.deteccao

import com.example.financacelular.data.Transacao
import java.time.LocalDate

/** Reconhece a mesma notificação chegando de novo em poucos minutos. Só guarda a chave, em memória. */
class FiltroDeRepeticao(
    private val agora: () -> Long = System::currentTimeMillis,
    private val janelaMs: Long = 120_000
) {
    private val vistas = mutableMapOf<String, Long>()

    @Synchronized
    fun jaVista(chave: String): Boolean {
        val instante = agora()
        vistas.values.removeAll { instante - it > janelaMs }
        if (chave in vistas) return true
        vistas[chave] = instante
        return false
    }
}

/** Já existe lançamento do mesmo valor, tipo e dia, com descrição parecida? */
fun jaLancada(detectada: TransacaoDetectada, hoje: LocalDate, transacoes: List<Transacao>): Boolean =
    transacoes.any {
        it.data == hoje && it.tipo == detectada.tipo &&
            Math.abs(it.valor - detectada.valor) < 0.005 &&
            descricoesParecidas(it.descricao, detectada.descricao)
    }

private fun descricoesParecidas(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() && b.isNullOrBlank()) return true
    if (a.isNullOrBlank() || b.isNullOrBlank()) return false
    return a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)
}
