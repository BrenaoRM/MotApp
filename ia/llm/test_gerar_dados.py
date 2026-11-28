import difflib
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
    gp.gerar(gp.TOTAL, pasta, usar_professor=False, log=lambda s: None)
    linhas = []
    for nome in ("plano_treino.jsonl", "plano_valid.jsonl"):
        linhas += [json.loads(l) for l in (pasta / nome).read_text(encoding="utf-8").splitlines()]
    return linhas, pasta


def _exemplos():
    return [gp.montar_exemplo(i) for i in range(gp.TOTAL)]


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
    assert n == gp.TOTAL
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
    assert len(linhas) == 65
    assert any(l["pergunta"] == "No que é melhor eu gastar menos? e em que eu gastei mais nesse mês, "
                                "quero que me fale também no que gastei mais no mês passado" for l in linhas)
    compostas = simples = continuacao = fora = 0
    for l in linhas:
        assert set(l) == {"pergunta", "mes_atual", "categorias", "historico", "plano_esperado"}
        esperado = [dict(c) for c in l["plano_esperado"]]
        plano = gp.serializar_plano(esperado)
        assert gp.aceita_pela_gramatica(plano, l["mes_atual"], l["categorias"]), l
        assert len(gp.ler_plano(plano, l["mes_atual"], l["categorias"])) == len(esperado), l
        if not esperado:
            fora += 1  # inclui os fora do escopo que vêm depois de histórico
        elif l["historico"]:
            continuacao += 1
        elif len(esperado) >= 2:
            compostas += 1
        else:
            simples += 1
    assert (compostas, simples, continuacao, fora) == (25, 15, 10, 15)


def _perguntas(planos_dir, nomes):
    for nome in nomes:
        for l in (planos_dir / nome).read_text(encoding="utf-8").splitlines():
            yield gp.normalizar(gp._pergunta_do_prompt(json.loads(l)["prompt"]))


def test_nenhuma_pergunta_de_teste_aparece_no_treino_nem_parecida(planos):
    _, pasta = planos
    guarda = gp.Guarda()
    assert len(guarda.teste) == 65
    for q in set(_perguntas(pasta, ("plano_treino.jsonl", "plano_valid.jsonl"))):
        assert not guarda.parecida_com_teste(q), q


def test_perguntas_de_teste_fora_do_escopo_nao_estao_nas_listas_do_gerador():
    teste = [json.loads(l) for l in TESTE.read_text(encoding="utf-8").splitlines()]
    for l in teste:
        if l["plano_esperado"] or l["historico"]:
            continue
        for f in gp.FORA_DE_ESCOPO:
            assert difflib.SequenceMatcher(None, gp.normalizar(l["pergunta"]), gp.normalizar(f)).ratio() < 0.85, (l["pergunta"], f)


def test_treino_e_validacao_disjuntos_e_sem_duplicata(planos):
    _, pasta = planos
    treino = list(_perguntas(pasta, ("plano_treino.jsonl",)))
    valid = set(_perguntas(pasta, ("plano_valid.jsonl",)))
    assert not set(treino) & valid
    assert len(treino) == len(set(treino))
    linhas = (pasta / "plano_treino.jsonl").read_text(encoding="utf-8").splitlines()
    assert len(linhas) == len(set(linhas))


def test_sem_conector_repetido_nas_perguntas(planos):
    _, pasta = planos
    for q in _perguntas(pasta, ("plano_treino.jsonl", "plano_valid.jsonl")):
        palavras = q.split()
        for k in range(len(palavras) - 1):
            assert palavras[k] != palavras[k + 1], q
        for k in range(len(palavras) - 3):
            assert palavras[k:k + 2] != palavras[k + 2:k + 4], q
    assert gp.limpar("Quero saber quero saber quanto, e agora agora") == "Quero saber quanto, e agora"


def test_parafrase_so_de_uma_chamada_e_sem_financas_no_fora_do_escopo(monkeypatch):
    dois = {"chamadas": [{"f": "ranking"}, {"f": "assinaturas"}], "historico": [], "pergunta": "x"}
    assert gp.parafrasear(dois, 1) is None
    respostas = iter(["quanto eu gastei este mês?", "me conta uma piada", "quanto gastei no mes passado", "o total deste mês"])
    monkeypatch.setattr(gp, "completar", lambda *a, **k: next(respostas))
    fora = {"chamadas": [], "historico": [], "pergunta": "Qual a capital da frança"}
    assert gp.parafrasear(fora, 1) is None
    assert gp.parafrasear(fora, 1) == "me conta uma piada"
    atual = {"chamadas": [{"f": "total_mes", "mes": "atual"}], "historico": [], "pergunta": "x"}
    assert gp.parafrasear(atual, 1) is None   # "mes passado" onde o plano diz atual
    assert gp.parafrasear(atual, 1) == "o total deste mês"


