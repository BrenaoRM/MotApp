# IA avançada (LLM local, 3 modos) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** As Perguntas do Assistente passam a entender mensagens compostas e responder em texto natural, com um LLM local (Qwen3 0.6B/1.7B/4B ajustado com LoRA) que planeja chamadas às ferramentas do app e escreve usando só fatos calculados e verificados; o modelo é baixado dentro do app por quem ativar.

**Architecture:** Contrato único (`assets/ia/llm/contrato.json`) define ferramentas, prompts e recusa, e é lido igual pelo Python (treino/avaliação) e pelo Kotlin (app). No app: llama.cpp via JNI gera (1) um plano JSON restrito por gramática GBNF, (2) o app executa as ferramentas e produz `Fato`s estruturados reaproveitando `RespondedorDePerguntas`, (3) o LLM escreve a resposta, que só aparece frase a frase depois de passar no verificador; qualquer falha volta ao sistema atual. No PC: dados sintéticos (fatos reais exportados do Kotlin + respostas de um modelo professor filtradas pelo verificador), LoRA com Unsloth, GGUF, avaliação com o mesmo llama.cpp e a mesma gramática do celular.

**Tech Stack:** Kotlin 2.2.10, Compose, WorkManager 2.9.0, NDK 27.1.12297006 + CMake 3.22.1, llama.cpp (tag fixada), Python 3.12, PyTorch CUDA, Unsloth/TRL/PEFT, Qwen3 (Apache 2.0).

**Spec:** `docs/superpowers/specs/2026-11-27-ia-llm-local-design.md`

## Global Constraints

- Prazo: rodando de verdade no S20 FE até 2026-12-04. Corte se apertar: só o modo mais inteligente que funcionar no celular.
- Todo número mostrado vem do cálculo do app; o modelo nunca fornece valores.
- Nada sai do aparelho além do download do modelo; o texto das perguntas nunca é gravado (conversa só em memória).
- O modelo não vai no APK. ABI só `arm64-v8a`; `minSdk` 26.
- Quem não ativar, celular sem suporte, ou qualquer erro → comportamento atual das Perguntas, sem mensagem de erro.
- Dinheiro em centavos `Long`, formatado por `formatarReaisInteiros`; data do hábito `dataCompra ?: data`; sem pagamento de fatura nem categoria Fatura; orçamento via `limiteDoMes`.
- UI: `FolhaApp`/`CabecalhoDeFolha`/componentes de `PaginaComponentes.kt` e `BotoesDePagina.kt`, nunca `AlertDialog`; preto/violeta; mesmo layout nos 3 temas; pouco texto. Protótipo HTML aprovado antes da Task 13.
- Modos: Rápida = Qwen3-0.6B, Média = Qwen3-1.7B, Inteligente = Qwen3-4B; só vai para o app o modo aprovado na avaliação e no S20 FE; se o 4B não couber, "Inteligente" = 1.7B.
- Commits a partir de `1.0.160` (`1.0.159` = este plano), um número por commit, no formato `<versão> - descrição` com os trailers `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` e `Claude-Session: https://claude.ai/code/session_01VdZfokKtTCAXAJwPLKnC5Q`. Nunca push sem o usuário pedir. Criar release no GitHub (Task 14) só com o OK do usuário.
- Toda task atualiza o vault (`C:/Users/Breno/Documents/Obsidian/Projeto MotApp/`): Histórico de Versões ("Não testado em aparelho" se não rodou no S20 FE), Decisões (`2026-MM-DD — decisão — motivo`), Backlog, e Padrões de UI/Arquitetura/Release e CI quando afetados. Nunca registrar segredos nem o PIN do celular.
- S20 FE (serial RQ8R6003G0J) é só de teste; adb em `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`.
- Arquivos grandes (modelos, binários, datasets gerados) ficam fora do git: `ia/llm/modelos/`, `ia/llm/bin/`, `ia/llm/dados/gerados/` no `.gitignore`.

## Review Focus

1. Nome de categoria com aspas, barra, acento ou emoji (`Pet "Rex"`, `Bar\Café`, `🍔 Lanche`) → a gramática continua válida e o plano cita o nome exato. Teste na Task 3.
2. Modelo apagado/corrompido depois de ativado (limpeza de armazenamento, troca de modo no meio) → Perguntas respondem pelo sistema atual e a sheet oferece baixar de novo. Teste na Task 11.
3. Pessoa sai do Assistente ou manda outra pergunta durante a geração → geração cancelada, nada de texto órfão, motor liberado sem crash. Teste na Task 10.
4. Rede cai ou falta espaço no meio do download → retoma de onde parou (Range) ou falha limpa; arquivo com SHA-256 errado é apagado e nunca carregado. Teste na Task 11.
5. Mês sem dados, mês futuro ("dezembro de 2027") ou usuário sem lançamentos → fatos "não encontrei", resposta sem número inventado. Teste na Task 10.

---

## Trilha PC (treino e avaliação)

### Task 1: Ambiente de treino e llama.cpp no PC

**Files:**
- Create: `ia/llm/requirements.txt`, `ia/llm/VERSAO_LLAMA_CPP`, `ia/llm/preparar_ambiente.py`, `ia/llm/README.md`
- Modify: `.gitignore` (linhas de `ia/llm/modelos/`, `ia/llm/bin/`, `ia/llm/dados/gerados/`, `ia/llm/.venv/`)

