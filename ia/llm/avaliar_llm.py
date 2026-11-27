"""Avaliação reproduzível do LLM do assistente (plano + resposta) e do sistema atual (classificador de intenções).

    python avaliar_llm.py --modelo modelos/gguf/base-0.6B-q4km.gguf --nome base-0.6B
    python avaliar_llm.py --sistema-atual         # grava ia/resultados/llm/atual.json
    python avaliar_llm.py --tabela                # junta os JSON em ia/resultados/llm.md

Plano: 60 perguntas de teste/perguntas.jsonl, gramática por pergunta, temperatura 0, seed fixa.
O prompt é exatamente o de contrato.montar_prompt_* (igual ao treino e ao app).
Fidelidade: respostas do modelo às perguntas de dados/gerados/resposta_valid.jsonl; vale se toda frase passa no verificador.
O llama-server próprio sobe na porta 8082 (nunca a 8081, do professor) e é sempre encerrado.
"""
import argparse
import json
import re
import subprocess
import sys
import time
from pathlib import Path

import requests

import contrato
import gerar_planos as gp
import verificador

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parents[1]
PERGUNTAS = AQUI / "teste" / "perguntas.jsonl"
GERADOS = AQUI / "dados" / "gerados"
SERVIDOR = AQUI / "bin" / "llama-server.exe"
RESULTADOS = RAIZ / "ia" / "resultados"
SAIDA_JSON = RESULTADOS / "llm"
TABELA = RESULTADOS / "llm.md"
PORTA = 8082
SEED = 42
THINK_VAZIO = "<think>\n\n</think>\n\n"  # só com --com-think; o prompt do app e do treino não leva


# ---------------------------------------------------------------- métricas

def _chave(chamada):
    return (chamada["ferramenta"], chamada["mes"], chamada["categoria"])


def _normalizar_chamadas(chamadas, mes_atual, categorias):
    """Chamadas em forma canônica (mês resolvido: "atual" == mês atual), como o app as lê."""
    lido = gp.ler_plano(json.dumps({"chamadas": chamadas}, ensure_ascii=False), mes_atual, categorias)
    return [_chave(c) for c in lido or []]


def plano_do_modelo(texto, mes_atual, categorias):
    """Chamadas lidas como o app lê; None = JSON inválido (erro, não recusa)."""
    lido = gp.ler_plano(texto, mes_atual, categorias)
    return None if lido is None else [_chave(c) for c in lido]


def _multi(lista):
    contagem = {}
    for c in lista:
        contagem[c] = contagem.get(c, 0) + 1
    return contagem


def plano_exato(previsto, esperado):
    """Mesmas chamadas, em qualquer ordem; plano inválido (None) nunca é exato."""
    if previsto is None:
        return False
    return _multi(previsto) == _multi(esperado)


def plano_f1(previsto, esperado):
    """F1 das chamadas (multiconjunto); dois planos vazios (recusa certa) valem 1; plano inválido (None) vale 0."""
    if previsto is None:
        return 0.0
    if not previsto and not esperado:
        return 1.0
    p, e = _multi(previsto), _multi(esperado)
    acertos = sum(min(n, e.get(c, 0)) for c, n in p.items())
    if acertos == 0:
        return 0.0
    precisao, cobertura = acertos / len(previsto), acertos / len(esperado)
    return 2 * precisao * cobertura / (precisao + cobertura)


def resumir_planos(casos):
    """casos: [(previsto, esperado)] -> métricas agregadas."""
    n = len(casos)
    fora = [(p, e) for p, e in casos if not e]
    return {
        "plano_exato": sum(plano_exato(p, e) for p, e in casos) / n,
        "plano_parcial": sum(plano_f1(p, e) for p, e in casos) / n,
        "recusa_correta": (sum(p == [] for p, _ in fora) / len(fora)) if fora else None,
        "n_perguntas": n,
        "n_fora_do_escopo": len(fora),
        "n_planos_invalidos": sum(p is None for p, _ in casos),
    }


# ---------------------------------------------------------------- troca de papel

