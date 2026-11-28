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

Release de `9b8f889` + as keep rules do WorkManager em `proguard-rules.pro` (ainda não commitadas), assinado com um keystore temporário (não o de produção), instalado no S20 FE (`RQ8R6003G0J`, Android 13). Dados de teste: 20 gastos e 1 ganho lançados pela tela em novembro/2026 (não há semente de dados nem importação; sem lançamentos em outubro). Mesmas 15 perguntas nos dois modos (índices 0, 1, 2, 3, 4, 6, 10, 12, 17, 25, 28, 31, 50, 52, 57 de `perguntas.jsonl`: 9 compostas, 3 simples, 3 fora de escopo), digitadas sem acento e sem pontuação (limite do `adb input text`), na mesma conversa (o histórico cresce).

**Como mediu:** script no aparelho (`uiautomator dump` em laço, ~2,2 s por amostra) marca o envio e acompanha o balão "pensando…". **Primeira frase** = primeira amostra sem "pensando…" (valor = ponto médio, erro de ±1,1 s); **total** = última mudança do texto da tela. O próprio `dump` pesa na CPU, então os tempos são um pouco pessimistas. RAM: `dumpsys meminfo` a cada ~0,7 s (TOTAL PSS e RSS do processo do app).

### Download (passo 1, release)

| Verificação | Resultado |
|---|---|
| Média (1,1 GB) baixa no release | Sim, ~3 min no Wi-Fi 2,4 GHz, 0 a 100 % com a pílula "Baixando N %"; ficou "Média · pronta" |
| Sair da tela e voltar durante o download | O progresso segue (4 % → 18 % → 29 %); o estado reaparece na folha e na pílula |
| Wi-Fi desligado no meio (30 %) | Fica em "Baixando 30 %" (parado por mais de 1 min), **sem** virar "Falhou"; com o Wi-Fi de volta retoma sozinho (30 % → 52 % em ~40 s, backoff de 30 s do WorkManager) e termina; a IA fica pronta |
| Rápida (397 MB) pelo "Trocar de modo" | Baixou em menos de 1 min e ficou pronta |

### Perguntas: tempo até a primeira frase e total

| Modo | Tipo (n) | 1ª frase (média, faixa) | Total (média, faixa) |
|---|---|---|---|
| Rápida (0.6B) | compostas (9) | 5,5 s (3,7 a 8,3) | 7,2 s (6,7 a 9,4) |
| Rápida | simples (3) | 3,5 s (3,4 a 3,6) | 4,6 s (4,5 a 4,7) |
| Rápida | fora de escopo (3) | < 2,3 s (recusa imediata: "Só consigo falar sobre os seus gastos e receitas.") | < 2,3 s |
| Média (1.7B) | compostas (9) | 9,2 s (8,3 a 11,0) | 12,4 s (9,6 a 14,5) |
| Média | simples (3) | 6,0 s (5,8 a 6,2) | 7,2 s (7,0 a 7,4) |
| Média | fora de escopo (3) | 5,9 s (5,7 a 6,0) | 7,1 s (6,9 a 7,2) |

A 1ª pergunta de cada modo inclui a carga do modelo (~2,5 s a mais: Rápida 8,3 s, Média 11,0 s). Nenhuma das 30 perguntas travou ou deu erro. Os valores citados conferem com os dados lançados (gasto R$ 1.346, recebido R$ 5.000, sobra R$ 3.654, maior gasto R$ 300 em Contas).

Qualidade observada (não são erros de execução):
- Média: nas 3 perguntas fora de escopo respondeu "Não vi nada para ajustar agora." (sem relação com a pergunta, em vez de recusar); a Rápida recusou direito.
- Frases repetidas nos dois modos (P1 e P5 da Média repetem "Não encontrei gastos em outubro"; a Rápida repete "Em novembro, você…" em P2 e P3).
- P2 (categorias inexistentes "mercado" e "academia"): a Média listou outras categorias como se fosse a resposta; a Rápida disse que não há dados de academia (melhor).
- Para meses sem dados as duas dizem "Não encontrei gastos em outubro" e param.

### Pico de RAM do processo do app

| Modo | PSS máx. nas 15 perguntas | RSS máx. | Observação |
|---|---|---|---|
| Rápida | 1.153 MB | 1.196 MB | PSS mínimo de 202 MB antes da 1ª pergunta (modelo ainda não carregado) |
| Média | 1.596 MB | 1.594 MB | amostras manuais logo depois da carga deram 2.110 a 2.210 MB (perto dos 2.433 MB do teste instrumentado); no ciclo de 5 min o máximo foi 1.473 MB (o Android devolve páginas do modelo mapeado) |