**Interfaces:**
- Produces: `ia/llm/bin/llama-server.exe`, `llama-quantize.exe`, `llama-gguf-split.exe` (release CUDA do Windows da tag em `VERSAO_LLAMA_CPP`); `ia/llm/bin/llama.cpp/convert_hf_to_gguf.py` (fonte da mesma tag); venv `ia/llm/.venv`; pesos base em `ia/llm/modelos/base/{Qwen3-0.6B,Qwen3-1.7B,Qwen3-4B}` (HF) e professor `ia/llm/modelos/professor/Qwen3-8B-Q4_K_M.gguf`.

- [ ] **Step 1:** Escrever `VERSAO_LLAMA_CPP` com a tag de release mais recente do llama.cpp neste dia (ex.: `b7xxx`). Ela é a única fonte da versão: a Task 9 lê o mesmo arquivo no CMake.
- [ ] **Step 2:** `requirements.txt` com torch (CUDA 12.x), unsloth, trl, peft, transformers, datasets, huggingface_hub, requests, pytest. Fallback se o Unsloth não instalar no Windows: transformers + peft + bitsandbytes, com o mesmo script de treino (anotar no README qual foi usado).
- [ ] **Step 3:** `preparar_ambiente.py` baixa binários e fonte da tag, os três modelos base e o professor GGUF, verificando que já existem antes de baixar de novo.
- [ ] **Step 4: Verificar**
  - Rodar: `ia/llm/.venv/Scripts/python -c "import torch;print(torch.cuda.is_available(), torch.cuda.get_device_name(0))"`. Esperado: `True NVIDIA GeForce RTX 4050 Laptop GPU`.
  - Rodar: `ia/llm/bin/llama-server.exe -m ia/llm/modelos/professor/Qwen3-8B-Q4_K_M.gguf -ngl 99 --port 8081` e depois um POST em `/completion` com "Olá". Esperado: texto em português.
- [ ] **Step 5:** Commit com o próximo número `1.0.N` e vault (Ambiente de Desenvolvimento: GPU, venv, onde ficam os modelos).

### Task 2: Contrato compartilhado (Python)

**Files:**
- Create: `app/src/main/assets/ia/llm/contrato.json`, `ia/llm/contrato.py`, `ia/llm/test_contrato.py`
- Create (golden, gerados pelo teste com `--atualizar-golden`): `app/src/test/resources/ia/llm/prompt_plano.txt`, `prompt_resposta.txt`, `gramatica_plano.gbnf`

**Interfaces:**
- `contrato.json`:
  - `ferramentas`: lista de `{nome, intencao, usa_mes, usa_categoria, categoria_obrigatoria, descricao}`.
  - Os nomes e as intenções são exatamente: `ranking`→`RANKING_CATEGORIAS`, `gasto_categoria`→`GASTO_EM_CATEGORIA` (categoria obrigatória), `total_mes`→`TOTAL_DO_MES`, `comparar_mes_anterior`→`COMPARAR_MES_ANTERIOR`, `maior_gasto`→`MAIOR_GASTO` (categoria opcional), `orcamento_restante`→`ORCAMENTO_RESTANTE` (categoria opcional), `assinaturas`→`ASSINATURAS` (sem mês), `poupanca`→`POUPANCA`, `receitas`→`RECEITAS`, `media_mensal`→`MEDIA_MENSAL` (categoria opcional, sem mês), `atipicos`→`ATIPICOS`, `como_economizar`→`COMO_ECONOMIZAR` (sem mês).
  - Também contém `sistema_plano`, `sistema_resposta` e `texto_recusa` = "Só consigo falar sobre os seus gastos e receitas."
- Formato de prompt: ChatML cru (`<|im_start|>system\n…<|im_end|>\n<|im_start|>user\n…<|im_end|>\n<|im_start|>assistant\n`), sem `apply_chat_template` e sem bloco `<think>`.
- `montar_prompt_plano(pergunta: str, mes_atual: str "YYYY-MM", categorias: list[str], historico: list[tuple[str, str]]) -> str`.
  - O `historico` tem até as 2 últimas (pergunta, plano JSON), que entram como turnos user/assistant anteriores.
  - O user traz: `Mês atual: novembro de 2026 (2026-11)`, depois `Categorias: A, B, C`, depois `Pergunta: …`.
- `montar_prompt_resposta(pergunta: str, fatos: list[dict]) -> str`. O user traz `Pergunta: …` e depois `Fatos:` com as linhas `1. <frase>`.
- Plano: `{"chamadas":[{"f":"<nome>","mes":"atual"|"anterior"|"YYYY-MM","categoria":"<nome exato>"}]}`.
  - `mes` e `categoria` só aparecem nas ferramentas que os usam.
  - Lista vazia = fora do escopo.
  - Máximo de 4 chamadas.
