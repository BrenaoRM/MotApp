package com.example.financacelular.data

/**
 * Dinheiro fica gravado em centavos (Long): soma de muitos lançamentos não acumula erro de Double.
 * As entidades expõem também o valor em reais (getter), só para mostrar e calcular proporções.
 */
fun reaisEmCentavos(reais: Double): Long = Math.round(reais * 100)

fun centavosEmReais(centavos: Long): Double = centavos / 100.0

/** Soma exata em centavos, devolvida em reais. */
inline fun <T> Iterable<T>.somaEmReais(centavos: (T) -> Long): Double = centavosEmReais(sumOf(centavos))
