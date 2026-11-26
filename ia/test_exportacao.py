from pathlib import Path

import numpy as np
import pytest

from texto import normalizar_para_modelo

RAIZ = Path(__file__).resolve().parent
ASSETS = RAIZ.parent / "app" / "src" / "main" / "assets" / "ia"


def test_normalizacao():
    assert normalizar_para_modelo("PAG* RESTAURANTE Sabor Caseiro 123") == "restaurante sabor caseiro"
    assert normalizar_para_modelo("Pix enviado - João") == "joão"
    assert normalizar_para_modelo("   ") == ""


def _descricoes(n=300):
    from exportar_modelo import descricoes_finia
    return descricoes_finia()[:n]


@pytest.mark.skipif(not (ASSETS / "modelo.onnx").exists(), reason="modelo ainda não exportado")
def test_poda_preserva_vetores():
    from exportar_modelo import carregar_onnx, carregar_original
    textos = [normalizar_para_modelo(d) for d in _descricoes(300)]
    podado = carregar_onnx(ASSETS)(textos)
    original = carregar_original(ASSETS)(textos)
    assert podado.shape == (300, 384)
    assert np.allclose(np.linalg.norm(podado, axis=1), 1.0, atol=1e-4)
    cos = (podado * original).sum(axis=1)
    assert cos.mean() >= 0.98
    assert cos.min() >= 0.95


@pytest.mark.skipif(not (ASSETS / "tokenizador.json").exists(), reason="modelo ainda não exportado")
def test_viterbi_igual_ao_tokenizador():
    from exportar_modelo import tokenizador_original_remapeado, viterbi_de_json
    import json
    dados = json.loads((ASSETS / "tokenizador.json").read_text(encoding="utf-8"))
    viterbi = viterbi_de_json(dados)
    tok = tokenizador_original_remapeado(ASSETS)
    textos = [normalizar_para_modelo(d) for d in _descricoes(500)]
    for t in textos:
        assert viterbi(dados["prefixo"] + t) == tok(dados["prefixo"] + t)
