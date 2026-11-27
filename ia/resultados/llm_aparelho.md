# LLM no aparelho (S20 FE)

Task 9, medido em 2026-11-27 com `MotorLlmTest#medicao` (instrumentado, logcat tag `MedicaoLlm`).

Aparelho: Galaxy S20 FE (Snapdragon 865, Android 13, 5,7 GB de RAM, ~2,4 GB disponíveis com o uso normal). `libmotor_llm.so` com llama.cpp `b12815` (`ia/llm/VERSAO_LLAMA_CPP`), `armv8.2-a+dotprod+fp16`, Release, 4 threads, contexto 2.048, só CPU.

Como rodar: `adb push <gguf> /data/local/tmp/motapp-teste.gguf` e
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.financacelular.MotorLlmTest#medicao -Pandroid.testInstrumentationRunnerArguments.medir=<nome>`.

Cada "pergunta" = plano com a gramática (até 96 tokens) + resposta sem gramática com 1 fato (até 120 tokens), 5 perguntas seguidas. PSS = `TOTAL PSS` do `dumpsys meminfo` do processo do teste, depois de cada geração (inclui o app de teste e o modelo mapeado).

| Modo | GGUF base (Q4_K_M) | Carga | Prompt | Geração | PSS máx. | Por pergunta | Bateria | Viável? |
|---|---|---|---|---|---|---|---|---|
| Rápida | Qwen3 0.6B (462 MB) | 1,10 s | 212,8 tok/s | 28,8 tok/s | 1.178 MB | 5,6–6,2 s | 31,9 → 33,1 °C (térmico 2) | sim |
| Média | Qwen3 1.7B (1,2 GB) | não medido | | | ~2,0 GB (estimado) | | | a medir |
| Inteligente | Qwen3 4B (2,3 GB) | não medido | | | ~3,3 GB (estimado) | | | provavelmente não |

**1.7B e 4B não foram medidos:** a rodada foi interrompida no início do 1.7B porque o PC ficou sem memória (treino e professor rodando ao mesmo tempo). Repetir com `MODELOS="1.7B 4B"` no script da medição quando o PC estiver livre.

Estimativa de PSS (não medida): tamanho do GGUF + ~0,7 GB (contexto de 2.048, buffers de cálculo e o próprio app), o mesmo excedente medido no 0.6B. Por ela, o 4B passa dos ~2,4 GB livres do S20 FE e o "Inteligente" deve virar o 1.7B, mas a decisão só sai com a medição.

## `ramMinimaBytes` (para a Task 11)

`ActivityManager.MemoryInfo.totalMem` mínimo sugerido; o total precisa deixar o sistema e outros apps de pé além do PSS do modelo.

| Modo | `ramMinimaBytes` | Base |
|---|---|---|
| Rápida | 4 GiB (`4L shl 30`) | medido: 1,18 GB de PSS |
| Média | 6 GiB (`6L shl 30`) | estimado; confirmar |
| Inteligente | 8 GiB (`8L shl 30`) se continuar 4B; 6 GiB se virar o 1.7B | estimado; confirmar |

## Ressalvas

- Modelos **base**, sem o LoRA (o treinado tem o mesmo tamanho e velocidade). O plano do base saiu `{"chamadas":[]}` e a resposta abre com `<think>`; a qualidade é medida em `llm.md`, não aqui.
- A carga foi medida com o arquivo já no cache de página (o teste copia o GGUF para o cache do app antes); a primeira abertura depois de reiniciar o aparelho deve ser mais lenta.
- CPU sem dotprod/fp16 (anterior ao Snapdragon 845 e equivalentes): `MotorLlm.carregar` devolve `null`.
- Cancelar no meio gera erros `graph_compute ... failed` no logcat (o abort do llama.cpp); são esperados.
