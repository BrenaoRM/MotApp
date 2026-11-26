# Spike do modelo de embeddings (Tarefa 1)

Gerado por `python exportar_modelo.py` (números brutos em `spike_modelo.json`). Data: 2026-11-26.

## Como foi medido
- Poda: corpus = descrições do FinIA (`data_gen.generate(n_per_arch=10, seed=42)`, normalizadas) + nomes de categoria
  (FinIA, esquemas da T2, padrões do app, estabelecimentos Pet) + palavras pt-BR (hunspell pt_BR do LibreOffice,
  312 mil radicais sem expandir afixos, + FrequencyWords pt_br_50k). Mantidos: ids usados, especiais (sem `<mask>`)
  e todas as peças de 1 caractere. Vocabulário: 250.002 → 36.939 peças (igual nos dois: mesmo tokenizador XLM-R).
- Exportação: `torch.onnx.export` (opset 17) de um wrapper com só `input_ids`/`attention_mask` → `last_hidden_state`;
  quantização `onnxruntime.quantization.quantize_dynamic` (QInt8, per-channel). Não usei `optimum`: o pip só
  resolveu o optimum 1.17, incompatível com transformers 5, e o `ORTQuantizer` é um invólucro dessa mesma função.
- Cosseno: 300 descrições do FinIA, modelo podado int8 (Viterbi do JSON) × original fp32 sem poda.
- kNN: k=5, cosseno, GroupKFold(5) por `merchant`, 11.193 gastos, 9 categorias. O mesmo protocolo reproduz o
  híbrido do FinIA em 0,7437.

## Resultados

| Candidato | `.onnx` int8 | cos médio | cos mín | kNN podado int8 | Híbrido léxico + kNN (int8) | kNN original fp32 |
|---|---|---|---|---|---|---|
| multilingual-e5-small (`query: `) | 34,8 MB | 0,9969 | 0,9841 | 0,340 | 0,754 | 0,368 |
| paraphrase-multilingual-MiniLM-L12-v2 | 34,8 MB | 0,9904 | 0,9503 | **0,389** | 0,749 | 0,351 |
| Híbrido FinIA (n-gramas + léxico + RL) | — | — | — | 0,744 | — | — |
| RL só n-gramas do FinIA (sem léxico) | — | — | — | 0,296 | — | — |

Variações (diagnóstico): kNN sobre descrições únicas 0,297 (e5) / 0,387 (MiniLM); RL sobre embeddings 0,448 / 0,433.

Tokenizador: a Viterbi em Python sobre `pecas` dá ids idênticos ao tokenizador HF + remapeamento em 6.319 de
6.319 descrições únicas do FinIA (100%).

Híbrido léxico + kNN: o léxico de palavras-chave do FinIA (`categorizer.KEYWORDS`) decide quando casa; senão vale o
kNN de embeddings (mesmas dobras). **Número otimista**: o léxico contém nomes de estabelecimentos do próprio conjunto
de teste (netflix, spotify, uber, ifood…), o que é vazamento no GroupKFold. Sem o léxico, TF-IDF de n-gramas + RL dá
0,296, abaixo dos dois modelos de embeddings.

## Decisão (2026-11-26)
**MiniLM (`paraphrase-multilingual-MiniLM-L12-v2`) podado int8, sem fallback TF-IDF.** Motivo: o critério de
escolha (≤ 40 MB e maior acurácia de kNN) aponta o MiniLM; o critério de fallback (0,70) disparava, mas a barra
comparava com um híbrido cujo 0,744 vem do léxico com vazamento, e o TF-IDF puro (0,296) é pior que os embeddings.
O usuário decidiu seguir com o MiniLM. O `prefixo` é `""`. Assets: `app/src/main/assets/ia/modelo.onnx` (34,8 MB)
e `tokenizador.json` (1,1 MB).