- `gramatica_plano(mes_atual: str, categorias: list[str]) -> str`: GBNF que só aceita esse formato. Os meses possíveis são `atual`, `anterior` e os 24 meses até `mes_atual`. As categorias vêm como literais com `"` e `\` escapados.
- Fato (dict/JSON):
  ```json
  {"ferramenta":"ranking","periodo":"novembro"|null,"frase":"…","itens":[{"rotulo":"Alimentação"|null,"tipo":"reais"|"porcento"|"numero","valor":"1.200"}]}
  ```

- [ ] **Step 1: Testes que falham**
  - `test_prompt_plano_golden`: igual ao golden para a pergunta "Onde gastei mais este mês e no anterior?", `mes_atual="2026-11"`, categorias `["Alimentação","Lazer","Pet \"Rex\""]` e histórico de 1 par.
  - `test_prompt_resposta_golden`: igual ao golden para 2 fatos fixos.
  - `test_gramatica_golden`: igual ao golden para as mesmas categorias.
  - `test_gramatica_aceita_e_recusa`: com `llama-server` e a gramática, gerar a partir do Qwen3-0.6B base. A saída deve fazer `json.loads` e todo `f` deve estar no contrato. Marcar como `@pytest.mark.llama` (pulado se o binário não existir).
- [ ] **Step 2:** `pytest ia/llm/test_contrato.py`. Esperado: FAIL (módulo não existe).
- [ ] **Step 3:** Implementar `contrato.py`. Os textos de sistema ficam em português e curtos:
  - plano: "responda só com o JSON das ferramentas; lista vazia se não for sobre as finanças da pessoa";
  - resposta: "use só os fatos, não invente números, cite mês e categoria junto de cada valor, no máximo 4 frases curtas e 1 conselho se houver fato de economia".
- [ ] **Step 4:** Rodar `pytest ia/llm/test_contrato.py --atualizar-golden` uma vez, revisar os golden à mão e rodar de novo sem a flag. Esperado: PASS.
- [ ] **Step 5:** Commit com o próximo número `1.0.N` e vault.

### Task 3: Contrato no Kotlin

**Files:**
- Create: `app/src/main/java/com/example/financacelular/ia/llm/ContratoLlm.kt`, `app/src/test/java/com/example/financacelular/ia/llm/ContratoLlmTest.kt`

**Interfaces:**
- Consumes: `contrato.json` e golden da Task 2.
- Produces:
  ```kotlin
  data class Chamada(val ferramenta: String, val mes: YearMonth?, val categoriaId: Long?)
  class ContratoLlm(json: String) {
      val textoRecusa: String
      fun promptPlano(pergunta: String, mesAtual: YearMonth, categorias: List<String>, historico: List<Pair<String, String>>): String
      fun promptResposta(pergunta: String, fatos: List<Fato>): String
      fun gramaticaPlano(mesAtual: YearMonth, categorias: List<String>): String
      /** null = JSON inválido; lista vazia = fora do escopo. Descarta chamada com categoria desconhecida ou obrigatória ausente. */
      fun lerPlano(json: String, mesAtual: YearMonth, categorias: Map<Long, String>): List<Chamada>?
      fun intencao(ferramenta: String): Intencao
      companion object { fun doAsset(context: Context): ContratoLlm }
  }
  ```
  (`Fato` vem da Task 4; a Task 3 pode declará-lo no arquivo da Task 4 primeiro, com os campos de lá.)

- [ ] **Step 1: Testes**
  - `promptPlano`, `promptResposta` e `gramaticaPlano` são idênticos aos golden (`GoldenIa.recurso("llm/…")`).
  - `lerPlano` resolve `"anterior"`→2026-10 e `"2026-08"`→2026-08.
  - `lerPlano` resolve `"Pet \"Rex\""`→id da categoria.
  - Categoria inexistente descarta só aquela chamada.
  - `{"chamadas":[]}` → `emptyList()`; `"lixo"` → `null`.
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests "*ContratoLlmTest*"`. Esperado: FAIL.
- [ ] **Step 3:** Implementar com `org.json`.
- [ ] **Step 4:** Rodar de novo. Esperado: PASS.
- [ ] **Step 5:** Commit com o próximo número `1.0.N` e vault.

### Task 4: Fatos estruturados no Respondedor + exportação para treino

**Files:**
- Create: `app/src/main/java/com/example/financacelular/ia/llm/Fato.kt`, `app/src/test/java/com/example/financacelular/ia/llm/FatosTest.kt`, `app/src/test/java/com/example/financacelular/ia/llm/ExportarFatosParaTreino.kt`
- Modify: `app/src/main/java/com/example/financacelular/ia/RespondedorDePerguntas.kt` (as 12 funções privadas passam a devolver `Fato`; `responder` devolve `.frase`)

**Interfaces:**
- Produces:
  ```kotlin
  enum class TipoValor { REAIS, PORCENTO, NUMERO }
  data class ItemDoFato(val rotulo: String?, val tipo: TipoValor, val valor: String)
  data class Fato(val ferramenta: Intencao, val periodo: String?, val frase: String, val itens: List<ItemDoFato>) { fun paraJson(): JSONObject }
  // em RespondedorDePerguntas:
  fun fato(p: Pergunta, transacoes: List<Transacao>, categorias: List<Categoria>, recorrentes: List<DespesaRecorrente>,
           orcamentos: List<Orcamento>, recomendacoes: List<Recomendacao>, atipicos: List<Atipico>, hoje: LocalDate): Fato
  ```
