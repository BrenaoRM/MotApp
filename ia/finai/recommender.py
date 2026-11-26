"""Módulo de recomendação: transforma saídas dos modelos (categorias, anomalias) + regras em ações.

Os limiares abaixo são HEURÍSTICAS configuráveis (não normas financeiras). A economia estimada é uma
ordem de grandeza, não uma promessa.
"""
import numpy as np
import pandas as pd
from scipy import stats
from text_utils import merchant_key

DISCRETIONARY_LIMITS = {"Restaurantes e Delivery": 0.12, "Lazer e Assinaturas": 0.10, "Compras": 0.10}
MIN_SAVINGS_RATE = 0.10
SUBS_COUNT_ALERT = 6
ANOM_IMPACT_ALERT = 0.04  # impacto mensal das anomalias materiais / despesa mensal média
ANOM_MIN_AMOUNT = 30.0


def _fmt(v):
    return f"R$ {v:,.0f}".replace(",", ".")


def complete_months(df):
    """Meses com cobertura suficiente (descarta último mês se parece parcial)."""
    per = df["date"].dt.to_period("M")
    months = sorted(per.unique())
    if len(months) > 1:
        last = months[-1]
        if df.loc[per == last, "date"].max().day < 0.8 * last.days_in_month:
            months = months[:-1]
    return months


def detect_subscriptions(exp: pd.DataFrame, cat_col="category_pred"):
    d = exp[exp[cat_col] == "Lazer e Assinaturas"].copy()
    d["key"] = d["description"].map(merchant_key)
    d["month"] = d["date"].dt.to_period("M")
    out = []
    for k, g in d.groupby("key"):
        nm = g["month"].nunique()
        amts = g["amount"].abs()
        if nm >= 3 and len(g) / nm <= 1.3 and amts.std(ddof=0) / max(amts.mean(), 1e-9) < 0.10 and amts.median() <= 200:
            out.append(dict(servico=k, valor_mensal=round(float(amts.median()), 2), meses=int(nm)))
    return pd.DataFrame(out, columns=["servico", "valor_mensal", "meses"]).sort_values("valor_mensal", ascending=False)


def recommend(df: pd.DataFrame, cat_col="category_pred"):
    """df: transações de UM usuário já com cat_col, anom_flag, dup, z_cat, z_key, ref_amount nas despesas."""
    d = df.copy()
    months = complete_months(d)
    d = d[d["date"].dt.to_period("M").isin(months)]
    exp = d[d.amount < 0].copy()
    exp["m"] = exp["date"].dt.to_period("M")
    inc = d[d.amount > 0]
    nm = max(len(months), 1)
    mean_exp = exp.amount.abs().sum() / nm
    mean_inc = inc.amount.sum() / nm
    recs = []

    # 1) taxa de poupança
    if mean_inc > 0:
        rate = 1 - mean_exp / mean_inc
        if rate < MIN_SAVINGS_RATE:
            gap = MIN_SAVINGS_RATE * mean_inc - (mean_inc - mean_exp)
            recs.append(dict(code="poupanca_baixa", category="Geral", severity="alta",
                             title="Taxa de poupança abaixo de 10%",
                             detail=f"Em média você guarda {rate*100:.0f}% da renda ({_fmt(mean_inc - mean_exp)}/mês). "
                                    f"Atingir 10% exigiria reduzir cerca de {_fmt(gap)}/mês.",
                             monthly_saving=float(max(gap, 0))))

    # 2) excesso e 3) tendência por categoria discricionária
    # excesso/tendência usam gasto "habitual": anomalias sinalizadas são tratadas à parte (recomendação própria)
    exp_norm = exp[exp["anom_flag"] != 1] if "anom_flag" in exp else exp
    monthly = exp_norm.pivot_table(index="m", columns=cat_col, values="amount", aggfunc=lambda x: x.abs().sum(), fill_value=0)
    monthly = monthly.reindex(months, fill_value=0)
    for c, lim in DISCRETIONARY_LIMITS.items():
        if c not in monthly or mean_inc <= 0:
            continue
        ser = monthly[c].values.astype(float)
        share = ser.mean() / mean_inc
        if share > lim:
            recs.append(dict(code="excesso_categoria", category=c, severity="média",
                             title=f"{c}: {share*100:.0f}% da renda",
                             detail=f"Gasto médio de {_fmt(ser.mean())}/mês, acima da referência heurística de {lim*100:.0f}% da renda.",
                             monthly_saving=float((share - lim) * mean_inc)))
        if len(ser) >= 5:
            x = np.arange(len(ser))
            lr = stats.linregress(x, ser)
            half = len(ser) // 2
            first, last = ser[:half].mean(), ser[-half:].mean()
            if lr.slope > 0 and lr.pvalue < 0.10 and last > 1.25 * max(first, 1) and (last - first) > 0.015 * mean_inc:
                recs.append(dict(code="tendencia_alta", category=c, severity="média",
                                 title=f"{c}: gasto em alta",
                                 detail=f"Média dos últimos meses ({_fmt(last)}) vs. primeiros ({_fmt(first)}): "
                                        f"+{(last/max(first,1)-1)*100:.0f}% com tendência crescente.",
                                 monthly_saving=float(max(last - first, 0))))

    # 4) assinaturas
    subs = detect_subscriptions(exp, cat_col)
    if len(subs) >= SUBS_COUNT_ALERT:
        total = subs.valor_mensal.sum()
        recs.append(dict(code="assinaturas_excesso", category="Lazer e Assinaturas", severity="média",
                         title=f"{len(subs)} assinaturas recorrentes ({_fmt(total)}/mês)",
                         detail="Revise quais ainda são usadas. Estimativa: cancelar 2 delas economiza "
                                f"cerca de {_fmt(2*subs.valor_mensal.median())}/mês.",
                         monthly_saving=float(2 * subs.valor_mensal.median())))

    # 5) gastos atípicos materiais
    if "anom_flag" in exp:
        mat = exp[(exp.anom_flag == 1) & (exp.amount.abs() >= ANOM_MIN_AMOUNT) &
                  ((exp.dup == 1) | (exp.z_key > 3) | (exp.z_cat > 3))].copy()
        if len(mat):
            mat["impacto"] = np.where(mat.dup == 1, mat.amount.abs(), (mat.amount.abs() - mat.ref_amount).clip(lower=0))
            impact = mat.impacto.sum() / nm
            if impact >= ANOM_IMPACT_ALERT * mean_exp:
                ndup = int((mat.dup == 1).sum())
                recs.append(dict(code="gastos_atipicos", category="Geral", severity="alta",
                                 title=f"{len(mat)} gastos fora do seu padrão",
                                 detail=f"Impacto estimado de {_fmt(impact)}/mês"
                                        + (f"; {ndup} possível(is) cobrança(s) em duplicidade — vale contestar." if ndup else "."),
                                 monthly_saving=float(impact)))
    order = {"alta": 0, "média": 1, "baixa": 2}
    recs.sort(key=lambda r: (order[r["severity"]], -r["monthly_saving"]))
    kpis = dict(receita_media=float(mean_inc), despesa_media=float(mean_exp),
                taxa_poupanca=float(1 - mean_exp / mean_inc) if mean_inc > 0 else float("nan"), meses=nm)
    return dict(recs=recs, kpis=kpis, monthly=monthly, subs=subs)
