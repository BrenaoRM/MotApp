import json
from pathlib import Path

import numpy as np
import pytest

RAIZ = Path(__file__).resolve().parent
CONFIG = RAIZ.parent / "app" / "src" / "main" / "assets" / "ia" / "modelo_ia.json"


def test_usuarios():
    from treinar_prior import dados_prior, lexico
    from usuarios_sinteticos import GENERICAS, MERCHANTS_PET, gerar_usuarios
    usuarios = gerar_usuarios(seed=42)
    assert len(usuarios) == 50
    d = [u for u in usuarios if u.esquema == "d"]
    assert d and all((u.transacoes.categoria_usuario == "Pet").sum() >= 10 for u in d)
    for u in usuarios:
        assert set(u.transacoes.categoria_usuario) <= set(u.categorias)
    _, _, merchants, _ = dados_prior()
    assert not (set(merchants) & MERCHANTS_PET)
    lex = lexico()
    assert set(lex.values()) <= set(GENERICAS)
    assert not any(p in termo for termo in lex for p in ("pet", "cobasi", "veterin"))


def test_prior_nas_categorias():
    from avaliar_categorias import prior_generico_nas_categorias
    s = np.array([[0.5, 0.2, 0.3]])
    # categorias 0 e 1 mapeiam para a genérica 0; categoria 2 não mapeia e fica com a massa livre (1 e 2)
    q = prior_generico_nas_categorias(s, np.array([0, 0, -1]))
    assert np.allclose(q, [[0.25, 0.25, 0.5]])


@pytest.fixture(scope="module")
def cfg():
    return json.loads(CONFIG.read_text(encoding="utf-8"))


def test_config(cfg):
    g = cfg["generico"]
    assert np.array(g["pesos"]).shape == (9, cfg["dimensao"])
    assert len(g["vieses"]) == 9 and np.array(g["vetoresNomes"]).shape == (9, cfg["dimensao"])
    assert set(cfg["lexico"].values()) <= set(g["nomes"])
    assert 0.0 < cfg["classificador"]["limiarSugestao"] < 1.0


def test_avaliacao(cfg):
    from avaliar_categorias import avaliar
    r = avaliar(seed=42)
    assert r["app"]["n=20"]["acuracia"] > r["memoria"]["n=20"]["acuracia"]  # IA supera o app atual
    assert r["app"]["n=0"]["esquema_d"]["acuracia"] > 0.30                  # zero-shot funciona
    assert 0.0 < cfg["classificador"]["limiarSugestao"] < 1.0


def test_lexico_por_palavra():
    from treinar_prior import generica_do_lexico
    from usuarios_sinteticos import GENERICAS
    lex = {"prime": "Lazer e Assinaturas", "smart fit": "Saúde"}
    assert generica_do_lexico("AMAZON PRIME VIDEO", lex) == GENERICAS.index("Lazer e Assinaturas")
    assert generica_do_lexico("PRIMEIRO MERCADO", lex) is None          # palavra inteira, não substring
    assert generica_do_lexico("SMART FIT ACADEMIA", lex) == GENERICAS.index("Saúde")
    assert generica_do_lexico("SMARTFIT", lex) is None


def _golden_entradas(n):
    from avaliar_categorias import chave_do_estabelecimento
    from gerar_golden_classificador import CLASSIFICADOR, LEXICO, montar
    g = montar()
    caso = next(c for c in g["casos"] if c["n"] == n)
    V = np.array([c["vetorNome"] for c in g["categorias"]])
    ex = caso["exemplos"]
    con = caso["consultas"]
    return (CLASSIFICADOR, g["generico"], LEXICO, V, np.array([e["x"] for e in ex]).reshape(n, g["dimensao"]),
            np.array([e["categoria"] for e in ex], dtype=int), [chave_do_estabelecimento(e["descricao"]) for e in ex],
            np.array([c["x"] for c in con]), [c["descricao"] for c in con],
            [chave_do_estabelecimento(c["descricao"]) for c in con])


def test_memoria_vence_lexico():
    """n=5: a pessoa rotulou "UBER TRIP" como Trabalho (2); o léxico diz Transporte (categoria 0 é a mapeada nela)."""
    from avaliar_categorias import modelo_app
    cc, cg, lex, V, exX, exy, exk, X, descs, chaves = _golden_entradas(5)
    sem_memoria = modelo_app(cc, cg, lex, V, exX, exy, ["?"] * 5, X, descs, chaves)
    assert sem_memoria["genericaLexico"][0] == cg["nomes"].index("Transporte") and sem_memoria["mapa"][0] == 2
    assert sem_memoria["pFinal"][0].argmax() != 2          # sem a precedência, o rótulo da pessoa perderia
    out = modelo_app(cc, cg, lex, V, exX, exy, exk, X, descs, chaves)
    assert out["rotuloMemoria"][0] == 2 and out["sugestao"][0] == 2 and out["sugestao"][1] == 2
    assert np.allclose(out["pFinal"][0], [0, 0, 1, 0])


def test_golden_classificador():
    from gerar_golden_classificador import SAIDA, montar
    salvo = json.loads(SAIDA.read_text(encoding="utf-8"))
    novo = json.loads(json.dumps(montar(), ensure_ascii=False))
    for cs, cn in zip(salvo["casos"], novo["casos"]):
        for k in cs["esperado"]:
            assert np.allclose(np.asarray(cs["esperado"][k], dtype=float), np.asarray(cn["esperado"][k], dtype=float),
                               atol=1e-9), k