Mesmo PID durante toda a sessão: nenhum processo morto pelo lowmemorykiller.

### Térmico: 5 min de perguntas na Média

12 perguntas em ciclo (compostas e simples), sem pausa, 15 respostas em 300 s. Partida com o aparelho aquecido pelas rodadas anteriores (esperei até o `ThermalStatus` cair a 1).

| t (s) | Bateria (°C) | `ThermalStatus` | Pico CPU (zonas `cpu-1-*-usr`, °C) |
|---|---|---|---|
| 0 | 35,6 | 1 | 55,7 |
| ~67 | 37,7 | 3 | 84,5 |
| ~135 | 38,6 | 3 | 84,5 |
| ~203 | 39,2 | 3 | 84,1 |
| ~271 | 39,7 | 3 | 84,5 |
| ~300 (fim) | 40,0 | 4 | 85,3 |

Tempo total por resposta no ciclo (s): 13,9, 11,8, 14,7, 14,1, 14,2, 12,3, 14,6, 9,5, 12,2, 9,7, 7,1, 7,6, 17,2, 14,8, 17,3. As 3 últimas (t a partir de 230 s) ficaram 20 a 25 % mais lentas que as primeiras do mesmo tipo (compostas: 11,8 a 14,7 s no início contra 14,8 a 17,3 s no fim). O núcleo grande alternou entre 0,84 e 2,84 GHz o tempo todo (`scaling_max_freq` nunca baixou), então a perda vem do controle térmico dinâmico (status 3 a partir de ~45 s, 4 no fim) e do histórico que cresce. Conclusão: a Média aguenta 5 min seguidos sem travar, mas chega ao status 4 e perde ~20 % de velocidade; com perguntas espaçadas o aparelho esfria entre elas.

### Review Focus

| Item | Resultado |
|---|---|
| Modo avião depois de ativada (Média) | Passou: `airplane_mode_on=1`, sem rede, pergunta composta respondida pelo LLM em ~12 s (1ª frase ~8 s); `t15-07-modo-aviao.png` |
| Apagar o arquivo pelo adb | **Não foi possível:** o release não é debuggable (`run-as` negado) e o arquivo fica no armazenamento interno. Substituto: "Desativar" pela folha (o app apaga o modelo): o estado vai para "Desligada", a pergunta seguinte é respondida pelo sistema atual ("Em novembro você gastou R$ 1.346.") e não há crash (`t15-10-apos-desativar.png`). Falta testar o arquivo sumindo com o estado "Pronta" (build debug) |
| Sair durante a geração | Passou: pergunta enviada e Home 1 s depois; 25 s depois o mesmo PID (sem `FATAL`), a resposta apareceu completa e não ficou "pensando…" preso (`t15-08-saiu-durante-geracao.png`) |
| Wi-Fi no meio do download | Passou com ressalva: pausa e retoma sozinho; o estado fica em "Baixando 30 %" sem avisar que parou (Bug 2) |

### Bugs

1. **(bloqueante, R8)** No release, `OverwritingInputMerger` fica sem construtor, o worker nunca roda e o download fica em 0 % (`WM-InputMerger NoSuchMethodException`). **Correção confirmada localmente:** com `-keep class androidx.work.OverwritingInputMerger { <init>(); }` e `-keep class * extends androidx.work.InputMerger { <init>(); }` em `proguard-rules.pro` o download roda (4 % em 8 s) e termina, sem erro de `InputMerger` no logcat. Falta entrar no repositório; conferir também o worker de backup do Drive.
2. **(menor)** Falha do worker antes do `doWork`, ou rede parada, mantém "Baixando N %" para sempre: observar o `WorkInfo` e chamar `falhar`, ou avisar "sem conexão, retomando".
3. **(qualidade, Média)** Pergunta fora de escopo responde "Não vi nada para ajustar agora." em vez de recusar como a Rápida; olhar o caminho de plano vazio no `AssistenteLlm`.
4. **(qualidade)** Frases repetidas ("Não encontrei gastos em outubro" duas vezes; "Em novembro, você…" várias vezes): deduplicar fatos iguais antes de escrever.
5. **(UI)** Na folha "Perguntas", com conversa longa e teclado aberto, o campo de texto e o botão enviar saem da tela (a folha passa da altura visível); só voltam ao fechar o teclado.

Screenshots em `.superpowers/sdd/2026-11-27-ia-llm-local/` (pasta ignorada pelo git): `t15-01-folha-ia.png`, `t15-04-wifi-off.png`, `t15-05-media-pronta.png`, `t15-06-media-fora-de-escopo.png`, `t15-07` a `t15-10`.
