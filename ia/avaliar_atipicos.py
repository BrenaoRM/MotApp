"""Avaliação do detector de gastos fora do padrão (Tarefa 7, passo 8): a tabela de anomalias do FinIA com as
pontuações do Kotlin.

Uso: `cd ia && python avaliar_atipicos.py`
  1. grava os gastos dos 50 usuários do FinIA em `app/build/ia/gastos_avaliacao.json`;
  2. roda `DetectorDeAtipicosTest.exportaPontuacoesParaAvaliacao` (Gradle, `-Dia.exportar=true`), que grava
     `app/build/ia/pontuacoes.json` (IsolationForest.kt + DetectorDeAtipicos.kt, com a chave do app e a do FinIA);
  3. mede precisão, revocação, F1 e AP a 4% (por usuário, como o `evaluate.py` do FinIA) e a revocação por tipo,
     e grava `resultados/atipicos.md`.
Categoria: a verdadeira do gerador (o FinIA usava a prevista, ~97% certa no cenário "conhecidos").
"""
import json
import math
import subprocess
import unicodedata

import numpy as np
from sklearn.metrics import average_precision_score, precision_recall_fscore_support

from exportar_modelo import ASSETS, RAIZ, gastos_finia  # antes dos módulos do FinIA: põe `finai/` no path

import anomaly  # noqa: E402
from data_gen import CATEGORIAS  # noqa: E402
from text_utils import merchant_key  # noqa: E402

BUILD = RAIZ.parent / "app" / "build" / "ia"
RESULTADOS = RAIZ / "resultados"
CFG = json.loads((ASSETS / "modelo_ia.json").read_text(encoding="utf-8"))["atipicos"]
LIMIAR_BASE = 3.5  # zscore_baseline do FinIA


def chave_do_app(texto, categoria):
    """`chaveDoEstabelecimento` do app; igual ao nome da categoria (ou vazia) → `#cat<id>`."""
    def norm(s):
        s = "".join(c for c in unicodedata.normalize("NFD", s) if not unicodedata.combining(c))
        return " ".join(s.lower().split())
    k = norm(texto or "")
    cid = CATEGORIAS.index(categoria) + 1
    return f"#cat{cid}" if not k or k == norm(categoria) else k


def preparar():
    gastos = gastos_finia()
    gastos["valorCentavos"] = (gastos.amount.abs() * 100).round().astype(int)
    gastos["amount"] = -gastos["valorCentavos"] / 100
    usuarios = []
    for uid, g in gastos.groupby("user_id"):
        usuarios.append({"usuario": uid, "gastos": [
            {"id": int(i), "data": str(r.date.date()), "valorCentavos": int(r.valorCentavos),
             "categoriaId": CATEGORIAS.index(r.category) + 1, "chaveApp": chave_do_app(r.description, r.category),
             "chaveFinia": merchant_key(r.description)}
            for i, r in g.iterrows()]})
    BUILD.mkdir(parents=True, exist_ok=True)
    (BUILD / "gastos_avaliacao.json").write_text(json.dumps(usuarios, ensure_ascii=False), encoding="utf-8")
    return gastos


def pontuar_no_kotlin():
    raiz = RAIZ.parent
    subprocess.run([str(raiz / "gradlew.bat"), "testDebugUnitTest", "--tests",
                    "*DetectorDeAtipicosTest.exportaPontuacoesParaAvaliacao", "--rerun", "-q", "-Dia.exportar=true"],
                   cwd=raiz, check=True, shell=False)
    return {u["usuario"]: u["variantes"] for u in json.loads((BUILD / "pontuacoes.json").read_text(encoding="utf-8"))}


def metricas(por_usuario):
    """por_usuario: lista de (rótulos, marcados, pontuação, tipos). Agregado como o evaluate.py do FinIA."""
    yt = np.concatenate([u[0] for u in por_usuario])
    yp = np.concatenate([u[1] for u in por_usuario])
    p, r, f, _ = precision_recall_fscore_support(yt, yp, average="binary", zero_division=0)
    aps = [average_precision_score(u[0], u[2]) for u in por_usuario if u[0].sum() > 0]
    tipos = {}
    for rot, marc, _, tp in por_usuario:
        for t in sorted(set(tp[rot == 1])):
            tipos.setdefault(t, []).append(marc[(rot == 1) & (tp == t)])
    return dict(P=p, R=r, F1=f, AP=float(np.mean(aps)), n_marcados=int(yp.sum()),
                tipos={t: float(np.concatenate(v).mean()) for t, v in sorted(tipos.items())})


