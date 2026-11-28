import json
import shutil
import subprocess
import time
from pathlib import Path

import pytest
import requests

import contrato

RAIZ = Path(__file__).resolve().parents[2]
GOLDEN = RAIZ / "app/src/test/resources/ia/llm"
SERVER = RAIZ / "ia/llm/bin/llama-server.exe"
GGUF = RAIZ / "ia/llm/modelos/gguf/base-0.6B-q4km.gguf"

CATEGORIAS = ["Alimentação", "Lazer", 'Pet "Rex"']
FATOS = [
    {"ferramenta": "ranking", "periodo": "novembro",
     "frase": "Em novembro, Alimentação foi a categoria com mais gastos: R$ 1.200.",
     "itens": [{"rotulo": "Alimentação", "tipo": "reais", "valor": "1.200"},
               {"rotulo": "Lazer", "tipo": "reais", "valor": "350"}]},
    {"ferramenta": "comparar_mes_anterior", "periodo": None,
     "frase": "Os gastos subiram 12% em relação a outubro.",
     "itens": [{"rotulo": None, "tipo": "porcento", "valor": "12"}]},
]


def confere(nome, texto, request):
    caminho = GOLDEN / nome
    if request.config.getoption("--atualizar-golden"):
        caminho.parent.mkdir(parents=True, exist_ok=True)
        with open(caminho, "w", encoding="utf-8", newline="\n") as f:
            f.write(texto)
    assert caminho.read_bytes().decode("utf-8") == texto


def test_prompt_plano_golden(request):
    historico = [("Quanto gastei em Lazer?",
                  '{"chamadas":[{"f":"gasto_categoria","mes":"atual","categoria":"Lazer"}]}')]
    texto = contrato.montar_prompt_plano(
        "Onde gastei mais este mês e no anterior?", "2026-11", CATEGORIAS, historico)
    confere("prompt_plano.txt", texto, request)


def test_prompt_resposta_golden(request):
    texto = contrato.montar_prompt_resposta("Onde gastei mais?", FATOS)
    confere("prompt_resposta.txt", texto, request)


def test_gramatica_golden(request):
    confere("gramatica_plano.gbnf", contrato.gramatica_plano("2026-11", CATEGORIAS), request)


def test_gramatica_sem_categorias_golden(request):
    g = contrato.gramatica_plano("2026-11", [])
    assert "categoria ::=" not in g and "gasto-categoria" not in g and "categoria" not in g.replace("gasto_categoria", "")
    confere("gramatica_plano_sem_categorias.gbnf", g, request)


def test_gramatica_nomes_especiais():
    g = contrato.gramatica_plano("2026-11", ["Bar\\Café", "🍔 Lanche", "A\nB"])
    esperado = ('categoria ::= "\\"Bar\\\\\\\\Café\\"" | "\\"🍔 Lanche\\"" | "\\"A\\\\nB\\""')
    assert esperado in g


def test_gramatica_estrutura():
    g = contrato.gramatica_plano("2026-11", CATEGORIAS)
    assert '"\\"Pet \\\\\\"Rex\\\\\\"\\""' in g
    assert "2024-12" in g and "2024-11" not in g


@pytest.mark.llama
@pytest.mark.skipif(not (SERVER.exists() and GGUF.exists()), reason="llama-server ausente")
def test_gramatica_aceita_e_recusa():
    porta = 18099
    proc = subprocess.Popen([str(SERVER), "-m", str(GGUF), "--port", str(porta), "-c", "2048"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        base = f"http://127.0.0.1:{porta}"
        for _ in range(120):
            try:
                if requests.get(base + "/health", timeout=2).status_code == 200:
                    break
            except requests.RequestException:
                pass
            time.sleep(1)
        else:
            pytest.fail("llama-server não subiu")
        nomes = {f["nome"] for f in contrato.carregar()["ferramentas"]}
        gram = contrato.gramatica_plano("2026-11", CATEGORIAS)
        for pergunta in ["Onde gastei mais este mês e no anterior?",
                         "Qual a capital da França?",
                         'Quanto gastei com Pet "Rex"?']:
            prompt = contrato.montar_prompt_plano(pergunta, "2026-11", CATEGORIAS, [])
            r = requests.post(base + "/completion", json={
                "prompt": prompt, "grammar": gram, "temperature": 0, "n_predict": 200}, timeout=120)
            saida = r.json()["content"]
            plano = json.loads(saida)
            assert len(plano["chamadas"]) <= 4
            for c in plano["chamadas"]:
                assert c["f"] in nomes
            print(pergunta, "->", saida)
    finally:
        proc.terminate()


@pytest.mark.llama
@pytest.mark.skipif(not (SERVER.exists() and GGUF.exists()), reason="llama-server ausente")
def test_gramatica_nomes_especiais_e_sem_categorias_no_llama():
    porta = 18097
    proc = subprocess.Popen([str(SERVER), "-m", str(GGUF), "--port", str(porta), "-c", "2048"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        base = f"http://127.0.0.1:{porta}"
        for _ in range(120):
            try:
                if requests.get(base + "/health", timeout=2).status_code == 200:
                    break
            except requests.RequestException:
                pass
            time.sleep(1)
        else:
            pytest.fail("llama-server não subiu")
        nomes = {f["nome"] for f in contrato.carregar()["ferramentas"]}
        exemplo = ('{"chamadas":[{"f":"gasto_categoria","mes":"atual","categoria":"Lazer"}]}')
        for cats in (["Bar\\Café", "🍔 Lanche"], []):
            gram = contrato.gramatica_plano("2026-11", cats)
            hist = [("Quanto gastei em Lazer?", exemplo)] if cats else []
            alvo = cats[0] if cats else "isso"
            prompt = contrato.montar_prompt_plano("Quanto gastei em " + alvo + "?", "2026-11", cats, hist)
            r = requests.post(base + "/completion", json={
                "prompt": prompt, "grammar": gram, "temperature": 0, "n_predict": 200}, timeout=120)
            corpo = r.json()
            assert r.status_code == 200 and "content" in corpo, corpo
            plano = json.loads(corpo["content"])
            for c in plano["chamadas"]:
                assert c["f"] in nomes
                if "categoria" in c:
                    assert c["categoria"] in cats
            print(cats, "->", corpo["content"])
        # força uma chamada com o nome exato: gramática só com a ferramenta de categoria
        # root exige exatamente 1 chamada gasto_categoria (substitui a 1a linha da gramática)
        raiz = 'root ::= "{\\"chamadas\\":[" c-gasto-categoria "]}"'
        for nome in ("Bar\\Café", "🍔 Lanche", "A\nB"):
            linhas = contrato.gramatica_plano("2026-11", [nome]).split("\n")
            assert linhas[0].startswith("root ::= ")
            gram = "\n".join([raiz] + linhas[1:])
            r = requests.post(base + "/completion", json={
                "prompt": "x", "grammar": gram, "temperature": 0, "n_predict": 200}, timeout=120)
            assert r.status_code == 200, r.text
            plano = json.loads(r.json()["content"])
            assert len(plano["chamadas"]) == 1
            assert plano["chamadas"][0]["f"] == "gasto_categoria"
            assert plano["chamadas"][0]["categoria"] == nome
    finally:
        proc.terminate()
