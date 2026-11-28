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

## Cache de prefixo e KV q8_0

Task 11b, medido em 2026-11-27 no S20 FE com a **Rápida treinada** (`motapp-rapida-q4km.gguf`, 397 MB) e `MotorLlmTest#medicao` (as mesmas 5 perguntas; agora também registra o tempo do prompt e o tempo até o primeiro token de cada geração). As quatro configurações rodaram em sequência, 120 s de pausa entre elas, depois de um "antes" com o código da 1.0.185. A configuração escolhe-se com `-Pandroid.testInstrumentationRunnerArguments.kv=f16` e `...cache=nao`.

O prefixo cacheado é o turno de sistema: 60 tokens no plano, 64 na resposta (de ~127 por prompt). Snapshot guardado por prefixo: 3,66 / 3,90 MB com q8_0 (6,88 / 7,34 MB com f16); o 1.7B tem as mesmas dimensões de KV (28 camadas, 8 cabeças KV de 128), então dá o mesmo ~7,6 MB no total, bem abaixo do limite de 60 MB.

Tempos em ms, perguntas 1→5 (a 1ª sempre processa e guarda o prefixo):

| Configuração | Prompt plano | Prompt resposta | 1º token plano | 1º token resposta | Prompt (tok/s) | PSS máx. |
|---|---|---|---|---|---|---|
| antes (1.0.185: f16, sem cache) | 963/753/751/761/766 | 823/810/836/837/849 | 1022/808/809/817/823 | 863/823/854/849/866 | 156,5 | 1.209 MB |
| f16, sem cache (repetição do antes) | 789/756/764/776/765 | 817/816/828/832/853 | 864/815/823/830/820 | 845/830/840/849/865 | 159,5 | 1.218 MB |
| f16 + cache | 1036/462/436/426/430 | 864/534/457/473/485 | 1087/517/493/480/482 | 894/552/473/488/501 | 139,0 | 1.219 MB |
| q8_0, sem cache | 925/844/850/857/870 | 887/885/898/912/922 | 1003/895/904/910/919 | 914/895/906/924/931 | 144,0 | 1.114 MB |
| **q8_0 + cache (o app)** | 949/481/490/518/544 | 916/494/520/517/584 | 1016/533/543/572/599 | 943/505/533/529/594 | 129,5 | **1.109 MB** |

"Prompt (tok/s)" conta só os tokens processados de verdade (com cache: 779 dos 1.275; 496 vieram do cache), por isso cai com o cache. A geração ficou igual em todas (20,7–21,1 tok/s).

- **Cache:** da 2ª pergunta em diante o prompt cai de ~760 para ~480 ms no plano e de ~830 para ~520 ms na resposta (−37 %); o primeiro token chega ~300 ms antes em cada geração, ~0,6 s por pergunta. RAM extra: os ~7,6 MB dos snapshots (não aparece no PSS, dentro do ruído).
- **KV q8_0:** −105 MB de PSS (1.218 → 1.109–1.114 MB; o KV de 2.048 posições cai de 224 para 119 MB). O prompt fica ~8 % mais lento (flash attention com V quantizado na CPU: 885 vs 820 ms sem cache, 500 vs 460 ms com cache). Não é queda grande e a RAM pesa mais no 1.7B (2,4 GB de PSS num aparelho com 2,4–2,8 GB livres): ficam K e V em q8_0.
- **Igualdade:** `cacheDoPrefixoNaoMudaASaida` — com o cache, plano e resposta saem byte a byte iguais aos sem cache nas 5 perguntas (q8_0). `kvQ8ContraF16`: 7 das 10 saídas iguais entre q8_0 e f16; os 5 planos iguais, 3 respostas com outra redação (mesmos valores em R$).

Qualidade do q8_0 no PC (`avaliar_llm.py` com `-ngl 0 -fa on`, na CPU porque a GPU está treinando a Média; o script não aceita argumentos do servidor, então rodou por um wrapper que só acrescenta `-ctk/-ctv`):

