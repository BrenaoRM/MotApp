"""Avaliação das categorias personalizadas (Tarefa 2, passos 3, 5 e 7) e geração do `modelo_ia.json`.

Uso: `cd ia && python avaliar_categorias.py`
  1. ajusta os hiperparâmetros do modelo do app nos 35 usuários de validação (grade do passo 5);
  2. escolhe `limiarSugestao` (precisão das sugestões ≥ 0,85 na validação) e mede a cobertura;
  3. treina o prior final e grava `app/src/main/assets/ia/modelo_ia.json`;
  4. roda UMA vez nos 15 usuários de teste e grava `resultados/categorias_personalizadas.{md,json}` e
     `resultados/curva_aprendizado.png`.
"""
import copy
import itertools
import json
import re
import unicodedata

import numpy as np
from sklearn.metrics import f1_score

from exportar_modelo import ASSETS, RAIZ
from treinar_prior import PriorForaDaDobra, generica_do_lexico, lexico, treinar_final, validar, vetor_de
from usuarios_sinteticos import ESQUEMAS, GENERICAS, dividir_validacao_teste, gerar_usuarios

NS = [0, 5, 10, 20, 50, 100]
MODELOS = ["memoria", "tfidf_rl", "knn", "prototipos", "app"]
NOMES_MODELOS = {"memoria": "(1) Memória exata", "tfidf_rl": "(2) TF-IDF n-gramas + RL pessoal",
                 "knn": "(3) kNN k=5 (embeddings)", "prototipos": "(4) Só protótipos (zero-shot)",
                 "app": "(5) Modelo do app"}
PRECISAO_ALVO = 0.85
ARQ_CONFIG = ASSETS / "modelo_ia.json"
RESULTADOS = RAIZ / "resultados"

CONFIG_PADRAO = {
    "versaoModelo": 1, "dimensao": 384, "maxTokens": 64,
    "classificador": {"epocas": 150, "taxa": 0.5, "lambdaL2": 0.001,
                      "pesoPrototipo": 3.0, "alfaK": 10.0, "temperaturaPrior": 0.05,
                      "limiarSugestao": 0.55, "minimoExemplos": 3},
    "generico": {"nomes": GENERICAS,
                 "discricionarias": {"Restaurantes e Delivery": 0.12, "Lazer e Assinaturas": 0.10, "Compras": 0.10},
                 "pesos": None, "vieses": None, "vetoresNomes": None, "similaridadeMinimaMapeamento": 0.5,
                 "confiancaLexico": 0.9},
    "atipicos": {"contaminacao": 0.04, "arvores": 200, "amostra": 256, "semente": 42,
                 "minimoGastos": 20, "valorMinimoCentavos": 3000, "zLimite": 3.0,
                 "janelaDuplicataDias": 3, "pisoMad": 0.2, "mesesHistorico": 6},
    "lexico": None,
}
GRADE = {"pesoPrototipo": [1.0, 3.0, 10.0], "alfaK": [5.0, 10.0, 20.0],
         "lambdaL2": [1e-4, 1e-3, 1e-2], "epocas": [100, 150, 300]}


def chave_do_estabelecimento(nome):
    """Espelho de `data/EstabelecimentoCategoria.kt` (memória exata que o app usa hoje)."""
    s = unicodedata.normalize("NFD", nome)
    s = "".join(ch for ch in s if not unicodedata.category(ch).startswith("M")).lower()
    return re.sub(r"\s+", " ", s).strip()


# ---------------------------------------------------------------- modelo do app (espelho da T5)

