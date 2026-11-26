"""Gerador de extratos bancários SINTÉTICOS (pt-BR) com rótulos de verdade-terreno.

Gera 5 arquétipos de usuário x N usuários, 6 meses de movimentações, com:
  - descrições ruidosas no estilo de extrato bancário;
  - categoria verdadeira (para treinar/avaliar o classificador);
  - anomalias injetadas e rotuladas (para avaliar o detector);
  - 'problemas plantados' por arquétipo (para checar o recomendador).

ATENÇÃO: dados sintéticos => validação de sanidade, NÃO prova de desempenho no mundo real.
"""
import re
import unicodedata
import numpy as np
import pandas as pd
from pathlib import Path

CATEGORIAS = ["Alimentação", "Restaurantes e Delivery", "Transporte", "Moradia", "Saúde",
              "Lazer e Assinaturas", "Educação", "Compras", "Outros"]

MERCHANTS = {
    "Alimentação": [("SUPERMERCADO BOM PRECO", 180), ("ATACADO ECONOMICO", 260), ("HORTIFRUTI VERDE VIDA", 45),
                    ("PADARIA PAO QUENTE", 22), ("ACOUGUE BOI DE OURO", 85), ("MERCEARIA DO ZE", 30),
                    ("SUPERMERCADO MAIS BARATO", 150), ("EMPORIO NATURAL", 60), ("MERCADO CENTRAL", 110),
                    ("SACOLAO DA ESQUINA", 35), ("PADARIA DOCE VIDA", 18), ("ATACAREJO SUPER", 300)],
    "Restaurantes e Delivery": [("IFOOD", 48), ("RESTAURANTE SABOR CASEIRO", 38), ("LANCHONETE DO BAIRRO", 26),
                                ("PIZZARIA NAPOLI", 65), ("HAMBURGUERIA BIG JOE", 55), ("SORVETERIA GELATO", 20),
                                ("CAFE E CIA", 24), ("RAPPI", 52), ("SUSHI TOKYO", 90), ("CHURRASCARIA GAUCHA", 110),
                                ("ACAI DA PRACA", 22), ("MARMITARIA FIT", 28)],
    "Transporte": [("UBER TRIP", 22), ("99 APP", 19), ("POSTO SAO JORGE", 190), ("AUTO POSTO CENTRAL", 160),
                   ("ESTACIONAMENTO PARK", 15), ("PEDAGIO AUTOPASS", 12), ("BILHETE UNICO RECARGA", 50),
                   ("BORRACHARIA DO NEGO", 70), ("OFICINA MECANICA", 320), ("COMBUSTIVEIS BRASIL", 170),
                   ("VIACAO EXPRESSO", 85)],
    "Moradia": [("GAS BUTANO REVENDA", 110), ("UTILIDADES DOMESTICAS", 60), ("ELETRICISTA SERVICOS", 150),
                ("MATERIAL CONSTRUCAO", 130), ("CHAVEIRO 24H", 80)],
    "Saúde": [("DROGARIA POPULAR", 55), ("FARMACIA VIDA", 70), ("CLINICA MEDICA", 220), ("LABORATORIO DIAGNOSTICOS", 140),
              ("DENTISTA SORRISO", 250), ("OTICA VISAO", 300)],
    "Lazer e Assinaturas": [("CINEMA CINEPLEX", 45), ("STEAM GAMES", 80), ("BAR DO CHICO", 90), ("INGRESSO SHOW", 180),
                            ("CLUBE ASSOCIACAO", 70), ("BOLICHE ARENA", 60), ("PARQUE DIVERSOES", 120)],
    "Educação": [("CURSO IDIOMAS INGLES", 250), ("LIVRARIA PAGINA UM", 70), ("UDEMY CURSO ONLINE", 40),
                 ("PAPELARIA ESCOLAR", 35), ("XEROX COPIAS", 12), ("ALURA CURSOS", 60)],
    "Compras": [("LOJA DE ROUPAS MODA", 140), ("ELETRO CASA", 220), ("MERCADOLIVRE", 95), ("SHOPEE", 45),
                ("AMAZON BR", 110), ("LOJA CALCADOS PASSO", 160), ("PET SHOP AMIGO FIEL", 90), ("ALIEXPRESS", 50)],
    "Outros": [("TARIFA BANCARIA", 15), ("SAQUE CAIXA ELETRONICO", 100), ("IOF", 8), ("TED TRANSFERENCIA", 150)],
}
BIG_NEW = [("JOALHERIA OURO FINO", 900), ("ELETRONICOS TECH STORE", 1200), ("MOVEIS PLANEJADOS", 1500)]
SUBS_POOL = [("NETFLIX", 39.9), ("SPOTIFY", 21.9), ("AMAZON PRIME", 19.9), ("DISNEY PLUS", 33.9), ("HBO MAX", 34.9),
             ("YOUTUBE PREMIUM", 24.9), ("SMART FIT ACADEMIA", 119.9), ("GLOBOPLAY", 24.9), ("DEEZER", 19.9),
             ("CRUNCHYROLL", 25.0), ("APPLE ICLOUD", 14.9), ("XBOX GAME PASS", 44.9)]
