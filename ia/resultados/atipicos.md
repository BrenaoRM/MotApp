# Detector de gastos fora do padrão — avaliação

Gerado por `ia/avaliar_atipicos.py`. 50 usuários sintéticos do FinIA (`data_gen.generate(10, seed=42)`), só despesas, categoria verdadeira. Métricas por gasto agregadas nos 50 usuários; AP = média por usuário com anomalia. Config: 200 árvores, amostra 256, semente 42.

Referência publicada pelo FinIA (cenário 'conhecidos', categoria prevista, 4%): IF P 0.42 · R 0.55 · F1 0.48 · AP 0.58; baseline P 0.25 · R 0.19 · AP 0.36; revocação por tipo duplicado 0.8 · novo_estabelecimento 0.57 · spike 0.33.

| Modelo | Marcados | P | R | F1 | AP | duplicado | novo_estabelecimento | spike |
|---|---|---|---|---|---|---|---|---|
| FinIA original (sklearn, 300 árvores, `contamination` 4%) | 472 | 0.44 | 0.57 | 0.50 | 0.59 | 0.79 | 0.59 | 0.37 |
| Baseline do FinIA (z categoria > 3.5) | 271 | 0.25 | 0.19 | 0.21 | 0.37 | 0.01 | 0.40 | 0.18 |
| IsolationForest.kt, chave do FinIA (`merchant_key`), top 4% | 474 | 0.45 | 0.59 | 0.51 | 0.61 | 0.85 | 0.61 | 0.35 |
| IsolationForest.kt, chave do app, top 4% | 474 | 0.41 | 0.54 | 0.46 | 0.56 | 0.82 | 0.58 | 0.26 |
| DetectorDeAtipicos (chave do app; valor ≥ R$ 30 e com motivo) | 314 | 0.51 | 0.44 | 0.47 | 0.56 | 0.53 | 0.58 | 0.26 |

Motivos na lista do detector (quantos são anomalia plantada / gasto normal):

| Motivo | Anomalia | Normal |
|---|---|---|
| DUPLICADO | 64 | 0 |
| LOCAL_NOVO_CARO | 15 | 57 |
| VALOR_ALTO_NA_CATEGORIA | 77 | 94 |
| VALOR_ALTO_NO_LOCAL | 4 | 3 |
