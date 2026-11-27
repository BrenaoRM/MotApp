import json

import pytest

import exportar_gguf as ex
import treinar_lora as tl


class TokFalso:
    """1 token por caractere (ord); <|im_end|> vira um único token 9999."""
    def __call__(self, texto, add_special_tokens=False):
        ids, i = [], 0
        while i < len(texto):
            if texto.startswith(tl.FIM_DE_TURNO, i):
                ids.append(9999)
                i += len(tl.FIM_DE_TURNO)
            else:
                ids.append(ord(texto[i]))
                i += 1
        return {"input_ids": ids}


def test_mascara_so_conta_a_saida_e_o_fim_de_turno():
    ids, labels = tl.tokenizar_exemplo(TokFalso(), "ab\n", "xy")
    assert ids == [97, 98, 10, 120, 121, 9999]
    assert labels == [-100, -100, -100, 120, 121, 9999]


def test_exemplo_longo_demais_e_descartado_sem_cortar():
    assert tl.tokenizar_exemplo(TokFalso(), "a" * 10, "b" * 5, contexto=15) is None
    assert tl.tokenizar_exemplo(TokFalso(), "a" * 10, "b" * 5, contexto=16) is not None
    lista, descartados = tl.tokenizar_conjunto(
        TokFalso(), [{"prompt": "a", "saida": "b"}, {"prompt": "a" * 2000, "saida": "b"}])
    assert len(lista) == 1 and descartados == 1


def test_juntar_lote_padding():
    lote = [{"input_ids": [1, 2, 3], "labels": [-100, 2, 3]}, {"input_ids": [4], "labels": [4]}]
    r = tl.juntar_lote(lote, pad_id=0)
    assert r["input_ids"] == [[1, 2, 3], [4, 0, 0]]
    assert r["labels"] == [[-100, 2, 3], [4, -100, -100]]
    assert r["attention_mask"] == [[1, 1, 1], [1, 0, 0]]


def test_mistura_e_leitura(tmp_path):
    def grava(nome, n, tag):
        linhas = "".join(json.dumps({"prompt": "%s%d" % (tag, i), "saida": "s"}) + "\n" for i in range(n))
        (tmp_path / nome).write_text(linhas + "\n", encoding="utf-8")
    grava("plano_treino.jsonl", 6, "p")
    grava("resposta_treino.jsonl", 4, "r")
    grava("plano_valid.jsonl", 3, "pv")
    grava("resposta_valid.jsonl", 2, "rv")
    treino, valid = tl.carregar_dados(dir_dados=tmp_path)
    assert len(treino) == 10 and len(valid) == 5
    assert {x["prompt"][0] for x in treino} == {"p", "r"}
    assert [x["prompt"][0] for x in treino] != ["p"] * 6 + ["r"] * 4  # embaralhou
    assert tl.carregar_dados(dir_dados=tmp_path) == (treino, valid)  # determinístico
    t2, v2 = tl.carregar_dados(max_exemplos=3, max_valid=2, dir_dados=tmp_path)
    assert (len(t2), len(v2)) == (3, 2)


def test_passos_e_config_de_vram():
    assert tl.CONFIG["4B"] == (1, 16, True)
    for lote, acum, _ in tl.CONFIG.values():
        assert lote * acum == 16
    assert tl.passos_por_epoca(12000, 1, 16) == 750
    assert tl.passos_de_avaliacao(12000, 1, 16) == 187
    assert tl.passos_de_avaliacao(10, 8, 2) == 10


def test_ultimo_checkpoint(tmp_path):
    assert tl.ultimo_checkpoint(tmp_path) is None
    for n in (100, 20, 300):
        (tmp_path / ("checkpoint-%d" % n)).mkdir()
        (tmp_path / ("checkpoint-%d" % n) / "trainer_state.json").write_text("{}")
    (tmp_path / "checkpoint-400").mkdir()  # incompleto (sem trainer_state): ignorado
    assert tl.ultimo_checkpoint(tmp_path).endswith("checkpoint-300")


def test_cli_do_treino():
    with pytest.raises(SystemExit):
        tl.main(["--base", "9B", "--saida", "x"])
    with pytest.raises(SystemExit):
        tl.main([])


def test_divisao_por_tamanho():
    assert not ex.precisa_dividir(ex.LIMITE_PARTE)
    assert ex.precisa_dividir(ex.LIMITE_PARTE + 1)


def test_exportar_cli_e_utilitarios(tmp_path):
    with pytest.raises(SystemExit):
        ex.main([])
    (tmp_path / "adapter_config.json").write_text(json.dumps({"base_model_name_or_path": "X/y"}))
    assert ex.base_do_adaptador(tmp_path) == "X/y"
    arq = tmp_path / "a.bin"
    arq.write_bytes(b"abc")
    assert ex.sha256(arq) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    assert "sha256 ba7816" in ex.relatorio([arq])[0]


def test_orquestracao_reconhece_linha_final_do_gerador():
    import rodar_treinos as rt
    assert rt.LINHA_FINAL.match("{'tentativas': 7000, 'aprovadas': 6500, 'rejeicoes': {}, 'fatos_pulados': 3}")
    assert not rt.LINHA_FINAL.match("[respostas] tentativas 288  aprovadas 257 (89%)")
    assert [n for n, _ in rt.MODOS] == ["rapida", "media", "inteligente"]
    assert dict(rt.MODOS) == {"rapida": "0.6B", "media": "1.7B", "inteligente": "4B"}


@pytest.mark.parametrize("base", sorted(tl.CONFIG))
def test_training_arguments_validos_para_as_tres_bases(base, tmp_path, monkeypatch):
    monkeypatch.setattr(tl, "_SEM_GPU", True)
    lote, acum, _ = tl.CONFIG[base]
    a = tl.montar_args(tmp_path, lote, acum, 12000)
    assert a.per_device_train_batch_size * a.gradient_accumulation_steps == 16
    assert a.per_device_eval_batch_size == tl.EVAL_LOTE <= 2
    assert a.warmup_steps == 0.03 and a.train_sampling_strategy == "group_by_length"
    assert a.eval_steps == a.save_steps and a.load_best_model_at_end
    assert tl.montar_args(tmp_path, lote, acum, 400, passos=20).max_steps == 20


def test_oom_retry_mantem_o_lote_efetivo():
    import rodar_treinos as rt
    assert rt.CONFIG_LOTE == {k: (v[0], v[1]) for k, v in tl.CONFIG.items()}


def test_exportar_divide_na_mesma_unidade_do_llama():
    assert ex.LIMITE_PARTE == 1900 * 1024 * 1024
