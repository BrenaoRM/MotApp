import re
import unicodedata

BOILERPLATE = {"compra", "cartao", "debito", "credito", "pag", "pgto", "pix", "enviado", "recebido", "boleto",
               "automatico", "de", "do", "da"}
CITIES = {"rio paranaiba", "patos de minas", "sao gotardo", "belo horizonte", "vicosa", "sao paulo"}


def normalize(s: str) -> str:
    """minúsculas, sem acentos, sem dígitos/pontuação, espaços colapsados."""
    s = unicodedata.normalize("NFKD", str(s)).encode("ascii", "ignore").decode().lower()
    s = re.sub(r"[^a-z\s]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def merchant_key(desc: str) -> str:
    """Chave aproximada do estabelecimento: remove prefixos bancários, cidade e códigos."""
    s = normalize(desc)
    for c in CITIES:
        s = s.replace(c, " ")
    toks = [t for t in s.split() if t not in BOILERPLATE]
    return " ".join(toks[:3])


def clean_text(s: str) -> str:
    """normalize + remove prefixos bancários e cidades (ruído que não identifica a categoria)."""
    t = normalize(s)
    for c in CITIES:
        t = t.replace(c, " ")
    return " ".join(w for w in t.split() if w not in BOILERPLATE)
