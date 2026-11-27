import json
from pathlib import Path

import pytest
import requests

import contrato
import gerar_planos as gp
import gerar_respostas as gr

TESTE = Path(__file__).resolve().parent / "teste" / "perguntas.jsonl"
FATOS = gr.FATOS


def _professor_no_ar():
    try:
        return requests.get(gp.PROFESSOR + "/health", timeout=2).status_code == 200
    except requests.RequestException:
        return False


@pytest.fixture(scope="module")
def planos(tmp_path_factory):
    pasta = tmp_path_factory.mktemp("planos")
    gp.gerar(1300, pasta, usar_professor=False, log=lambda s: None)
    linhas = []
    for nome in ("plano_treino.jsonl", "plano_valid.jsonl"):
        linhas += [json.loads(l) for l in (pasta / nome).read_text(encoding="utf-8").splitlines()]
    return linhas, pasta


def _exemplos():
    return [gp.montar_exemplo(i) for i in range(1300)]


def test_toda_saida_passa_no_ler_plano_e_na_gramatica():
    for e in _exemplos():
        assert gp.aceita_pela_gramatica(e["saida"], e["mes_atual"], e["categorias"]), e
        lido = gp.ler_plano(e["saida"], e["mes_atual"], e["categorias"])
        assert lido is not None and len(lido) == len(e["chamadas"]), e
        assert [c["f"] for c in e["chamadas"]] == [c["ferramenta"] for c in lido]


def test_ler_plano_descarta_o_que_nao_vale():
    cats = ["Mercado", 'Pet "Rex"']
    ler = lambda s: gp.ler_plano(s, "2026-11", cats)
    assert ler("lixo") is None
    assert ler('{"chamadas":[]}') == []
    assert ler('{"chamadas":[{"f":"xx","mes":"atual"}]}') == []
    assert ler('{"chamadas":[{"f":"ranking","mes":"2026-13"}]}') == []
    assert ler('{"chamadas":[{"f":"gasto_categoria","mes":"atual"}]}') == []
    assert ler('{"chamadas":[{"f":"gasto_categoria","mes":"atual","categoria":"Outra"}]}') == []
    assert ler('{"chamadas":[{"f":"gasto_categoria","mes":"anterior","categoria":"Pet \\"Rex\\""}]}') == [
        {"ferramenta": "gasto_categoria", "mes": "2026-10", "categoria": 'Pet "Rex"'}]
    assert len(ler('{"chamadas":' + json.dumps([{"f": "assinaturas"}] * 6) + "}")) == 4


def test_proporcoes_de_planos(planos):
    linhas, _ = planos
    n = len(linhas)
    assert n > 1200
    compostas = sum(l["saida"].count('{"f"') >= 2 for l in linhas)
    fora = sum(l["saida"] == '{"chamadas":[]}' for l in linhas)
    historico = sum(l["prompt"].count("<|im_start|>user") > 1 for l in linhas)
    assert compostas / n >= 0.30
    assert fora / n >= 0.10
    assert historico / n >= 0.10


def test_planos_sao_deterministicos_e_retomaveis(tmp_path):
    gp.gerar(64, tmp_path, usar_professor=False, log=lambda s: None)
    antes = (tmp_path / "plano_treino.jsonl").read_text(encoding="utf-8")
    gp.gerar(64, tmp_path, usar_professor=False, log=lambda s: None)  # nada a fazer
    assert (tmp_path / "plano_treino.jsonl").read_text(encoding="utf-8") == antes
    gp.gerar(96, tmp_path, usar_professor=False, log=lambda s: None)
    outra = tmp_path / "outra"
    gp.gerar(96, outra, usar_professor=False, log=lambda s: None)
    assert (tmp_path / "plano_treino.jsonl").read_text(encoding="utf-8") == (outra / "plano_treino.jsonl").read_text(encoding="utf-8")


def test_perguntas_de_teste_bem_formadas():
    linhas = [json.loads(l) for l in TESTE.read_text(encoding="utf-8").splitlines()]
    assert len(linhas) == 60
    assert any(l["pergunta"] == "No que é melhor eu gastar menos? e em que eu gastei mais nesse mês, "
                                "quero que me fale também no que gastei mais no mês passado" for l in linhas)
    compostas = simples = continuacao = fora = 0
    for l in linhas:
        assert set(l) == {"pergunta", "mes_atual", "categorias", "historico", "plano_esperado"}
        esperado = [dict(c) for c in l["plano_esperado"]]
        plano = gp.serializar_plano(esperado)
        assert gp.aceita_pela_gramatica(plano, l["mes_atual"], l["categorias"]), l
        assert len(gp.ler_plano(plano, l["mes_atual"], l["categorias"])) == len(esperado), l
        if l["historico"]:
            continuacao += 1
        elif not esperado:
            fora += 1
        elif len(esperado) >= 2:
            compostas += 1
        else:
            simples += 1
    assert (compostas, simples, continuacao, fora) == (25, 15, 10, 10)