# Fatos com vários itens de mesmo (rotulo, tipo) e papéis diferentes. Ordem dos itens em RespondedorDePerguntas.kt:
#   POUPANCA: renda, gasto, saldo (sobrou/faltou), % da renda;  ORCAMENTO_RESTANTE: restante, limite, % usado
#   (estourado: excedido, limite);  ATIPICOS: por transação valor e, se houver, típico (mesmo rótulo).
PALAVRAS_DE_PAPEL = {
    "POUPANCA": {
        "renda": r"receb\w*|renda|receita\w*|ganh\w*|entrou|entraram",
        "gasto": r"gast\w*|despesa\w*|saiu|sairam",
        "saldo": r"sobr\w*|saldo|falt\w*",
    },
    "ORCAMENTO_RESTANTE": {
        "restante": r"rest\w*|sobra\w*|falta\w*|passou|estour\w*",
        "limite": r"limite\w*|orcamento\w*",
    },
    "ATIPICOS": {
        "valor": r"valor|pag\w*|gast\w*|cust\w*",
        "tipico": r"tipic\w*|normal|comum|costum\w*|esperad\w*",
    },
}
_NUM = r"(?:r\$ ?)?[0-9]+(?:\.[0-9]{3})*(?:,[0-9]+)?"
_LIGACAO = r"(?:\s+(?:de|do|da|em|foi|e|era|seria|ficou|total|um|uma|o|a|com|:)){0,2}"


def _papeis_dos_itens(fato):
    """[(papel, valor canônico)] dos itens em reais; None se o fato não é dos que trocam papel."""
    if fato["ferramenta"] not in PALAVRAS_DE_PAPEL:
        return None
    itens = [i for i in fato["itens"] if i["tipo"] == "reais"]
    if fato["ferramenta"] == "POUPANCA":
        nomes = ["renda", "gasto", "saldo"]
    elif fato["ferramenta"] == "ORCAMENTO_RESTANTE":
        nomes = ["restante", "limite"]  # estourado: o excedido ocupa o lugar do restante
    else:
        return _papeis_atipicos(fato, itens)
    return [(nomes[k], verificador._canonico(i["valor"])) for k, i in enumerate(itens) if k < len(nomes) and nomes[k]]


_TRECHO_TIPICO = re.compile(r"t[ií]pico R\$ ?([0-9]+(?:\.[0-9]{3})*(?:,[0-9]+)?)")
_REAIS = re.compile(r"R\$ ?([0-9]+(?:\.[0-9]{3})*(?:,[0-9]+)?)")


def _papeis_atipicos(fato, itens):
    """O "típico" só existe quando há valor de referência, então a posição dos itens não diz o papel: lê-se da frase
    ("Fora do padrão em X: onde, R$ valor (motivo[, típico R$ ref]); ..."). None se a frase não bate com os itens."""
    corpo = fato["frase"].split(": ", 1)[-1]
    papeis = []
    for trecho in corpo.split("; "):
        numeros = _REAIS.findall(trecho)
        if not numeros:
            continue
        papeis.append(("valor", verificador._canonico(numeros[0])))
        tipico = _TRECHO_TIPICO.search(trecho)
        if tipico:
            papeis.append(("tipico", verificador._canonico(tipico.group(1))))
    if [v for _, v in papeis] != [verificador._canonico(i["valor"]) for i in itens]:
        return None
    return papeis


def fato_ambiguo(fato):
    """Fato de papéis que a heurística não consegue identificar (fica de fora da troca_de_papel)."""
    return fato["ferramenta"] in PALAVRAS_DE_PAPEL and len(fato["itens"]) >= 2 and not _papeis_dos_itens(fato)


def troca_de_papel(resposta, fatos):
    """True se alguma frase põe, logo depois de uma palavra de papel, o valor de OUTRO papel do mesmo fato.

    Heurística: palavra de papel + até 2 palavras de ligação + número. O verificador não pega isso porque os dois
    valores são verdadeiros, só estão trocados. Só vale para fatos de poupança, orçamento e gasto atípico.
    """
    for fato in fatos:
        itens = _papeis_dos_itens(fato)
        if not itens or len({p for p, _ in itens}) < 2:
            continue
        for frase in verificador.frases(resposta):
            texto = verificador._normalizar(frase)
            for papel, regex in PALAVRAS_DE_PAPEL[fato["ferramenta"]].items():
                for m in re.finditer(r"\b(?:" + regex + r")\b" + _LIGACAO + r"\s+(" + _NUM + r")(?!\d)", texto):
                    valor = verificador._canonico(re.sub(r"r\$ ?", "", m.group(1)))
                    proprio = {v for p, v in itens if p == papel}
                    outro = {v for p, v in itens if p != papel}
                    if valor in outro and valor not in proprio:
                        return True
    return False


