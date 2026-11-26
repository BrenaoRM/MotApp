"""Golden da T3 (tokens e vetores) a partir dos assets finais, com a Viterbi podada em Python, e da T7
(`golden_isolation.json`: atributos do `build_features` do FinIA e pontuações do Isolation Forest do sklearn).

Uso: `cd ia && python gerar_golden.py` (tudo) ou `python gerar_golden.py isolation` (só a parte da T7).
Grava em `app/src/test/resources/ia/`.
"""
import json
import sys

from exportar_modelo import ASSETS, RAIZ, _ler_json, carregar_onnx, descricoes_finia, viterbi_de_json
from texto import normalizar_para_modelo

DESTINO = RAIZ.parent / "app" / "src" / "test" / "resources" / "ia"

DIFICEIS = [
    "", "   ", "123", "0001 2345", "😀", "😀 iFood", "ÇÃO", "AÇÃO", "iFood *Pedido", "IFOOD*PEDIDO 123",
    "Jean-Paul", "Ana-Maria Silva", "São Paulo", "SAO PAULO", "Pão de Açúcar", "padaria pão quente",
    "ﬁ", "① posto", "ＡＢＣ", "Café & Cia", "café", "CAFÉ", "Müller", "naïve", "coração",
    "  dois   espaços  ", "a", "x y z", "PAG*", "PIX ENVIADO", "Pix enviado - João", "pix recebido maria",
    "COMPRA CARTAO DEBITO", "uber*trip", "99 APP", "ÁÉÍÓÚ âêô ãõ à", "ñandú", "ß", "Ελλάδα", "москва",
    "東京", "ඞ", "𓀀𓀁 abc", "tab\tseparado", "linha\nnova", "under_score", "hífen‐unicode",
    "palavra " * 80, "supercalifragilisticexpialidocious", "ACOUGUE BOI DE OURO RIO PARANAIBA",
]


# Golden da T7: um usuário de cada perfil bem diferente (estudante, autônomo com mais anomalias, equilibrado)
USUARIOS_ISOLATION = ["u00", "u30", "u40"]


def golden_isolation():
    """Atributos já calculados (isola o teste do IF do cálculo dos atributos) + `-score_samples` do sklearn."""
    import numpy as np
    from sklearn.ensemble import IsolationForest

    import anomaly
    from data_gen import CATEGORIAS
    from exportar_modelo import gastos_finia
    from text_utils import merchant_key

    cfg = json.loads((ASSETS / "modelo_ia.json").read_text(encoding="utf-8"))["atipicos"]
    gastos = gastos_finia()
    saida = []
    for uid in USUARIOS_ISOLATION:
        g = gastos[gastos.user_id == uid].copy()
        g["valorCentavos"] = (g.amount.abs() * 100).round().astype(int)
        g["amount"] = -g["valorCentavos"] / 100  # o Kotlin vê centavos: o Python calcula sobre o mesmo valor
        d = anomaly.build_features(g, cat_col="category")
        x = d[anomaly.FEATS].to_numpy(dtype=float)
        iso = IsolationForest(n_estimators=cfg["arvores"], max_samples=min(cfg["amostra"], len(d)),
                              random_state=cfg["semente"]).fit(x)
        pont = -iso.score_samples(x)
        saida.append({"usuario": uid, "gastos": [
            {"id": int(i), "data": str(r.date.date()), "valorCentavos": int(r.valorCentavos),
             "categoriaId": CATEGORIAS.index(r.category) + 1, "chave": merchant_key(r.description),
             "atributos": [float(v) for v in x[k]], "sklearn": float(pont[k])}
            for k, (i, r) in enumerate(d.iterrows())]})
        assert np.isfinite(x).all()
    DESTINO.mkdir(parents=True, exist_ok=True)
    (DESTINO / "golden_isolation.json").write_text(json.dumps(saida, ensure_ascii=False), encoding="utf-8")
    print(", ".join(f"{u['usuario']}: {len(u['gastos'])} gastos" for u in saida), "em golden_isolation.json")


def main():
    if sys.argv[1:] == ["isolation"]:
        golden_isolation()
        return
    dados = _ler_json(ASSETS)
    codificar = viterbi_de_json(dados)
    brutos = descricoes_finia()[:150] + DIFICEIS
    assert len(brutos) == 200
    tokens = []
    for b in brutos:
        n = normalizar_para_modelo(b)
        tokens.append({"bruto": b, "normalizado": n, "ids": codificar(dados["prefixo"] + n, dados["max_tokens"])})
    textos = [normalizar_para_modelo(b) for b in descricoes_finia()[150:190]] + \
        [normalizar_para_modelo(b) for b in DIFICEIS[:10]]
    # um texto por inferência (lote=1): a quantização int8 dinâmica faz o vetor depender dos vizinhos do lote
    vetores = carregar_onnx(ASSETS, lote=1)(textos)
    DESTINO.mkdir(parents=True, exist_ok=True)
    (DESTINO / "golden_tokens.json").write_text(json.dumps(tokens, ensure_ascii=False, indent=1), encoding="utf-8")
    (DESTINO / "golden_vetores.json").write_text(json.dumps(
        [{"texto": t, "vetor": [round(float(x), 7) for x in v]} for t, v in zip(textos, vetores)],
        ensure_ascii=False), encoding="utf-8")
    print(len(tokens), "tokens,", len(textos), "vetores em", DESTINO)
    golden_isolation()


if __name__ == "__main__":
    main()