def test_resume_nao_duplica_linhas(tmp_path):
    gp.gerar(96, tmp_path, usar_professor=False, log=lambda s: None)
    # simula queda no meio do bloco seguinte: linhas escritas sem registro no .feito
    with open(tmp_path / "plano_treino.jsonl", "a", encoding="utf-8") as f:
        f.write('{"prompt":"lixo","saida":"x"}' + chr(10))
    gp.gerar(160, tmp_path, usar_professor=False, log=lambda s: None)
    limpo = tmp_path / "limpo"
    gp.gerar(160, limpo, usar_professor=False, log=lambda s: None)
    for nome in ("plano_treino.jsonl", "plano_valid.jsonl"):
        assert (tmp_path / nome).read_text(encoding="utf-8") == (limpo / nome).read_text(encoding="utf-8")


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
    fato = {"ferramenta": "TOTAL_DO_MES", "periodo": "novembro", "frase": "Em novembro você gastou R$ 1.946 com Casa.",
            "itens": [{"rotulo": "Casa", "tipo": "reais", "valor": "1.946"}]}
    assert gr.avaliar_resposta("Em novembro você gastou R$ 1.946 com Casa.", [fato]) is None
    assert gr.avaliar_resposta("Em novembro você gastou R$ 1.900 com Casa.", [fato]) == "verificador"
    assert gr.avaliar_resposta("Em novembro você gastou quase dois mil reais com Casa.", [fato]) == "extenso"
    assert gr.avaliar_resposta("Em novembro seus gastos foram altos.", [fato]) == "sem_fato"
    assert gr.avaliar_resposta("", [fato]) == "formato"


def test_limite_de_frases_vem_do_contrato_e_conselho_so_com_economia_com_itens():
    assert gr.MAX_FRASES == 4
    fato = {"ferramenta": "TOTAL_DO_MES", "periodo": None, "frase": "x", "itens": [{"rotulo": None, "tipo": "numero", "valor": "7"}]}
    cinco = "Gastou 7. Gastou 7. Gastou 7. Gastou 7. Gastou 7."
    assert gr.avaliar_resposta(cinco, [fato]) == "tamanho"
    assert gr.avaliar_resposta("Gastou 7. Gastou 7. Gastou 7. Gastou 7.", [fato]) is None
    vazio = {"ferramenta": "COMO_ECONOMIZAR", "periodo": None, "frase": "Não vi nada para ajustar.", "itens": []}
    cheio = {"ferramenta": "COMO_ECONOMIZAR", "periodo": None, "frase": "Revise 7 assinaturas.",
             "itens": [{"rotulo": None, "tipo": "numero", "valor": "7"}]}
    assert not gr._tem_economia([vazio]) and gr._tem_economia([cheio])
    assert gr.avaliar_resposta("Gastou 7. Conselho: revise as assinaturas.", [fato]) == "meta"
    assert gr.avaliar_resposta("Gastou 7. Gastou 7. Gastou 7. Gastou 7. Conselho: revise as assinaturas.", [fato, cheio]) is None


@pytest.mark.skipif(not FATOS.exists(), reason="fatos.jsonl não gerado")
def test_caso_de_resposta_usa_fatos_verificaveis_e_sem_itens_so_deterministico():
    fatos, pulados = gr.carregar_fatos()
    assert len(fatos) > 10000 and 0 <= pulados < 200
    com, sem = gr.separar_fatos(fatos)
    assert sem and all(not f["itens"] for f in sem) and all(f["itens"] for f in com)
    nomes = {f["intencao"]: f["nome"] for f in contrato.carregar()["ferramentas"]}
    for i in range(200):
        pergunta, escolhidos, det = gr.montar_caso(i, com, sem, nomes)
        assert pergunta and 1 <= len(escolhidos) <= 4
        assert all(gr.fato_se_verifica(f) for f in escolhidos)
        if i % gr.SEM_ITENS_A_CADA == 0:
            assert det and len(escolhidos) == 1 and not escolhidos[0]["itens"]
            assert gr._tentar(pergunta, escolhidos, True)[1] == escolhidos[0]["frase"]
        else:
            assert not det and all(f["itens"] for f in escolhidos)


@pytest.mark.skipif(not FATOS.exists(), reason="fatos.jsonl não gerado")
def test_perguntas_de_resposta_nao_vazam_o_teste():
    fatos, _ = gr.carregar_fatos()
    com, sem = gr.separar_fatos(fatos)
    nomes = {f["intencao"]: f["nome"] for f in contrato.carregar()["ferramentas"]}
    guarda = gp.Guarda()
    # o guarda é usado pelo gerador antes de chamar o professor; aqui só confirmo que ele pega os casos de teste
    teste = json.loads(TESTE.read_text(encoding="utf-8").splitlines()[26])["pergunta"]
    assert not guarda.aceita(teste)
    assert not guarda.aceita(teste.upper() + "!!")


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
