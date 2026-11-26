"""Intenções das perguntas ao Assistente (Tarefa 13): RL softmax sobre embeddings, limiar de recusa e relatório.

Uso: `cd ia && python treinar_intencoes.py`
  1. lê `intencoes/treino.csv` e expande `{cat}` com nomes de categoria dos 4 esquemas da T2;
  2. treina a RL sobre os embeddings (mesmo `normalizar_para_modelo` + ONNX da T1), sem a classe DESCONHECIDA;
  3. `limiar` = menor confiança em que, na validação cruzada do treino (5 dobras estratificadas), a precisão das
     respostas dadas é >= 0,9; abaixo dele a intenção vira DESCONHECIDA. A confiança da RL só compara intenções
     entre si e não separa bem o que está fora do escopo; por isso a seção também leva os `centroides` das intenções
     e uma `similaridadeMinima` (percentil 5 da similaridade, fora da dobra, das frases do treino ao centroide mais
     próximo): frase longe de todos os centroides vira DESCONHECIDA;
  4. mede no `intencoes/teste.csv` (com as frases fora do escopo), compara com o baseline TF-IDF + RL e grava
     `resultados/intencoes.md` e a seção `intencoes` do `modelo_ia.json` (o resto do arquivo fica como está).
"""
import csv
import json

import numpy as np

from exportar_modelo import ASSETS, RAIZ
from texto import normalizar_para_modelo
from treinar_prior import vetor_de

PASTA = RAIZ / "intencoes"
ARQ_CONFIG = ASSETS / "modelo_ia.json"
RESULTADOS = RAIZ / "resultados"
DESCONHECIDA = "DESCONHECIDA"
PRECISAO_ALVO = 0.9
SIMILARIDADE_CATEGORIA = 0.6
NOMES_CATEGORIA = [  # nomes dos 4 esquemas de `usuarios_sinteticos` (T2)
    "Alimentação", "Restaurantes e Delivery", "Transporte", "Moradia", "Saúde", "Lazer e Assinaturas", "Educação",
    "Compras", "Outros", "Mercado", "Comida fora", "Carro", "Casa", "Farmácia", "Diversão", "Estudos",
    "Essenciais", "Rolê", "Pet", "Presentes",
]
POR_MOLDE = 3  # nomes de categoria sorteados para cada frase com {cat}
GRADE_C = [3.0, 10.0, 30.0, 100.0, 300.0]
C_RL = 10.0  # substituído pelo valor da grade com maior acurácia na validação cruzada
PERCENTIL_SIMILARIDADE = 5


def ler(arquivo, semente):
    """[(intenção, frase, molde)] com `{cat}` expandido; `molde` identifica a frase de origem (grupo da validação)."""
    rng = np.random.default_rng(semente)
    saida = []
    with open(PASTA / arquivo, encoding="utf-8", newline="") as f:
        for molde, linha in enumerate(csv.DictReader(f)):
            frase = linha["frase"].strip()
            if "{cat}" in frase:
                for nome in rng.choice(NOMES_CATEGORIA, size=POR_MOLDE, replace=False):
                    saida.append((linha["intencao"], frase.replace("{cat}", str(nome)), molde))
            else:
                saida.append((linha["intencao"], frase, molde))
    return saida


def vetorizar(frases):
    return vetor_de(frases)