def resposta_fiel(resposta, fatos):
    """Toda frase aprovada pelo verificador, com o período vigente passando de frase em frase."""
    lista = verificador.frases(resposta or "")
    if not lista:
        return False
    periodo = None
    for frase in lista:
        ok, periodo = verificador.verificar_frase(frase, fatos, periodo)
        if not ok:
            return False
    return True


def cita_papeis(fatos):
    return any(f["ferramenta"] in PALAVRAS_DE_PAPEL and len(f["itens"]) >= 2 for f in fatos)


# ---------------------------------------------------------------- llama-server

class Servidor:
    def __init__(self, modelo, porta=PORTA):
        assert porta != 8081, "8081 é do professor"
        self.url = "http://127.0.0.1:%d" % porta
        self.modelo, self.porta, self.proc = Path(modelo), porta, None

    def __enter__(self):
        GERADOS.mkdir(parents=True, exist_ok=True)
        log = open(GERADOS / ("avaliacao-servidor-%d.log" % self.porta), "w", encoding="utf-8")
        self.proc = subprocess.Popen(
            [str(SERVIDOR), "-m", str(self.modelo), "-ngl", "99", "-c", "2048", "-np", "1", "--port", str(self.porta)],
            stdout=log, stderr=subprocess.STDOUT)
        limite = time.time() + 180
        while time.time() < limite:
            if self.proc.poll() is not None:
                raise RuntimeError("llama-server saiu com código %s (veja o log)" % self.proc.returncode)
            try:
                if requests.get(self.url + "/health", timeout=2).status_code == 200:
                    return self
            except requests.RequestException:
                pass
            time.sleep(1)
        self.__exit__()
        raise RuntimeError("llama-server não ficou pronto")

    def __exit__(self, *_):
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(20)
            except subprocess.TimeoutExpired:
                self.proc.kill()

    def completar(self, prompt, grammar=None, n_predict=256):
        corpo = {"prompt": prompt, "temperature": 0, "seed": SEED, "n_predict": n_predict, "stop": ["<|im_end|>"],
                 "cache_prompt": True}
        if grammar:
            corpo["grammar"] = grammar
        r = requests.post(self.url + "/completion", json=corpo, timeout=300)
        r.raise_for_status()
        j = r.json()
        t = j.get("timings", {})
        return j["content"].strip(), t.get("predicted_n", 0), t.get("predicted_ms", 0.0)


def _ler_jsonl(caminho):
    return [json.loads(l) for l in Path(caminho).read_text(encoding="utf-8").splitlines() if l.strip()]


def _fatos_por_frase():
    return {f["frase"]: f for f in _ler_jsonl(GERADOS / "fatos.jsonl")}


def _fatos_do_prompt(prompt, por_frase):
    """Fatos (com itens) do prompt de resposta, achados pela frase; None se algum não existe em fatos.jsonl."""
    corpo = prompt.split("\nFatos:\n", 1)[1].rsplit("<|im_end|>", 1)[0]
    saida = []
    for linha in corpo.strip().splitlines():
        frase = re.sub(r"^\d+\. ", "", linha)
        if frase not in por_frase:
            return None
        saida.append(por_frase[frase])
    return saida


# ---------------------------------------------------------------- avaliações

def outros_processos_na_gpu():
    """PIDs com a GPU em uso agora (nvidia-smi); [] se a GPU está livre ou o nvidia-smi não existe."""
    try:
        saida = subprocess.run(["nvidia-smi", "--query-compute-apps=pid", "--format=csv,noheader"],
                               capture_output=True, text=True, timeout=20).stdout
    except (OSError, subprocess.SubprocessError):
        return []
    return [l.strip() for l in saida.splitlines() if l.strip().isdigit()]


