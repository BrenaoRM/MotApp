"""Gera os exemplos de treino do plano (pergunta -> JSON de chamadas) do assistente.

Formas escritas à mão por ferramenta, categorias sorteadas como em ExportarFatosParaTreino.kt e, em 30% das
perguntas, uma paráfrase do professor (Qwen3-8B no llama-server, porta 8081). Retomável: o arquivo `plano.feito`
guarda o último id gravado e o gerador continua dali.

    python gerar_planos.py [--limite N] [--saida-dir DIR] [--sem-professor]
"""
import argparse
import json
import random
import re
import sys
import time
import unicodedata
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import requests

import contrato

AQUI = Path(__file__).resolve().parent
SAIDA_PADRAO = AQUI / "dados" / "gerados"
PERGUNTAS_TESTE = AQUI / "teste" / "perguntas.jsonl"
PROFESSOR = "http://127.0.0.1:8081"
TOTAL = 13000          # 12.000 treino + 1.000 validação
VALID_A_CADA = 13      # id % 13 == 0 vai para a validação
MES_ATUAL_MIN, MES_ATUAL_MAX = "2025-06", "2026-12"

PADRAO = ["Alimentação", "Transporte", "Lazer", "Contas", "Outros"]
INVENTADAS = ["Mercado", "Saúde", 'Pet "Rex"', "🍔 Lanche", "Educação", "Academia", "Viagem", "Casa", "Farmácia",
              "Café & Cia", "Streaming", "Presentes", "Carro 2", "Roupas"]

MESES_ATUAL = ["este mês", "neste mês", "nesse mês", "agora", "no momento", "até agora", "do mês"]
MESES_ANTERIOR = ["no mês passado", "mês passado", "no mês anterior", "do mês passado", "no último mês"]

