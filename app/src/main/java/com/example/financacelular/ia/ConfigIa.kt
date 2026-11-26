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

private fun floats(a: JSONArray): FloatArray = FloatArray(a.length()) { a.getDouble(it).toFloat() }

private fun matriz(a: JSONArray): List<FloatArray> = List(a.length()) { floats(a.getJSONArray(it)) }
