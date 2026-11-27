# IA LLM local (assistente do MotApp)

Treino e conversao de um Qwen3 pequeno para o assistente do app. Tudo roda no PC (RTX 4050 Laptop, 6 GB).

## Ambiente
- Python 3.12, venv em `ia/llm/.venv` (fora do git).
- `python -m venv ia/llm/.venv && ia/llm/.venv/Scripts/python -m pip install -r ia/llm/requirements.txt`
- `ia/llm/.venv/Scripts/python ia/llm/preparar_ambiente.py` baixa binarios e fonte do llama.cpp (tag em `VERSAO_LLAMA_CPP`, fonte unica da versao), os pesos base e o professor. Pula o que ja existe.

## Pilha de treino instalada
**Unsloth** (unsloth 2026.11.12, torch 2.14.1 CUDA 13.0, trl, peft, transformers) instalou sem problemas no Windows. O fallback (transformers + peft + bitsandbytes, mesmo script de treino) nao foi necessario; as linhas dele estao comentadas em `requirements.txt`.

## Onde ficam os arquivos (todos ignorados pelo git)
- `bin/`: `llama-server.exe`, `llama-quantize.exe`, `llama-gguf-split.exe` (release CUDA 12.4 do Windows) e `bin/llama.cpp/` (fonte da mesma tag, com `convert_hf_to_gguf.py`).
- `modelos/base/Qwen3-{0.6B,1.7B,4B}`: pesos HF.
- `modelos/professor/Qwen3-8B-Q4_K_M.gguf`: professor (repo `Qwen/Qwen3-8B-GGUF`).
- `modelos/gguf/base-{0.6B,1.7B,4B}-q4km.gguf`: bases convertidas (HF -> f16 -> Q4_K_M) para baseline e benchmark no celular.
- `dados/gerados/`: dados sinteticos gerados.

## Converter um modelo para GGUF
```
ia/llm/.venv/Scripts/python ia/llm/bin/llama.cpp/convert_hf_to_gguf.py ia/llm/modelos/base/Qwen3-0.6B --outtype f16 --outfile x-f16.gguf
ia/llm/bin/llama-quantize.exe x-f16.gguf ia/llm/modelos/gguf/base-0.6B-q4km.gguf Q4_K_M
```

## Servidor do professor
`ia/llm/bin/llama-server.exe -m ia/llm/modelos/professor/Qwen3-8B-Q4_K_M.gguf -ngl 99 --port 8081`
