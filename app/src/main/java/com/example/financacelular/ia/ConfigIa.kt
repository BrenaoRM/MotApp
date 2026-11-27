package com.example.financacelular.ia

import org.json.JSONArray
import org.json.JSONObject

/** Parâmetros do modelo lidos de `assets/ia/modelo_ia.json` (gerado pela T2 em `ia/avaliar_categorias.py`). */
data class ConfigIa(
    val versaoModelo: Int,
    val dimensao: Int,
    val maxTokens: Int,
    val classificador: ConfigClassificador,
    val generico: ConfigGenerico,
    val atipicos: ConfigAtipicos,
    /** Termo (já em `normalizar_para_modelo`) → nome da categoria genérica (em [ConfigGenerico.nomes]). */
    val lexico: Map<String, String>,
    /** Intenções das perguntas ao Assistente; ausente no JSON = [ConfigIntencoes.VAZIA] (toda pergunta é desconhecida). */
    val intencoes: ConfigIntencoes = ConfigIntencoes.VAZIA,
) {
    companion object {
        fun deJson(json: String): ConfigIa {
            val o = JSONObject(json)
            return ConfigIa(
                versaoModelo = o.getInt("versaoModelo"),
                dimensao = o.getInt("dimensao"),
                maxTokens = o.getInt("maxTokens"),
                classificador = ConfigClassificador.deJson(o.getJSONObject("classificador")),
                generico = ConfigGenerico.deJson(o.getJSONObject("generico")),
                atipicos = ConfigAtipicos.deJson(o.getJSONObject("atipicos")),
                lexico = lexicoDeJson(o.getJSONObject("lexico")),
                // Seção malformada desliga só as Perguntas (tudo vira desconhecida), não o resto da IA
                intencoes = runCatching { o.optJSONObject("intencoes")?.let(ConfigIntencoes::deJson) }.getOrNull()
                    ?: ConfigIntencoes.VAZIA,
            )
        }

        internal fun lexicoDeJson(o: JSONObject): Map<String, String> =
            LinkedHashMap<String, String>().apply { o.keys().forEach { put(it, o.getString(it)) } }
    }
}

data class ConfigClassificador(
    val epocas: Int,
    val taxa: Float,
    val lambdaL2: Float,
    val pesoPrototipo: Float,
    val alfaK: Float,
    val temperaturaPrior: Float,
    val limiarSugestao: Float,
    val minimoExemplos: Int,
) {
    companion object {
        internal fun deJson(o: JSONObject) = ConfigClassificador(
            epocas = o.getInt("epocas"),
            taxa = o.getDouble("taxa").toFloat(),
            lambdaL2 = o.getDouble("lambdaL2").toFloat(),
            pesoPrototipo = o.getDouble("pesoPrototipo").toFloat(),
            alfaK = o.getDouble("alfaK").toFloat(),
            temperaturaPrior = o.getDouble("temperaturaPrior").toFloat(),
            limiarSugestao = o.getDouble("limiarSugestao").toFloat(),
            minimoExemplos = o.getInt("minimoExemplos"),
        )
    }
}

/** Prior genérico: RL sobre embeddings para as 9 categorias genéricas ([nomes]) e o mapeamento por nome. */
data class ConfigGenerico(
    val nomes: List<String>,
    val discricionarias: Map<String, Float>,
    val pesos: List<FloatArray>,
    val vieses: FloatArray,
    val vetoresNomes: List<FloatArray>,
    val similaridadeMinimaMapeamento: Float,
    val confiancaLexico: Float,
) {
    companion object {
        internal fun deJson(o: JSONObject): ConfigGenerico {
            val nomes = o.getJSONArray("nomes")
            val disc = o.optJSONObject("discricionarias")
            return ConfigGenerico(
                nomes = List(nomes.length()) { nomes.getString(it) },
                discricionarias = disc?.keys()?.asSequence()?.associateWith { disc.getDouble(it).toFloat() }.orEmpty(),
                pesos = matriz(o.getJSONArray("pesos")),
                vieses = floats(o.getJSONArray("vieses")),
                vetoresNomes = matriz(o.getJSONArray("vetoresNomes")),
                similaridadeMinimaMapeamento = o.getDouble("similaridadeMinimaMapeamento").toFloat(),
                confiancaLexico = o.getDouble("confiancaLexico").toFloat(),
            )
        }
    }
}

data class ConfigAtipicos(
    val contaminacao: Float,
    val arvores: Int,
    val amostra: Int,
    val semente: Int,
    val minimoGastos: Int,
    val valorMinimoCentavos: Long,
    val zLimite: Float,
    val janelaDuplicataDias: Int,
    val pisoMad: Float,
    val mesesHistorico: Int,
) {
    companion object {
        internal fun deJson(o: JSONObject) = ConfigAtipicos(
            contaminacao = o.getDouble("contaminacao").toFloat(),
            arvores = o.getInt("arvores"),
            amostra = o.getInt("amostra"),
            semente = o.getInt("semente"),
            minimoGastos = o.getInt("minimoGastos"),
            valorMinimoCentavos = o.getLong("valorMinimoCentavos"),
            zLimite = o.getDouble("zLimite").toFloat(),
            janelaDuplicataDias = o.getInt("janelaDuplicataDias"),
            pisoMad = o.getDouble("pisoMad").toFloat(),
            mesesHistorico = o.getInt("mesesHistorico"),
        )
    }
}

/**
 * RL softmax sobre o embedding da pergunta (`softmax(pesos · v + vieses)`, uma linha por [nomes]). Abaixo de [limiar]
 * de confiança a pergunta é desconhecida; o mesmo vale para a que fica longe de todos os [centroides] (cosseno máximo
 * abaixo de [similaridadeMinima]), pois a confiança só compara intenções entre si. [similaridadeCategoria] é o cosseno
 * mínimo entre a pergunta e o nome de uma categoria para tomá-la como a citada. Sem centroides a porta fica desligada.
 */
data class ConfigIntencoes(
    val nomes: List<String>,
    val pesos: List<FloatArray>,
    val vieses: FloatArray,
    val limiar: Float,
    val similaridadeCategoria: Float,
    val centroides: List<FloatArray> = emptyList(),
    val similaridadeMinima: Float = 0f,
) {
    companion object {
        val VAZIA = ConfigIntencoes(emptyList(), emptyList(), FloatArray(0), 1f, 1f)

        internal fun deJson(o: JSONObject): ConfigIntencoes {
            val nomes = o.getJSONArray("nomes")
            return ConfigIntencoes(
                nomes = List(nomes.length()) { nomes.getString(it) },
                pesos = matriz(o.getJSONArray("pesos")),
                vieses = floats(o.getJSONArray("vieses")),
                limiar = o.getDouble("limiar").toFloat(),
                similaridadeCategoria = o.getDouble("similaridadeCategoria").toFloat(),
                centroides = o.optJSONArray("centroides")?.let(::matriz).orEmpty(),
                similaridadeMinima = o.optDouble("similaridadeMinima", 0.0).toFloat(),
            )
        }
    }
}

private fun floats(a: JSONArray): FloatArray = FloatArray(a.length()) { a.getDouble(it).toFloat() }

private fun matriz(a: JSONArray): List<FloatArray> = List(a.length()) { floats(a.getJSONArray(it)) }
