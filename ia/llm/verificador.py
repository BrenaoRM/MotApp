"""Verificador de frases da IA avançada: todo número da resposta precisa vir dos fatos.

Mesma semântica (e mesmos passos) de app/src/main/java/com/example/financacelular/ia/llm/Verificador.kt;
os dois testes leem app/src/test/resources/ia/llm/casos_verificador.jsonl. Um fato é o dict de
Fato.paraJson(): {"periodo", "frase", "itens": [{"rotulo", "tipo", "valor"}]}.
"""
import re
import unicodedata

_FIM_DE_FRASE = re.compile(r"(?<=[.!?])[ \t\r\n]+|[\r\n]+")
_ESPACOS = re.compile(r"[ \t\r\n\x0b\x0c]+")
# "R$ 1.200,00" = reais, "65%" = porcento, outro número = numero. O ponto só vale como milhar (".200").
_NUMERO = re.compile(r"(r\$ ?)?([0-9]+(?:\.[0-9]{3})*(?:,[0-9]+)?)( ?%)?")


def frases(texto):
    """Divide em frases sem quebrar "R$ 1.200": só corta em .!? seguido de espaço, ou em quebra de linha."""
    return [f.strip(" \t\r\n") for f in _FIM_DE_FRASE.split(texto) if f.strip(" \t\r\n")]


def verificar_frase(frase, fatos, periodo_vigente):
    """Devolve (aprovada, novo período vigente)."""
    periodo, casamentos = _casamentos(frase, fatos, periodo_vigente)
    return all(casamentos), periodo


def fatos_citados(frases_aprovadas, fatos):
    """Índices (base 0) dos fatos com algum item citado nas frases, seguindo o período vigente."""
    periodo, citados = None, set()
    for frase in frases_aprovadas:
        periodo, casamentos = _casamentos(frase, fatos, periodo)
        for indices in casamentos:
            citados |= indices
    return citados


def _casamentos(frase, fatos, periodo_vigente):
    """Novo período vigente e, para cada número da frase, os índices dos fatos com item que casa."""
    texto = _normalizar(frase)
    periodo = _periodo(texto, fatos, periodo_vigente)
    casamentos = []
    for m in _NUMERO.finditer(_mascarar(texto, fatos)):
        tipo = "reais" if m.group(1) else "porcento" if m.group(3) else "numero"
        valor = _canonico(m.group(2))
        casamentos.append({
            n for n, f in enumerate(fatos)
            if (f["periodo"] is None or f["periodo"] == periodo) and any(
                i["tipo"] == tipo and _canonico(i["valor"]) == valor
                and (i["rotulo"] is None or _posicao(_normalizar(i["rotulo"]), texto) >= 0)
                for i in f["itens"])
        })
    return periodo, casamentos


def _periodo(texto, fatos, anterior):
    """Regra 1: o primeiro período dos fatos citado na frase; empate na posição fica com o mais longo."""
    melhor, posicao = anterior, -1
    for p in dict.fromkeys(f["periodo"] for f in fatos if f["periodo"] is not None):
        i = _posicao(_normalizar(p), texto)
        if i >= 0 and (posicao < 0 or i < posicao
                       or (i == posicao and len(_normalizar(p)) > len(_normalizar(melhor)))):
            melhor, posicao = p, i
    return melhor


def _mascarar(texto, fatos):
    """Regra 2: apaga da frase os períodos ("outubro de 2025") e os rótulos com letra ("Carro 2") que têm dígito.

    Rótulo só de dígitos ("99") não é apagado: os números dele são conferidos como qualquer outro.
    """
    rotulos = {_normalizar(f["periodo"]) for f in fatos if f["periodo"] is not None}
    rotulos |= {r for r in (_normalizar(i["rotulo"]) for f in fatos for i in f["itens"] if i["rotulo"] is not None)
                if any(c.isalpha() for c in r)}
    for r in sorted((r for r in rotulos if any(c.isdigit() for c in r)), key=lambda r: (-len(r), r)):
        i = _posicao(r, texto)
        while i >= 0:
            texto = texto[:i] + " " * len(r) + texto[i + len(r):]
            i = _posicao(r, texto)
    return texto


def _posicao(rotulo, texto):
    """Primeira ocorrência de rotulo em texto como palavra inteira, ou -1."""
    if not rotulo:
        return -1
    i = texto.find(rotulo)
    while i >= 0:
        fim = i + len(rotulo)
        if (i == 0 or not texto[i - 1].isalnum()) and (fim == len(texto) or not texto[fim].isalnum()):
            return i
        i = texto.find(rotulo, i + 1)
    return -1


def _normalizar(s):
    """Minúsculas, só ASCII (sem acento e sem emoji), espaços simples."""
    s = "".join(c for c in unicodedata.normalize("NFKD", s.lower()) if ord(c) < 128)
    return _ESPACOS.sub(" ", s).strip(" ")


def _canonico(valor):
    """"1.200,00" e "1200" viram "1200"; centavos que não são zero ficam ("1200,50")."""
    inteiro, _, fracao = valor.replace(".", "").partition(",")
    return inteiro if fracao.strip("0") == "" else inteiro + "," + fracao
