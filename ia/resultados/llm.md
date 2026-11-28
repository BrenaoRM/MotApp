# Avaliação do LLM local

Gerado por `ia/llm/avaliar_llm.py --tabela` a partir de `ia/resultados/llm/*.json`.

| métrica | atual | base-0.6B | media | rapida |
|---|---|---|---|---|
| plano exato | 38.3% | 28.3% | 91.7% | 91.7% |
| plano parcial (F1) | 62.3% | 29.4% | 95.6% | 94.7% |
| recusa correta | 100.0% | 100.0% | 100.0% | 100.0% |
| fidelidade | - | 11.1% | 99.2% | 97.9% |
| troca de papel (menor é melhor) | - | 0.0% | 0.8% | 0.0% |
| tokens/s (PC, GPU compartilhada) | - | 101 | 132 | 154 |
| perguntas de plano | 60 | 60 | 60 | 60 |
| planos inválidos (contam como erro) | - | 0 | 0 | 0 |
| respostas avaliadas | - | 9 | 483 | 483 |

Plano: 60 perguntas de `ia/llm/teste/perguntas.jsonl`. O sistema atual só prevê a intenção (mapeada para a ferramenta do contrato), então o plano exato dele só conta perguntas de 1 chamada. Fidelidade: respostas às perguntas de `resposta_valid.jsonl` com todas as frases aprovadas pelo verificador. Troca de papel: heurística de palavra de papel + número em respostas de poupança, orçamento e gasto atípico.