# {m} = período, {c} = categoria. Sem pontuação final (o conector acrescenta).
FORMAS = {
    "ranking": [
        "onde eu mais gastei {m}", "em que eu gasto mais {m}", "ranking de gastos {m}", "quais categorias mais pesaram {m}",
        "me mostra o ranking das categorias {m}", "qual a categoria que mais gasto {m}", "no que gastei mais {m}",
        "top gastos {m}", "quais foram meus maiores gastos por categoria {m}", "onde foi parar meu dinheiro {m}",
        "quero ver as categorias em ordem de gasto {m}", "qual categoria lidera os gastos {m}", "me diz onde mais gastei {m}",
        "gastos por categoria {m}", "o que mais pesou no bolso {m}", "em que categoria eu mais gastei {m}",
    ],
    "gasto_categoria": [
        "quanto gastei com {c} {m}", "quanto foi de {c} {m}", "gasto com {c} {m}", "me diz quanto gastei em {c} {m}",
        "qto gastei de {c} {m}", "quanto saiu em {c} {m}", "quanto eu gastei na categoria {c} {m}", "total de {c} {m}",
        "qual foi o gasto de {c} {m}", "quanto já foi de {c} {m}", "quanto paguei de {c} {m}", "{c} {m}, quanto deu",
        "quero saber quanto gastei com {c} {m}", "quanto você acha que gastei com {c} {m}", "me fala o gasto em {c} {m}",
        "quanto custou {c} {m}",
    ],
    "total_mes": [
        "quanto gastei {m}", "qual o total de gastos {m}", "total gasto {m}", "quanto eu gastei no total {m}",
        "me diz o total do mês {m}", "quanto saiu da minha conta {m}", "soma dos gastos {m}", "gastei quanto {m}",
        "qual meu gasto total {m}", "quanto já gastei {m}", "total das despesas {m}", "quanto foi de despesa {m}",
        "me fala quanto gastei ao todo {m}", "quanto dinheiro eu gastei {m}", "quanto gastei em tudo {m}",
        "qual o valor total que gastei {m}",
    ],
    "comparar_mes_anterior": [
        "gastei mais ou menos que no mês anterior {m}", "como {m} se compara com o mês anterior",
        "comparar {m} com o mês anterior", "gastei mais do que antes {m}", "estou gastando mais que no mês anterior {m}",
        "compara meus gastos {m} com o mês anterior", "melhorei ou piorei {m}", "como estão meus gastos {m} contra o mês anterior",
        "subiu ou desceu o gasto {m}", "gasto {m} versus mês anterior", "tô gastando mais ou menos {m}",
        "me diz se gastei mais {m} do que no mês anterior", "diferença de gasto para o mês anterior {m}",
        "evoluí nos gastos {m}", "comparação com o mês anterior {m}", "meus gastos aumentaram {m}",
    ],
    "maior_gasto": [
        "qual foi meu maior gasto {m}", "maior gasto {m}", "qual a maior despesa {m}", "o que foi mais caro {m}",
        "qual compra mais cara {m}", "me mostra o maior gasto {m}", "qual foi o gasto mais alto {m}",
        "maior despesa individual {m}", "onde gastei mais de uma vez só {m}", "qual foi a compra mais pesada {m}",
        "quero ver meu maior gasto {m}", "me diz o maior gasto {m}", "a maior saída de dinheiro {m}",
        "qual a maior compra {m}", "gasto mais alto {m}", "o que mais custou {m}",
    ],
    "maior_gasto_cat": [
        "qual foi meu maior gasto em {c} {m}", "maior gasto de {c} {m}", "qual a maior despesa com {c} {m}",
        "o que foi mais caro em {c} {m}", "maior compra de {c} {m}", "me mostra o maior gasto em {c} {m}",
        "qual foi o gasto mais alto de {c} {m}", "maior saída de {c} {m}", "qual foi a compra mais cara em {c} {m}",
        "quero ver o maior gasto com {c} {m}", "me diz o maior gasto em {c} {m}", "maior despesa de {c} {m}",
        "maior gasto na categoria {c} {m}", "{c}: qual foi o maior gasto {m}", "o maior gasto com {c} {m}",
        "qual a compra mais pesada em {c} {m}",
    ],
    "orcamento_restante": [
        "quanto resta do orçamento {m}", "quanto ainda posso gastar {m}", "orçamento restante {m}",
        "quanto sobrou do orçamento {m}", "meu orçamento {m}, quanto falta", "ainda tenho orçamento {m}",
        "quanto falta pra estourar o orçamento {m}", "quanto do orçamento ainda está livre {m}",
        "me diz quanto sobra do orçamento {m}", "como está meu orçamento {m}", "resta quanto de orçamento {m}",
        "quanto dá pra gastar ainda {m}", "estourei o orçamento {m}", "quanto tenho disponível pra gastar {m}",
        "saldo do orçamento {m}", "quanto sobra pra gastar {m}",
    ],
    "orcamento_restante_cat": [
        "quanto resta do orçamento de {c} {m}", "quanto ainda posso gastar com {c} {m}", "orçamento de {c} {m}",
        "quanto sobrou do orçamento de {c} {m}", "meu orçamento de {c} {m}, quanto falta", "ainda tenho orçamento de {c} {m}",
        "quanto falta pra estourar {c} {m}", "quanto do orçamento de {c} está livre {m}", "resta quanto de {c} {m}",
        "como está o orçamento de {c} {m}", "quanto dá pra gastar ainda em {c} {m}", "estourei o orçamento de {c} {m}",
        "saldo do orçamento de {c} {m}", "quanto sobra de {c} {m}", "limite de {c} {m}, quanto resta",
        "quanto posso gastar em {c} {m}",
    ],
    "assinaturas": [
        "quais são minhas assinaturas", "minhas assinaturas", "quanto pago de assinaturas", "quais gastos recorrentes eu tenho",
        "me mostra as assinaturas", "o que eu pago todo mês", "lista de assinaturas", "quanto gasto com assinaturas",
        "quais assinaturas estão ativas", "tenho muita assinatura", "gastos fixos mensais", "quais cobranças recorrentes eu tenho",
        "me diz as assinaturas que eu pago", "assinaturas e recorrentes", "o que cobra todo mês no meu cartão",
        "quanto vai de assinatura por mês",
    ],
    "poupanca": [
        "quanto sobrou {m}", "quanto eu poupei {m}", "consegui guardar dinheiro {m}", "quanto economizei {m}",
        "sobrou alguma coisa {m}", "quanto guardei {m}", "qual minha poupança {m}", "meu saldo {m}, quanto sobrou",
        "tô conseguindo poupar {m}", "quanto sobra do que eu ganho {m}", "quanto fiquei no positivo {m}",
        "me fala quanto poupei {m}", "dinheiro que sobrou {m}", "ganhei mais do que gastei {m}", "quanto restou no fim {m}",
        "taxa de poupança {m}",
    ],
    "receitas": [
        "quanto recebi {m}", "minhas receitas {m}", "quanto entrou {m}", "qual o total de receitas {m}",
        "quanto ganhei {m}", "me mostra as receitas {m}", "quanto dinheiro entrou na conta {m}", "total que recebi {m}",
        "quais foram minhas entradas {m}", "quanto foi de salário {m}", "recebi quanto {m}", "qual minha renda {m}",
        "quanto de dinheiro eu recebi {m}", "entradas {m}", "me diz quanto entrou {m}", "ganhos {m}",
    ],
    "media_mensal": [
        "qual minha média mensal de gastos", "média de gasto por mês", "quanto gasto em média por mês",
        "gasto médio mensal", "em média quanto eu gasto", "me diz a média mensal", "média dos meus gastos mensais",
        "quanto costumo gastar por mês", "qual a média de despesas por mês", "gasto mensal médio",
        "normalmente quanto gasto num mês", "média mensal geral", "quanto é minha média de gasto",
        "me fala a média por mês", "média mensal das despesas", "quanto gasto por mês em média",
    ],
    "media_mensal_cat": [
        "qual minha média mensal de {c}", "média de gasto com {c} por mês", "quanto gasto em média com {c}",
        "gasto médio mensal de {c}", "em média quanto eu gasto em {c}", "me diz a média mensal de {c}",
        "média dos meus gastos com {c}", "quanto costumo gastar com {c} por mês", "média mensal em {c}",
        "normalmente quanto gasto com {c} num mês", "média de {c} por mês", "quanto é minha média de {c}",
        "me fala a média mensal de {c}", "quanto gasto por mês em média com {c}", "{c}: média por mês",
        "qual a média de {c}",
    ],
    "atipicos": [
        "algum gasto estranho {m}", "tem gasto fora do padrão {m}", "gastos atípicos {m}", "teve alguma coisa diferente {m}",
        "me mostra os gastos fora do normal {m}", "algum gasto suspeito {m}", "gastei algo fora do comum {m}",
        "tem despesa esquisita {m}", "gastos incomuns {m}", "o que fugiu do padrão {m}", "teve gasto anormal {m}",
        "quero ver gastos fora da curva {m}", "alguma compra que não é a minha cara {m}", "me diz se tem gasto atípico {m}",
        "gastos inesperados {m}", "algum gasto que destoa {m}",
    ],
    "como_economizar": [
        "como posso economizar", "onde dá pra economizar", "me dá dicas pra gastar menos", "no que é melhor gastar menos",
        "como eu gasto menos", "onde posso cortar gastos", "como economizar mais", "tem como eu gastar menos",
        "o que eu devo cortar", "me ajuda a economizar", "onde estou gastando demais", "dicas de economia",
        "quero economizar, por onde começo", "o que posso reduzir", "como faço pra sobrar mais dinheiro",
        "o que cortar pra economizar",
    ],
}
USA_MES = {f["nome"]: f["usa_mes"] for f in contrato.carregar()["ferramentas"]}

