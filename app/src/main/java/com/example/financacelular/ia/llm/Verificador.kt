package com.example.financacelular.ia.llm

import java.text.Normalizer

/**
 * Verificador de frases da IA avançada: todo número da resposta precisa vir dos [Fato]s.
 * Mesma semântica (e mesmos passos) de ia/llm/verificador.py; os dois testes leem casos_verificador.jsonl.
 */
object Verificador {
    private val FIM_DE_FRASE = Regex("""(?<=[.!?])[ \t\r\n]+|[\r\n]+""")
    private val ESPACOS = Regex("""[ \t\r\n\x0B\f]+""")
    // "R$ 1.200,00" = reais, "65%" = porcento, outro número = numero. O ponto só vale como milhar (".200").
    private val NUMERO = Regex("""(r\$ ?)?([0-9]+(?:\.[0-9]{3})*(?:,[0-9]+)?)( ?%)?""")

    /** Divide em frases sem quebrar "R$ 1.200": só corta em .!? seguido de espaço, ou em quebra de linha. */
    fun frases(texto: String): List<String> =
        texto.split(FIM_DE_FRASE).map { it.trim(' ', '\t', '\r', '\n') }.filter { it.isNotEmpty() }

    /** periodoVigente: rótulo de mês em vigor antes desta frase. Devolve (aprovada, novo periodoVigente). */
    fun verificarFrase(frase: String, fatos: List<Fato>, periodoVigente: String?): Pair<Boolean, String?> {
        val (periodo, casamentos) = casamentos(frase, fatos, periodoVigente)
        return casamentos.all { it.isNotEmpty() } to periodo
    }

    /** Índices (base 0) dos fatos com algum item citado nas frases, seguindo o período vigente. */
    fun fatosCitados(frasesAprovadas: List<String>, fatos: List<Fato>): Set<Int> {
        var periodo: String? = null
        val citados = mutableSetOf<Int>()
        for (frase in frasesAprovadas) {
            val (novo, casamentos) = casamentos(frase, fatos, periodo)
            periodo = novo
            casamentos.forEach { citados += it }
        }
        return citados
    }

    /** Novo período vigente e, para cada número da frase, os índices dos fatos com item que casa. */
    private fun casamentos(frase: String, fatos: List<Fato>, periodoVigente: String?): Pair<String?, List<Set<Int>>> {
        val texto = normalizar(frase)
        val periodo = periodo(texto, fatos, periodoVigente)
        val casamentos = NUMERO.findAll(mascarar(texto, fatos)).map { m ->
            val tipo = when {
                m.groups[1] != null -> TipoValor.REAIS
                m.groups[3] != null -> TipoValor.PORCENTO
                else -> TipoValor.NUMERO
            }
            val valor = canonico(m.groupValues[2])
            fatos.indices.filter { n ->
                val f = fatos[n]
                (f.periodo == null || f.periodo == periodo) && f.itens.any { i ->
                    i.tipo == tipo && canonico(i.valor) == valor &&
                        (i.rotulo == null || posicao(normalizar(i.rotulo), texto) >= 0)
                }
            }.toSet()
        }.toList()
        return periodo to casamentos
    }

    /** Regra 1: o primeiro período dos fatos citado na frase; empate na posição fica com o mais longo. */
    private fun periodo(texto: String, fatos: List<Fato>, anterior: String?): String? {
        var melhor = anterior
        var posicao = -1
        for (p in fatos.mapNotNull { it.periodo }.distinct()) {
            val i = posicao(normalizar(p), texto)
            if (i >= 0 && (posicao < 0 || i < posicao ||
                    (i == posicao && normalizar(p).length > normalizar(melhor!!).length))
            ) {
                melhor = p
                posicao = i
            }
        }
        return melhor
    }

    /** Regra 2: apaga da frase os rótulos dos fatos que têm dígito ("outubro de 2025", "Carro 2"). */
    private fun mascarar(texto: String, fatos: List<Fato>): String {
        val rotulos = fatos.flatMap { f -> listOf(f.periodo) + f.itens.map { it.rotulo } }.filterNotNull().map { normalizar(it) }.toSet()
        var saida = texto
        for (r in rotulos.filter { r -> r.any { it.isDigit() } }.sortedWith(compareBy({ -it.length }, { it }))) {
            var i = posicao(r, saida)
            while (i >= 0) {
                saida = saida.substring(0, i) + " ".repeat(r.length) + saida.substring(i + r.length)
                i = posicao(r, saida)
            }
        }
        return saida
    }

    /** Primeira ocorrência de [rotulo] em [texto] como palavra inteira, ou -1. */
    private fun posicao(rotulo: String, texto: String): Int {
        if (rotulo.isEmpty()) return -1
        var i = texto.indexOf(rotulo)
        while (i >= 0) {
            val fim = i + rotulo.length
            if ((i == 0 || !texto[i - 1].isLetterOrDigit()) && (fim == texto.length || !texto[fim].isLetterOrDigit())) return i
            i = texto.indexOf(rotulo, i + 1)
        }
        return -1
    }

    /** Minúsculas, só ASCII (sem acento e sem emoji), espaços simples. */
    private fun normalizar(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFKD).filter { it.code < 128 }.replace(ESPACOS, " ").trim(' ')

    /** "1.200,00" e "1200" viram "1200"; centavos que não são zero ficam ("1200,50"). */
    private fun canonico(valor: String): String {
        val semPonto = valor.replace(".", "")
        val inteiro = semPonto.substringBefore(',')
        val fracao = semPonto.substringAfter(',', "")
        return if (fracao.trim('0').isEmpty()) inteiro else "$inteiro,$fracao"
    }
}
