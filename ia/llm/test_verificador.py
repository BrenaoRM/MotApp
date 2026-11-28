import json
from pathlib import Path

import pytest

import verificador

CASOS = Path(__file__).resolve().parents[2] / "app/src/test/resources/ia/llm/casos_verificador.jsonl"


def _casos():
    return [json.loads(l) for l in CASOS.read_text(encoding="utf-8").splitlines() if l.strip()]


def test_casos_suficientes():
    assert len(_casos()) >= 25


@pytest.mark.parametrize("caso", _casos(), ids=lambda c: c["nota"])
def test_caso(caso):
    obtido = verificador.verificar_frase(caso["frase"], caso["fatos"], caso["periodo_vigente"])
    assert obtido == (caso["aprovada"], caso["periodo_depois"])


def test_frases():
    assert verificador.frases("") == []
    assert verificador.frases("  \n ") == []
    assert verificador.frases("Sobraram R$ 1.200.") == ["Sobraram R$ 1.200."]
    assert verificador.frases("Em novembro você gastou R$ 1.200. Depois vem Casa (R$ 300).") == [
        "Em novembro você gastou R$ 1.200.", "Depois vem Casa (R$ 300)."]
    assert verificador.frases("Olá!\nTudo bem?  Sim") == ["Olá!", "Tudo bem?", "Sim"]
    assert verificador.frases("Total: R$ 1.200,50 no mês") == ["Total: R$ 1.200,50 no mês"]


def test_fatos_citados():
    fatos = [c["fatos"] for c in _casos() if c["nota"] == "mês trocado"][0]  # ranking de novembro, total de outubro
    assert verificador.fatos_citados(["Em outubro você gastou R$ 7.315."], fatos) == {1}
    assert verificador.fatos_citados(["Em novembro Casa levou R$ 1.946.", "Em outubro, R$ 7.315."], fatos) == {0, 1}
    assert verificador.fatos_citados(["Você está indo bem."], fatos) == set()