FIRST = ["JOAO", "MARIA", "PEDRO", "ANA", "LUCAS", "JULIA", "CARLOS", "FERNANDA", "RAFAEL", "BEATRIZ", "GABRIEL",
         "LARISSA", "THIAGO", "CAMILA", "BRUNO", "AMANDA", "FELIPE", "LETICIA", "DANIEL", "PATRICIA"]
LAST = ["SILVA", "SOUZA", "OLIVEIRA", "SANTOS", "PEREIRA", "LIMA", "COSTA", "FERREIRA", "RODRIGUES", "ALMEIDA",
        "NASCIMENTO", "CARVALHO", "GOMES", "MARTINS", "ARAUJO"]
PIX_PESSOA_ALT = ["Restaurantes e Delivery", "Alimentação", "Lazer e Assinaturas", "Transporte", "Compras"]

BASE_RATES = {"Alimentação": 6, "Restaurantes e Delivery": 4, "Transporte": 8, "Moradia": 0.7, "Saúde": 1,
              "Lazer e Assinaturas": 1.5, "Educação": 0.6, "Compras": 2, "Outros": 3}

ARCH = {
    "estudante": dict(income=3000, rent=600, amt=0.34, subs=2, rate_mult={"Restaurantes e Delivery": 1.5}, growth={"Restaurantes e Delivery": 0.40},
                      planted=["delivery_alto_crescente"]),
    "assalariado_assinaturas": dict(income=5200, rent=1300, amt=1.0, subs=8, target=0.80, planted=["assinaturas_excesso"]),
    "familia_apertada": dict(income=5000, rent=1500, amt=1.3, subs=3, target=0.93, planted=["poupanca_baixa"]),
    "autonomo": dict(income=5500, rent=1200, amt=1.1, subs=3, target=0.60, anom=0.09, var_income=True,
                     planted=["gastos_atipicos"]),
    "equilibrado": dict(income=6000, rent=1400, amt=1.0, subs=3, target=0.72, planted=[]),
}
MONTHS = [(2026, m) for m in range(5, 11)]


