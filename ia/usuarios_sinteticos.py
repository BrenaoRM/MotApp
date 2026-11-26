"""Usuários sintéticos com categorias personalizadas (Tarefa 2, passo 1).

Parte dos 50 usuários do FinIA (`data_gen.generate(n_per_arch=10, seed=42)`, só gastos) e dá a cada um um
dos 4 esquemas de categorias: (a) as 9 genéricas, (b) renomeadas, (c) agrupadas, (d) renomeadas + "Pet" e
"Presentes" (com gastos novos inseridos).
"""
from dataclasses import dataclass

import numpy as np
import pandas as pd

from exportar_modelo import gastos_finia

GENERICAS = ["Alimentação", "Restaurantes e Delivery", "Transporte", "Moradia", "Saúde",
             "Lazer e Assinaturas", "Educação", "Compras", "Outros"]

RENOMEADAS = {"Alimentação": "Mercado", "Restaurantes e Delivery": "Comida fora", "Transporte": "Carro",
              "Moradia": "Casa", "Saúde": "Farmácia", "Lazer e Assinaturas": "Diversão", "Educação": "Estudos",
              "Compras": "Compras", "Outros": "Outros"}

AGRUPADAS = {"Alimentação": "Essenciais", "Moradia": "Essenciais", "Saúde": "Essenciais",
             "Restaurantes e Delivery": "Rolê", "Lazer e Assinaturas": "Rolê", "Transporte": "Transporte",
             "Educação": "Outros", "Compras": "Outros", "Outros": "Outros"}

# Estabelecimentos "Pet": os inseridos no esquema (d) + o pet shop que o FinIA põe em "Compras".
# Nenhum deles entra no treino do prior genérico nem no léxico.
PET_INSERIDOS = [("PETZ", 120), ("COBASI", 95), ("CLINICA VETERINARIA", 180), ("AGROPET", 70)]
PET_FINIA = "PET SHOP AMIGO FIEL"
MERCHANTS_PET = {m for m, _ in PET_INSERIDOS} | {PET_FINIA}
PRESENTES_INSERIDOS = [("FLORICULTURA BELA FLOR", 90), ("CACAU SHOW", 60), ("PRESENTES E CIA", 110),
                       ("BRINQUEDOS ALEGRIA", 130)]
MERCHANTS_PRESENTES = {m for m, _ in PRESENTES_INSERIDOS}
PET_POR_MES, PRESENTES_POR_MES = 3, 2

ESQUEMAS = "abcd"


@dataclass
class UsuarioSintetico:
    id: str
    esquema: str
    transacoes: pd.DataFrame  # descricao, categoria_usuario, categoria_generica, merchant, data, valor
    categorias: list


def _categorias(esquema):
    if esquema == "a":
        return list(GENERICAS)
    if esquema == "c":
        return ["Essenciais", "Rolê", "Transporte", "Outros"]
    base = list(dict.fromkeys(RENOMEADAS.values()))
    return base + ["Pet", "Presentes"] if esquema == "d" else base


def _inseridos(rng, g, merchants, por_mes, categoria):
    from data_gen import _noise_desc
    linhas = []
    for periodo in sorted({d.to_period("M") for d in g.data}):
        for _ in range(por_mes):
            merch, med = merchants[rng.integers(len(merchants))]
            dia = int(rng.integers(1, periodo.days_in_month + 1))
            linhas.append(dict(descricao=_noise_desc(rng, merch, "card"), categoria_usuario=categoria,
                               categoria_generica="Compras", merchant=merch,
                               data=pd.Timestamp(periodo.year, periodo.month, dia),
                               valor=round(float(med * rng.lognormal(0, 0.35)), 2)))
    return linhas


def gerar_usuarios(seed=42):
    rng = np.random.default_rng(seed)
    df = gastos_finia()
    ids = sorted(df.user_id.unique())
    esquemas = rng.permutation(np.resize(list(ESQUEMAS), len(ids)))  # 13/13/12/12, sorteado
    usuarios = []
    for uid, esq in zip(ids, esquemas):
        g = df[df.user_id == uid]
        t = pd.DataFrame(dict(descricao=g.description.to_numpy(), categoria_generica=g.category.to_numpy(),
                              merchant=g.merchant.to_numpy(), data=g.date.to_numpy(),
                              valor=(-g.amount).to_numpy()))
        if esq == "a":
            t["categoria_usuario"] = t.categoria_generica
        elif esq == "c":
            t["categoria_usuario"] = t.categoria_generica.map(AGRUPADAS)
        else:
            t["categoria_usuario"] = t.categoria_generica.map(RENOMEADAS)
        if esq == "d":
            t.loc[t.merchant == PET_FINIA, "categoria_usuario"] = "Pet"
            extra = (_inseridos(rng, t, PET_INSERIDOS, PET_POR_MES, "Pet")
                     + _inseridos(rng, t, PRESENTES_INSERIDOS, PRESENTES_POR_MES, "Presentes"))
            t = pd.concat([t, pd.DataFrame(extra)], ignore_index=True)
        t = t.sort_values("data", kind="stable").reset_index(drop=True)
        t = t[["descricao", "categoria_usuario", "categoria_generica", "merchant", "data", "valor"]]
        usuarios.append(UsuarioSintetico(uid, str(esq), t, _categorias(esq)))
    return usuarios


def dividir_validacao_teste(usuarios, seed=42):
    """35 de validação e 15 de teste, estratificado por esquema (4/4/4/3 no teste)."""
    rng = np.random.default_rng(seed + 1)
    teste = set()
    for e, cota in zip(ESQUEMAS, [4, 4, 4, 3]):
        teste |= set(rng.choice([u.id for u in usuarios if u.esquema == e], size=cota, replace=False))
    return [u for u in usuarios if u.id not in teste], [u for u in usuarios if u.id in teste]