- Regras dos campos:
  - `periodo` = o mesmo rótulo de mês usado na frase (`rotulo(mes, hoje)`), ou `null` em assinaturas, média e economizar.
  - `valor` = os dígitos exatamente como estão na frase (`"1.200"`, `"35"`, `"12"`), sem `R$` nem `%`.
  - `rotulo` = o nome da categoria, assinatura ou descrição a que o número pertence, ou `null` quando o número é do mês todo.
  - Dias ("no dia 12"), contagens ("3 assinaturas") e "últimos 6 meses" também viram itens `NUMERO`.

- [ ] **Step 1: Testes**
  - Os testes existentes de `RespondedorDePerguntasTest` continuam passando sem mudança: o texto é idêntico.
  - Novo `FatosTest`, um caso por intenção, por exemplo o ranking:
    ```kotlin
    assertEquals(listOf(ItemDoFato("Alimentação", REAIS, "1.200"), ItemDoFato("Alimentação", PORCENTO, "35"),
        ItemDoFato("Lazer", REAIS, "500"), ItemDoFato("Mercado", REAIS, "300")), f.itens)
    ```
  - Toda frase satisfaz a invariante: cada número dela corresponde a um item.
- [ ] **Step 2:** Rodar os dois testes. Esperado: `FatosTest` FAIL.
- [ ] **Step 3:** Implementar `Fato`, `fato(...)` e a refatoração. O cálculo não muda, só se anota o que já é calculado.
- [ ] **Step 4:** Rodar `./gradlew :app:testDebugUnitTest`. Esperado: todos PASS (eram 288 testes, mais os novos).
- [ ] **Step 5:** `ExportarFatosParaTreino`:
  - É um teste que só roda com `-Dexportar.fatos=<saida>`.
  - Gera com semente fixa 300 usuários sintéticos com 3–8 categorias (as padrão do app mais nomes inventados) e 6 meses de lançamentos.
  - Para cada usuário, grava em JSONL os fatos de chamadas sorteadas de todas as ferramentas e meses.
  - Rodar com `./gradlew :app:testDebugUnitTest --tests "*ExportarFatosParaTreino*" -Dexportar.fatos=../ia/llm/dados/gerados/fatos.jsonl`. Esperado: arquivo com ≥ 5.000 linhas.
- [ ] **Step 6:** Commit com o próximo número `1.0.N` e vault.

### Task 5: Verificador (Kotlin e Python, mesmos casos)

**Files:**
- Create: `app/src/main/java/com/example/financacelular/ia/llm/Verificador.kt`, `app/src/test/java/com/example/financacelular/ia/llm/VerificadorTest.kt`, `ia/llm/verificador.py`, `ia/llm/test_verificador.py`, `app/src/test/resources/ia/llm/casos_verificador.jsonl`

**Interfaces:**
- ```kotlin
  object Verificador {
      /** Divide em frases sem quebrar "R$ 1.200". */
      fun frases(texto: String): List<String>
      /** periodoVigente: rótulo de mês em vigor antes desta frase. Devolve (aprovada, novo periodoVigente). */
      fun verificarFrase(frase: String, fatos: List<Fato>, periodoVigente: String?): Pair<Boolean, String?>
      fun fatosCitados(frasesAprovadas: List<String>, fatos: List<Fato>): Set<Int>
  }
  ```
  Python: `frases(texto)`, `verificar_frase(frase, fatos, periodo_vigente)`, `fatos_citados(frases, fatos)`, com a mesma semântica.
- Regra (versão refinada da spec, que deve ser registrada nas Decisões):
  1. O período vigente passa a ser o primeiro rótulo de mês dos fatos citado na frase; se nenhum for citado, continua o anterior.
  2. Tira os anos que fazem parte de um rótulo ("outubro de 2025").
  3. Cada número da frase (`R$ x` = REAIS, `x%` = PORCENTO, outro inteiro = NUMERO) precisa casar com algum item que tenha:
     - o mesmo tipo e valor;
     - fato com `periodo == null` ou igual ao vigente;
     - `rotulo == null` ou rótulo presente na frase (comparação sem acento e sem maiúsculas).
  4. Frase sem número é aprovada.

- [ ] **Step 1:** Escrever ≥ 25 casos em `casos_verificador.jsonl` (`{frase, fatos, periodo_vigente, aprovada, periodo_depois}`):
  - valor certo na categoria errada → reprovada;
  - mês trocado → reprovada;
  - frase seguinte sem mês herda o período → aprovada;
  - "dia 12" certo → aprovada;
  - número inventado → reprovada;
  - "R$ 1.200,00" → tratar como 1.200;
  - porcentagem arredondada diferente → reprovada.
- [ ] **Step 2:** `VerificadorTest` e `test_verificador.py` leem os mesmos casos. Rodar os dois. Esperado: FAIL.
- [ ] **Step 3:** Implementar os dois.
- [ ] **Step 4:** Rodar os dois. Esperado: PASS.
- [ ] **Step 5:** Commit com o próximo número `1.0.N`, vault e Decisões (regra do período vigente).

### Task 6: Dados de treino e perguntas de teste

