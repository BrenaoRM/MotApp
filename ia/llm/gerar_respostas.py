"""Gera os exemplos de treino da resposta (pergunta + fatos -> texto) com o professor, só com respostas verificadas.

Junta 1-4 fatos reais de dados/gerados/fatos.jsonl em uma pergunta (composta quando há mais de um), pede a resposta
ao professor (Qwen3-8B, llama-server na porta 8081) e só guarda se toda frase passa no verificador. Retomável:
`resposta.feito` tem uma linha JSON por tentativa; o gerador pula os ids já tentados e para ao juntar `--alvo` aprovadas.

    python gerar_respostas.py [--limite N] [--saida-dir DIR]     (--limite = quantas respostas aprovadas)
"""
import argparse
import json
import random
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import contrato
import gerar_planos as gp
import verificador

AQUI = Path(__file__).resolve().parent
FATOS = AQUI / "dados" / "gerados" / "fatos.jsonl"
ALVO = 6500            # ~6.000 treino + 500 validação
VALID_A_CADA = 13

# Valor por extenso (o verificador só confere dígitos): dezenas e centenas sempre; unidades só com moeda/escala.
_EXTENSO = re.compile(
    r"\b(cem|cento|duzentos|duzentas|trezentos|trezentas|quatrocentos|quatrocentas|quinhentos|quinhentas|seiscentos|seiscentas"
    r"|setecentos|setecentas|oitocentos|oitocentas|novecentos|novecentas|mil|milh[aã]o|milh[oõ]es|vinte|trinta|quarenta"
    r"|cinquenta|sessenta|setenta|oitenta|noventa|dez|onze|doze|treze|quatorze|catorze|quinze|dezesseis|dezessete|dezoito"
    r"|dezenove)\b"
    r"|\b(um|uma|dois|duas|tr[eê]s|quatro|cinco|seis|sete|oito|nove|meio|meia)\s+(reais|real|centavos?|mil|por cento|%)",
    re.IGNORECASE)

# Só o professor vê estas instruções extras; o prompt de treino é o do contrato.
EXTRA_PROFESSOR = " Não repita a pergunta. Escreva só a resposta, sem listas e sem markdown."
EXTRA_SEM_ECONOMIA = " Escreva uma frase por fato, só dizendo o que o fato diz."
_CONSELHO = re.compile(r"conselho|considere|recomend|(revise|reduza|evite|mantenha|controle|tente|procure|aproveite)", re.IGNORECASE)
_META = re.compile(r"fato de economia|n[aã]o h[aá] (outros|mais|fatos|categorias|gastos)|n[aã]o foram? (mencionad|citad)|mencionad[oa]s? nos fatos",
                   re.IGNORECASE)


def tem_valor_por_extenso(texto):
    return _EXTENSO.search(texto) is not None


def fato_se_verifica(fato):
    """Fato cuja própria frase passa no verificador (os de nome com dígito, como "Carro 2", nunca passam)."""
    periodo = None
    for f in verificador.frases(fato["frase"]):
        ok, periodo = verificador.verificar_frase(f, [fato], periodo)
        if not ok:
            return False
    return True


def carregar_fatos(caminho=FATOS):
    fatos = [json.loads(l) for l in Path(caminho).read_text(encoding="utf-8").splitlines() if l.strip()]
    bons = [f for f in fatos if fato_se_verifica(f)]
    return bons, len(fatos) - len(bons)


def pergunta_do_fato(fato, rnd, nome_da_ferramenta):
    """Pergunta escrita à mão (formas de gerar_planos) sobre um fato: período do fato, categoria do primeiro rótulo."""
    nome = nome_da_ferramenta[fato["ferramenta"]]
    periodo = fato["periodo"]
    rotulos = [i["rotulo"] for i in fato["itens"] if i["rotulo"]]
    com_categoria = nome in ("gasto_categoria", "maior_gasto", "orcamento_restante", "media_mensal") and bool(rotulos) \
        and (nome == "gasto_categoria" or rnd.random() < 0.5)
    forma = rnd.choice(gp.FORMAS[gp._chave_forma(nome, com_categoria)])
    mes = "em " + periodo if periodo else rnd.choice(gp.MESES_ATUAL)
    categoria = rotulos[0] if rotulos else "isso"
    texto = forma.replace("{m}", mes if gp.USA_MES[nome] else "").replace("{c}", categoria)
    return re.sub(r"\s+", " ", texto).strip()


def montar_caso(i, fatos, nome_da_ferramenta):
    rnd = random.Random(5000 + i)
    n = rnd.choices([1, 2, 3, 4], [0.4, 0.35, 0.18, 0.07])[0]
    escolhidos = rnd.sample(fatos, n)
    pergunta = gp._juntar([pergunta_do_fato(f, rnd, nome_da_ferramenta) for f in escolhidos], rnd)
    return pergunta, escolhidos