def avaliar_modelo(modelo, nome, com_think=False, limite_validacao=None, log=print):
    sufixo = THINK_VAZIO if com_think else ""
    na_gpu = outros_processos_na_gpu()
    perguntas = _ler_jsonl(PERGUNTAS)
    tokens, ms = 0, 0.0
    with Servidor(modelo) as s:
        casos, detalhes = [], []
        for q in perguntas:
            prompt = contrato.montar_prompt_plano(q["pergunta"], q["mes_atual"], q["categorias"], q["historico"]) + sufixo
            texto, n, t = s.completar(prompt, contrato.gramatica_plano(q["mes_atual"], q["categorias"]), n_predict=200)
            tokens, ms = tokens + n, ms + t
            previsto = plano_do_modelo(texto, q["mes_atual"], q["categorias"])
            esperado = _normalizar_chamadas(q["plano_esperado"], q["mes_atual"], q["categorias"])
            casos.append((previsto, esperado))
            detalhes.append({"pergunta": q["pergunta"], "saida": texto, "exato": plano_exato(previsto, esperado)})
        resultado = resumir_planos(casos)
        log("[plano] %s" % resultado)

        validacao = GERADOS / "resposta_valid.jsonl"
        puladas = ambiguos = 0
        fieis = com_papeis = trocas_papeis = usados = 0
        if validacao.exists():
            por_frase = _fatos_por_frase()
            for l in _ler_jsonl(validacao)[:limite_validacao]:
                fatos = _fatos_do_prompt(l["prompt"], por_frase)
                if fatos is None:
                    puladas += 1
                    continue
                resp, n, t = s.completar(l["prompt"] + sufixo, n_predict=260)
                tokens, ms = tokens + n, ms + t
                usados += 1
                fieis += resposta_fiel(resp, fatos)
                ambiguos += sum(fato_ambiguo(f) for f in fatos)
                if cita_papeis(fatos):
                    com_papeis += 1
                    trocas_papeis += troca_de_papel(resp, fatos)
            log("[resposta] %d respostas (%d puladas), fiéis %d, com papéis %d, trocas %d" % (
                usados, puladas, fieis, com_papeis, trocas_papeis))
    resultado.update({
        "fidelidade": (fieis / usados) if usados else None,
        "troca_de_papel": (trocas_papeis / com_papeis) if com_papeis else None,
        "n_respostas": usados, "n_respostas_puladas": puladas, "n_respostas_com_papeis": com_papeis,
        "n_fatos_ambiguos_na_troca": ambiguos,
        "tokens_por_s_pc": (1000 * tokens / ms) if ms else None,
        "modelo": Path(modelo).name, "nome": nome, "seed": SEED, "com_think": com_think,
        "gpu_compartilhada": bool(na_gpu), "processos_na_gpu": len(na_gpu),
    })
    resultado["falhas_plano"] = [d for d in detalhes if not d["exato"]][:10]
    return resultado


def intencao_para_ferramenta():
    """Mapa lido do contrato.json (campo `intencao` de cada ferramenta): RANKING_CATEGORIAS->ranking,
    GASTO_EM_CATEGORIA->gasto_categoria, TOTAL_DO_MES->total_mes, COMPARAR_MES_ANTERIOR->comparar_mes_anterior,
    MAIOR_GASTO->maior_gasto, ORCAMENTO_RESTANTE->orcamento_restante, ASSINATURAS->assinaturas, POUPANCA->poupanca,
    RECEITAS->receitas, MEDIA_MENSAL->media_mensal, ATIPICOS->atipicos, COMO_ECONOMIZAR->como_economizar.
    Recusa (confiança abaixo do limiar ou longe dos centroides) = plano vazio."""
    return {f["intencao"]: f["nome"] for f in contrato.carregar()["ferramentas"]}


def avaliar_sistema_atual():
    import numpy as np
    sys.path.insert(0, str(RAIZ / "ia"))
    import treinar_intencoes as ti
    cfg = json.loads(ti.ARQ_CONFIG.read_text(encoding="utf-8"))["intencoes"]
    mapa = intencao_para_ferramenta()
    perguntas = _ler_jsonl(PERGUNTAS)
    X = ti.vetorizar([q["pergunta"] for q in perguntas])
    P = ti.prever(X, cfg["pesos"], cfg["vieses"])
    S = ti.similaridade_maxima(X, np.asarray(cfg["centroides"]))
    recusa = (P.max(1) < cfg["limiar"]) | (S < cfg["similaridadeMinima"])
    exatos, f1s, cobertura, fora_ok, fora, compostas = [], [], [], 0, 0, 0
    for q, p, r in zip(perguntas, P, recusa):
        esperado = [c["f"] for c in q["plano_esperado"]]
        previsto = [] if r else [mapa[cfg["nomes"][int(p.argmax())]]]
        if not esperado:
            fora += 1
            fora_ok += not previsto
            exatos.append(not previsto)
            f1s.append(1.0 if not previsto else 0.0)
            continue
        acerto = bool(previsto) and previsto[0] in esperado
        n = len(esperado)
        exatos.append(acerto and n == 1)
        f1s.append(2 / (n + 1) if acerto else 0.0)
        cobertura.append(1 / n if acerto else 0.0)
        compostas += n > 1
    n = len(perguntas)
    return {
        "plano_exato": sum(exatos) / n, "plano_parcial": sum(f1s) / n,
        "recusa_correta": fora_ok / fora if fora else None,
        "n_perguntas": n, "n_fora_do_escopo": fora, "n_compostas": compostas,
        "cobertura_dos_planos_com_escopo": sum(cobertura) / len(cobertura),
        "fidelidade": None, "troca_de_papel": None, "tokens_por_s_pc": None,
        "nome": "atual", "modelo": "modelo_ia.json (regressão logística + recusa por centroide)",
        "nota": "Só intenção (sem mês nem categoria); exato = intenção certa em pergunta de 1 chamada; "
                "parcial = F1 com 1 chamada prevista (acerto em composta de n chamadas = 2/(n+1)).",
    }