**Files:**
- Create: `ia/llm/gerar_planos.py`, `ia/llm/gerar_respostas.py`, `ia/llm/test_gerar_dados.py`, `ia/llm/teste/perguntas.jsonl` (escritas à mão)

**Interfaces:**
- Consumes: `contrato.py`, `verificador.py`, `dados/gerados/fatos.jsonl`, professor via `llama-server` (porta 8081).
- Produces:
  - `dados/gerados/plano_treino.jsonl` e `plano_valid.jsonl`, com linhas `{"prompt":…, "saida":…}` já no formato da Task 2;
  - `dados/gerados/resposta_treino.jsonl` e `resposta_valid.jsonl`;
  - `teste/perguntas.jsonl`, com linhas `{"pergunta", "mes_atual", "categorias", "historico", "plano_esperado"}`.

- [ ] **Step 1: Teste do gerador de planos**
  - Toda saída passa em `lerPlano` (equivalente Python) e na gramática.
  - Há ≥ 30% de perguntas compostas (2–4 chamadas), ≥ 10% fora do escopo e ≥ 10% com histórico ("e no mês passado?").
  - Nenhuma pergunta de `teste/perguntas.jsonl` aparece no treino (comparação normalizada).
- [ ] **Step 2:** Rodar `pytest ia/llm/test_gerar_dados.py`. Esperado: FAIL.
- [ ] **Step 3:** Implementar `gerar_planos.py`:
  - ≥ 15 formas escritas à mão por ferramenta, com espaços para mês e categoria, e conectores ("e", "também", "quero saber", "?").
  - Categorias sorteadas como na Task 4.
  - O professor parafraseia 30% das perguntas, com a instrução de manter todos os pedidos.
  - Alvo: ~12.000 exemplos de treino e 1.000 de validação.
- [ ] **Step 4:** Implementar `gerar_respostas.py`:
  - Junta 1–4 fatos reais em uma pergunta composta.
  - O professor escreve a resposta seguindo `sistema_resposta`.
  - Só ficam as respostas cujas frases passam todas no verificador.
  - Alvo: ~6.000 de treino e 500 de validação. Registrar a taxa de aprovação do professor.
- [ ] **Step 5:** Escrever à mão as 60 perguntas de teste, como uma pessoa digitaria no celular, com erros de digitação, gírias e frases compostas como o exemplo do usuário. Distribuição: 25 compostas, 15 simples, 10 de continuação e 10 fora do escopo.
- [ ] **Step 6:** Rodar o teste. Esperado: PASS. Commit com o próximo número `1.0.N` (sem os dados gerados) e vault.

### Task 7: Avaliação reproduzível

**Files:**
- Create: `ia/llm/avaliar_llm.py`, `ia/llm/test_avaliar_llm.py`

**Interfaces:**
- `avaliar_llm.py --modelo <gguf> --nome <rotulo>`:
  - sobe o `llama-server` com `-ngl 99 -c 2048`, decodificação gulosa (`temperature 0`) e a gramática por pergunta;
  - grava `ia/resultados/llm/<rotulo>.json`.
- Métricas:
  - `plano_exato`: lista de chamadas igual à esperada, sem importar a ordem;
  - `plano_parcial`: F1 das chamadas;
  - `recusa_correta`: nas perguntas fora do escopo;
  - `fidelidade`: % de respostas da validação com todas as frases aprovadas;
  - `tokens_por_s_pc`.
- `--sistema-atual`: avalia o classificador de hoje nas mesmas perguntas e grava `atual.json`.
  - Usa os pesos de `app/src/main/assets/ia/modelo_ia.json` com `treinar_intencoes.vetorizar` + `prever`.
  - Conta como acerto a intenção certa quando o plano esperado tem 1 chamada. Nas compostas, conta a cobertura de 1/n.
- `--tabela`: junta todos os JSON em `ia/resultados/llm.md`.

- [ ] **Step 1:** Escrever o teste das métricas com planos de exemplo, sem llama: plano em outra ordem → exato; 2 de 3 chamadas → F1 0,8.
- [ ] **Step 2:** Rodar. Esperado: FAIL. Implementar. Rodar. Esperado: PASS.
- [ ] **Step 3:** Rodar a avaliação no `Qwen3-0.6B` base (convertido para GGUF Q4_K_M) e no sistema atual. Esperado: os dois JSON gerados e a tabela com 2 colunas.
- [ ] **Step 4:** Commit com o próximo número `1.0.N` e vault.

### Task 8: Treino LoRA dos três modos

**Files:**
- Create: `ia/llm/treinar_lora.py`, `ia/llm/exportar_gguf.py`
- Modify: `ia/README.md` (como reproduzir), `ia/resultados/llm.md`

**Interfaces:**
- `treinar_lora.py --base {0.6B|1.7B|4B} --saida ia/llm/modelos/lora/<modo>`:
  - um único modelo, treinado com os dois conjuntos (plano + resposta) misturados;
  - a perda só conta na `saida`;
  - QLoRA de 4 bits, r=16, alpha=32, 2 épocas, contexto de 1.024;
  - lote ajustado à VRAM de 6 GB (4B: lote 1, acumulação 16, gradient checkpointing);
  - guarda o melhor checkpoint pela perda de validação.
