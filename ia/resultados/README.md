# Resultados: índice das métricas

Índice dos arquivos desta pasta e dos números-chave de cada um. Como gerar cada arquivo está em [`ia/README.md`](../README.md). Os dados são sintéticos (gerador `ia/finai/data_gen.py` do FinIA, seed 42). Nenhum número aqui veio de usuário real.

## Arquivos

| Arquivo | Tarefa | Gerado por | Conteúdo |
|---|---|---|---|
| `spike_modelo.md` / `.json` | T1 | `exportar_modelo.py` (o `.md` é escrito à mão) | Escolha do modelo de embeddings |
| `categorias_personalizadas.md` / `.json` | T2 | `avaliar_categorias.py` | Prior genérico, hiperparâmetros, curva de aprendizado no teste, especificação do modelo do app (T5) |
| `curva_aprendizado.png` | T2 | `avaliar_categorias.py` | Gráfico da curva de aprendizado |
| `atipicos.md` | T7 | `avaliar_atipicos.py` | Detector de gastos fora do padrão contra o FinIA |
| `intencoes.md` | T13 | `treinar_intencoes.py` | Classificador das perguntas ao Assistente, limiares e matriz de confusão |

## T1: modelo de embeddings

Fonte: `spike_modelo.md` e `spike_modelo.json`; relatório `task-1-report.md`.

| Candidato | `.onnx` int8 | Cosseno médio (podado × original) | Cosseno mínimo | kNN k=5, podado int8 | Híbrido léxico + kNN |
|---|---|---|---|---|---|
| multilingual-e5-small | 34,8 MB | 0,9969 | 0,9841 | 0,340 | 0,754 |
| **paraphrase-multilingual-MiniLM-L12-v2 (escolhido)** | 34,8 MB | 0,9904 | 0,9503 | **0,389** | 0,749 |
| Híbrido do FinIA (referência) | | | | 0,744 | |

- Vocabulário: 250.002 → 36.939 peças (`spike_modelo.md`).
- Viterbi em Python igual ao tokenizador HF em 6.319 de 6.319 descrições únicas (`spike_modelo.json`, `viterbi_igual_hf`).
- O híbrido é otimista: o léxico de palavras-chave tem nomes de estabelecimentos do conjunto de teste (vazamento no GroupKFold). Sem o léxico, TF-IDF + RL dá 0,296.
- Decisão de 2026-11-26: MiniLM, sem fallback TF-IDF (`spike_modelo.md`, seção Decisão).

## T2: categorias personalizadas

Fonte: `categorias_personalizadas.md` e `.json`; relatórios `task-2-report.md` (inclui a rodada de correções) e a seção "Especificação" do `.md`.

Prior genérico (GroupKFold por estabelecimento, 11.114 gastos):

| Prior | Acurácia em estabelecimento novo |
|---|---|
| Só embeddings | 0,441 |
| Prior + léxico do FinIA | 0,782 (otimista, ver nota do `.md`) |

Hiperparâmetros escolhidos na validação (35 usuários): `pesoPrototipo` 1, `alfaK` 10, `lambdaL2` 0,0001, `epocas` 300. Limiar de sugestão: 0,42 (precisão 0,850 na validação).

Acurácia média no teste (15 usuários, rodado uma vez), por número de lançamentos rotulados `n`:

| Modelo | n=0 | n=5 | n=10 | n=20 | n=50 | n=100 |
|---|---|---|---|---|---|---|
| (1) Memória exata | 0,000 | 0,004 | 0,008 | 0,017 | 0,042 | 0,075 |
| (2) TF-IDF n-gramas + RL pessoal | 0,000 | 0,296 | 0,371 | 0,514 | 0,743 | **0,849** |
| (3) kNN k=5 (embeddings) | 0,000 | 0,211 | 0,299 | 0,397 | 0,500 | 0,689 |
| (4) Só protótipos | 0,352 | 0,354 | 0,353 | 0,353 | 0,349 | 0,354 |
| (5) Modelo do app | **0,530** | **0,569** | **0,608** | **0,662** | **0,756** | 0,793 |

- O modelo do app vence até n=50. Em n=100, o TF-IDF + RL pessoal passa à frente (F1 macro 0,748 contra 0,651 do app).
- Sugestões no teste (limiar 0,42, todas as n juntas): cobertura 0,545, precisão 0,844.
- Pelo `.md`, a memória exata quase nunca acerta, porque a descrição do extrato muda a cada compra.

## T7: gastos fora do padrão