def softmax(z):
    z = z - z.max(axis=-1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(axis=-1, keepdims=True)


def prever(X, pesos, vieses):
    """Probabilidades como o app calcula: softmax(W x + b)."""
    return softmax(X @ np.asarray(pesos).T + np.asarray(vieses))


def _rl():
    from sklearn.linear_model import LogisticRegression
    return LogisticRegression(C=C_RL, max_iter=3000, class_weight="balanced")


def treinar_rl(X, y):
    rl = _rl().fit(X, y)
    return rl.classes_, rl.coef_, rl.intercept_


def centroides(X, y):
    """Média dos embeddings de cada intenção, renormalizada (cosseno = produto escalar)."""
    C = np.stack([X[y == k].mean(0) for k in range(int(y.max()) + 1)])
    return C / np.linalg.norm(C, axis=1, keepdims=True)


def similaridade_maxima(X, C):
    return (X @ C.T).max(1)


def probabilidades_cv(X, y, grupos):
    """Probabilidades e similaridade ao centroide mais próximo, fora da dobra (5 dobras estratificadas, frases do
    mesmo molde na mesma dobra)."""
    from sklearn.model_selection import StratifiedGroupKFold
    P = np.zeros((len(y), len(np.unique(y))))
    S = np.zeros(len(y))
    for treino, teste in StratifiedGroupKFold(n_splits=5, shuffle=True, random_state=42).split(X, y, grupos):
        P[teste] = _rl().fit(X[treino], y[treino]).predict_proba(X[teste])
        S[teste] = similaridade_maxima(X[teste], centroides(X[treino], y[treino]))
    return P, S


def escolher_limiar(P, y):
    """Menor limiar (arredondado para cima em 0,01) com precisão das respostas dadas >= PRECISAO_ALVO."""
    pred, conf = P.argmax(1), P.max(1)
    for limiar in np.arange(0.0, 1.0001, 0.01):
        dadas = conf >= limiar
        if dadas.sum() and (pred[dadas] == y[dadas]).mean() >= PRECISAO_ALVO:
            return round(float(limiar), 2)
    return None


def recusadas(P, S, limiar, sim_minima):
    """Mesma regra do app: confiança abaixo do limiar ou longe de todos os centroides."""
    if limiar is None:
        return np.ones(len(S), dtype=bool)
    return (P.max(1) < limiar) | (S < sim_minima)


def metricas(P, S, y_idx, limiar, sim_minima):
    """Acurácia/F1 sobre as frases conhecidas, com e sem recusa; `y_idx` só tem as conhecidas."""
    from sklearn.metrics import f1_score
    pred = P.argmax(1)
    recusa = recusadas(P, S, limiar, sim_minima)
    return {
        "acuracia_sem_recusa": float((pred == y_idx).mean()),
        "acuracia_com_recusa": float(((pred == y_idx) & ~recusa).mean()),
        "f1_macro": float(f1_score(y_idx, pred, average="macro")),
        "taxa_recusa_conhecidas": float(recusa.mean()),
    }


def baseline_tfidf(treino, teste):
    """TF-IDF (n-gramas de caracteres) + RL: acurácia no teste, com o limiar escolhido do mesmo jeito na CV."""
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    from sklearn.model_selection import StratifiedGroupKFold
    from sklearn.pipeline import make_pipeline

    def novo():
        return make_pipeline(TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 5), sublinear_tf=True),
                             LogisticRegression(C=C_RL, max_iter=3000, class_weight="balanced"))

    textos = np.array([normalizar_para_modelo(f) for _, f, _ in treino])
    y = np.array([i for i, _, _ in treino])
    grupos = np.array([g for _, _, g in treino])
    P = np.zeros((len(y), len(np.unique(y))))
    for a, b in StratifiedGroupKFold(n_splits=5, shuffle=True, random_state=42).split(textos, y, grupos):
        P[b] = novo().fit(textos[a], y[a]).predict_proba(textos[b])
    limiar = escolher_limiar(P, y)  # None: nenhuma confiança da CV chega a 0,9 de precisão
    modelo = novo().fit(textos, y)
    conhecidas = [(i, f) for i, f, _ in teste if i != DESCONHECIDA]
    fora = [f for i, f, _ in teste if i == DESCONHECIDA]
    Pt = modelo.predict_proba([normalizar_para_modelo(f) for _, f in conhecidas])
    yt = np.array([i for i, _ in conhecidas])
    Pf = modelo.predict_proba([normalizar_para_modelo(f) for f in fora])
    r = {"limiar": limiar, "acuracia_sem_recusa": float((modelo.classes_[Pt.argmax(1)] == yt).mean())}
    if limiar is not None:
        r["acuracia_com_recusa"] = float(((modelo.classes_[Pt.argmax(1)] == yt) & (Pt.max(1) >= limiar)).mean())
        r["taxa_recusa_fora_do_escopo"] = float((Pf.max(1) < limiar).mean())
    return r


def escolher_c(X, y, grupos):
    """C da RL com maior acurácia fora da dobra no treino (o teste não participa)."""
    global C_RL
    melhor = (-1.0, C_RL)
    for c in GRADE_C:
        C_RL = c
        acuracia = float((probabilidades_cv(X, y, grupos)[0].argmax(1) == y).mean())
        melhor = max(melhor, (acuracia, c))
    C_RL = melhor[1]


def avaliar():
    treino = ler("treino.csv", semente=1)
    teste = ler("teste.csv", semente=2)
    treino = [t for t in treino if t[0] != DESCONHECIDA]
    nomes = sorted({i for i, _, _ in treino})
    y = np.array([nomes.index(i) for i, _, _ in treino])
    grupos = np.array([g for _, _, g in treino])
    X = vetorizar([f for _, f, _ in treino])

    escolher_c(X, y, grupos)
    P_cv, S_cv = probabilidades_cv(X, y, grupos)
    limiar = escolher_limiar(P_cv, y)
    sim_minima = round(float(np.percentile(S_cv, PERCENTIL_SIMILARIDADE)), 2)
    classes, W, b = treinar_rl(X, y)
    assert list(classes) == list(range(len(nomes)))
    C = centroides(X, y)

    conhecidas = [t for t in teste if t[0] != DESCONHECIDA]
    fora = [t for t in teste if t[0] == DESCONHECIDA]
    Xt = vetorizar([f for _, f, _ in conhecidas])
    yt = np.array([nomes.index(i) for i, _, _ in conhecidas])
    Pt = prever(Xt, W, b)
    Xf = vetorizar([f for _, f, _ in fora])
    Pf = prever(Xf, W, b)
    r = metricas(Pt, similaridade_maxima(Xt, C), yt, limiar, sim_minima)
    r.update({
        "c": C_RL, "limiar": limiar, "similaridade_minima": sim_minima, "nomes": nomes, "centroides": C,
        "taxa_recusa_fora_do_escopo": float(recusadas(Pf, similaridade_maxima(Xf, C), limiar, sim_minima).mean()),
        "recusa_fora_so_pela_confianca": float((Pf.max(1) < limiar).mean()),
        "n_treino": len(treino), "n_teste_conhecidas": len(conhecidas), "n_teste_fora": len(fora),
        "precisao_cv_no_limiar": float((P_cv.argmax(1)[P_cv.max(1) >= limiar] == y[P_cv.max(1) >= limiar]).mean()),
        "cobertura_cv_no_limiar": float((P_cv.max(1) >= limiar).mean()),
        "acuracia_cv_sem_recusa": float((P_cv.argmax(1) == y).mean()),
    })
    from sklearn.metrics import confusion_matrix
    r["matriz"] = confusion_matrix(yt, Pt.argmax(1), labels=range(len(nomes))).tolist()
    r["baseline_tfidf"] = baseline_tfidf(treino, teste)
    return r, W, b