- `exportar_gguf.py --lora <dir> --saida ia/llm/modelos/gguf/motapp-<modo>-q4km.gguf`:
  - faz o merge e converte com `convert_hf_to_gguf.py`;
  - quantiza com `llama-quantize Q4_K_M`;
  - divide com `llama-gguf-split --split-max-size 1900M` quando passar de 1,9 GB;
  - imprime o SHA-256 e o tamanho de cada arquivo.

- [ ] **Step 1:** Treinar a Rápida, exportar e rodar `avaliar_llm.py`.
  - Critério de aprovação: `plano_exato` ≥ 0,80 nas compostas do teste, `recusa_correta` ≥ 0,90, `fidelidade` ≥ 0,90, e melhor que o sistema atual nas compostas.
  - Se não passar, ajustar os dados ou as épocas, uma rodada por vez, registrando cada uma em `llm.md`.
- [ ] **Step 2:** Mesmo processo para a Média (1.7B) e a Inteligente (4B). Rodar em segundo plano, porque o PC fica ligado ~20 h/dia. Se o 4B não couber na placa, registrar e seguir sem ele.
- [ ] **Step 3:** `avaliar_llm.py --tabela`. Esperado: `llm.md` com as colunas atual, base, Rápida, Média e Inteligente.
- [ ] **Step 4:** Commits `1.0.N` por modo (sem os pesos) e vault, incluindo a tabela de resultados.

## Trilha App

### Task 9: llama.cpp no app + medição no S20 FE

**Files:**
- Create: `app/src/main/cpp/CMakeLists.txt`, `app/src/main/cpp/motor_llm.cpp`, `app/src/main/java/com/example/financacelular/ia/llm/MotorLlm.kt`, `app/src/androidTest/java/com/example/financacelular/MotorLlmTest.kt`
- Modify: `app/build.gradle.kts` (`ndkVersion = "27.1.12297006"`, `externalNativeBuild { cmake { path = "src/main/cpp/CMakeLists.txt"; version = "3.22.1" } }`, flags `-DGGML_OPENMP=OFF -DLLAMA_CURL=OFF -DBUILD_SHARED_LIBS=OFF`)

**Interfaces:**
- CMake: `FetchContent` do llama.cpp com `GIT_TAG` lido de `ia/llm/VERSAO_LLAMA_CPP`, e a biblioteca `motor_llm`.
- ```kotlin
  interface MotorDeTexto {
      /** Bloqueante; chamar fora da Main. aoPedaco recebe só UTF-8 completo. Para quando a coroutine for cancelada. */
      suspend fun gerar(prompt: String, gramatica: String?, maxTokens: Int, aoPedaco: (String) -> Unit): String
      fun liberar()
  }
  class MotorLlm private constructor(handle: Long) : MotorDeTexto {
      companion object { fun carregar(caminho: String, threads: Int = 4, contexto: Int = 2048): MotorLlm? }
  }
  ```
  Decodificação gulosa. O parâmetro `gramatica` é GBNF (sampler de gramática do llama.cpp). O cancelamento é uma flag checada a cada token.

- [ ] **Step 1:** `MotorLlmTest` (instrumentado) carrega o GGUF de `/data/local/tmp/motapp-teste.gguf`, enviado com `adb push`.
  - Gera com a gramática da Task 3 e o resultado faz parse em `lerPlano`.
  - Cancela no meio sem crash.
  - Gera de novo depois de cancelar.
- [ ] **Step 2:** Rodar. Esperado: FAIL (classe não existe).
- [ ] **Step 3:** Implementar o JNI e o Kotlin.
- [ ] **Step 4:** `./gradlew :app:connectedDebugAndroidTest --tests "*MotorLlmTest*"` no S20 FE. Esperado: PASS.
- [ ] **Step 5: Medição**, com os GGUF base da Rápida, Média e Inteligente: tempo de carga, tokens/s do prompt, tokens/s da geração, PSS máximo (`dumpsys meminfo`) e temperatura depois de 5 perguntas seguidas. Gravar em `ia/resultados/llm_aparelho.md`.
  - Daí sai `ramMinimaBytes` por modo, que alimenta a Task 11, e quais modos são viáveis.
  - Se o 4B não couber ou passar de ~30 s por resposta típica, "Inteligente" vira o 1.7B (Decisões).
- [ ] **Step 6:** Conferir o tamanho do APK release, que deve crescer pouco (só o `.so`). Também confirmar que o build de CI compila o nativo, revisando se `build_apk.yml` precisa do NDK. Commit `1.0.N` e vault (Arquitetura, Release e CI).

### Task 10: Pipeline da resposta (plano → fatos → texto verificado)

**Files:**
- Create: `app/src/main/java/com/example/financacelular/ia/llm/AssistenteLlm.kt`, `app/src/test/java/com/example/financacelular/ia/llm/AssistenteLlmTest.kt`

