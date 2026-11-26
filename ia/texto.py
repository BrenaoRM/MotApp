"""Normalização da descrição antes de passar pelo modelo de embeddings.

Diferente do `finai.text_utils.clean_text`, aqui os acentos ficam: o tokenizador do modelo usa.
"""
import re
import unicodedata

from finai.text_utils import CITIES

BOILERPLATE = {
    "compra", "cartao", "cartão", "debito", "débito", "credito", "crédito", "pag", "pgto", "pix",
    "enviado", "recebido", "boleto", "automatico", "automático",
}


def _sem_acento(s: str) -> str:
    return unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()


# Cidades como sequência de palavras sem acento ("sao paulo" também pega "são paulo").
_CIDADES = sorted((tuple(c.split()) for c in CITIES), key=len, reverse=True)


def normalizar_para_modelo(desc: str) -> str:
    s = unicodedata.normalize("NFKC", str(desc)).lower()
    s = re.sub(r"[\d\W_]", " ", s)  # dígitos e pontuação viram espaço; letras acentuadas ficam
    palavras = s.split()
    base = [_sem_acento(p) for p in palavras]
    manter = [True] * len(palavras)
    for cidade in _CIDADES:
        n = len(cidade)
        for i in range(len(palavras) - n + 1):
            if tuple(base[i:i + n]) == cidade:
                manter[i:i + n] = [False] * n
    return " ".join(p for p, m in zip(palavras, manter) if m and p not in BOILERPLATE)
