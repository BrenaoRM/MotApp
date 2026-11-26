# Intenções das perguntas (Tarefa 13)

RL softmax (C=300, escolhido na CV; pesos balanceados) sobre os embeddings do modelo do app; `DESCONHECIDA` não entra no treino.
Treino: 415 frases (com `{cat}` expandido). Teste: 176 frases conhecidas + 35 fora do escopo.

Limiar de confiança: **0.55** (menor valor com precisão >= 0.9 nas respostas dadas, validação cruzada estratificada de 5 dobras: precisão 0.900, cobertura 0.894; acurácia da CV sem recusa 0.848).

Só a confiança da RL recusa apenas 0.343 das frases fora do escopo (ela só compara intenções entre si). Por isso o app também recusa frase longe de todos os centroides das intenções: `similaridadeMinima` = **0.57** (percentil 5 da similaridade, fora da dobra, das frases de treino ao centroide mais próximo; não foi ajustada no teste).

| Modelo | Acurácia no teste (com recusa) | Acurácia no teste (sem recusa) | F1 macro | Recusa fora do escopo |
|---|---|---|---|---|
| Embeddings + RL + centroides (app) | 0.864 | 0.909 | 0.912 | 0.971 |
| Baseline TF-IDF + RL | n/a | 0.966 | - | n/a (nenhuma confiança da CV chega a precisão 0,9, então não há limiar) |

Acurácia "com recusa" conta como erro a frase conhecida recusada (confiança abaixo do limiar ou longe dos centroides).

## Matriz de confusão (teste, frases conhecidas, sem recusa; linhas = real, colunas = previsto)

| real / previsto | ASSINATURAS | ATIPICOS | COMO_ECONOMIZAR | COMPARAR_MES_ANTERIOR | GASTO_EM_CATEGORIA | MAIOR_GASTO | MEDIA_MENSAL | ORCAMENTO_RESTANTE | POUPANCA | RANKING_CATEGORIAS | RECEITAS | TOTAL_DO_MES |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ASSINATURAS | 9 | 0 | 0 | 0 | 1 | 0 | 0 | 1 | 0 | 1 | 0 | 0 |
| ATIPICOS | 0 | 11 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
| COMO_ECONOMIZAR | 0 | 0 | 12 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| COMPARAR_MES_ANTERIOR | 0 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| GASTO_EM_CATEGORIA | 2 | 0 | 0 | 0 | 31 | 0 | 2 | 0 | 1 | 0 | 0 | 0 |
| MAIOR_GASTO | 0 | 0 | 0 | 0 | 1 | 13 | 0 | 0 | 0 | 0 | 0 | 0 |
| MEDIA_MENSAL | 0 | 0 | 0 | 0 | 0 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| ORCAMENTO_RESTANTE | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 14 | 0 | 0 | 0 | 0 |
| POUPANCA | 0 | 0 | 0 | 1 | 1 | 0 | 1 | 0 | 8 | 1 | 0 | 0 |
| RANKING_CATEGORIAS | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 12 | 0 | 0 |
| RECEITAS | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 11 | 0 |
| TOTAL_DO_MES | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 11 |
