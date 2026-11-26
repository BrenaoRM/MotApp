"""Spike da Tarefa 1: poda o vocabulário dos dois candidatos, exporta para ONNX int8, mede e escolhe.

Uso: `cd ia && python exportar_modelo.py` (baixa os modelos do Hugging Face para `ia/.cache/`).
Precisa de `ia/.cache/dl/pt_BR.dic` (hunspell pt_BR do LibreOffice) e `ia/.cache/dl/pt_br_50k.txt`
(FrequencyWords, hermitdave), ver `resultados/spike_modelo.md`.
"""
import json
import math
import os
import shutil
import sys
import unicodedata
from functools import lru_cache
from pathlib import Path

import numpy as np

RAIZ = Path(__file__).resolve().parent
CACHE = RAIZ / ".cache"
os.environ.setdefault("HF_HOME", str(CACHE / "hf"))
os.environ.setdefault("HF_HUB_DISABLE_SYMLINKS_WARNING", "1")
sys.path.insert(0, str(RAIZ / "finai"))  # os módulos do FinIA importam uns aos outros sem pacote

from texto import normalizar_para_modelo  # noqa: E402

CANDIDATOS = {
    "e5": ("intfloat/multilingual-e5-small", "query: "),
    "minilm": ("sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2", ""),
}
MAX_TOKENS = 64
ESPACO = "▁"  # ▁
ASSETS = RAIZ.parent / "app" / "src" / "main" / "assets" / "ia"
SAIDA = CACHE / "saida"
ESCOLHIDO = "minilm"

# Nomes de categoria que aparecem nas avaliações (FinIA + esquemas da T2 + padrões do app).
NOMES_CATEGORIA = [
    "Alimentação", "Restaurantes e Delivery", "Transporte", "Moradia", "Saúde", "Lazer e Assinaturas",
    "Educação", "Compras", "Outros", "Mercado", "Comida fora", "Carro", "Casa", "Farmácia", "Diversão",
    "Estudos", "Essenciais", "Rolê", "Pet", "Presentes", "Lazer", "Contas", "Salário", "Fatura",
    "Petz", "Cobasi", "Clinica Veterinaria", "Agropet",
]


# ---------------------------------------------------------------- dados

@lru_cache(maxsize=1)
def gastos_finia():
    import data_gen
    df = data_gen.generate(n_per_arch=10, seed=42)  # mesmo corpus da avaliação do FinIA (0,744)
    return df[df.amount < 0].reset_index(drop=True)


def descricoes_finia():
    """Descrições únicas dos gastos do FinIA, em ordem aleatória fixa."""
    unicas = sorted(set(gastos_finia().description))
    np.random.default_rng(0).shuffle(unicas)
    return unicas


def palavras_pt_br():
    dl = CACHE / "dl"
    palavras = set()
    for linha in (dl / "pt_BR.dic").read_text(encoding="utf-8-sig").splitlines()[1:]:
        p = linha.split("/")[0].split()[0] if linha.strip() else ""
        if p:
            palavras.add(p.lower())
    for linha in (dl / "pt_br_50k.txt").read_text(encoding="utf-8").splitlines():
        if linha.strip():
            palavras.add(linha.split()[0].lower())
    return palavras


def corpus_de_poda(prefixo):
    corpus = {normalizar_para_modelo(d) for d in gastos_finia().description}
    corpus |= {normalizar_para_modelo(n) for n in NOMES_CATEGORIA}
    corpus |= {normalizar_para_modelo(p) for p in palavras_pt_br()}
    corpus.add(prefixo)
    return sorted(c for c in corpus if c)


# ---------------------------------------------------------------- poda e exportação

def _vocab_original(tok):
    return json.loads(tok.backend_tokenizer.to_str())["model"]["vocab"]


def podar(nome, prefixo):
    """Devolve os ids originais mantidos, em ordem crescente (os especiais 0..3 continuam 0..3)."""
    from transformers import AutoTokenizer
    tok = AutoTokenizer.from_pretrained(nome)
    vocab = _vocab_original(tok)
    mantidos = {tok.bos_token_id, tok.pad_token_id, tok.eos_token_id, tok.unk_token_id}
    for ids in tok(corpus_de_poda(prefixo), add_special_tokens=False)["input_ids"]:
        mantidos.update(ids)
    mantidos |= {i for i, (peca, _) in enumerate(vocab) if len(peca) == 1}
    mantidos.discard(tok.mask_token_id)
    return np.array(sorted(mantidos)), tok, vocab