| KV | plano_exato | plano_parcial | recusa_correta | fidelidade | troca_de_papel |
|---|---|---|---|---|---|
| f16 (CPU) | 0,933 | 0,964 | 1,000 | 0,977 | 0,008 |
| q8_0 (CPU) | 0,917 | 0,947 | 1,000 | 0,986 | 0,008 |
| referência `llm/rapida.json` (GPU, f16) | 0,917 | 0,947 | 1,000 | 0,979 | 0,000 |

q8_0 fica dentro do limite: plano_exato igual à referência (0,917; −0,017 contra o f16 na mesma CPU, 1 caso), fidelidade 0,986 (melhor). Na CPU do PC o q8_0 com flash attention gerou bem mais devagar (17 contra 70 tok/s); no aparelho a geração não mudou (ARM), então a velocidade do PC não se aplica.

## Teste fim a fim (2026-11-27)

**Status: bloqueado no passo 1.** O download da IA avançada não anda no build **release** (R8), então nenhum modo foi ativado e as medições (tempo até a 1ª frase, total por pergunta, pico de RAM, curva térmica) e os 4 itens do Review Focus **não foram feitos**. Nada foi inventado: as tabelas abaixo ficam pendentes.

Preparo feito: release de `9b8f889` com `assembleRelease` assinado por um keystore temporário (gerado só para este teste; o de produção não foi usado), debug desinstalado, app instalado no S20 FE (`RQ8R6003G0J`), perfil criado ("Usar só neste aparelho") e 20 gastos + 1 ganho lançados pela tela (não há semente de dados nem importação no app; a IA do Assistente só abre com 20 gastos). Folha "IA avançada" mostra Rápida, Média e Inteligente (Em breve), como esperado.

### Bug 1 (bloqueante): R8 remove o construtor de `OverwritingInputMerger` e o `BaixarModeloWorker` nunca roda

- Passos: instalar o release, Assistente → IA avançada → Média → Baixar.
- Observado: a pílula fica em "Baixando… 0%" para sempre (80 s+ medidos, sem nenhum byte). Logcat: `E WM-InputMerger: Trouble instantiating androidx.work.OverwritingInputMerger` / `NoSuchMethodException: androidx.work.OverwritingInputMerger.<init> []` e `E WM-WorkerWrapper: Could not create Input Merger`. O worker falha antes do `doWork`.
- Causa: `app/proguard-rules.pro` confia nas regras embutidas do WorkManager 2.9.0, mas com o R8 da release (`optimization { enable = true }`) o construtor sem argumentos é removido. No debug (sem R8) o download funcionou, por isso só aparece na release. Provavelmente o worker de backup do Drive (`MainActivity`) sofre do mesmo problema.
- Sugestão de correção (não aplicada; a regra de não mexer no código do app valeu, e a tentativa de rebuild com ela foi negada pelo ambiente): em `proguard-rules.pro`, `-keep class androidx.work.OverwritingInputMerger { <init>(); }` e `-keep class * extends androidx.work.InputMerger { <init>(); }`; depois rebuild e repetir o passo 1.

### Bug 2: falha do worker antes do `doWork` não vira "Falhou"

O estado da IA fica em `Baixando 0%` (pílula e Configurações) em vez de `Falhou · toque para tentar de novo`, porque quem chama `GerenciadorDeModelo.falhar` é o próprio `doWork`. Sugestão: observar o `WorkInfo` (estado FAILED/CANCELLED) e chamar `falhar`, ou um tempo limite sem progresso.

### Pendente depois da correção

Tabela por modo (15 perguntas de `perguntas.jsonl`, com compostas e fora de escopo: 1ª frase, total, erros), pico de RAM (`dumpsys meminfo`, 1 amostra/s), 5 min de perguntas na Média com bateria/CPU/`thermalservice` por minuto, e o Review Focus (modo avião após ativar, apagar o arquivo pelo adb, sair da tela durante a geração, Wi-Fi off no meio do download). Screenshots: `.superpowers/sdd/2026-11-27-ia-llm-local/t15-01-folha-ia.png`, `t15-02-baixando-media.png`, `t15-03-travado-0pct.png`.