def gravar_config(r, W, b):
    cfg = json.loads(ARQ_CONFIG.read_text(encoding="utf-8"))
    cfg["intencoes"] = {
        "nomes": r["nomes"],
        "pesos": np.round(W, 6).tolist(),
        "vieses": np.round(b, 6).tolist(),
        "limiar": r["limiar"],
        "similaridadeCategoria": SIMILARIDADE_CATEGORIA,
        "centroides": np.round(r["centroides"], 6).tolist(),
        "similaridadeMinima": r["similaridade_minima"],
    }
    ARQ_CONFIG.write_text(json.dumps(cfg, ensure_ascii=False), encoding="utf-8")


def gravar_relatorio(r):
    n = r["nomes"]
    base = r["baseline_tfidf"]
    if base["limiar"] is None:
        linha_base = (f"| Baseline TF-IDF + RL | n/a | {base['acuracia_sem_recusa']:.3f} | - | n/a "
                      "(nenhuma confiança da CV chega a precisão 0,9, então não há limiar) |")
    else:
        linha_base = (f"| Baseline TF-IDF + RL (limiar {base['limiar']}) | {base['acuracia_com_recusa']:.3f} | "
                      f"{base['acuracia_sem_recusa']:.3f} | - | {base['taxa_recusa_fora_do_escopo']:.3f} |")
    linhas = [
        "# Intenções das perguntas (Tarefa 13)", "",
        f"RL softmax (C={r['c']:g}, escolhido na CV; pesos balanceados) sobre os embeddings do modelo do app; `DESCONHECIDA` não entra no treino.",
        f"Treino: {r['n_treino']} frases (com `{{cat}}` expandido). Teste: {r['n_teste_conhecidas']} frases conhecidas "
        f"+ {r['n_teste_fora']} fora do escopo.", "",
        f"Limiar de confiança: **{r['limiar']}** (menor valor com precisão >= {PRECISAO_ALVO} nas respostas dadas, "
        f"validação cruzada estratificada de 5 dobras: precisão {r['precisao_cv_no_limiar']:.3f}, "
        f"cobertura {r['cobertura_cv_no_limiar']:.3f}; acurácia da CV sem recusa {r['acuracia_cv_sem_recusa']:.3f}).", "",
        f"Só a confiança da RL recusa apenas {r['recusa_fora_so_pela_confianca']:.3f} das frases fora do escopo (ela só "
        "compara intenções entre si). Por isso o app também recusa frase longe de todos os centroides das intenções: "
        f"`similaridadeMinima` = **{r['similaridade_minima']}** (percentil {PERCENTIL_SIMILARIDADE} da similaridade, "
        "fora da dobra, das frases de treino ao centroide mais próximo; não foi ajustada no teste).", "",
        "| Modelo | Acurácia no teste (com recusa) | Acurácia no teste (sem recusa) | F1 macro | Recusa fora do escopo |",
        "|---|---|---|---|---|",
        f"| Embeddings + RL + centroides (app) | {r['acuracia_com_recusa']:.3f} | {r['acuracia_sem_recusa']:.3f} | "
        f"{r['f1_macro']:.3f} | {r['taxa_recusa_fora_do_escopo']:.3f} |",
        linha_base, "",
        "Acurácia \"com recusa\" conta como erro a frase conhecida recusada (confiança abaixo do limiar ou longe dos "
        "centroides).", "",
        "## Matriz de confusão (teste, frases conhecidas, sem recusa; linhas = real, colunas = previsto)", "",
        "| real / previsto | " + " | ".join(n) + " |",
        "|---|" + "---|" * len(n),
    ]
    for nome, linha in zip(n, r["matriz"]):
        linhas.append(f"| {nome} | " + " | ".join(str(v) for v in linha) + " |")
    RESULTADOS.mkdir(exist_ok=True)
    (RESULTADOS / "intencoes.md").write_text(chr(10).join(linhas) + chr(10), encoding="utf-8")


def main():
    r, W, b = avaliar()
    gravar_config(r, W, b)
    gravar_relatorio(r)
    print(json.dumps({k: v for k, v in r.items() if k not in ("matriz", "nomes", "centroides")}, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