def tabela():
    arquivos = sorted(SAIDA_JSON.glob("*.json"), key=lambda p: (p.stem != "atual", p.stem))
    dados = [json.loads(p.read_text(encoding="utf-8")) for p in arquivos]
    if not dados:
        raise SystemExit("sem JSON em " + str(SAIDA_JSON))

    def pct(v):
        return "-" if v is None else "%.1f%%" % (100 * v)

    linhas = [
        ("plano exato", lambda d: pct(d["plano_exato"])),
        ("plano parcial (F1)", lambda d: pct(d["plano_parcial"])),
        ("recusa correta", lambda d: pct(d["recusa_correta"])),
        ("fidelidade", lambda d: pct(d["fidelidade"])),
        ("troca de papel (menor é melhor)", lambda d: pct(d["troca_de_papel"])),
        ("tokens/s (PC, GPU compartilhada)", lambda d: "-" if d["tokens_por_s_pc"] is None else "%.0f" % d["tokens_por_s_pc"]),
        ("perguntas de plano", lambda d: str(d["n_perguntas"])),
        ("planos inválidos (contam como erro)", lambda d: str(d.get("n_planos_invalidos", "-"))),
        ("respostas avaliadas", lambda d: str(d.get("n_respostas", "-"))),
    ]
    texto = "# Avaliação do LLM local\n\nGerado por `ia/llm/avaliar_llm.py --tabela` a partir de `ia/resultados/llm/*.json`.\n\n"
    texto += "| métrica | " + " | ".join(d["nome"] for d in dados) + " |\n|---|" + "---|" * len(dados) + "\n"
    for nome, f in linhas:
        texto += "| %s | %s |\n" % (nome, " | ".join(f(d) for d in dados))
    texto += ("\nPlano: 60 perguntas de `ia/llm/teste/perguntas.jsonl`. O sistema atual só prevê a intenção "
              "(mapeada para a ferramenta do contrato), então o plano exato dele só conta perguntas de 1 chamada. "
              "Fidelidade: respostas às perguntas de `resposta_valid.jsonl` com todas as frases aprovadas pelo verificador. "
              "Troca de papel: heurística de palavra de papel + número em respostas de poupança, orçamento e gasto atípico.\n")
    TABELA.write_text(texto, encoding="utf-8")
    return texto


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--modelo")
    ap.add_argument("--nome")
    ap.add_argument("--sistema-atual", action="store_true")
    ap.add_argument("--tabela", action="store_true")
    ap.add_argument("--com-think", action="store_true", help="acrescenta <think> vazio ao prompt (o app e o treino não usam)")
    ap.add_argument("--limite-validacao", type=int)
    a = ap.parse_args(argv)
    sys.stdout.reconfigure(encoding="utf-8")
    SAIDA_JSON.mkdir(parents=True, exist_ok=True)
    codigo = 0
    if a.modelo:
        if not a.nome:
            ap.error("--modelo exige --nome")
        r = avaliar_modelo(a.modelo, a.nome, a.com_think, a.limite_validacao, log=lambda s: print(s, flush=True))
        (SAIDA_JSON / (a.nome + ".json")).write_text(json.dumps(r, ensure_ascii=False, indent=1), encoding="utf-8")
        if r["n_respostas_puladas"]:
            print("ERRO: %d respostas da validação puladas (fatos fora de fatos.jsonl)" % r["n_respostas_puladas"], file=sys.stderr)
            codigo = 1
    if a.sistema_atual:
        (SAIDA_JSON / "atual.json").write_text(json.dumps(avaliar_sistema_atual(), ensure_ascii=False, indent=1), encoding="utf-8")
    if a.tabela:
        print(tabela())
    sys.exit(codigo)


if __name__ == "__main__":
    main()
