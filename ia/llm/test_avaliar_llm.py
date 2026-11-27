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