def _tem_economia(fatos):
    return any(f["ferramenta"] == "COMO_ECONOMIZAR" for f in fatos)


def avaliar_resposta(resposta, fatos):
    """None se aprovada; senão o motivo da rejeição."""
    if not resposta or "<think" in resposta or "**" in resposta:
        return "formato"
    if tem_valor_por_extenso(resposta):
        return "extenso"
    if _META.search(resposta) or (_CONSELHO.search(resposta) and not _tem_economia(fatos)):
        return "meta"
    lista = verificador.frases(resposta)
    if not lista or len(lista) > 5:
        return "tamanho"
    periodo, aprovadas = None, []
    for frase in lista:
        ok, periodo = verificador.verificar_frase(frase, fatos, periodo)
        if not ok:
            return "verificador"
        aprovadas.append(frase)
    if any(f["itens"] for f in fatos) and not verificador.fatos_citados(aprovadas, fatos):
        return "sem_fato"
    return None


def _sistema_do_professor(sistema, fatos):
    """Sem fato de economia o professor nem vê a menção a conselho (senão escreve "não há conselho")."""
    if _tem_economia(fatos):
        return sistema + EXTRA_PROFESSOR
    return sistema.replace(" e 1 conselho se houver fato de economia", "") + EXTRA_PROFESSOR + EXTRA_SEM_ECONOMIA


def _tentar(i, fatos, nome_da_ferramenta):
    pergunta, escolhidos = montar_caso(i, fatos, nome_da_ferramenta)
    prompt = contrato.montar_prompt_resposta(pergunta, escolhidos)
    sistema = contrato.carregar()["sistema_resposta"]
    resposta = gp.completar(prompt.replace(sistema, _sistema_do_professor(sistema, escolhidos), 1), seed=i, temperatura=0.6, n_predict=260)
    return prompt, resposta, avaliar_resposta(resposta, escolhidos)


def gerar(limite, saida_dir, workers=4, fatos_arquivo=FATOS, log=print):
    saida_dir = Path(saida_dir)
    saida_dir.mkdir(parents=True, exist_ok=True)
    treino, valid, feito = (saida_dir / "resposta_treino.jsonl", saida_dir / "resposta_valid.jsonl", saida_dir / "resposta.feito")
    fatos, pulados = carregar_fatos(fatos_arquivo)
    nome_da_ferramenta = {f["intencao"]: f["nome"] for f in contrato.carregar()["ferramentas"]}
    tentativas = {}
    if feito.exists():
        for l in feito.read_text(encoding="utf-8").splitlines():
            r = json.loads(l)
            tentativas[r["id"]] = r["motivo"]
    aprovadas = sum(m is None for m in tentativas.values())
    proximo = max(tentativas, default=-1) + 1
    motivos = {}
    for m in tentativas.values():
        if m is not None:
            motivos[m] = motivos.get(m, 0) + 1
    t0 = time.time()
    log("[respostas] fatos usáveis %d, pulados (frase própria reprovada) %d" % (len(fatos), pulados))
    with open(treino, "a", encoding="utf-8") as ft, open(valid, "a", encoding="utf-8") as fv, \
            open(feito, "a", encoding="utf-8") as ff, ThreadPoolExecutor(workers) as pool:
        while aprovadas < limite:
            ids = list(range(proximo, proximo + 32))
            for i, (prompt, resposta, motivo) in zip(ids, pool.map(lambda i: _tentar(i, fatos, nome_da_ferramenta), ids)):
                if motivo is None:
                    (fv if i % VALID_A_CADA == 0 else ft).write(
                        json.dumps({"prompt": prompt, "saida": resposta}, ensure_ascii=False) + "\n")
                    aprovadas += 1
                else:
                    motivos[motivo] = motivos.get(motivo, 0) + 1
                ff.write(json.dumps({"id": i, "motivo": motivo}) + "\n")
            ft.flush(), fv.flush(), ff.flush()
            proximo = ids[-1] + 1
            log("[respostas] tentativas %d  aprovadas %d (%.0f%%)  rejeições %s  %.0fs" % (
                proximo, aprovadas, 100 * aprovadas / proximo, motivos, time.time() - t0))
    return {"tentativas": proximo, "aprovadas": aprovadas, "rejeicoes": motivos, "fatos_pulados": pulados}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--limite", type=int, default=ALVO, help="respostas aprovadas a gerar (padrão 6500)")
    ap.add_argument("--saida-dir", default=str(gp.SAIDA_PADRAO))
    a = ap.parse_args(argv)
    sys.stdout.reconfigure(encoding="utf-8")
    print(gerar(a.limite, a.saida_dir, log=lambda s: print(s, flush=True)))


if __name__ == "__main__":
    main()
