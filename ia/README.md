# IA financeira: como reproduzir os resultados

Scripts Python que geram os modelos do app, os arquivos de teste (golden) e as tabelas de [`resultados/`](resultados/README.md). Todos rodam a partir da pasta `ia/`. Depois do download dos modelos de embeddings, nada aqui usa a rede. O app lê só os assets gerados.

## 1. Ambiente Python

A versão do Python não está fixada no repositório. Os números dos relatórios saíram de um venv em `ia/.cache/venv`, que está no `.gitignore`.

```powershell
cd ia
python -m venv .cache\venv
.cache\venv\Scripts\activate
pip install torch==2.15.0 --index-url https://download.pytorch.org/whl/cpu
pip install -r requirements.txt
```

`requirements.txt` fixa as versões usadas (torch, transformers, onnx, onnxruntime, numpy, pandas, scikit-learn, pytest, matplotlib). O torch vem antes do arquivo porque a versão CPU sai do índice do PyTorch.

No Windows, rode os scripts com `PYTHONIOENCODING=utf-8` (usado nos relatórios para a saída com acentos no console).

## 2. Arquivos externos (não versionados)

| Arquivo | Para quê | Origem |
|---|---|---|
| `ia/.cache/dl/pt_BR.dic` | Radicais do corpus de poda do vocabulário (`exportar_modelo.py`) | Dicionário hunspell pt_BR do LibreOffice (`LibreOffice/dictionaries/pt_BR/pt_BR.dic`, segundo `task-1-report.md`) |
| `ia/.cache/dl/pt_br_50k.txt` | Frequências de palavras para a poda | FrequencyWords pt_br 50k (hermitdave) |
| Modelos `intfloat/multilingual-e5-small` e `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` | Embeddings (candidatos da T1) | Hugging Face, baixados na primeira execução de `exportar_modelo.py` para `ia/.cache/hf` |

O repositório não guarda URLs de download para os dois arquivos de `dl/`. O script não os baixa, então precisam estar no caminho acima antes de rodar o passo 1.

Os dados sintéticos vêm do próprio pacote: `ia/finai/` (gerador `data_gen`, copiado do FinIA sem alteração). Não há dataset externo.

## 3. Ordem de execução

| # | Comando (dentro de `ia/`) | Lê | Grava | Reproduz |
|---|---|---|---|---|
| 1 | `python exportar_modelo.py` | `.cache/dl/`, modelos do Hugging Face | `resultados/spike_modelo.json`; `app/src/main/assets/ia/modelo.onnx` e `tokenizador.json` (MiniLM int8) | Tabela de candidatos em `spike_modelo.md` |
| 2 | `python avaliar_categorias.py` | assets do passo 1 | `app/src/main/assets/ia/modelo_ia.json`; `resultados/categorias_personalizadas.md`, `.json` e `curva_aprendizado.png` | Tabelas de categorias personalizadas |
| 3 | `python treinar_intencoes.py` | `intencoes/treino.csv`, `intencoes/teste.csv`, assets | Seção `intencoes` do `modelo_ia.json`; `resultados/intencoes.md` | Tabelas de intenções |
| 4 | `python gerar_golden.py` | assets, `modelo_ia.json` | `app/src/test/resources/ia/golden_tokens.json`, `golden_vetores.json`, `golden_isolation.json` | Golden da T3 e da T7 |
| 5 | `python gerar_golden_classificador.py` | `avaliar_categorias.py` (`modelo_app`) | `app/src/test/resources/ia/golden_classificador.json` | Golden da T5 |
| 6 | `python avaliar_atipicos.py` | `modelo_ia.json` (`atipicos`), dados do FinIA | `app/build/ia/gastos_avaliacao.json`, `app/build/ia/pontuacoes.json`; `resultados/atipicos.md` | Tabela do detector de atípicos |

