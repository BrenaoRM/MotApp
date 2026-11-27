import avaliar_llm as av

A = ("ranking", "2026-11", None)
B = ("total_mes", "2026-11", None)
C = ("poupanca", "2026-11", None)


def test_plano_em_outra_ordem_e_exato():
    assert av.plano_exato([B, A, C], [A, B, C])
    assert not av.plano_exato([A, B], [A, B, C])


def test_f1_duas_de_tres():
    assert abs(av.plano_f1([A, B], [A, B, C]) - 0.8) < 1e-9


def test_f1_vazios_e_erro():
    assert av.plano_f1([], []) == 1.0
    assert av.plano_f1([A], []) == 0.0
    assert av.plano_f1([], [A]) == 0.0


def test_resumo_e_recusa():
    r = av.resumir_planos([([A], [A]), ([], []), ([A], []), ([A], [A, B])])
    assert r["recusa_correta"] == 0.5 and r["n_fora_do_escopo"] == 2
    assert r["plano_exato"] == 0.5


def test_normaliza_mes_atual():
    esperado = av._normalizar_chamadas([{"f": "ranking", "mes": "atual"}], "2026-11", [])
    assert esperado == av.plano_do_modelo('{"chamadas":[{"f":"ranking","mes":"2026-11"}]}', "2026-11", [])


POUPANCA = {"ferramenta": "POUPANCA", "periodo": "outubro",
            "frase": "Em outubro você recebeu R$ 6.740 e gastou R$ 5.000: sobraram R$ 1.740 (26% da renda).",
            "itens": [{"tipo": "reais", "valor": "6.740", "rotulo": None}, {"tipo": "reais", "valor": "5.000", "rotulo": None},
                      {"tipo": "reais", "valor": "1.740", "rotulo": None}, {"tipo": "porcento", "valor": "26", "rotulo": None}]}


def test_troca_de_papel_detectada():
    assert av.troca_de_papel("Em outubro você recebeu R$ 5.000 e gastou R$ 6.740.", [POUPANCA])


def test_papeis_corretos_nao_sao_troca():
    assert not av.troca_de_papel("Em outubro você recebeu R$ 6.740 e gastou R$ 5.000, e sobraram R$ 1.740.", [POUPANCA])


def test_orcamento_trocado():
    f = {"ferramenta": "ORCAMENTO_RESTANTE", "periodo": "outubro", "frase": "x",
         "itens": [{"tipo": "reais", "valor": "247", "rotulo": "Outros"}, {"tipo": "reais", "valor": "876", "rotulo": "Outros"},
                   {"tipo": "porcento", "valor": "72", "rotulo": "Outros"}]}
    assert av.troca_de_papel("Restam R$ 876 do limite de R$ 247.", [f])
    assert not av.troca_de_papel("Restam R$ 247 do limite de R$ 876.", [f])


def test_fidelidade_com_periodo():
    assert av.resposta_fiel("Em outubro você recebeu R$ 6.740 e gastou R$ 5.000.", [POUPANCA])
    assert not av.resposta_fiel("Em outubro você recebeu R$ 9.999.", [POUPANCA])
    assert not av.resposta_fiel("", [POUPANCA])


class _Falso:
    prompts = []
    saida_plano = '{"chamadas":[]}'

    def __init__(self, *_):
        pass

    def __enter__(self):
        return self

    def __exit__(self, *_):
        pass

    def completar(self, prompt, grammar=None, n_predict=256):
        _Falso.prompts.append(prompt)
        return (_Falso.saida_plano if grammar else "Em outubro você recebeu R$ 6.740."), 1, 1.0


def _preparar(monkeypatch, tmp_path):
    import json
    import contrato
    _Falso.prompts = []
    monkeypatch.setattr(av, "Servidor", _Falso)
    monkeypatch.setattr(av, "GERADOS", tmp_path)
    monkeypatch.setattr(av, "outros_processos_na_gpu", lambda: [])
    (tmp_path / "fatos.jsonl").write_text(json.dumps(POUPANCA, ensure_ascii=False) + "\n", encoding="utf-8")
    prompt = contrato.montar_prompt_resposta("quanto sobrou?", [POUPANCA])
    return prompt, json


