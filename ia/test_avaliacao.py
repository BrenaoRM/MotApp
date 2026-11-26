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
