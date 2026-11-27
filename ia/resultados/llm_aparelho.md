# LLM no aparelho (S20 FE)

Task 9, medido em 2026-11-27 com `MotorLlmTest#medicao` (instrumentado, logcat tag `MedicaoLlm`).

Aparelho: Galaxy S20 FE `SM-G780G` (Snapdragon 865, Android 13, 6 GB de RAM; `MemTotal` 5,5 GiB, ~2,4–2,8 GB disponíveis com o uso normal). `libmotor_llm.so` com llama.cpp `b12815` (`ia/llm/VERSAO_LLAMA_CPP`), `armv8.2-a+dotprod+fp16`, Release, 4 threads, contexto 2.048, só CPU.

Como rodar: `adb push <gguf> /data/local/tmp/motapp-teste.gguf` e
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.financacelular.MotorLlmTest#medicao -Pandroid.testInstrumentationRunnerArguments.medir=<nome>`.

Os três rodaram em sequência, com 150 s de pausa entre eles. Cada "pergunta" = plano com a gramática (até 96 tokens) + resposta sem gramática com 1 fato (até 120 tokens), 5 perguntas seguidas. PSS = `TOTAL PSS` do `dumpsys meminfo` do processo do teste, depois de cada geração (inclui o app de teste e o modelo mapeado).

| Modo | GGUF base (Q4_K_M) | Carga | Prompt | Geração | PSS máx. | Por pergunta | Bateria / térmico | Viável? |
|---|---|---|---|---|---|---|---|---|
| Rápida | Qwen3 0.6B (462 MB) | 1,10 s | 212,8 tok/s | 28,8 tok/s | 1.178 MB | 5,6–6,2 s | 31,9 → 33,1 °C, status 2 | sim |
| Média | Qwen3 1.7B (1,2 GB) | 5,82 s | 57,3 tok/s | 14,2 tok/s | 2.433 MB | 13,7–13,9 s | 32,4 → 35,3 °C, status 3 | sim |
| Inteligente (4B) | Qwen3 4B (2,3 GB) | 12,50 s | 17,1 tok/s | 5,1 tok/s | 2.929 MB | 56,3 / 39,9 / 34,4 / 34,9 / 36,4 s | 33,9 → 40,1 °C, status 4 | **não** |

Prompt somado das 10 gerações: 1.275 tokens; geração: ~636 tokens. Nenhum dos três foi morto pelo lowmemorykiller (o 4B coube, com 2,9 GB de PSS num aparelho de 5,5 GiB), mas o 4B passou de 30 s em todas as perguntas e levou o aparelho ao status térmico 4 (de 0 a 6) em menos de 4 minutos.

**Conclusão:** o "Inteligente" não pode ser o 4B no S20 FE. Fica o 1.7B (ver Decisões no vault, 2026-11-27); o que o diferencia da Média (treino próprio ou o mesmo modelo) fica para a Task 11.

## `ramMinimaBytes` (para a Task 11)

Comparar com `ActivityManager.MemoryInfo.totalMem`, a RAM total que o Android informa (no S20 FE `SM-G780G` de 6 GB, `MemTotal` = 5.763.308 kB ≈ 5,5 GiB). O total precisa deixar o sistema e outros apps de pé além do PSS do modelo.

| Modo | `ramMinimaBytes` | Por quê |
|---|---|---|
| Rápida (0.6B) | `3_500_000_000L` | 1,18 GB de PSS: cabe em aparelho de 4 GB (que informa ~3,6–3,8 GB) |
| Média (1.7B) | `5_000_000_000L` | 2,43 GB de PSS: aparelho de 6 GB para cima (informa ~5,4–5,8 GB); exclui os de 4 GB |
| Inteligente (1.7B) | `5_000_000_000L` | mesmo modelo-base da Média |
| (4B, descartado) | — | 2,93 GB de PSS e 34–56 s por pergunta |

## Ressalvas

- Modelos **base**, sem o LoRA (o treinado tem o mesmo tamanho e velocidade). O plano do base saiu `{"chamadas":[]}` e a resposta abre com `<think>`; a qualidade é medida em `llm.md`, não aqui.
- A carga foi medida com o arquivo já no cache de página (o teste copia o GGUF para o cache do app antes); a primeira abertura depois de reiniciar o aparelho deve ser mais lenta.
- CPU sem dotprod/fp16 (anterior ao Snapdragon 845 e equivalentes): `MotorLlm.carregar` devolve `null`.
- Cancelar no meio gera erros `graph_compute ... failed` no logcat (o abort do llama.cpp); são esperados.
