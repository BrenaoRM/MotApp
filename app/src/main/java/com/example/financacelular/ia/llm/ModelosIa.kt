package com.example.financacelular.ia.llm

enum class ModoIa(val titulo: String) { RAPIDA("Rápida"), MEDIA("Média"), INTELIGENTE("Inteligente") }

data class ArquivoDeModelo(val nome: String, val url: String, val sha256: String, val bytes: Long)

/** [arquivos] são os shards em ordem; todos precisam existir e o primeiro é o que o llama.cpp carrega. */
data class ModeloDisponivel(val modo: ModoIa, val arquivos: List<ArquivoDeModelo>, val ramMinimaBytes: Long, val tempoLimiteMs: Long)

object ModelosIa {
    // Medido num S20 FE de 6 GB (o sistema enxerga 5,76 GB)
    const val RAM_MINIMA_RAPIDA = 3_500_000_000L
    const val RAM_MINIMA_MEDIA = 5_000_000_000L
    const val RAM_MINIMA_INTELIGENTE = 5_000_000_000L

    /** Vazio até a Task 14 definir os arquivos finais. */
    val disponiveis: List<ModeloDisponivel> = emptyList()
}