def main():
    gastos = preparar()
    kotlin = pontuar_no_kotlin()
    linhas = {"finia": [], "base": [], "kt_finia": [], "kt_app": [], "detector": []}
    motivos = {}
    for uid, g in gastos.groupby("user_id"):
        rot = g.is_anomaly.astype(int)
        tp = g.anomaly_type.fillna("").astype(str)
        # FinIA original (sklearn, 300 árvores, `contamination=0.04` no predict) e o baseline z_cat > 3,5
        d = anomaly.detect(g, cat_col="category", contamination=CFG["contaminacao"])
        linhas["finia"].append((d.is_anomaly.astype(int).values, d.anom_flag.values, d.anom_score.values,
                                d.anomaly_type.fillna("").astype(str).values))
        linhas["base"].append((d.is_anomaly.astype(int).values, anomaly.zscore_baseline(d).values,
                               d.z_cat.clip(lower=-5).values, d.anomaly_type.fillna("").astype(str).values))
        k = math.ceil(CFG["contaminacao"] * len(g) - 1e-6)
        for variante, nome in [("chaveFinia", "kt_finia"), ("chaveApp", "kt_app")]:
            v = kotlin[uid][variante]
            s = {o["id"]: o["pontuacao"] for o in v["pontuacoes"]}
            score = np.array([s[i] for i in g.index])
            topo = set(sorted(s, key=lambda i: (-s[i], i))[:k])
            linhas[nome].append((rot.values, np.array([int(i in topo) for i in g.index]), score, tp.values))
            if variante == "chaveApp":
                lista = {o["id"]: o["motivo"] for o in v["atipicos"]}
                linhas["detector"].append((rot.values, np.array([int(i in lista) for i in g.index]), score, tp.values))
                for i, m in lista.items():
                    certo = "anomalia" if rot[i] else "normal"
                    motivos.setdefault(m, {"anomalia": 0, "normal": 0})[certo] += 1

    nomes = {
        "finia": "FinIA original (sklearn, 300 árvores, `contamination` 4%)",
        "base": f"Baseline do FinIA (z categoria > {LIMIAR_BASE})",
        "kt_finia": "IsolationForest.kt, chave do FinIA (`merchant_key`), top 4%",
        "kt_app": "IsolationForest.kt, chave do app, top 4%",
        "detector": "DetectorDeAtipicos (chave do app; valor ≥ R$ 30 e com motivo)",
    }
    res = {n: metricas(v) for n, v in linhas.items()}
    md = ["# Detector de gastos fora do padrão — avaliação\n",
          "Gerado por `ia/avaliar_atipicos.py`. 50 usuários sintéticos do FinIA (`data_gen.generate(10, seed=42)`), "
          "só despesas, categoria verdadeira. Métricas por gasto agregadas nos 50 usuários; AP = média por usuário "
          f"com anomalia. Config: {CFG['arvores']} árvores, amostra {CFG['amostra']}, semente {CFG['semente']}.\n",
          "Referência publicada pelo FinIA (cenário 'conhecidos', categoria prevista, 4%): "
          "IF P 0.42 · R 0.55 · F1 0.48 · AP 0.58; baseline P 0.25 · R 0.19 · AP 0.36; revocação por tipo "
          "duplicado 0.8 · novo_estabelecimento 0.57 · spike 0.33.\n",
          "| Modelo | Marcados | P | R | F1 | AP | duplicado | novo_estabelecimento | spike |",
          "|---|---|---|---|---|---|---|---|---|"]
    for n, m in res.items():
        t = m["tipos"]
        md.append(f"| {nomes[n]} | {m['n_marcados']} | {m['P']:.2f} | {m['R']:.2f} | {m['F1']:.2f} | {m['AP']:.2f} | "
                  f"{t.get('duplicado', 0):.2f} | {t.get('novo_estabelecimento', 0):.2f} | {t.get('spike', 0):.2f} |")
    md += ["\nMotivos na lista do detector (quantos são anomalia plantada / gasto normal):\n",
           "| Motivo | Anomalia | Normal |", "|---|---|---|"]
    md += [f"| {m} | {c['anomalia']} | {c['normal']} |" for m, c in sorted(motivos.items())]
    RESULTADOS.mkdir(exist_ok=True)
    (RESULTADOS / "atipicos.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    print("\n".join(md))


if __name__ == "__main__":
    main()