def softmax(z):
    z = z - z.max(axis=-1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(axis=-1, keepdims=True)


def treinar_pessoal(X, y, pesos, n_classes, lam, taxa, epocas):
    """Descida do gradiente em lote completo a partir de W=0, b=0. `epocas` é uma lista: devolve
    {e: (W, b)} com o estado depois de e épocas."""
    W = np.zeros((n_classes, X.shape[1]))
    b = np.zeros(n_classes)
    Y = np.eye(n_classes)[y]
    w = pesos[:, None] / pesos.sum()
    estados, alvo = {}, set(epocas)
    for e in range(1, max(epocas) + 1):
        G = (softmax(X @ W.T + b) - Y) * w       # (N, C)
        W = W - taxa * (G.T @ X + lam * W)
        b = b - taxa * G.sum(axis=0)
        if e in alvo:
            estados[e] = (W.copy(), b.copy())
    return estados


def mapear_genericas(vetores_categorias, vetores_genericos, minimo):
    """g(c) = argmax_g cos(vetorNome_c, vetoresNomes_g) se ≥ minimo; senão -1 (não mapeada)."""
    S = vetores_categorias @ vetores_genericos.T
    g = S.argmax(axis=1)
    return np.where(S.max(axis=1) >= minimo, g, -1), S


def prior_generico_nas_categorias(s, mapa):
    """s: (N, 9) distribuição genérica. Categoria mapeada recebe s_g dividido entre as categorias que
    mapeiam para o mesmo g; as não mapeadas dividem igualmente a massa das genéricas que ninguém pegou.
    Normaliza; linha toda zero vira uniforme."""
    N, C = s.shape[0], len(mapa)
    q = np.zeros((N, C))
    pegas = {g for g in mapa if g >= 0}
    for c, g in enumerate(mapa):
        if g >= 0:
            q[:, c] = s[:, g] / sum(1 for gg in mapa if gg == g)
    soltas = [c for c, g in enumerate(mapa) if g < 0]
    if soltas:
        livre = s[:, [g for g in range(s.shape[1]) if g not in pegas]].sum(axis=1)
        q[:, soltas] = (livre / len(soltas))[:, None]
    tot = q.sum(axis=1, keepdims=True)
    return np.where(tot > 0, q / np.where(tot > 0, tot, 1), 1.0 / C)


def distribuicao_generica(s_emb, lex_idx, confianca):
    """Léxico casou → (1-confianca)/9 + confianca·one-hot(g*); senão softmax(Wg·x + bg)."""
    s = s_emb.copy()
    G = s.shape[1]
    for i, g in enumerate(lex_idx):
        if g >= 0:
            s[i] = (1 - confianca) / G
            s[i, g] += confianca
    return s


def prior_do_app(X, V, s, mapa, temperatura):
    q = prior_generico_nas_categorias(s, mapa)
    zs = softmax((X @ V.T) / temperatura)
    return 0.5 * q + 0.5 * zs


def mistura(p_pessoal, prior, n, alfaK):
    a = n / (n + alfaK)
    return a * p_pessoal + (1 - a) * prior


def memoria_exata(chaves, y):
    """chave do estabelecimento → último rótulo dado pela pessoa (o mais recente vence)."""
    return {k: int(c) for k, c in zip(chaves, y)}


def aplicar_memoria(p, rotulo_memoria):
    """Precedência: se a chave exata já foi rotulada pela pessoa, esse rótulo vence qualquer modelo
    (p vira one-hot nele). `rotulo_memoria` = -1 onde não há memória."""
    p = p.copy()
    hit = np.asarray(rotulo_memoria) >= 0
    p[hit] = 0.0
    p[hit, np.asarray(rotulo_memoria)[hit]] = 1.0
    return p, hit


def modelo_app(cc, cg, lex, V, ex_X, ex_y, ex_chaves, X, descs, chaves):
    """Modelo do app completo para consultas (X, descs, chaves), com os intermediários (golden da T5).
    cc = classificador, cg = generico (nomes, pesos, vieses, vetoresNomes, ...), V = vetorNome das categorias."""
    C, n = len(V), len(ex_y)
    Xtr = np.vstack([ex_X, V]) if n else V
    ytr = np.concatenate([ex_y, np.arange(C)]).astype(int)
    w = np.concatenate([np.ones(n), np.full(C, cc["pesoPrototipo"])])
    W, b = treinar_pessoal(Xtr, ytr, w, C, cc["lambdaL2"], cc["taxa"], [cc["epocas"]])[cc["epocas"]]
    p_pessoal = softmax(X @ W.T + b)
    nomes = cg["nomes"]
    lex_idx = np.array([(lambda g: -1 if g is None else g)(generica_do_lexico(d, lex, nomes)) for d in descs])
    s_emb = softmax(X @ np.asarray(cg["pesos"]).T + np.asarray(cg["vieses"]))
    s = distribuicao_generica(s_emb, lex_idx, cg["confiancaLexico"])
    mapa, _ = mapear_genericas(V, np.asarray(cg["vetoresNomes"]), cg["similaridadeMinimaMapeamento"])
    prior = prior_do_app(X, V, s, mapa, cc["temperaturaPrior"])
    alfa = n / (n + cc["alfaK"])
    memo = memoria_exata(ex_chaves, ex_y)
    rot = np.array([memo.get(k, -1) for k in chaves])
    p_final, hit = aplicar_memoria(mistura(p_pessoal, prior, n, cc["alfaK"]), rot)
    pred = p_final.argmax(axis=1)
    cont = np.bincount(np.asarray(ex_y, dtype=int), minlength=C)
    sugere = hit | ((p_final.max(axis=1) >= cc["limiarSugestao"])
                    & ((cont[pred] >= cc["minimoExemplos"]) | (mapa[pred] >= 0)))
    return {"mapa": mapa, "genericaLexico": lex_idx, "s": s, "prior": prior, "pPessoal": p_pessoal,
            "alfa": alfa, "rotuloMemoria": rot, "pFinal": p_final, "sugestao": np.where(sugere, pred, -1)}


# ---------------------------------------------------------------- dados por usuário

class DadosUsuario:
    def __init__(self, u, prior_oof, lex, vetores_genericos, cfg_gen):
        t = u.transacoes
        self.u = u
        self.cats = u.categorias
        self.y = t.categoria_usuario.map(self.cats.index).to_numpy()
        self.descs = t.descricao.tolist()
        self.merchants = t.merchant.to_numpy()
        self.chaves = [chave_do_estabelecimento(d) for d in self.descs]
        self.X = vetor_de(self.descs).astype(np.float64)
        self.V = vetor_de(self.cats).astype(np.float64)
        self.s_emb = prior_oof.probabilidades(self.X.astype(np.float32), self.merchants)
        self.lex = np.array([(lambda g: -1 if g is None else g)(generica_do_lexico(d, lex)) for d in self.descs])
        self.mapa, _ = mapear_genericas(self.V, vetores_genericos, cfg_gen["similaridadeMinimaMapeamento"])
        self.s_com_lex = distribuicao_generica(self.s_emb, self.lex, cfg_gen["confiancaLexico"])

    def prior(self, idx, temperatura):
        return prior_do_app(self.X[idx], self.V, self.s_com_lex[idx], self.mapa, temperatura)

    def treino_app(self, n, pesoPrototipo):
        C = len(self.cats)
        Xtr = np.vstack([self.X[:n], self.V])
        ytr = np.concatenate([self.y[:n], np.arange(C)])
        w = np.concatenate([np.ones(n), np.full(C, pesoPrototipo)])
        return Xtr, ytr, w


def prever_app(d, n, cc, epocas_lista=None):
    """Probabilidades finais do modelo do app para as transações n.. (uma por época pedida)."""
    resto = np.arange(n, len(d.y))
    Xtr, ytr, w = d.treino_app(n, cc["pesoPrototipo"])
    epocas_lista = epocas_lista or [cc["epocas"]]
    estados = treinar_pessoal(Xtr, ytr, w, len(d.cats), cc["lambdaL2"], cc["taxa"], epocas_lista)
    prior = d.prior(resto, cc["temperaturaPrior"])
    return resto, prior, {e: softmax(d.X[resto] @ W.T + b) for e, (W, b) in estados.items()}


# ---------------------------------------------------------------- outros modelos

def prever_memoria(d, n, resto):
    memoria = {}
    for k in range(n):
        memoria[d.chaves[k]] = d.y[k]
    return np.array([memoria.get(d.chaves[i], -1) for i in resto])


def prever_tfidf(d, n, resto):
    if n == 0:
        return np.full(len(resto), -1)
    ytr = d.y[:n]
    if len(set(ytr)) == 1:
        return np.full(len(resto), ytr[0])
    from categorizer import make_model
    m = make_model("lr").set_params(tfidf__min_df=1)  # min_df=2 do FinIA zera o vocabulário com n pequeno
    m.fit(d.descs[:n], ytr)
    return m.predict([d.descs[i] for i in resto])


def prever_knn(d, n, resto):
    if n == 0:
        return np.full(len(resto), -1)
    from sklearn.neighbors import KNeighborsClassifier
    knn = KNeighborsClassifier(n_neighbors=min(5, n), metric="cosine").fit(d.X[:n], d.y[:n])
    return knn.predict(d.X[resto])


def prever_prototipos(d, n, resto):
    return (d.X[resto] @ d.V.T).argmax(axis=1)


# ---------------------------------------------------------------- métricas

def _metricas(y, pred, visto):
    m = {"acuracia": float((pred == y).mean()),
         "f1": float(f1_score(y, pred, labels=sorted(set(y)), average="macro", zero_division=0))}
    for nome, mask in (("visto", visto), ("novo", ~visto)):
        m[f"acuracia_{nome}"] = float((pred[mask] == y[mask]).mean()) if mask.any() else None
    return m


def _media(lista):
    out = {}
    for k in lista[0]:
        vals = [m[k] for m in lista if m[k] is not None]
        out[k] = float(np.mean(vals)) if vals else None
    return out


def avaliar_usuarios(dados, cfg):
    """Curva de aprendizado: r[modelo]["n=K"] com médias entre usuários, por esquema e visto/novo."""
    cc = cfg["classificador"]
    por = {m: {n: [] for n in NS} for m in MODELOS}
    sugestoes = []  # (p_max, acertou, sugere?) do modelo do app
    for d in dados:
        for n in NS:
            if n >= len(d.y):
                continue
            resto, prior, pp = prever_app(d, n, cc)
            memo = prever_memoria(d, n, resto)
            p, hit = aplicar_memoria(mistura(pp[cc["epocas"]], prior, n, cc["alfaK"]), memo)
            y = d.y[resto]
            visto = np.isin(d.merchants[resto], d.merchants[:n])
            preds = {"memoria": memo, "tfidf_rl": prever_tfidf(d, n, resto),
                     "knn": prever_knn(d, n, resto), "prototipos": prever_prototipos(d, n, resto),
                     "app": p.argmax(axis=1)}
            for m in MODELOS:
                por[m][n].append((d.u.esquema, _metricas(y, np.asarray(preds[m]), visto)))
            sugestoes.append((n, p.max(axis=1), preds["app"] == y, pode_sugerir(d, n, preds["app"]) | hit))
    r = {}
    for m in MODELOS:
        r[m] = {}
        for n in NS:
            lst = por[m][n]
            r[m][f"n={n}"] = _media([x for _, x in lst])
            for e in ESQUEMAS:
                sub = [x for esq, x in lst if esq == e]
                if sub:
                    r[m][f"n={n}"][f"esquema_{e}"] = _media(sub)
    return r, sugestoes


def pode_sugerir(d, n, pred):
    """Trava da T5: a categoria prevista tem ≥ minimoExemplos exemplos rotulados OU é mapeada a uma genérica."""
    cont = np.bincount(d.y[:n], minlength=len(d.cats))
    return (cont[pred] >= CONFIG_PADRAO["classificador"]["minimoExemplos"]) | (d.mapa[pred] >= 0)


def limiar_e_cobertura(sugestoes, alvo=PRECISAO_ALVO):
    """Menor limiar t (passo 0,01) em que a precisão das sugestões feitas (p ≥ t e trava ok) é ≥ alvo."""
    p = np.concatenate([s[1] for s in sugestoes])
    ok = np.concatenate([s[2] for s in sugestoes])
    trava = np.concatenate([s[3] for s in sugestoes])
    for t in np.round(np.arange(0.0, 1.0, 0.01), 2):
        feitas = (p >= t) & trava
        if feitas.any() and ok[feitas].mean() >= alvo:
            return float(t), float(feitas.mean()), float(ok[feitas].mean())
    return None, 0.0, None


def precisao_cobertura(sugestoes, t):
    out = {}
    for n in NS:
        ss = [s for s in sugestoes if s[0] == n]
        if not ss:
            continue
        p = np.concatenate([s[1] for s in ss])
        ok = np.concatenate([s[2] for s in ss])
        feitas = (p >= t) & np.concatenate([s[3] for s in ss])
        out[f"n={n}"] = {"cobertura": float(feitas.mean()),
                         "precisao": float(ok[feitas].mean()) if feitas.any() else None}
    return out


# ---------------------------------------------------------------- ajuste (só validação)

def ajustar(dados, cfg):
    """Grade do passo 5 sobre os usuários de validação. Critério: acurácia média do modelo do app em
    n ∈ {5,10,20,50,100} (em n=0 α=0 e os hiperparâmetros não mudam nada)."""
    cc = cfg["classificador"]
    soma = {}
    conta = {}
    for d in dados:
        for n in NS[1:]:
            if n >= len(d.y):
                continue
            for peso, lam in itertools.product(GRADE["pesoPrototipo"], GRADE["lambdaL2"]):
                c2 = dict(cc, pesoPrototipo=peso, lambdaL2=lam)
                resto, prior, pp = prever_app(d, n, c2, GRADE["epocas"])
                y = d.y[resto]
                memo = prever_memoria(d, n, resto)
                for ep, pe in pp.items():
                    for a in GRADE["alfaK"]:
                        k = (peso, a, lam, ep)
                        acc = float((aplicar_memoria(mistura(pe, prior, n, a), memo)[0].argmax(axis=1) == y).mean())
                        soma[k] = soma.get(k, 0.0) + acc
                        conta[k] = conta.get(k, 0) + 1
    medias = {k: soma[k] / conta[k] for k in soma}
    melhor = max(medias, key=medias.get)
    return dict(zip(["pesoPrototipo", "alfaK", "lambdaL2", "epocas"], melhor)), medias


# ---------------------------------------------------------------- config

def carregar_config():
    return json.loads(ARQ_CONFIG.read_text(encoding="utf-8"))


def montar_config(hiper, limiar, prior_final):
    cfg = copy.deepcopy(CONFIG_PADRAO)
    cfg["classificador"].update(hiper)
    cfg["classificador"]["epocas"] = int(cfg["classificador"]["epocas"])
    cfg["classificador"]["limiarSugestao"] = limiar
    g = cfg["generico"]
    arred = lambda a: [round(float(v), 6) for v in a]  # noqa: E731
    g["pesos"] = [arred(l) for l in prior_final["pesos"]]
    g["vieses"] = arred(prior_final["vieses"])
    g["vetoresNomes"] = [arred(l) for l in prior_final["vetoresNomes"]]
    cfg["lexico"] = prior_final["lexico"]
    return cfg


def _dados(usuarios, cfg, prior_oof=None, vetores_genericos=None):
    prior_oof = prior_oof or PriorForaDaDobra()
    vg = vetores_genericos if vetores_genericos is not None else vetor_de(GENERICAS).astype(np.float64)
    lex = cfg["lexico"] or lexico()
    return [DadosUsuario(u, prior_oof, lex, vg, cfg["generico"]) for u in usuarios]


def avaliar(seed=42, cfg=None, usuarios="teste"):
    """Avaliação com o `modelo_ia.json` gravado (ou `cfg`) nos usuários de teste (ou validação)."""
    cfg = cfg or carregar_config()
    val, teste = dividir_validacao_teste(gerar_usuarios(seed), seed)
    r, sug = avaliar_usuarios(_dados(teste if usuarios == "teste" else val, cfg), cfg)
    r["sugestoes"] = precisao_cobertura(sug, cfg["classificador"]["limiarSugestao"])
    p = np.concatenate([s[1] for s in sug])
    feitas = (p >= cfg["classificador"]["limiarSugestao"]) & np.concatenate([s[3] for s in sug])
    ok = np.concatenate([s[2] for s in sug])
    r["sugestoes"]["total"] = {"cobertura": float(feitas.mean()), "precisao": float(ok[feitas].mean())}
    return r


# ---------------------------------------------------------------- relatório

def _f(v):
    return "—" if v is None else f"{v:.3f}".replace(".", ",")


def grafico(r, caminho):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig, ax = plt.subplots(figsize=(7, 4.2), dpi=120)
    for m in MODELOS:
        ax.plot(NS, [r[m][f"n={n}"]["acuracia"] for n in NS], marker="o", label=NOMES_MODELOS[m])
    ax.set_xscale("symlog", linthresh=5)
    ax.set_xticks(NS)
    ax.set_xticklabels([str(n) for n in NS])
    ax.set_xlabel("n exemplos rotulados (os n primeiros por data)")
    ax.set_ylabel("acurácia no restante (média dos 15 usuários de teste)")
    ax.set_ylim(0, 1)
    ax.grid(alpha=0.3)
    ax.legend(fontsize=8, loc="upper left")
    fig.tight_layout()
    fig.savefig(caminho)
    plt.close(fig)


def relatorio(r, val_info, prior_info, cfg, dados_val):
    cc = cfg["classificador"]
    L = ["# Categorias personalizadas e prior genérico (Tarefa 2)", "",
         "Gerado por `python avaliar_categorias.py` (números brutos em `categorias_personalizadas.json`).",
         "Data: 2026-11-26. Embeddings do modelo do app (`assets/ia/modelo.onnx` int8 + `tokenizador.json`).", "",
         "## Dados",
         "50 usuários do FinIA (`data_gen.generate(n_per_arch=10, seed=42)`, só gastos), cada um com um esquema",
         "sorteado (13/13/12/12): (a) 9 genéricas; (b) renomeadas; (c) agrupadas (Essenciais, Rolê, Transporte,",
         "Outros); (d) renomeadas + \"Pet\" (3 gastos/mês de PETZ, COBASI, CLINICA VETERINARIA, AGROPET, e o",
         "PET SHOP AMIGO FIEL do FinIA passa a ser Pet) + \"Presentes\" (2 gastos/mês de floricultura, Cacau Show,",
         "loja de presentes e brinquedos). Divisão estratificada por esquema: 35 de validação e 15 de teste",
         "(4/4/4/3). Hiperparâmetros e limiar escolhidos só na validação; o teste rodou uma vez.", "",
         "Curva de aprendizado: transações de cada usuário em ordem de data; os n primeiros são rotulados, prevê-se",
         "o resto. \"Visto\" = o `merchant` verdadeiro aparece entre os n primeiros.", "",
         "## Prior genérico (`treinar_prior.py`)",
         f"RL multinomial (`LogisticRegression(C=1.0, max_iter=2000)`) sobre embeddings de {prior_info['n']} gastos",
         "do FinIA (sem os estabelecimentos Pet) → 9 genéricas. GroupKFold(5) por merchant (estabelecimento novo):", "",
         "| Prior | Acurácia (estabelecimento novo) |", "|---|---|",
         f"| Só embeddings | {_f(prior_info['acuracia_so_embeddings'])} |",
         f"| Prior + léxico do FinIA | {_f(prior_info['acuracia_prior_mais_lexico'])} |", "",
         f"O léxico casa em {_f(prior_info['cobertura_lexico'])} dos gastos e acerta {_f(prior_info['acuracia_lexico_quando_casa'])}",
         "quando casa. **Número otimista:** o léxico contém nomes de estabelecimentos do próprio conjunto de teste",
         "(netflix, spotify, uber, ifood, steam, shopee…), o que é vazamento no GroupKFold. O número honesto do",
         "prior aprendido é o só-embeddings.", "",
         "Na avaliação abaixo o prior de cada transação vem do modelo da dobra que **não** viu aquele merchant",
         "(estabelecimentos Pet/Presentes, que não estão no FinIA, usam o modelo final, que também não os viu).", "",
         "## Hiperparâmetros (validação, 35 usuários)",
         "Grade: `pesoPrototipo` {1,3,10} × `alfaK` {5,10,20} × `lambdaL2` {1e-4,1e-3,1e-2} × `epocas` {100,150,300};",
         "critério: acurácia média do modelo do app em n ∈ {5,10,20,50,100}.", "",
         "| pesoPrototipo | alfaK | lambdaL2 | epocas | acurácia validação |", "|---|---|---|---|---|"]
    top = sorted(val_info["medias"].items(), key=lambda kv: -kv[1])[:5]
    for (peso, a, lam, ep), v in top:
        L.append(f"| {peso:g} | {a:g} | {lam:g} | {ep} | {_f(v)} |")
    padrao = val_info["medias"][(3.0, 10.0, 1e-3, 150)]
    L += ["", f"Padrão do brief (3, 10, 1e-3, 150): {_f(padrao)}. Escolhido: `pesoPrototipo`={cc['pesoPrototipo']:g},",
          f"`alfaK`={cc['alfaK']:g}, `lambdaL2`={cc['lambdaL2']:g}, `epocas`={cc['epocas']}.", "",
          f"**`limiarSugestao` = {_f(cc['limiarSugestao'])}**: menor limiar (passo 0,01) em que a precisão das sugestões",
          f"feitas é ≥ 0,85 na validação (todas as n juntas). Na validação: precisão {_f(val_info['precisao'])},",
          f"cobertura {_f(val_info['cobertura'])} (fração dos lançamentos que recebe pré-seleção).", "",
          "Mapeamento categoria → genérica (cos ≥ 0,5 com o vetor do nome genérico):", ""]
    vistos = {}
    for d in dados_val:
        for c, g in zip(d.cats, d.mapa):
            vistos[c] = GENERICAS[g] if g >= 0 else "— (não mapeada)"
    L += [", ".join(f"{c} → {g}" for c, g in vistos.items()), ""]
    L += ["## Resultados no teste (15 usuários, rodado uma vez)", "",
          "### Acurácia × n (média entre usuários)", "",
          "| Modelo | " + " | ".join(f"n={n}" for n in NS) + " |", "|---|" + "---|" * len(NS)]
    for m in MODELOS:
        L.append(f"| {NOMES_MODELOS[m]} | " + " | ".join(_f(r[m][f'n={n}']['acuracia']) for n in NS) + " |")
    L += ["", "### F1 macro × n", "", "| Modelo | " + " | ".join(f"n={n}" for n in NS) + " |",
          "|---|" + "---|" * len(NS)]
    for m in MODELOS:
        L.append(f"| {NOMES_MODELOS[m]} | " + " | ".join(_f(r[m][f'n={n}']['f1']) for n in NS) + " |")
    L += ["", "### Acurácia por esquema (modelo × n × esquema)", "",
          "| Modelo | n | (a) genéricas | (b) renomeadas | (c) agrupadas | (d) + Pet/Presentes |", "|---|---|---|---|---|---|"]
    for m in MODELOS:
        for n in NS:
            q = r[m][f"n={n}"]
            L.append(f"| {NOMES_MODELOS[m]} | {n} | " + " | ".join(
                _f(q.get(f"esquema_{e}", {}).get("acuracia")) for e in ESQUEMAS) + " |")
    L += ["", "### Estabelecimento já visto / novo (acurácia)", "",
          "| Modelo | " + " | ".join(f"n={n} visto / novo" for n in NS[1:]) + " |", "|---|" + "---|" * len(NS[1:])]
    for m in MODELOS:
        L.append(f"| {NOMES_MODELOS[m]} | " + " | ".join(
            f"{_f(r[m][f'n={n}']['acuracia_visto'])} / {_f(r[m][f'n={n}']['acuracia_novo'])}" for n in NS[1:]) + " |")
    s = r["sugestoes"]
    L += ["", f"### Sugestões no teste (limiar {_f(cc['limiarSugestao'])})", "",
          "| n | cobertura | precisão |", "|---|---|---|"]
    for n in NS:
        if f"n={n}" in s:
            L.append(f"| {n} | {_f(s[f'n={n}']['cobertura'])} | {_f(s[f'n={n}']['precisao'])} |")
    L.append(f"| todas | {_f(s['total']['cobertura'])} | {_f(s['total']['precisao'])} |")
    L += ["", "![curva](curva_aprendizado.png)", "", LEITURA, "", ESPEC, ""]
    return "\n".join(L)


LEITURA = """## Leitura
- A memória exata do app (chave = descrição inteira sem acento) quase nunca acerta nestes dados: a descrição de
  extrato muda a cada compra (prefixo, número, cidade, erro de digitação). O TF-IDF de n-gramas acerta ~99% dos
  estabelecimentos já vistos com as mesmas descrições ruidosas.
- O modelo do app vence todos até n=50; em n=100 o TF-IDF + RL pessoal passa à frente (acurácia e F1). O ganho do
  modelo do app está no começo (prior + protótipos) e nos estabelecimentos novos.
- O mapeamento por cosseno do nome erra vários nomes (Mercado → Compras, Comida fora → Alimentação, Farmácia →
  Alimentação, Diversão → Outros, Rolê → Moradia, Essenciais → Saúde); é o que mais derruba os esquemas (b)–(d) em n=0.
- O melhor ponto da grade ficou na borda (pesoPrototipo=1, lambdaL2=1e-4, epocas=300): o modelo pessoal ainda está
  subajustado; a grade é a do plano e não foi estendida.
- O limiar é um só para todos os n; em n=0 a precisão das sugestões fica abaixo de 0,85.
- Os números do app em n=0 incluem o léxico, montado com nomes de estabelecimentos do próprio FinIA: são otimistas
  (ver a seção do prior, embeddings sem léxico).
- A vantagem sobre a memória exata depende do ruído das descrições sintéticas do FinIA; num extrato real com
  descrições estáveis a memória acertaria bem mais (e agora tem precedência no modelo do app)."""


ESPEC = r"""## Especificação do modelo do app (a T5 replica exatamente)

Entradas: categorias da pessoa `c = 0..C-1` com `vetorNome_c = vetorizar(normalizar_para_modelo(nome_c))`;
exemplos rotulados `(x_i, y_i)` (só de categorias que ainda existem; `n` = quantos são);
`x = vetorizar(normalizar_para_modelo(descricao))` (vetores L2-normalizados, 384).

1. **Modelo pessoal.** Treino = exemplos (peso 1) + um protótipo por categoria `(vetorNome_c, c, peso = pesoPrototipo)`.
   `W = 0 (C×384)`, `b = 0`. Por `epocas` épocas, lote completo, `taxa`:
   `P = softmax(X·Wᵀ + b)` (por linha, subtraindo o máximo); `G_ic = peso_i · (P_ic − 1[y_i=c]) / Σ peso`;
   `W ← W − taxa · (Gᵀ·X + lambdaL2 · W)`; `b ← b − taxa · Σ_i G_i` (viés sem L2). `p_pessoal = softmax(W·x + b)`.
2. **Distribuição genérica `s` (9).** Léxico: `t = sem_acento(normalizar_para_modelo(descricao))`, onde
   `normalizar_para_modelo` (NFKC, minúsculas, `[\d\W_]` → espaço) já tirou as sequências de cidades e as palavras de
   BOILERPLATE antes do match, e `sem_acento(u) = NFKD(u)` codificado em ASCII **descartando** o que não for ASCII
   (`unicodedata.normalize("NFKD", u).encode("ascii", "ignore")`: ø, ß e símbolos somem, não viram letra). Um termo
   casa se aparece como **palavra(s) inteira(s)**: `(" " + termo + " ")` contido em `(" " + t + " ")` (termos de duas
   palavras, como "smart fit", casam a sequência; "prime" não casa "primeiro"). Se algum casa, `g*` = a categoria de **menor
   índice em `generico.nomes`** entre as que casaram (é a ordem "1º acerto vence" do FinIA) e
   `s_g = (1 − confiancaLexico)/9 + confiancaLexico · 1[g = g*]`. Se nenhum casa, `s = softmax(pesos·x + vieses)`.
3. **Mapeamento.** `g(c) = argmax_g cos(vetorNome_c, vetoresNomes_g)` se esse máximo ≥ `similaridadeMinimaMapeamento`;
   senão `c` é não mapeada.
4. **Prior nas categorias da pessoa.** `q_c = s_{g(c)} / #{c' : g(c') = g(c)}` para as mapeadas; as não mapeadas
   dividem igualmente `Σ s_g` das genéricas que nenhuma categoria pegou. `q ← q / Σ q` (se `Σ q = 0`, uniforme).
   `z_c = softmax_c(cos(x, vetorNome_c) / temperaturaPrior)`. `prior = 0,5·q + 0,5·z`.
5. **Mistura.** `α = n / (n + alfaK)`; `p_final = α·p_pessoal + (1 − α)·prior` (soma 1).
6. **Precedência da memória exata.** Se a chave do estabelecimento da transação (`chaveDoEstabelecimento`: NFD, tira
   `\p{M}`, minúsculas, espaços colapsados, trim) já foi rotulada pela pessoa (o rótulo mais recente vale), esse
   rótulo vence antes de qualquer modelo: `p_final` vira one-hot nele e ele é sempre sugerido (ignora limiar e
   `minimoExemplos`). Assim o léxico nunca sobrepõe um rótulo da pessoa para o mesmo estabelecimento.
7. **Sugestão.** Sem memória: `c* = argmax p_final`; sugere se `p_final[c*] ≥ limiarSugestao` **e** (`c*` tem
   ≥ `minimoExemplos` exemplos rotulados **ou** `c*` é mapeada).

Referência numérica para a T5: `app/src/test/resources/ia/golden_classificador.json` (gerado por
`python gerar_golden_classificador.py` com `modelo_app`): entradas pequenas e todos os intermediários (`mapa`, `genericaLexico`,
`s`, `prior`, `pPessoal`, `alfa`, `rotuloMemoria`, `pFinal`, `sugestao`).

Python usa float64; em Float (Kotlin) as probabilidades devem bater em ~1e-4."""


def main():
    usuarios = gerar_usuarios(42)
    val, teste = dividir_validacao_teste(usuarios, 42)
    cfg = copy.deepcopy(CONFIG_PADRAO)
    cfg["lexico"] = lexico()
    prior_oof = PriorForaDaDobra()
    vg = vetor_de(GENERICAS).astype(np.float64)
    dados_val = _dados(val, cfg, prior_oof, vg)

    hiper, medias = ajustar(dados_val, cfg)
    print("hiperparâmetros:", hiper)
    cfg["classificador"].update(hiper)
    _, sug_val = avaliar_usuarios(dados_val, cfg)
    limiar, cob, prec = limiar_e_cobertura(sug_val)
    print("limiar:", limiar, "cobertura:", cob, "precisão:", prec)

    cfg = montar_config(hiper, limiar, treinar_final())
    ARQ_CONFIG.write_text(json.dumps(cfg, ensure_ascii=False), encoding="utf-8")

    r = avaliar(42, cfg)  # teste: uma vez
    prior_info = validar()
    RESULTADOS.mkdir(exist_ok=True)
    bruto = {"teste": r, "prior": prior_info, "hiperparametros": hiper,
             "validacao": {"limiar": limiar, "cobertura": cob, "precisao": prec,
                           "grade": {str(k): v for k, v in medias.items()}}}
    (RESULTADOS / "categorias_personalizadas.json").write_text(
        json.dumps(bruto, ensure_ascii=False, indent=2), encoding="utf-8")
    grafico(r, RESULTADOS / "curva_aprendizado.png")
    (RESULTADOS / "categorias_personalizadas.md").write_text(
        relatorio(r, {"medias": medias, "cobertura": cob, "precisao": prec}, prior_info, cfg, dados_val),
        encoding="utf-8")
    print(json.dumps({m: {k: r[m][k]["acuracia"] for k in r[m]} for m in MODELOS}, indent=1))


if __name__ == "__main__":
    main()