FORA_DE_ESCOPO = [
    "qual a capital da frança", "me conta uma piada", "vai chover amanhã", "quem ganhou o jogo ontem",
    "como faço bolo de cenoura", "oi", "olá, tudo bem", "você é o chatgpt", "quanto custa um iphone 15",
    "traduz good morning pra português", "qual o melhor investimento em ações hoje", "me explica o que é inflação",
    "quem descobriu o brasil", "escreve um poema sobre o mar", "qual a previsão do tempo pra sexta",
    "como instalar o windows", "me indica um filme", "quantos habitantes tem são paulo", "qual a cotação do dólar",
    "o que é bitcoin", "como aprender inglês rápido", "me ajuda com meu currículo", "qual seu nome", "bom dia",
    "como emagrecer rápido", "quem é o presidente do brasil", "receita de brigadeiro", "qual o sentido da vida",
    "me conta uma curiosidade", "como declarar imposto de renda", "qual o melhor celular", "obrigado", "tchau",
    "quanto é 15 vezes 12", "como funciona a bolsa de valores", "qual o melhor banco digital",
    "dá pra investir em fundo imobiliário", "quando é o próximo feriado", "me diz um conselho de vida",
    "como tirar manchas de roupa", "quanto tempo dura uma bateria de carro",
]
_PAISES = ["frança", "japão", "argentina", "itália", "canadá", "portugal", "austrália", "egito", "chile", "alemanha", "méxico", "peru"]
_COMIDAS = ["bolo de cenoura", "brigadeiro", "lasanha", "pão de queijo", "feijoada", "panqueca", "arroz de forno", "mousse de maracujá", "strogonoff", "tapioca"]
_CIDADES = ["são paulo", "curitiba", "recife", "salvador", "porto alegre", "manaus", "fortaleza", "belo horizonte", "brasília", "goiânia"]
_TEMAS = ["inflação", "bitcoin", "fotossíntese", "buraco negro", "democracia", "machine learning", "o big bang", "a revolução francesa", "blockchain", "o efeito estufa"]
_IDIOMAS = ["inglês", "espanhol", "francês", "alemão", "italiano", "japonês"]
_PRODUTOS = ["um iphone 15", "uma geladeira", "um carro popular", "uma passagem pra lisboa", "um notebook gamer", "uma tv 50 polegadas", "um violão", "uma bicicleta"]
_COISAS = ["jogar xadrez", "tocar violão", "falar inglês", "programar em python", "nadar", "cozinhar", "fazer crochê", "desenhar", "meditar", "dirigir"]
_PESSOAS = ["einstein", "pelé", "santos dumont", "machado de assis", "marie curie", "ayrton senna", "dom pedro", "leonardo da vinci"]
_TIMES = ["flamengo", "corinthians", "palmeiras", "são paulo", "grêmio", "vasco", "cruzeiro", "santos"]
_FILMES = ["de comédia", "de terror", "de ação", "pra ver em família", "de ficção científica", "de romance", "de animação"]
FORA_FORMAS = [
    "qual a capital de {a}", "me conta sobre {a}", "como faço {b}", "receita de {b}", "vai chover em {c} amanhã", "previsão do tempo em {c}",
    "me explica {d}", "o que é {d}", "traduz bom dia pra {e}", "como se diz obrigado em {e}", "quanto custa {f}", "onde comprar {f}",
    "como aprender a {g}", "dicas pra {g}", "quem foi {h}", "me fala de {h}", "o {i} ganhou ontem", "quando o {i} joga",
    "me indica um filme {j}", "sugere uma série {j}", "quanto é {n} vezes {n2}", "quanto é {n} mais {n2}", "qual a raiz quadrada de {n}",
    "que horas são em {c}", "qual a distância de {c} até {c2}", "me conta uma piada sobre {i}", "escreve um poema sobre {a}",
    "como chegar em {c}", "qual o melhor celular de {n} reais", "o que fazer em {c} no fim de semana",
]


