"""Teste da Tarefa 13: o modelo de intenções gravado em `modelo_ia.json` (seção `intencoes`), aplicado como o app
aplica (softmax(W x + b), limiar de confiança e similaridade mínima ao centroide mais próximo), no `teste.csv`."""
import json

import numpy as np
import pytest

from exportar_modelo import ASSETS
from treinar_intencoes import DESCONHECIDA, ler, prever, vetorizar

pytestmark = pytest.mark.skipif(not (ASSETS / "modelo.onnx").exists(), reason="modelo ainda não exportado")


@pytest.fixture(scope="module")
def modelo():
    return json.loads((ASSETS / "modelo_ia.json").read_text(encoding="utf-8"))["intencoes"]


def _decidir(modelo, frases):
    """Intenção prevista por frase, ou DESCONHECIDA, como `Perguntas.entender` do app."""
    X = vetorizar(frases)
    P = prever(X, modelo["pesos"], modelo["vieses"])
    C = np.asarray(modelo["centroides"])
    sim = (X @ C.T).max(1)
    recusa = (P.max(1) < modelo["limiar"]) | (sim < modelo["similaridadeMinima"])
    return [DESCONHECIDA if r else modelo["nomes"][i] for r, i in zip(recusa, P.argmax(1))], P


def test_formato_da_secao(modelo):
    k = len(modelo["nomes"])
    assert k == 12 and DESCONHECIDA not in modelo["nomes"]
    assert np.asarray(modelo["pesos"]).shape == (k, 384)
    assert len(modelo["vieses"]) == k
    assert np.asarray(modelo["centroides"]).shape == (k, 384)
    assert 0 < modelo["limiar"] < 1 and 0 < modelo["similaridadeCategoria"] < 1


def test_acuracia_nas_intencoes_conhecidas(modelo):
    teste = [t for t in ler("teste.csv", semente=2) if t[0] != DESCONHECIDA]
    pred, P = _decidir(modelo, [f for _, f, _ in teste])
    real = [i for i, _, _ in teste]
    sem_recusa = np.mean([modelo["nomes"][i] == r for i, r in zip(P.argmax(1), real)])
    com_recusa = np.mean([p == r for p, r in zip(pred, real)])
    assert sem_recusa >= 0.85, sem_recusa
    assert com_recusa >= 0.85, com_recusa  # recusar uma pergunta conhecida também conta como erro


def test_fora_do_escopo_vira_desconhecida(modelo):
    fora = [f for i, f, _ in ler("teste.csv", semente=2) if i == DESCONHECIDA]
    assert len(fora) >= 30
    pred, _ = _decidir(modelo, fora)
    assert np.mean([p == DESCONHECIDA for p in pred]) >= 0.80
