"""Contrato compartilhado entre treino/avaliação (Python) e o app (Kotlin).

Os textos e a gramática aqui gerados devem bater byte a byte com os golden em
app/src/test/resources/ia/llm. Mantenha tudo com concatenação simples.
"""
import json
from pathlib import Path

CONTRATO = Path(__file__).resolve().parents[2] / "app/src/main/assets/ia/llm/contrato.json"
NOMES_DOS_MESES = [
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
]
MAX_CHAMADAS = 4
MESES_NA_GRAMATICA = 24


def carregar() -> dict:
    return json.loads(CONTRATO.read_text(encoding="utf-8"))


def _turno(papel: str, texto: str) -> str:
    return "<|im_start|>" + papel + "\n" + texto + "<|im_end|>\n"


def _nome_do_mes(mes: str) -> str:
    ano, numero = mes.split("-")
    return NOMES_DOS_MESES[int(numero) - 1] + " de " + ano


def montar_prompt_plano(pergunta, mes_atual, categorias, historico) -> str:
    texto = _turno("system", carregar()["sistema_plano"])
    for p, plano in historico[-2:]:
        texto += _turno("user", "Pergunta: " + p)
        texto += _turno("assistant", plano)
    usuario = ("Mês atual: " + _nome_do_mes(mes_atual) + " (" + mes_atual + ")\n"
               + "Categorias: " + ", ".join(categorias) + "\n"
               + "Pergunta: " + pergunta)
    return texto + _turno("user", usuario) + "<|im_start|>assistant\n"


def montar_prompt_resposta(pergunta, fatos) -> str:
    linhas = "".join(str(i + 1) + ". " + f["frase"] + "\n" for i, f in enumerate(fatos))
    usuario = "Pergunta: " + pergunta + "\nFatos:\n" + linhas.rstrip("\n")
    return (_turno("system", carregar()["sistema_resposta"])
            + _turno("user", usuario) + "<|im_start|>assistant\n")


def _gbnf(literal: str) -> str:
    """Literal GBNF entre aspas, com barra invertida e aspas escapadas."""
    return '"' + literal.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _meses_ate(mes_atual: str) -> list:
    ano, mes = (int(x) for x in mes_atual.split("-"))
    saida = []
    for _ in range(MESES_NA_GRAMATICA):
        saida.append("%04d-%02d" % (ano, mes))
        mes -= 1
        if mes == 0:
            mes, ano = 12, ano - 1
    return saida


def gramatica_plano(mes_atual, categorias) -> str:
    ferramentas = carregar()["ferramentas"]
    meses = ["atual", "anterior"] + _meses_ate(mes_atual)
    regras = ["root ::= " + _gbnf('{"chamadas":[') + " (chamada (" + _gbnf(",")
              + " chamada){0," + str(MAX_CHAMADAS - 1) + "})? " + _gbnf("]}")]
    regras.append("chamada ::= " + " | ".join("c-" + f["nome"].replace("_", "-") for f in ferramentas))
    for f in ferramentas:
        corpo = _gbnf('{"f":"' + f["nome"] + '"')
        if f["usa_mes"]:
            corpo += " " + _gbnf(',"mes":') + " mes"
        if f["usa_categoria"]:
            parte = _gbnf(',"categoria":') + " categoria"
            corpo += " " + (parte if f["categoria_obrigatoria"] else "(" + parte + ")?")
        corpo += " " + _gbnf("}")
        regras.append("c-" + f["nome"].replace("_", "-") + " ::= " + corpo)
    regras.append("mes ::= " + " | ".join(_gbnf('"' + m + '"') for m in meses))
    regras.append("categoria ::= " + " | ".join(
        _gbnf(json.dumps(c, ensure_ascii=False)) for c in categorias))
    return "\n".join(regras) + "\n"