def test_nenhuma_pergunta_de_teste_aparece_no_treino(tmp_path):
    teste = {gp.normalizar(json.loads(l)["pergunta"]) for l in TESTE.read_text(encoding="utf-8").splitlines()}
    gp.gerar(2000, tmp_path, usar_professor=False, log=lambda s: None)
    for nome in ("plano_treino.jsonl", "plano_valid.jsonl"):
        for l in (tmp_path / nome).read_text(encoding="utf-8").splitlines():
            assert gp.normalizar(gp._pergunta_do_prompt(json.loads(l)["prompt"])) not in teste


def test_normalizar():
    assert gp.normalizar("  Quanto gastei, com  Mercado?! ") == "quanto gastei com mercado"
    assert gp.normalizar("Saúde: ÇÃO") == "saude cao"


def test_valor_por_extenso():
    for t in ["Você gastou duzentos reais.", "Foram mil reais em maio.", "Gastou cem com lazer", "gastou três reais",
              "cerca de quinhentos", "dois mil", "vinte por cento"]:
        assert gr.tem_valor_por_extenso(t), t
    for t in ["Você gastou R$ 200 em Lazer.", "Um conselho: reduza o delivery.", "Em maio, três categorias subiram.",
              "Gastou 10% a mais."]:
        assert not gr.tem_valor_por_extenso(t), t


def test_avaliar_resposta():
    fato = {"periodo": "novembro", "frase": "Em novembro você gastou R$ 1.946 com Casa.",
            "itens": [{"rotulo": "Casa", "tipo": "reais", "valor": "1.946"}]}
    assert gr.avaliar_resposta("Em novembro você gastou R$ 1.946 com Casa.", [fato]) is None
    assert gr.avaliar_resposta("Em novembro você gastou R$ 1.900 com Casa.", [fato]) == "verificador"
    assert gr.avaliar_resposta("Em novembro você gastou quase dois mil reais com Casa.", [fato]) == "extenso"
    assert gr.avaliar_resposta("Em novembro seus gastos foram altos.", [fato]) == "sem_fato"
    assert gr.avaliar_resposta("", [fato]) == "formato"


@pytest.mark.skipif(not FATOS.exists(), reason="fatos.jsonl não gerado")
def test_caso_de_resposta_usa_fatos_verificaveis():
    fatos, pulados = gr.carregar_fatos()
    assert len(fatos) > 10000 and 0 <= pulados < 200
    nomes = {f["intencao"]: f["nome"] for f in contrato.carregar()["ferramentas"]}
    for i in range(50):
        pergunta, escolhidos = gr.montar_caso(i, fatos, nomes)
        assert pergunta and 1 <= len(escolhidos) <= 4
        assert all(gr.fato_se_verifica(f) for f in escolhidos)
        assert gr.avaliar_resposta(" ".join(f["frase"] for f in escolhidos), escolhidos) in (None, "tamanho", "sem_fato")


@pytest.mark.llama
@pytest.mark.skipif(not _professor_no_ar(), reason="professor (porta 8081) fora do ar")
def test_professor_parafraseia_planos(tmp_path):
    gp.gerar(40, tmp_path, usar_professor=True, taxa_parafrase=1.0, log=lambda s: None)
    linhas = [json.loads(l) for l in (tmp_path / "plano_treino.jsonl").read_text(encoding="utf-8").splitlines()]
    assert linhas
    for l in linhas:
        assert l["saida"].startswith('{"chamadas":[')


@pytest.mark.llama
@pytest.mark.skipif(not (_professor_no_ar() and FATOS.exists()), reason="professor ou fatos ausentes")
def test_professor_gera_respostas_verificadas(tmp_path):
    resultado = gr.gerar(6, tmp_path, log=lambda s: None)
    assert resultado["aprovadas"] >= 6
    linhas = [json.loads(l) for l in (tmp_path / "resposta_treino.jsonl").read_text(encoding="utf-8").splitlines()]
    assert linhas and all(l["prompt"].endswith("<|im_start|>assistant\n") and l["saida"] for l in linhas)
