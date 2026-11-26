"""Prior genérico (Tarefa 2, passo 4): RL multinomial sobre embeddings do FinIA → 9 categorias genéricas,
mais o léxico de palavras-chave do FinIA. Valida com GroupKFold por merchant (estabelecimento novo).

Uso: `cd ia && python treinar_prior.py` (imprime as acurácias; quem grava o `modelo_ia.json` é
`avaliar_categorias.py`, que chama `treinar_final`).
"""
import numpy as np

from exportar_modelo import ASSETS, CACHE, carregar_onnx, gastos_finia
from texto import _sem_acento, normalizar_para_modelo
from usuarios_sinteticos import GENERICAS, MERCHANTS_PET

N_DOBRAS = 5
_ARQ_VETORES = None  # ia/.cache/vetores_t2_<hash do modelo.onnx>.npz (cache velho não vale para outro modelo)
_vetorizar = None
_cache = None


def vetores(textos_normalizados):
    """Embeddings do modelo do app (int8 podado + Viterbi), com cache em `ia/.cache/` por hash do modelo."""
    global _vetorizar, _cache, _ARQ_VETORES
    if _ARQ_VETORES is None:
        import hashlib
        h = hashlib.sha256((ASSETS / "modelo.onnx").read_bytes()).hexdigest()[:12]
        _ARQ_VETORES = CACHE / f"vetores_t2_{h}.npz"
    if _cache is None:
        _cache = {}
        if _ARQ_VETORES.exists():
            z = np.load(_ARQ_VETORES, allow_pickle=False)
            _cache = dict(zip(z["textos"].tolist(), z["vetores"]))
    faltam = sorted(set(textos_normalizados) - _cache.keys())
    if faltam:
        if _vetorizar is None:
            _vetorizar = carregar_onnx(ASSETS)
        _cache.update(zip(faltam, _vetorizar(faltam)))
        np.savez(_ARQ_VETORES, textos=np.array(list(_cache)), vetores=np.stack(list(_cache.values())))
    return np.stack([_cache[t] for t in textos_normalizados]).astype(np.float32)


def vetor_de(textos_brutos):
    return vetores([normalizar_para_modelo(t) for t in textos_brutos])


# ---------------------------------------------------------------- léxico

def lexico():
    """Termo normalizado → categoria genérica (o `categorizer.KEYWORDS` do FinIA, sem termos de pet)."""
    from categorizer import KEYWORDS
    lex = {}
    for categoria, termos in KEYWORDS:
        for termo in termos:
            t = normalizar_para_modelo(termo)
            if t and not any(p in t for p in ("pet", "cobasi", "veterin")):
                lex.setdefault(t, categoria)
    return lex


def texto_do_lexico(desc):
    return _sem_acento(normalizar_para_modelo(desc))


def generica_do_lexico(desc, lex, nomes=GENERICAS):
    """Índice (em `nomes`) da categoria do léxico, ou None. Regra: casam os termos que aparecem como palavras
    inteiras em `sem_acento(normalizar_para_modelo(desc))` (" " + termo + " " dentro de " " + t + " ");
    vence a categoria de menor índice em `nomes`."""
    t = " " + texto_do_lexico(desc) + " "
    achadas = [nomes.index(c) for termo, c in lex.items() if " " + termo + " " in t]
    return min(achadas) if achadas else None


# ---------------------------------------------------------------- dados e treino

def dados_prior():
    """Gastos do FinIA sem os estabelecimentos "Pet": (X, y como índice de GENERICAS, merchants, descrições)."""
    df = gastos_finia()
    df = df[~df.merchant.isin(MERCHANTS_PET)].reset_index(drop=True)
    X = vetor_de(df.description)
    y = df.category.map(GENERICAS.index).to_numpy()
    return X, y, df.merchant.to_numpy(), df.description.to_numpy()


def _rl():
    from sklearn.linear_model import LogisticRegression
    return LogisticRegression(C=1.0, max_iter=2000)


def dobras_por_merchant(merchants):
    from sklearn.model_selection import GroupKFold
    return list(GroupKFold(n_splits=N_DOBRAS).split(np.zeros(len(merchants)), groups=merchants))


def validar():
    """Acurácia em estabelecimento novo (GroupKFold por merchant): só embeddings e prior + léxico."""
    X, y, merchants, descs = dados_prior()
    pred = np.empty_like(y)
    for treino, teste in dobras_por_merchant(merchants):
        pred[teste] = _rl().fit(X[treino], y[treino]).predict(X[teste])
    lex = lexico()
    lex_idx = [generica_do_lexico(d, lex) for d in descs]
    hibrido = np.array([l if l is not None else p for l, p in zip(lex_idx, pred)])
    casou = np.array([l is not None for l in lex_idx])
    return {"n": int(len(y)), "acuracia_so_embeddings": float((pred == y).mean()),
            "acuracia_prior_mais_lexico": float((hibrido == y).mean()),
            "cobertura_lexico": float(casou.mean()),
            "acuracia_lexico_quando_casa": float((hibrido[casou] == y[casou]).mean())}


class PriorForaDaDobra:
    """Prior usado na avaliação: para cada merchant, o modelo da dobra que NÃO o viu no treino; para
    merchants fora do FinIA (Pet, Presentes) o modelo final, que também não os viu."""

    def __init__(self):
        X, y, merchants, _ = dados_prior()
        self.final = _rl().fit(X, y)
        self.dobra_de = {}
        self.modelos = []
        for k, (treino, teste) in enumerate(dobras_por_merchant(merchants)):
            self.modelos.append(_rl().fit(X[treino], y[treino]))
            self.dobra_de.update({m: k for m in set(merchants[teste])})

    def probabilidades(self, X, merchants):
        P = self.final.predict_proba(X)
        for k, m in enumerate(self.modelos):
            linhas = [i for i, mm in enumerate(merchants) if self.dobra_de.get(mm) == k]
            if linhas:
                P[linhas] = m.predict_proba(X[linhas])
        return P


def treinar_final():
    """Pesos/vieses (linha g = GENERICAS[g]), vetoresNomes e léxico para o `modelo_ia.json`."""
    X, y, _, _ = dados_prior()
    rl = _rl().fit(X, y)
    assert list(rl.classes_) == list(range(len(GENERICAS)))
    return {"pesos": rl.coef_.astype(np.float32), "vieses": rl.intercept_.astype(np.float32),
            "vetoresNomes": vetor_de(GENERICAS), "lexico": lexico()}


if __name__ == "__main__":
    import json
    print(json.dumps(validar(), ensure_ascii=False, indent=2))
