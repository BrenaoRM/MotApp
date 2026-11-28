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

    private const val BASE = "https://github.com/BrenaoRM/MotApp/releases/download/modelo-ia-v1"
    private const val BASE_V2 = "https://github.com/BrenaoRM/MotApp/releases/download/modelo-ia-v2"

    // tempoLimiteMs: ~5x (Rápida) e ~3x (Média) o tempo por pergunta medido no S20 FE (5,6-6,2 s e 13,7-13,9 s).
    // O da Inteligente (Qwen3.5 2B) ainda não foi medido no aparelho: limite provisório e folgado.
    val disponiveis: List<ModeloDisponivel> = listOf(
        ModeloDisponivel(
            ModoIa.RAPIDA,
            listOf(
                ArquivoDeModelo(
                    "motapp-rapida-q4km.gguf", "$BASE/motapp-rapida-q4km.gguf",
                    "0f214ed39295b8ef80a94451100505b19c1a1b7ca77888a433aceecc9162eaf0", 396_704_608L,
                ),
            ),
            RAM_MINIMA_RAPIDA, 30_000L,
        ),
        ModeloDisponivel(
            ModoIa.MEDIA,
            listOf(
                ArquivoDeModelo(
                    "motapp-media-q4km.gguf", "$BASE/motapp-media-q4km.gguf",
                    "b4a3ab8069194eba2b32d719500e6e98b3370ba199efc975ae9b680c25df80ef", 1_107_408_736L,
                ),
            ),
            RAM_MINIMA_MEDIA, 45_000L,
        ),
        ModeloDisponivel(
            ModoIa.INTELIGENTE,
            listOf(
                ArquivoDeModelo(
                    "motapp-inteligente-q4km.gguf", "$BASE_V2/motapp-inteligente-q4km.gguf",
                    "c0d040954bb5fba31b9fbd48755f85e037cb832e67d373c46eb1a34d84a6e830", 1_285_207_680L,
                ),
            ),
            RAM_MINIMA_INTELIGENTE, 90_000L,
        ),
    )
}