def test_prompts_enviados_sao_os_do_contrato(monkeypatch, tmp_path):
    import contrato
    prompt, json = _preparar(monkeypatch, tmp_path)
    (tmp_path / "resposta_valid.jsonl").write_text(json.dumps({"prompt": prompt, "saida": "x"}) + "\n", encoding="utf-8")
    q = av._ler_jsonl(av.PERGUNTAS)[0]
    r = av.avaliar_modelo("x.gguf", "t", log=lambda *_: None)
    esperado = contrato.montar_prompt_plano(q["pergunta"], q["mes_atual"], q["categorias"], q["historico"])
    assert _Falso.prompts[0] == esperado and esperado.endswith("<|im_start|>assistant\n")
    assert _Falso.prompts[-1] == prompt and prompt.endswith("<|im_start|>assistant\n")
    assert r["com_think"] is False and r["n_respostas"] == 1 and r["n_respostas_puladas"] == 0


def test_com_think_so_se_pedido(monkeypatch, tmp_path):
    _preparar(monkeypatch, tmp_path)
    av.avaliar_modelo("x.gguf", "t", com_think=True, log=lambda *_: None)
    assert _Falso.prompts[0].endswith(av.THINK_VAZIO)


def test_resposta_com_fato_desconhecido_e_contada(monkeypatch, tmp_path):
    import json
    import contrato
    _preparar(monkeypatch, tmp_path)
    outro = dict(POUPANCA, frase="Frase que não está em fatos.jsonl.")
    (tmp_path / "resposta_valid.jsonl").write_text(
        json.dumps({"prompt": contrato.montar_prompt_resposta("q", [outro]), "saida": "x"}) + "\n", encoding="utf-8")
    r = av.avaliar_modelo("x.gguf", "t", log=lambda *_: None)
    assert r["n_respostas_puladas"] == 1 and r["n_respostas"] == 0


def test_plano_invalido_e_erro_e_nao_recusa(monkeypatch, tmp_path):
    _preparar(monkeypatch, tmp_path)
    _Falso.saida_plano = '{"chamadas":[{"f":"ran'
    try:
        r = av.avaliar_modelo("x.gguf", "t", log=lambda *_: None)
    finally:
        _Falso.saida_plano = '{"chamadas":[]}'
    assert r["n_planos_invalidos"] == 60 and r["plano_exato"] == 0 and r["recusa_correta"] == 0
    assert av.plano_do_modelo('{"chamadas":[{"f":"ran', "2026-11", []) is None
    assert av.plano_f1(None, []) == 0.0 and not av.plano_exato(None, [])


ATIPICOS = {"ferramenta": "ATIPICOS", "periodo": "outubro",
            "frase": "Fora do padrão em outubro: Outros, R$ 300 (possível duplicado); "
                     "Outros, R$ 900 (acima do comum na categoria, típico R$ 200).",
            "itens": [{"tipo": "reais", "valor": "300", "rotulo": "Outros"}, {"tipo": "reais", "valor": "900", "rotulo": "Outros"},
                      {"tipo": "reais", "valor": "200", "rotulo": "Outros"}]}


def test_atipicos_primeiro_sem_referencia():
    assert av._papeis_dos_itens(ATIPICOS) == [("valor", "300"), ("valor", "900"), ("tipico", "200")]
    assert not av.troca_de_papel("A compra de R$ 900 ficou acima do típico R$ 200.", [ATIPICOS])
    assert av.troca_de_papel("O valor típico era R$ 900.", [ATIPICOS])
    assert not av.fato_ambiguo(ATIPICOS)
    assert av.fato_ambiguo(dict(ATIPICOS, frase="Fora do padrão em outubro: Outros, R$ 300."))