def _fora_de_escopo(rnd):
    forma = rnd.choice(FORA_FORMAS + FORA_DE_ESCOPO * 2)
    slots = {"{a}": _PAISES, "{b}": _COMIDAS, "{c}": _CIDADES, "{c2}": _CIDADES, "{d}": _TEMAS, "{e}": _IDIOMAS, "{f}": _PRODUTOS,
             "{g}": _COISAS, "{h}": _PESSOAS, "{i}": _TIMES, "{j}": _FILMES}
    for chave, lista in slots.items():
        forma = forma.replace(chave, rnd.choice(lista))
    forma = forma.replace("{n2}", str(rnd.randint(2, 99))).replace("{n}", str(rnd.randint(2, 99)))
    texto = rnd.choice(ABERTURAS) + forma
    if rnd.random() < 0.2:
        texto += rnd.choice([" por favor", " pfv", " rapidinho", " valeu"])
    texto = texto[0].upper() + texto[1:] if rnd.random() < 0.7 else texto
    return texto + rnd.choice(["?", "", "?", " ?"])


CONECTORES = [" e ", ", e ", " e também ", ". Também ", " e quero saber ", ", e ", ". E ", " e me diz ", ", além disso ", " e ainda "]
ABERTURAS = ["", "", "", "oi, ", "me diz: ", "queria saber ", "quero saber ", "por favor, ", "pode me dizer ", "ei, "]