def _json_tokenizador(nome, prefixo, mantidos, tok, vocab):
    novo = {int(v): i for i, v in enumerate(mantidos)}
    return {
        "modelo_origem": nome,
        "prefixo": prefixo,
        "unk": novo[tok.unk_token_id], "bos": novo[tok.bos_token_id],
        "eos": novo[tok.eos_token_id], "pad": novo[tok.pad_token_id],
        "max_tokens": MAX_TOKENS,
        "dimensao": 384,
        # Como replicar o normalizador do XLM-R (Precompiled ≈ NFKC) e o pré-tokenizador:
        "normalizacao": {
            "nfkc": True,
            "dividir_em_espacos": True,          # WhitespaceSplit: espaços repetidos/bordas somem
            "marcador_de_palavra": ESPACO,       # Metaspace: cada palavra vira "▁palavra"
            "viterbi_por_palavra": True,         # split=True: cada "▁palavra" é segmentada sozinha
            "especiais_fora_do_trie": True,      # <s>, </s>, <pad>, <unk> nunca casam com o texto
            "unk_consecutivos_fundidos": True,   # caracteres sem peça seguidos viram um só <unk>
            "indice_por_codepoint": True,        # peças comparadas por code point (cuidado com surrogates)
        },
        "pecas": [[vocab[i][0], vocab[i][1]] for i in mantidos],
    }


