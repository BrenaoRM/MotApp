package com.example.financacelular.ia

import kotlin.math.abs

/**
 * Valor em centavos como `R$ 1.234`: sem centavos (arredondado, meio para cima em módulo), milhar com ponto e
 * independente de locale/JVM. Negativo: `-R$ 1.234`. Usado pelo [Recomendador] e pelas respostas do Assistente.
 */
fun formatarReaisInteiros(centavos: Long): String {
    val reais = (abs(centavos) + 50) / 100
    val digitos = reais.toString()
    val agrupado = buildString {
        digitos.forEachIndexed { i, c ->
            if (i > 0 && (digitos.length - i) % 3 == 0) append('.')
            append(c)
        }
    }
    return (if (centavos < 0 && reais > 0) "-R$ " else "R$ ") + agrupado
}
