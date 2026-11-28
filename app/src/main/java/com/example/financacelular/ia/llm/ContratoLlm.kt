package com.example.financacelular.ia.llm

import android.content.Context
import com.example.financacelular.ia.Intencao
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

data class Chamada(val ferramenta: String, val mes: YearMonth?, val categoriaId: Long?)

/** Contrato com o LLM local: prompts e gramática idênticos byte a byte aos de `ia/llm/contrato.py`. */
class ContratoLlm(json: String) {
    private class Ferramenta(
        val nome: String,
        val intencao: Intencao,
        val usaMes: Boolean,
        val usaCategoria: Boolean,
        val categoriaObrigatoria: Boolean,
    )

    private val raiz = JSONObject(json)
    private val sistemaPlano = raiz.getString("sistema_plano")
    private val sistemaResposta = raiz.getString("sistema_resposta")
    private val ferramentas: List<Ferramenta> = raiz.getJSONArray("ferramentas").let { a ->
        (0 until a.length()).map {
            val f = a.getJSONObject(it)
            Ferramenta(
                f.getString("nome"), Intencao.valueOf(f.getString("intencao")),
                f.getBoolean("usa_mes"), f.getBoolean("usa_categoria"), f.getBoolean("categoria_obrigatoria"),
            )
        }
    }

    val textoRecusa: String = raiz.getString("texto_recusa")

    /** Início fixo de cada prompt (o turno de sistema): o motor guarda o processamento dele entre gerações. */
    val prefixoPlano: String = turno("system", sistemaPlano)
    val prefixoResposta: String = turno("system", sistemaResposta)

    fun promptPlano(pergunta: String, mesAtual: YearMonth, categorias: List<String>, historico: List<Pair<String, String>>): String {
        val texto = StringBuilder(prefixoPlano)
        for ((p, plano) in historico.takeLast(2)) {
            texto.append(turno("user", "Pergunta: $p")).append(turno("assistant", plano))
        }
        val usuario = "Mês atual: " + nomeDoMes(mesAtual) + " (" + mesAtual + ")\n" +
            "Categorias: " + categorias.joinToString(", ") + "\n" +
            "Pergunta: " + pergunta
        return texto.append(turno("user", usuario)).append("<|im_start|>assistant\n").toString()
    }

    fun promptResposta(pergunta: String, fatos: List<Fato>): String {
        val linhas = fatos.withIndex().joinToString("") { (i, f) -> "${i + 1}. ${f.frase}\n" }
        val usuario = "Pergunta: $pergunta\nFatos:\n" + linhas.trimEnd('\n')
        return prefixoResposta + turno("user", usuario) + "<|im_start|>assistant\n"
    }

    fun gramaticaPlano(mesAtual: YearMonth, categorias: List<String>): String {
        val fs = if (categorias.isEmpty()) {
            // sem categorias: some quem exige categoria e a cláusula opcional de categoria
            ferramentas.filter { !it.categoriaObrigatoria }.map { Ferramenta(it.nome, it.intencao, it.usaMes, false, false) }
        } else {
            ferramentas
        }
        val meses = listOf("atual", "anterior") + (0 until MESES_NA_GRAMATICA).map { mesAtual.minusMonths(it.toLong()).toString() }
        val regras = ArrayList<String>()
        regras.add(
            "root ::= " + gbnf("{\"chamadas\":[") + " (chamada (" + gbnf(",") + " chamada){0," + (MAX_CHAMADAS - 1) + "})? " + gbnf("]}"),
        )
        regras.add("chamada ::= " + fs.joinToString(" | ") { "c-" + it.nome.replace("_", "-") })
        for (f in fs) {
            var corpo = gbnf("{\"f\":\"" + f.nome + "\"")
            if (f.usaMes) corpo += " " + gbnf(",\"mes\":") + " mes"
            if (f.usaCategoria) {
                val parte = gbnf(",\"categoria\":") + " categoria"
                corpo += " " + (if (f.categoriaObrigatoria) parte else "($parte)?")
            }
            corpo += " " + gbnf("}")
            regras.add("c-" + f.nome.replace("_", "-") + " ::= " + corpo)
        }
        regras.add("mes ::= " + meses.joinToString(" | ") { gbnf("\"" + it + "\"") })
        if (categorias.isNotEmpty()) {
            regras.add("categoria ::= " + categorias.joinToString(" | ") { gbnf(jsonString(it)) })
        }
        return regras.joinToString("\n") + "\n"
    }

    /** null = JSON inválido; lista vazia = fora do escopo. Descarta chamada com categoria desconhecida ou obrigatória ausente. */
    fun lerPlano(json: String, mesAtual: YearMonth, categorias: Map<Long, String>): List<Chamada>? {
        val chamadas: JSONArray = try {
            JSONObject(json).getJSONArray("chamadas")
        } catch (_: Exception) {
            return null
        }
        val porNome = categorias.entries.associate { it.value to it.key }
        val saida = ArrayList<Chamada>()
        for (i in 0 until minOf(chamadas.length(), MAX_CHAMADAS)) {
            val c = chamadas.optJSONObject(i) ?: continue
            val f = ferramentas.firstOrNull { it.nome == c.optString("f") } ?: continue
            val mes = if (f.usaMes) lerMes(c.optString("mes", "atual"), mesAtual) ?: continue else null
            var categoriaId: Long? = null
            if (f.usaCategoria) {
                val nome = if (c.has("categoria") && !c.isNull("categoria")) c.optString("categoria") else null
                if (nome != null) {
                    categoriaId = porNome[nome] ?: continue
                } else if (f.categoriaObrigatoria) {
                    continue
                }
            }
            saida.add(Chamada(f.nome, mes, categoriaId))
        }
        return saida
    }

    fun intencao(ferramenta: String): Intencao = ferramentas.firstOrNull { it.nome == ferramenta }?.intencao ?: Intencao.DESCONHECIDA

    private fun lerMes(texto: String, mesAtual: YearMonth): YearMonth? = when (texto) {
        "atual" -> mesAtual
        "anterior" -> mesAtual.minusMonths(1)
        else -> try {
            YearMonth.parse(texto)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val MAX_CHAMADAS = 4
        private const val MESES_NA_GRAMATICA = 24
        private val NOMES_DOS_MESES = listOf(
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
        )

        fun doAsset(context: Context): ContratoLlm =
            ContratoLlm(context.assets.open("ia/llm/contrato.json").use { it.readBytes().toString(Charsets.UTF_8) })

        private fun turno(papel: String, texto: String) = "<|im_start|>$papel\n$texto<|im_end|>\n"

        private fun nomeDoMes(mes: YearMonth) = NOMES_DOS_MESES[mes.monthValue - 1] + " de " + mes.year

        /** Literal GBNF entre aspas: escapa `\` e `"`, nada mais. */
        private fun gbnf(literal: String) = "\"" + literal.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        /** Igual a json.dumps(ensure_ascii=False): acentos e emoji ficam literais. */
        internal fun jsonString(s: String): String {
            val sb = StringBuilder("\"")
            for (ch in s) when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch == '\b' -> sb.append("\\b")
                ch == '\u000C' -> sb.append("\\f")
                ch < ' ' -> sb.append("\\u%04x".format(ch.code))
                else -> sb.append(ch)
            }
            return sb.append('"').toString()
        }
    }
}