class _SoEntradasDoApp:
    """Wrapper com só `input_ids` e `attention_mask` (token_type_ids fica zero por dentro)."""

    def __new__(cls, modelo):
        import torch

        class M(torch.nn.Module):
            def __init__(self):
                super().__init__()
                self.m = modelo

            def forward(self, input_ids, attention_mask):
                return self.m(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state

        return M().eval()


def exportar(chave):
    import torch
    from onnxruntime.quantization import QuantType, quantize_dynamic
    from transformers import AutoModel

    nome, prefixo = CANDIDATOS[chave]
    mantidos, tok, vocab = podar(nome, prefixo)
    modelo = AutoModel.from_pretrained(nome).eval()
    pesos = modelo.embeddings.word_embeddings.weight.data[torch.as_tensor(mantidos)].clone()
    modelo.embeddings.word_embeddings = torch.nn.Embedding.from_pretrained(
        pesos, freeze=True, padding_idx=int(np.searchsorted(mantidos, tok.pad_token_id)))
    modelo.config.vocab_size = len(mantidos)

    pasta = SAIDA / chave
    pasta.mkdir(parents=True, exist_ok=True)
    fp32 = pasta / "modelo_fp32.onnx"
    exemplo = (torch.tensor([[0, 10, 20, 2]]), torch.ones(1, 4, dtype=torch.long))
    torch.onnx.export(
        _SoEntradasDoApp(modelo), exemplo, str(fp32),
        input_names=["input_ids", "attention_mask"], output_names=["last_hidden_state"],
        dynamic_axes={"input_ids": {0: "lote", 1: "seq"}, "attention_mask": {0: "lote", 1: "seq"},
                      "last_hidden_state": {0: "lote", 1: "seq"}},
        opset_version=17, dynamo=False,
    )
    quantize_dynamic(str(fp32), str(pasta / "modelo.onnx"), weight_type=QuantType.QInt8, per_channel=True)
    dados = _json_tokenizador(nome, prefixo, mantidos, tok, vocab)
    (pasta / "tokenizador.json").write_text(json.dumps(dados, ensure_ascii=False), encoding="utf-8")
    return pasta, len(vocab), len(mantidos)


# ---------------------------------------------------------------- tokenização (espelho do Kotlin)

def viterbi_de_json(dados):
    """Unigram Viterbi puro sobre `pecas`; é o que o Tokenizador.kt vai replicar."""
    especiais = {dados["unk"], dados["bos"], dados["eos"], dados["pad"]}
    pecas = {p: (i, s) for i, (p, s) in enumerate(dados["pecas"]) if i not in especiais}
    maior = max(len(p) for p in pecas)
    unk, bos, eos = dados["unk"], dados["bos"], dados["eos"]

    def palavra(s):
        n = len(s)
        melhor = [-math.inf] * (n + 1)
        melhor[0] = 0.0
        volta = [None] * (n + 1)  # (inicio, id)
        for i in range(n):
            if melhor[i] == -math.inf:
                continue
            achou_um = False
            for j in range(i + 1, min(n, i + maior) + 1):
                p = pecas.get(s[i:j])
                if p is None:
                    continue
                if j == i + 1:
                    achou_um = True
                if melhor[i] + p[1] > melhor[j]:
                    melhor[j], volta[j] = melhor[i] + p[1], (i, p[0])
            if not achou_um and melhor[i] > melhor[i + 1]:  # caractere sem peça: <unk>
                melhor[i + 1], volta[i + 1] = melhor[i], (i, unk)
        ids, j = [], n
        while j > 0:
            i, pid = volta[j]
            ids.append(pid)
            j = i
        ids.reverse()
        fundidos = []
        for pid in ids:
            if not (pid == unk and fundidos and fundidos[-1] == unk):
                fundidos.append(pid)
        return fundidos

    def codificar(texto, max_tokens=MAX_TOKENS):
        ids = []
        for w in unicodedata.normalize("NFKC", texto).split():
            ids += palavra(ESPACO + w)
        return [bos] + ids[: max_tokens - 2] + [eos]

    return codificar


def _ler_json(pasta):
    return json.loads((Path(pasta) / "tokenizador.json").read_text(encoding="utf-8"))


def tokenizador_original_remapeado(pasta):
    """Tokenizador HF original + remapeamento para os ids podados (referência dos testes)."""
    from transformers import AutoTokenizer
    dados = _ler_json(pasta)
    tok = AutoTokenizer.from_pretrained(dados["modelo_origem"])
    vocab = _vocab_original(tok)
    novo = {p: i for i, (p, _) in enumerate(dados["pecas"])}

    def codificar(texto):
        ids = tok(texto, truncation=True, max_length=MAX_TOKENS)["input_ids"]
        return [novo[vocab[i][0]] for i in ids]  # KeyError = peça podada

    return codificar


# ---------------------------------------------------------------- vetorização

def _pooling(saida, mascara):
    m = mascara[..., None].astype(np.float32)
    v = (saida * m).sum(1) / np.maximum(m.sum(1), 1e-9)
    return v / np.linalg.norm(v, axis=1, keepdims=True)


def carregar_onnx(pasta, lote=64):
    """Modelo podado int8 + Viterbi do JSON: `f(list[str]) -> np.ndarray[N, 384]` L2-normalizado.
    Os textos já devem vir de `normalizar_para_modelo`; o prefixo é aplicado aqui."""
    import onnxruntime as ort
    pasta = Path(pasta)
    dados = _ler_json(pasta)
    codificar = viterbi_de_json(dados)
    sessao = ort.InferenceSession(str(pasta / "modelo.onnx"), providers=["CPUExecutionProvider"])

    def vetorizar(textos):
        saida = []
        for k in range(0, len(textos), lote):
            ids = [codificar(dados["prefixo"] + t) for t in textos[k:k + lote]]
            L = max(map(len, ids))
            entrada = np.full((len(ids), L), dados["pad"], dtype=np.int64)
            mascara = np.zeros((len(ids), L), dtype=np.int64)
            for r, seq in enumerate(ids):
                entrada[r, :len(seq)], mascara[r, :len(seq)] = seq, 1
            h = sessao.run(["last_hidden_state"], {"input_ids": entrada, "attention_mask": mascara})[0]
            saida.append(_pooling(h, mascara))
        return np.concatenate(saida) if saida else np.zeros((0, dados["dimensao"]), np.float32)

    return vetorizar


def carregar_original(pasta, lote=64):
    """Modelo original fp32 sem poda (referência do cosseno)."""
    import torch
    from transformers import AutoModel, AutoTokenizer
    dados = _ler_json(pasta)
    tok = AutoTokenizer.from_pretrained(dados["modelo_origem"])
    modelo = AutoModel.from_pretrained(dados["modelo_origem"]).eval()

    def vetorizar(textos):
        saida = []
        with torch.no_grad():
            for k in range(0, len(textos), lote):
                b = tok([dados["prefixo"] + t for t in textos[k:k + lote]], padding=True,
                        truncation=True, max_length=MAX_TOKENS, return_tensors="pt")
                h = modelo(input_ids=b["input_ids"], attention_mask=b["attention_mask"]).last_hidden_state
                saida.append(_pooling(h.numpy(), b["attention_mask"].numpy()))
        return np.concatenate(saida)

    return vetorizar


# ---------------------------------------------------------------- medição

def acuracia_knn(vetorizar):
    """kNN (k=5, cosseno), GroupKFold(5) por merchant: cenário "estabelecimento novo"."""
    from sklearn.model_selection import GroupKFold, cross_val_predict
    from sklearn.neighbors import KNeighborsClassifier
    df = gastos_finia()
    textos = [normalizar_para_modelo(d) for d in df.description]
    unicos = sorted(set(textos))
    vet = dict(zip(unicos, vetorizar(unicos)))
    X = np.stack([vet[t] for t in textos])
    pred = cross_val_predict(KNeighborsClassifier(n_neighbors=5, metric="cosine"), X, df.category.to_numpy(),
                             groups=df.merchant.to_numpy(), cv=GroupKFold(n_splits=5))
    return float((pred == df.category.to_numpy()).mean()), pred


def categoria_do_lexico(desc):
    """Léxico de palavras-chave do FinIA: 1ª categoria cuja palavra aparece; None se nenhuma casa.
    Otimista no GroupKFold: o léxico contém nomes de estabelecimentos do próprio conjunto de teste."""
    from categorizer import KEYWORDS
    from text_utils import normalize
    t = normalize(desc)
    return next((c for c, kws in KEYWORDS if any(k in t for k in kws)), None)


def acuracia_hibrido(pred_knn):
    """Híbrido: o léxico decide quando casa; senão vale o kNN de embeddings (mesmas dobras)."""
    df = gastos_finia()
    lex = [categoria_do_lexico(d) for d in df.description]
    pred = np.array([l if l is not None else k for l, k in zip(lex, pred_knn)], dtype=object)
    return float((pred == df.category.to_numpy()).mean())


def taxa_viterbi(pasta, textos):
    dados = _ler_json(pasta)
    viterbi = viterbi_de_json(dados)
    original = tokenizador_original_remapeado(pasta)
    iguais = sum(viterbi(dados["prefixo"] + t) == original(dados["prefixo"] + t) for t in textos)
    return iguais / len(textos)


def medir(chave, pasta, n_vocab, n_mantidos):
    textos300 = [normalizar_para_modelo(d) for d in descricoes_finia()[:300]]
    podado, original = carregar_onnx(pasta), carregar_original(pasta)
    cos = (podado(textos300) * original(textos300)).sum(1)
    todas = [normalizar_para_modelo(d) for d in descricoes_finia()]
    acc, pred = acuracia_knn(podado)
    return {
        "candidato": chave, "modelo": CANDIDATOS[chave][0],
        "vocab_original": n_vocab, "vocab_podado": n_mantidos,
        "mb_onnx_int8": (pasta / "modelo.onnx").stat().st_size / 2**20,
        "mb_tokenizador": (pasta / "tokenizador.json").stat().st_size / 2**20,
        "cos_medio": float(cos.mean()), "cos_min": float(cos.min()),
        "acuracia_knn_podado_int8": acc,
        "acuracia_hibrido_lexico_podado_int8": acuracia_hibrido(pred),
        "acuracia_knn_original_fp32": acuracia_knn(original)[0],
        "viterbi_igual_hf": taxa_viterbi(pasta, todas), "n_viterbi": len(todas),
    }


def main():
    resultados = []
    for chave in CANDIDATOS:
        pasta, n_vocab, n_mantidos = exportar(chave)
        r = medir(chave, pasta, n_vocab, n_mantidos)
        print(json.dumps(r, ensure_ascii=False))
        resultados.append(r)
    (RAIZ / "resultados").mkdir(exist_ok=True)
    (RAIZ / "resultados" / "spike_modelo.json").write_text(
        json.dumps(resultados, ensure_ascii=False, indent=2), encoding="utf-8")
    # Decisão de 2026-11-26 (ver resultados/spike_modelo.md): MiniLM, sem fallback TF-IDF.
    ASSETS.mkdir(parents=True, exist_ok=True)
    for f in ("modelo.onnx", "tokenizador.json"):
        shutil.copy(SAIDA / ESCOLHIDO / f, ASSETS / f)


if __name__ == "__main__":
    main()