**Interfaces:**
- Consumes: `MotorDeTexto` (Task 9), `ContratoLlm` (Task 3), `RespondedorDePerguntas.fato` (Task 4), `Verificador` (Task 5).
- Produces:
  ```kotlin
  data class DadosDaConversa(val transacoes: List<Transacao>, val categorias: List<Categoria>, val recorrentes: List<DespesaRecorrente>,
      val orcamentos: List<Orcamento>, val recomendacoes: List<Recomendacao>, val atipicos: List<Atipico>, val mesAtual: YearMonth, val hoje: LocalDate)
  sealed interface EventoResposta {
      data class Frase(val texto: String) : EventoResposta          // já verificada; a tela acrescenta
      data class Fim(val planoJson: String, val sugestoes: List<String>) : EventoResposta
      data object UsarSistemaAtual : EventoResposta                  // nada foi mostrado ainda
  }
  class AssistenteLlm(motor: MotorDeTexto, contrato: ContratoLlm, tempoLimiteMs: Long) {
      fun responder(pergunta: String, historico: List<Pair<String, String>>, dados: DadosDaConversa): Flow<EventoResposta>
  }
  ```
- Comportamento:
  - Plano `null` ou tempo esgotado antes da primeira frase → `UsarSistemaAtual`.
  - Plano vazio → `Frase(textoRecusa)` e depois `Fim`.
  - Cada frase reprovada interrompe a geração. Depois vêm, como `Frase`, as frases prontas dos fatos ainda não citados, na ordem do plano, e por fim `Fim`.
  - As categorias passadas ao modelo são as de despesa sem Fatura, igual ao `AssistenteViewModel.responder` de hoje.
  - As sugestões são as de `SUGESTOES_DEPOIS` da primeira chamada.

- [ ] **Step 1: Testes com `MotorFalso`** (respostas roteirizadas por prompt)
  - Pergunta composta do usuário → 3 chamadas → frases aprovadas na ordem.
  - Frase com valor trocado → corta e completa com as frases prontas.
  - JSON inválido → `UsarSistemaAtual`.
  - Plano vazio → recusa.
  - Cancelar o `collect` no meio → `motor.gerar` cancelado e nenhum evento depois (Review Focus 3).
  - Mês futuro ou sem lançamentos → a frase "Não encontrei gastos em dezembro de 2027." passa e não há número inventado (Review Focus 5).
  - Tempo esgotado depois de 1 frase → completa com as frases prontas.
- [ ] **Step 2:** Rodar. Esperado: FAIL. Implementar. Rodar. Esperado: PASS.
- [ ] **Step 3:** Commit e vault.

### Task 11: Catálogo, download e ativação (sem UI)