Notas:
- Os passos 1 a 5 têm de rodar nessa ordem depois de qualquer mudança no modelo de embeddings. Se o passo 1 mudar o modelo, rode 2, 3, 4 e 5 de novo.
- O passo 2 relê o `modelo_ia.json` e mantém a seção `intencoes`, mas não a regenera. Depois de trocar o modelo de embeddings, rode o passo 3.
- `treinar_prior.py` não grava nada. Só imprime as acurácias do prior (quem grava é o passo 2). Ele guarda os vetores em `ia/.cache/vetores_t2_<hash>.npz`, com o hash do `modelo.onnx`.
- O passo 1 sobrescreve só o JSON dos candidatos. O `spike_modelo.md` é texto escrito à mão, com a decisão.
- O passo 6 chama o Gradle sozinho: roda `gradlew.bat testDebugUnitTest --tests "*DetectorDeAtipicosTest.exportaPontuacoesParaAvaliacao" --rerun -Dia.exportar=true`. Por isso ele é só para Windows (usa `gradlew.bat`). Não é preciso rodar o Gradle à mão.

## 4. Testes Python

```powershell
python -m pytest test_exportacao.py -v   # 3 testes: normalização, poda, Viterbi
python -m pytest test_avaliacao.py -v    # 7 testes: usuários, prior, config, avaliação, léxico, memória, golden
python -m pytest test_intencoes.py -v    # 3 testes: formato, acurácia, fora do escopo (pulam se o modelo não existir)
```

Os três somam 13 testes. Esse é o número do `task-13-report.md`. Não rodei de novo nesta tarefa.

## 5. Testes Kotlin

- JVM (raiz do repositório): `./gradlew testDebugUnitTest` (no Windows, `gradlew.bat`). Último resultado registrado: 280 testes, 0 falhas (`task-10-report.md`).
- No aparelho (S20 FE, celular de teste): `./gradlew connectedDebugAndroidTest`. Para rodar só o classificador:
  `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.financacelular.ia.ClassificadorNoAparelhoTest`

## 6. Golden files

Os golden são a referência numérica entre Python e Kotlin. Quem gera e quem consome:

| Arquivo (`app/src/test/resources/ia/`) | Gerado por | Usado por |
|---|---|---|
| `golden_tokens.json` (200 casos) | `gerar_golden.py` | `TokenizadorTest` (JVM) |
| `golden_vetores.json` (50 vetores de 384 dimensões, lote 1) | `gerar_golden.py` | `VetorizadorJvmTest` (JVM); `VetorizadorOnnxTest` e `ClassificadorNoAparelhoTest` (aparelho) |
| `golden_isolation.json` (3 usuários) | `gerar_golden.py isolation` | `AtributosDeGastoTest`, `IsolationForestTest` |
| `golden_classificador.json` (n = 0 e n = 5, 7 consultas cada) | `gerar_golden_classificador.py` | `ClassificadorDeCategoriaTest.goldenClassificadorIgualAoPython`; `test_avaliacao.py::test_golden_classificador` |

`python gerar_golden.py` sem argumento grava `golden_tokens.json`, `golden_vetores.json` e `golden_isolation.json`. `python gerar_golden.py isolation` grava só o `golden_isolation.json`.

## 7. Diferenças em relação ao brief

| Brief | Repositório |
|---|---|
| Quatro comandos (`exportar_modelo`, `avaliar_categorias`, `avaliar_atipicos`) | Também existem `treinar_intencoes.py`, `gerar_golden.py` e `gerar_golden_classificador.py`, que são necessários para os golden e para a T13. |
| `avaliar_atipicos.py` como comando simples | Roda o Gradle por conta própria e só funciona no Windows (`gradlew.bat`). |
| `requirements.txt` com as versões do plano | `requirements.txt` fixa `onnxruntime==1.32.0`, mas o app (`app/build.gradle.kts`, `onnxruntime-android:1.31.0`) e os golden de T3 (`task-3-report.md`) usam 1.31.0. Para reproduzir os golden exatos, instale `onnxruntime==1.31.0` antes de rodar `gerar_golden.py`. Alinhar o `requirements.txt` com o app fica para decisão do controlador. |

## 8. Medições do aparelho e do APK

- Tamanho do APK de release: a medir (T11).
- Latência da vetorização, tempo de treino e memória no aparelho: a medir (T11).

Não há números oficiais disso no repositório. Os relatórios T3 e T5 têm medições provisórias no S20 FE, mas o plano manda que a tabela oficial saia da T11.