def _noise_desc(rng, merchant, kind="card"):
    prefixes = {"card": ["COMPRA CARTAO", "COMPRA DEBITO", "COMPRA CREDITO", "PAG*", "PGTO", ""],
                "auto": ["DEBITO AUTOMATICO", "PAG*", "COMPRA CREDITO", "PGTO BOLETO"],
                "pix": ["PIX ENVIADO"]}[kind]
    words = merchant.split()
    if rng.random() < 0.15 and len(words) > 0:  # typo: dropa um caractere de uma palavra
        i = rng.integers(len(words))
        w = words[i]
        if len(w) > 4:
            j = rng.integers(1, len(w) - 1)
            words[i] = w[:j] + w[j + 1:]
    s = " ".join(words)
    pre = prefixes[rng.integers(len(prefixes))]
    s = f"{pre} {s}".strip()
    if rng.random() < 0.25:
        s += " " + str(rng.integers(10 ** 3, 10 ** 8))
    if rng.random() < 0.30:
        s += " " + ["RIO PARANAIBA", "PATOS DE MINAS", "SAO GOTARDO", "BELO HORIZONTE", "VICOSA", "SAO PAULO"][rng.integers(6)]
    if rng.random() < 0.12:
        s = s[: max(14, int(len(s) * rng.uniform(0.7, 0.95)))]
    return s


def _month_days(y, m):
    return pd.Period(f"{y}-{m:02d}").days_in_month