**Files:**
- Create: `app/src/main/java/com/example/financacelular/ia/llm/ModelosIa.kt`, `GerenciadorDeModelo.kt`, `BaixarModeloWorker.kt`, `app/src/test/java/com/example/financacelular/ia/llm/GerenciadorDeModeloTest.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`FOREGROUND_SERVICE_DATA_SYNC`, `androidx.work.impl.foreground.SystemForegroundService` com `foregroundServiceType="dataSync"` via `tools:node="merge"`)

**Interfaces:**
- ```kotlin
  enum class ModoIa(val titulo: String) { RAPIDA("Rápida"), MEDIA("Média"), INTELIGENTE("Inteligente") }
  data class ArquivoDeModelo(val nome: String, val url: String, val sha256: String, val bytes: Long)
  data class ModeloDisponivel(val modo: ModoIa, val arquivos: List<ArquivoDeModelo>, val ramMinimaBytes: Long, val tempoLimiteMs: Long)
  object ModelosIa { val disponiveis: List<ModeloDisponivel> /* vazio até a Task 14 */ }
  sealed interface EstadoModelo {
      data object Desativado : EstadoModelo
      data class Baixando(val modo: ModoIa, val progresso: Float) : EstadoModelo
      data class Pronto(val modo: ModoIa) : EstadoModelo
      data class Falhou(val modo: ModoIa) : EstadoModelo
  }
  object GerenciadorDeModelo {
      val estado: StateFlow<EstadoModelo>
      fun modosSuportados(context: Context): List<ModeloDisponivel>  // arm64 && totalMem >= ramMinimaBytes
      fun ativar(context: Context, modo: ModoIa)                      // agenda o worker (único, REPLACE); apaga o modo anterior ao concluir
      fun cancelar(context: Context)
      fun desativar(context: Context)                                  // apaga arquivos e preferência
      fun caminhoPronto(context: Context): String?                     // primeiro shard, só se todos existem e o hash conferiu
      internal fun conferir(arquivo: File, esperado: ArquivoDeModelo): Boolean
  }
  ```
- Detalhes:
  - Os arquivos ficam em `filesDir/ia/llm/`; o download é gravado em `<nome>.part` e retomado com o cabeçalho `Range`.
  - O SHA-256 é conferido ao terminar. Se der errado, o arquivo é apagado e o estado vira `Falhou`.
  - O worker roda em primeiro plano com uma notificação de progresso e exige rede (`NetworkType.CONNECTED`).
  - As preferências (`SharedPreferences "ia_avancada"`) guardam o modo e se o hash já foi conferido.
  - `caminhoPronto` volta a conferir só a existência e o tamanho dos arquivos (o hash completo só no download).

- [ ] **Step 1: Testes JVM** (lógica pura extraída em funções `internal` sobre `File` e um servidor HTTP falso com `com.sun.net.httpserver`)
  - Retoma com `Range` depois de cortar o download no meio.
  - Hash errado apaga o arquivo.
  - Tamanho diferente faz `caminhoPronto` = null (Review Focus 2).
  - Shard faltando faz `caminhoPronto` = null.
  - `modosSuportados` filtra por RAM.
- [ ] **Step 2:** Rodar. Esperado: FAIL. Implementar. Rodar. Esperado: PASS.
- [ ] **Step 3:** Commit e vault (Arquitetura e Segredos e Credenciais: nenhum segredo, as URLs são públicas).

### Task 12: Protótipo da tela (porta de aprovação)

**Files:**
- Create: protótipo HTML no scratchpad, publicado como Artifact (fora do repo)

- [ ] **Step 1:** Ler `Projeto MotApp/03 - Arquitetura/Padrões de UI.md` e as telas `AssistenteScreen.kt`, `AtualizacaoSheet.kt` e `ConfiguracoesScreen.kt`.
- [ ] **Step 2:** Montar o protótipo clicável nos 3 temas com:
  - (a) a entrada "IA avançada" nas Perguntas e em Configurações;
  - (b) a sheet de ativação: os 3 modos com tamanho e tempo típico, aviso de Wi-Fi, botão de baixar, progresso, cancelar, trocar de modo e desativar;
  - (c) a conversa com "pensando…" e o texto entrando frase a frase;
  - (d) o estado de quando o celular não suporta (a entrada não aparece).
- [ ] **Step 3:** Mostrar ao usuário e **esperar aprovação**. Registrar os ajustes pedidos no Backlog. Sem aprovação, a Task 13 não começa.

### Task 13: UI da IA avançada

**Files:**
- Create: `app/src/main/java/com/example/financacelular/ui/IaAvancadaSheet.kt`
- Modify: `app/src/main/java/com/example/financacelular/ui/AssistenteScreen.kt`, `AssistenteViewModel.kt`, `ConfiguracoesScreen.kt`

**Interfaces:**
- Consumes: `GerenciadorDeModelo`, `ModelosIa`, `AssistenteLlm`, `MotorLlm.carregar`.
- Produces:
  - Em `AssistenteViewModel`, `perguntar` passa a usar `AssistenteLlm` quando `caminhoPronto != null`. Ele acrescenta a mensagem de resposta e vai anexando `Frase`s nela.
  - Em `UsarSistemaAtual` ou exceção, usa o `responder` atual sem mudança.
  - O histórico (pergunta, plano) fica em memória, só as 2 últimas trocas.
  - O motor carrega na primeira pergunta e é liberado em `onCleared` e `onTrimMemory`.
  - `pensando` continua sendo o indicador e fica true até o `Fim`.

- [ ] **Step 1:** Teste JVM do ViewModel com `MotorFalso`: as frases são anexadas à mesma mensagem, o fallback usa o sistema atual e uma nova pergunta durante a geração é ignorada (regra atual de `_pensando`).
- [ ] **Step 2:** Rodar. Esperado: FAIL. Implementar a tela conforme o protótipo aprovado. Rodar. Esperado: PASS.
- [ ] **Step 3:** Build release, instalar no S20 FE e conferir nos 3 temas: com o modelo empurrado à mão para `filesDir`, a pergunta composta do usuário responde com as 3 partes.
- [ ] **Step 4:** Commit e vault (Padrões de UI: a nova sheet e o indicador).

### Task 14: Publicar os modelos aprovados

- [ ] **Step 1:** **Pedir OK ao usuário** para criar a release `modelo-ia-v1` em `BrenaoRM/MotApp`, que é pública. A release não pode ser marcada como latest, para não confundir a atualização do app, que lê `releases/latest`.
- [ ] **Step 2:** `gh release create modelo-ia-v1 --prerelease --title "Modelos da IA avançada" <arquivos gguf>`. Conferir que a `releases/latest` continua apontando para o app.
- [ ] **Step 3:** Preencher `ModelosIa.disponiveis` com as URLs `https://github.com/BrenaoRM/MotApp/releases/download/modelo-ia-v1/<nome>` e com o SHA-256, os bytes, a `ramMinimaBytes` e o `tempoLimiteMs` medidos na Task 9. Só entram os modos aprovados. Teste: o JSON das URLs aponta para arquivos que existem (`curl -I` com status 302/200), rodado à mão.
- [ ] **Step 4:** Commit e vault (Release e CI: onde ficam os modelos e como publicar uma versão nova).

### Task 15: Teste fim a fim no S20 FE e revisão

- [ ] **Step 1:** Instalar o build release e ativar cada modo aprovado pelo app: o download real, com progresso, saindo da tela e voltando.
- [ ] **Step 2:** Rodar as 60 perguntas de teste pela tela (amostra de 15 por modo). Anotar tempo até a primeira frase, tempo total e erros em `ia/resultados/llm_aparelho.md`.
- [ ] **Step 3:** Testar o Review Focus no aparelho:
  - modo avião depois de ativado;
  - apagar o arquivo pelo adb (volta ao sistema atual);
  - sair durante a geração;
  - derrubar o Wi-Fi no meio do download.
- [ ] **Step 4:** Revisão final do branch (opus) e correções.
- [ ] **Step 5:** Atualizar o vault: Histórico, Backlog, Decisões e Arquitetura. Perguntar ao usuário sobre o merge e o push (release `1.21`).