Fonte: `atipicos.md`; relatório `task-7-report.md`. 50 usuários do FinIA, categoria verdadeira, 4% de contaminação, métricas agregadas por gasto.

| Modelo | Marcados | P | R | F1 | AP |
|---|---|---|---|---|---|
| FinIA original (sklearn, 300 árvores) | 472 | 0,44 | 0,57 | 0,50 | 0,59 |
| Baseline (z da categoria > 3,5) | 271 | 0,25 | 0,19 | 0,21 | 0,37 |
| IsolationForest.kt, chave do FinIA, top 4% | 474 | 0,45 | 0,59 | 0,51 | 0,61 |
| IsolationForest.kt, chave do app, top 4% | 474 | 0,41 | 0,54 | 0,46 | 0,56 |
| **DetectorDeAtipicos (chave do app, valor ≥ R$ 30, com motivo)** | 314 | **0,51** | 0,44 | 0,47 | 0,56 |

- Paridade com o sklearn (Spearman, 200 árvores, amostra 256, semente 42): u00 0,9799; u30 0,9879; u40 0,9839 (`task-7-report.md`).
- Motivos na lista do detector (anomalias plantadas / gastos normais): DUPLICADO 64/0; LOCAL_NOVO_CARO 15/57; VALOR_ALTO_NA_CATEGORIA 77/94; VALOR_ALTO_NO_LOCAL 4/3 (`atipicos.md`).
- O `atipicos.md` usa a categoria verdadeira. O FinIA original usava a prevista, por isso a linha dele fica um pouco acima da publicada (P 0,42, R 0,55).
- A regra de R$ 30 tira duplicados baratos: a revocação de duplicados cai de 0,82 para 0,53 (`task-7-report.md`, ressalvas).

## T13: intenções das perguntas

Fonte: `intencoes.md`; relatório `task-13-report.md`. Teste: 176 frases conhecidas e 35 fora do escopo. As frases de teste foram escritas pelo autor do código, com redação diferente da do treino.

| Métrica | Valor |
|---|---|
| Acurácia no teste, sem recusa (Python) | 0,909 |
| Acurácia no teste, recusa contada como erro | 0,864 |
| F1 macro | 0,912 |
| Recusa fora do escopo (Python) | 0,971 |
| Acurácia no teste, ONNX no JVM (`PerguntasTest`) | 0,910 |
| Recusa fora do escopo, ONNX no JVM | 0,943 |
| Baseline TF-IDF (char_wb 2-5) + RL, sem recusa | 0,966 (sem limiar: nenhuma confiança da CV chega a precisão 0,9) |

- Limiar de confiança: 0,55 (precisão 0,900 e cobertura 0,894 na CV de 5 dobras).
- Porta de similaridade: `similaridadeMinima` 0,57 (percentil 5 do treino). Só a confiança da RL recusava 0,343 das frases fora do escopo, por isso a porta foi adicionada. Não foi ajustada no teste.
- A matriz de confusão (12 intenções) está no `intencoes.md`.

## Goldens (referência entre Python e Kotlin)

Fonte: relatórios `task-3-report.md`, `task-5-report.md` e `task-7-report.md`.

| Golden | Conteúdo | Resultado registrado |
|---|---|---|
| `golden_tokens.json` | 200 casos (150 descrições do FinIA + 50 difíceis) | Ids iguais ao Python no `TokenizadorTest` |
| `golden_vetores.json` | 50 vetores de 384 dimensões, lote 1 | Cosseno mínimo 0,9999999 na JVM (ORT 1.31.0) |
| `golden_isolation.json` | 3 usuários (u00, u30, u40) | Spearman 0,98 a 0,99 |
| `golden_classificador.json` | n = 0 e n = 5, 7 consultas cada, intermediários | Diferença máxima de 2,1e-8 nas probabilidades (T5) |

## Medições do aparelho e do APK (T11)

Ainda não medidas: a T11 não foi executada.

| Medida | Valor |
|---|---|
| Tamanho do APK de release | a medir (T11) |
| Latência da vetorização por descrição no aparelho | a medir (T11) |
| Tempo de treino do classificador no aparelho | a medir (T11) |
| Memória em uso | a medir (T11) |

Os relatórios T3 e T5 têm medições provisórias no S20 FE. Elas não entram nesta tabela, porque o plano põe as medições oficiais na T11.

## Limitações

- Dados sintéticos do gerador do FinIA. Não há teste com pessoas reais registrado no repositório.
- Os números de léxico (T1, T2 e o app em n=0) são otimistas, porque o léxico tem nomes de estabelecimentos do conjunto de teste.