def gen_user(rng, uid, arch_name):
    cfg = ARCH[arch_name]
    amt_user = cfg["amt"] * rng.lognormal(0, 0.12)
    income_user = cfg["income"] * rng.lognormal(0, 0.10)
    prefs = {c: [MERCHANTS[c][i] for i in rng.choice(len(MERCHANTS[c]), size=min(len(MERCHANTS[c]), rng.integers(4, 7)), replace=False)]
             for c in MERCHANTS}
    subs = [SUBS_POOL[i] for i in rng.choice(len(SUBS_POOL), size=cfg["subs"], replace=False)]
    sub_day = {s[0]: int(rng.integers(1, 28)) for s in subs}
    has_condo = rng.random() < 0.5
    rows = []

    def add(date, desc, amount, cat, merch, anom=0, atype="", excess=0.0):
        rows.append(dict(user_id=uid, archetype=arch_name, date=pd.Timestamp(date), description=desc,
                         amount=round(float(amount), 2), category=cat, merchant=merch,
                         is_anomaly=anom, anomaly_type=atype, excess=round(float(excess), 2)))

    for mi, (y, m) in enumerate(MONTHS):
        nd = _month_days(y, m)
        # receitas
        if cfg.get("var_income"):
            total = income_user * rng.lognormal(0, 0.25)
            k = int(rng.integers(2, 5))
            parts = rng.dirichlet(np.ones(k)) * total
            for p in parts:
                add(f"{y}-{m:02d}-{int(rng.integers(1, nd + 1)):02d}",
                    f"PIX RECEBIDO {FIRST[rng.integers(len(FIRST))]} {LAST[rng.integers(len(LAST))]}", p, "Receita", "RECEITA")
            month_income = total
        else:
            month_income = income_user
            add(f"{y}-{m:02d}-05", "CREDITO SALARIO EMPRESA" if arch_name != "estudante" else "CREDITO BOLSA ESTAGIO",
                month_income, "Receita", "RECEITA")
        # despesas fixas
        fixed = []
        fixed.append((f"{y}-{m:02d}-10", _noise_desc(rng, "ALUGUEL IMOBILIARIA", "auto"), cfg["rent"], "Moradia", "ALUGUEL"))
        if has_condo:
            fixed.append((f"{y}-{m:02d}-12", _noise_desc(rng, "CONDOMINIO RESIDENCIAL", "auto"), 280, "Moradia", "CONDOMINIO"))
        fixed.append((f"{y}-{m:02d}-15", _noise_desc(rng, "ENERGIA CEMIG", "auto"), 140 * rng.lognormal(0, 0.15), "Moradia", "ENERGIA"))
        fixed.append((f"{y}-{m:02d}-16", _noise_desc(rng, "AGUA COPASA", "auto"), 70 * rng.lognormal(0, 0.10), "Moradia", "AGUA"))
        fixed.append((f"{y}-{m:02d}-20", _noise_desc(rng, "INTERNET VIVO FIBRA", "auto"), 99.9, "Moradia", "INTERNET"))
        for s, p in subs:
            fixed.append((f"{y}-{m:02d}-{min(sub_day[s], nd):02d}", _noise_desc(rng, s, "auto"), p, "Lazer e Assinaturas", s))
        # despesas variáveis
        var = []
        for c, base in BASE_RATES.items():
            rate = base * cfg.get("rate_mult", {}).get(c, 1) * (1 + cfg.get("growth", {}).get(c, 0) * mi)
            for _ in range(rng.poisson(rate)):
                if c == "Outros" and rng.random() < 0.6:
                    name = f"{FIRST[rng.integers(len(FIRST))]} {LAST[rng.integers(len(LAST))]}"
                    cat = "Outros" if rng.random() < 0.65 else PIX_PESSOA_ALT[rng.integers(len(PIX_PESSOA_ALT))]
                    desc, med, merch = _noise_desc(rng, name, "pix"), 80, "PESSOA_" + name
                else:
                    merch, med = prefs[c][rng.integers(len(prefs[c]))]
                    cat, desc = c, _noise_desc(rng, merch, "card")
                amount = med * amt_user * rng.lognormal(0, 0.35)
                var.append([f"{y}-{m:02d}-{int(rng.integers(1, nd + 1)):02d}", desc, amount, cat, merch])
        if "target" in cfg:
            fixed_sum = sum(f[2] for f in fixed)
            var_sum = sum(v[2] for v in var)
            tgt = cfg["target"] * rng.uniform(0.97, 1.02) * month_income
            factor = max(0.3, (tgt - fixed_sum) / max(var_sum, 1))
            for v in var:
                v[2] *= factor
        for f in fixed:
            add(*f)
        arate = cfg.get("anom", 0.03)
        for v in var:
            date, desc, amount, cat, merch = v
            if rng.random() < arate:
                t = rng.choice(["spike", "duplicado", "novo_estabelecimento"], p=[0.45, 0.30, 0.25])
                if t == "spike":
                    k = rng.uniform(4, 10)
                    add(date, desc, amount * k, cat, merch, 1, t, excess=amount * (k - 1))
                    continue
                if t == "duplicado":
                    add(date, desc, amount, cat, merch)
                    add(pd.Timestamp(date) + pd.Timedelta(days=int(rng.integers(0, 2))), desc, amount, cat, merch, 1, t, excess=amount)
                    continue
                nm, med = BIG_NEW[rng.integers(len(BIG_NEW))]
                val = med * rng.uniform(0.7, 1.3)
                add(date, _noise_desc(rng, nm, "card"), val, "Compras", nm, 1, t, excess=max(val - 100, 0))
                continue
            add(date, desc, amount, cat, merch)

    df = pd.DataFrame(rows)
    df.loc[df.category != "Receita", "amount"] *= -1
    return df


def generate(n_per_arch=4, seed=42):
    rng = np.random.default_rng(seed)
    dfs, uid = [], 0
    for a in ARCH:
        for _ in range(n_per_arch):
            dfs.append(gen_user(rng, f"u{uid:02d}", a))
            uid += 1
    df = pd.concat(dfs, ignore_index=True).sort_values(["user_id", "date"]).reset_index(drop=True)
    return df


if __name__ == "__main__":
    out = Path(__file__).parent / "data"
    out.mkdir(exist_ok=True)
    df = generate()
    df.to_csv(out / "extratos_sinteticos.csv", index=False)
    # amostra no formato que o usuário real enviaria (apenas 3 colunas)
    u = df[df.user_id == "u00"][["date", "description", "amount"]]
    u.to_csv(out / "exemplo_extrato.csv", index=False)
    print(df.shape, df.user_id.nunique(), "usuários;", int(df.is_anomaly.sum()), "anomalias")
    print(df.groupby("archetype").apply(lambda g: pd.Series({
        "taxa_poupanca": 1 + g[g.amount < 0].amount.sum() / g[g.amount > 0].amount.sum()})).round(3))