_MESES_ACENTO = contrato.NOMES_DOS_MESES


# ---------------------------------------------------------------- utilidades

def normalizar(texto):
    """Minúsculas, sem acento nem pontuação, espaços simples (checagem de vazamento teste -> treino)."""
    s = "".join(c for c in unicodedata.normalize("NFKD", texto.lower()) if not unicodedata.combining(c))
    s = re.sub(r"[^\w\s]|_", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def _mes_para_tupla(mes):
    ano, numero = mes.split("-")
    return int(ano), int(numero)


def _mes_somar(mes, delta):
    ano, numero = _mes_para_tupla(mes)
    indice = ano * 12 + numero - 1 + delta
    return "%04d-%02d" % (indice // 12, indice % 12 + 1)


def serializar_plano(chamadas):
    return json.dumps({"chamadas": chamadas}, ensure_ascii=False, separators=(",", ":"))


def ler_plano(texto, mes_atual, categorias):
    """Espelho de ContratoLlm.lerPlano: None = JSON inválido; [] = fora do escopo.

    Chamada com ferramenta desconhecida, mês inválido, categoria desconhecida ou categoria obrigatória
    ausente é descartada; no máximo 4 chamadas.
    """
    try:
        chamadas = json.loads(texto)["chamadas"]
    except (ValueError, KeyError, TypeError):
        return None
    if not isinstance(chamadas, list):
        return None
    ferramentas = {f["nome"]: f for f in contrato.carregar()["ferramentas"]}
    saida = []
    for c in chamadas[:contrato.MAX_CHAMADAS]:
        if not isinstance(c, dict) or c.get("f") not in ferramentas:
            continue
        f = ferramentas[c["f"]]
        mes = None
        if f["usa_mes"]:
            texto_mes = c.get("mes", "atual")
            if texto_mes == "atual":
                mes = mes_atual
            elif texto_mes == "anterior":
                mes = _mes_somar(mes_atual, -1)
            elif isinstance(texto_mes, str) and re.fullmatch(r"\d{4}-(0[1-9]|1[0-2])", texto_mes):
                mes = texto_mes
            else:
                continue
        categoria = None
        if f["usa_categoria"]:
            nome = c.get("categoria")
            if nome is not None:
                if nome not in categorias:
                    continue
                categoria = nome
            elif f["categoria_obrigatoria"]:
                continue
        saida.append({"ferramenta": f["nome"], "mes": mes, "categoria": categoria})
    return saida


def aceita_pela_gramatica(texto, mes_atual, categorias):
    """Confere a saída contra as regras da gramática: JSON compacto, chaves na ordem, mês e categoria permitidos."""
    try:
        dados = json.loads(texto)
    except ValueError:
        return False
    chamadas = dados.get("chamadas") if isinstance(dados, dict) and list(dados) == ["chamadas"] else None
    if not isinstance(chamadas, list) or len(chamadas) > contrato.MAX_CHAMADAS:
        return False
    if serializar_plano(chamadas) != texto:
        return False
    ferramentas = {f["nome"]: f for f in contrato.carregar()["ferramentas"]}
    meses = {"atual", "anterior"} | set(contrato._meses_ate(mes_atual))
    for c in chamadas:
        f = ferramentas.get(c.get("f")) if isinstance(c, dict) else None
        if f is None:
            return False
        esperado = ["f"] + (["mes"] if f["usa_mes"] else [])
        if f["usa_categoria"] and ("categoria" in c or f["categoria_obrigatoria"]):
            esperado.append("categoria")
        if list(c) != esperado or ("mes" in c and c["mes"] not in meses):
            return False
        if "categoria" in c and c["categoria"] not in categorias:
            return False
    return True


def sortear_categorias(rnd):
    nomes = PADRAO + rnd.sample(INVENTADAS, 4)
    rnd.shuffle(nomes)
    return nomes[:rnd.randint(3, 8)]


def _escrever_categoria(nome, rnd):
    if nome.isascii() and rnd.random() < 0.25:
        return nome.lower()
    return nome


def _frase_do_mes(mes, mes_atual, rnd):
    """Texto do período e o valor do plano ("atual", "anterior" ou "AAAA-MM")."""
    if mes == "atual":
        return rnd.choice(MESES_ATUAL)
    if mes == "anterior":
        return rnd.choice(MESES_ANTERIOR)
    ano, numero = _mes_para_tupla(mes)
    nome = _MESES_ACENTO[numero - 1]
    return rnd.choice(["em %s" % nome, "em %s de %d" % (nome, ano), "em %s" % nome])


def _sortear_mes(rnd, mes_atual):
    r = rnd.random()
    if r < 0.45:
        return "atual"
    if r < 0.7:
        return "anterior"
    return _mes_somar(mes_atual, -rnd.randint(1, 11))


def _chave_forma(nome, com_categoria):
    if com_categoria and nome in ("maior_gasto", "orcamento_restante", "media_mensal"):
        return nome + "_cat"
    return nome


def _sortear_chamada(rnd, mes_atual, categorias, evitar=()):
    """(chamada do plano, texto da pergunta)."""
    ferramenta = rnd.choice(list(FORMAS_POR_FERRAMENTA))
    usa_categoria = ferramenta in ("gasto_categoria", "maior_gasto", "orcamento_restante", "media_mensal")
    com_categoria = usa_categoria and (ferramenta == "gasto_categoria" or rnd.random() < 0.5)
    chamada = {"f": ferramenta}
    mes = None
    if USA_MES[ferramenta]:
        mes = _sortear_mes(rnd, mes_atual)
        chamada["mes"] = mes
    categoria = None
    if com_categoria:
        categoria = rnd.choice(categorias)
        chamada["categoria"] = categoria
    if chamada in evitar:
        return _sortear_chamada(rnd, mes_atual, categorias, evitar)
    forma = rnd.choice(FORMAS[_chave_forma(ferramenta, com_categoria)])
    texto = forma.replace("{m}", _frase_do_mes(mes, mes_atual, rnd) if mes else "").replace(
        "{c}", _escrever_categoria(categoria, rnd) if categoria else "")
    return chamada, re.sub(r"\s+", " ", texto).strip()


FORMAS_POR_FERRAMENTA = [f["nome"] for f in contrato.carregar()["ferramentas"]]


def _juntar(textos, rnd):
    saida = rnd.choice(ABERTURAS) + textos[0]
    for t in textos[1:]:
        conector = rnd.choice(CONECTORES)
        saida += conector + (t[0].upper() + t[1:] if conector.startswith(".") else t)
    saida = saida[0].upper() + saida[1:]
    return saida + rnd.choice(["?", "?", "?", "", " ?", "??"])


def _acompanhamento(rnd, mes_atual, categorias, anterior):
    """Pergunta de continuação sobre a chamada anterior; None se nada se aplica."""
    f = anterior["f"]
    pode_mes = USA_MES[f]
    pode_cat = f in ("gasto_categoria", "maior_gasto", "orcamento_restante", "media_mensal")
    if not (pode_mes or pode_cat):
        return None
    if pode_cat and (not pode_mes or rnd.random() < 0.4):
        novo = rnd.choice([c for c in categorias if c != anterior.get("categoria")] or categorias)
        chamada = dict(anterior, categoria=novo)
        texto = rnd.choice(["e com {c}", "e de {c}", "e em {c}", "e {c}", "e a {c}", "e sobre {c}", "agora {c}", "e {c} tbm"])
        return chamada, texto.replace("{c}", _escrever_categoria(novo, rnd)) + rnd.choice(["?", "", "?"])
    novo = rnd.choice([m for m in ["atual", "anterior"] + [_mes_somar(mes_atual, -k) for k in range(2, 8)]
                       if m != anterior["mes"]])
    chamada = dict(anterior, mes=novo)
    frase = _frase_do_mes(novo, mes_atual, rnd)
    texto = rnd.choice(["e {m}", "e {m}", "e como foi {m}", "agora {m}", "e {m} tbm", "o mesmo {m}", "e como ficou {m}"]).replace("{m}", frase)
    return chamada, texto + rnd.choice(["?", "", "?"])


def montar_exemplo(i, rnd=None):
    """Exemplo determinístico do id i. Devolve dict com prompt, saida e metadados para paráfrase."""
    rnd = rnd or random.Random(1000 + i)
    ano_min, mes_min = _mes_para_tupla(MES_ATUAL_MIN)
    ano_max, mes_max = _mes_para_tupla(MES_ATUAL_MAX)
    mes_atual = _mes_somar(MES_ATUAL_MIN, rnd.randint(0, (ano_max * 12 + mes_max) - (ano_min * 12 + mes_min)))
    categorias = sortear_categorias(rnd)
    r = rnd.random()
    historico = []
    if r < 0.14:
        pergunta, chamadas = _fora_de_escopo(rnd), []
    elif r < 0.28:
        for _ in range(rnd.choice([1, 1, 2])):
            c, t = _sortear_chamada(rnd, mes_atual, categorias)
            historico.append((_juntar([t], rnd), serializar_plano([c])))
        anterior = json.loads(historico[-1][1])["chamadas"][0]
        achado = _acompanhamento(rnd, mes_atual, categorias, anterior)
        if achado is None:
            return montar_exemplo(i, rnd)
        chamada, pergunta = achado
        pergunta = pergunta[0].upper() + pergunta[1:]
        chamadas = [chamada]
    elif r < 0.66:
        n = rnd.choices([2, 3, 4], [0.6, 0.28, 0.12])[0]
        chamadas, textos = [], []
        for _ in range(n):
            c, t = _sortear_chamada(rnd, mes_atual, categorias, chamadas)
            chamadas.append(c)
            textos.append(t)
        pergunta = _juntar(textos, rnd)
    else:
        c, t = _sortear_chamada(rnd, mes_atual, categorias)
        chamadas, pergunta = [c], _juntar([t], rnd)
    return {
        "id": i, "pergunta": pergunta, "mes_atual": mes_atual, "categorias": categorias, "historico": historico,
        "chamadas": chamadas, "saida": serializar_plano(chamadas),
    }


def prompt_do_exemplo(ex, pergunta=None):
    return contrato.montar_prompt_plano(pergunta or ex["pergunta"], ex["mes_atual"], ex["categorias"], ex["historico"])


# ---------------------------------------------------------------- professor

def completar(prompt, seed, temperatura=0.7, n_predict=256, base=PROFESSOR):
    """Chama o professor (/completion) sem pensar; ChatML cru, como em contrato.py."""
    corpo = {"prompt": prompt + "<think>\n\n</think>\n\n", "seed": seed, "temperature": temperatura,
             "top_p": 0.9, "n_predict": n_predict, "stop": ["<|im_end|>"]}
    ultimo = None
    for tentativa in range(4):
        try:
            r = requests.post(base + "/completion", json=corpo, timeout=300)
            r.raise_for_status()
            return r.json()["content"].strip()
        except (requests.RequestException, KeyError, ValueError) as e:
            ultimo = e
            time.sleep(2 * (tentativa + 1))
    raise RuntimeError("professor indisponível: %s" % ultimo)


SISTEMA_PARAFRASE = (
    "Você reescreve perguntas de um app de finanças pessoais do jeito que uma pessoa digitaria no celular, "
    "em português do Brasil. Mantenha TODOS os pedidos da pergunta, os nomes de categoria exatamente como estão "
    "e os meses citados. Não responda a pergunta. Escreva só a pergunta reescrita, em uma linha."
)


def parafrasear(ex, seed):
    """Pergunta reescrita pelo professor, ou None se perdeu algum pedido, mês ou categoria."""
    prompt = ("<|im_start|>system\n" + SISTEMA_PARAFRASE + "<|im_end|>\n<|im_start|>user\n" + ex["pergunta"]
              + "<|im_end|>\n<|im_start|>assistant\n")
    texto = completar(prompt, seed, 0.8, 120).split("\n")[0].strip().strip('"“”')
    if not texto or len(texto) > 300:
        return None
    norm = normalizar(texto)
    for c in ex["chamadas"]:
        if c.get("categoria") and normalizar(c["categoria"]) not in norm:
            return None
        mes = c.get("mes")
        if mes == "anterior" and not re.search(r"passad|anterior|ultimo", norm):
            return None
        if mes and re.fullmatch(r"\d{4}-\d{2}", mes):
            if normalizar(_MESES_ACENTO[_mes_para_tupla(mes)[1] - 1]) not in norm:
                return None
    if not ex["chamadas"] and len(norm.split()) < 1:
        return None
    return texto


# ---------------------------------------------------------------- execução

def _perguntas_de_teste():
    if not PERGUNTAS_TESTE.exists():
        return set()
    return {normalizar(json.loads(l)["pergunta"]) for l in PERGUNTAS_TESTE.read_text(encoding="utf-8").splitlines() if l.strip()}


def _pergunta_do_prompt(prompt):
    return prompt.rsplit("Pergunta: ", 1)[1].split("<|im_end|>")[0]


def _ultimo_feito(caminho):
    if not caminho.exists():
        return -1
    linhas = caminho.read_text(encoding="utf-8").split()
    return int(linhas[-1]) if linhas else -1


def gerar(limite, saida_dir, usar_professor=True, taxa_parafrase=0.3, workers=4, log=print):
    saida_dir = Path(saida_dir)
    saida_dir.mkdir(parents=True, exist_ok=True)
    treino, valid, feito = (saida_dir / "plano_treino.jsonl", saida_dir / "plano_valid.jsonl", saida_dir / "plano.feito")
    proibidas = _perguntas_de_teste()
    vistas = set()
    for caminho in (treino, valid):
        if caminho.exists():
            for l in caminho.read_text(encoding="utf-8").splitlines():
                vistas.add(normalizar(_pergunta_do_prompt(json.loads(l)["prompt"])))
    inicio = _ultimo_feito(feito) + 1
    parafraseadas = descartadas = 0
    t0 = time.time()
    with open(treino, "a", encoding="utf-8") as ft, open(valid, "a", encoding="utf-8") as fv, \
            open(feito, "a", encoding="utf-8") as ff, ThreadPoolExecutor(workers) as pool:
        for bloco in range(inicio, limite, 32):
            ids = range(bloco, min(bloco + 32, limite))
            exemplos = [montar_exemplo(i) for i in ids]
            novas = {}
            if usar_professor:
                alvos = [e for e in exemplos if not e["historico"] and random.Random(7000 + e["id"]).random() < taxa_parafrase]
                for e, nova in zip(alvos, pool.map(lambda e: parafrasear(e, e["id"]), alvos)):
                    novas[e["id"]] = nova
            for e in exemplos:
                anterior = "|" + normalizar(e["historico"][-1][0]) if e["historico"] else ""
                nova = novas.get(e["id"])
                pergunta = e["pergunta"]
                if nova and normalizar(nova) not in proibidas and normalizar(nova) + anterior not in vistas:
                    pergunta = nova
                    parafraseadas += 1
                chave = normalizar(pergunta) + anterior
                tentativa = 0
                while normalizar(pergunta) in proibidas or chave in vistas:
                    # repetida: sorteia outra variação do mesmo id (determinístico)
                    tentativa += 1
                    descartadas += 1
                    if tentativa > 50:
                        raise RuntimeError("não há variações novas para o id %d" % e["id"])
                    e = montar_exemplo(e["id"], random.Random(1000 + e["id"] + tentativa * 100003))
                    pergunta = e["pergunta"]
                    anterior = "|" + normalizar(e["historico"][-1][0]) if e["historico"] else ""
                    chave = normalizar(pergunta) + anterior
                vistas.add(chave)
                linha = json.dumps({"prompt": prompt_do_exemplo(e, pergunta), "saida": e["saida"]}, ensure_ascii=False)
                (fv if e["id"] % VALID_A_CADA == 0 else ft).write(linha + "\n")
            ft.flush(), fv.flush()
            ff.write("%d\n" % (ids[-1])), ff.flush()
            log("[planos] id %d/%d  parafraseadas %d  descartadas %d  %.0fs" % (ids[-1] + 1, limite, parafraseadas, descartadas, time.time() - t0))
    return {"parafraseadas": parafraseadas, "descartadas": descartadas}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--limite", type=int, default=TOTAL, help="quantos ids gerar (padrão 13000)")
    ap.add_argument("--saida-dir", default=str(SAIDA_PADRAO))
    ap.add_argument("--sem-professor", action="store_true")
    a = ap.parse_args(argv)
    sys.stdout.reconfigure(encoding="utf-8")
    print(gerar(a.limite, a.saida_dir, not a.sem_professor, log=lambda s: print(s, flush=True)))


if __name__ == "__main__":
    main()
