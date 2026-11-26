"""Detecção de gastos atípicos relativos ao histórico do PRÓPRIO usuário (Isolation Forest por usuário)."""
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from text_utils import merchant_key

FEATS = ["log_amount", "z_key", "z_cat", "dup", "gap_cat", "n_same_day"]


def _robust_z(la, grp_median, grp_mad, floor=0.2):
    return (la - grp_median) / (1.4826 * np.maximum(grp_mad, floor))


def build_features(df: pd.DataFrame, cat_col="category_pred") -> pd.DataFrame:
    """df: apenas despesas de UM usuário (amount<0), colunas date, description, amount, cat_col."""
    d = df.sort_values("date").copy()
    d["abs_amount"] = d["amount"].abs()
    d["log_amount"] = np.log1p(d["abs_amount"])
    d["key"] = d["description"].map(merchant_key)
    glob_med, glob_mad = d.log_amount.median(), (d.log_amount - d.log_amount.median()).abs().median()

    def ref(col):
        g = d.groupby(col)["log_amount"]
        med = g.transform("median")
        mad = d.assign(_dev=(d.log_amount - med).abs()).groupby(col)["_dev"].transform("median")
        n = g.transform("count")
        return med.where(n >= 3, np.nan), mad.where(n >= 3, np.nan)

    kmed, kmad = ref("key")
    cmed, cmad = ref(cat_col)
    cmed, cmad = cmed.fillna(glob_med), cmad.fillna(glob_mad)
    d["z_cat"] = _robust_z(d.log_amount, cmed, cmad)
    d["z_key"] = _robust_z(d.log_amount, kmed.fillna(cmed), kmad.fillna(cmad))
    d["ref_amount"] = np.expm1(kmed.fillna(cmed))
    # duplicidade: mesma chave e mesmo valor numa janela de 3 dias, anterior à transação atual
    dup = np.zeros(len(d), dtype=int)
    dates, keys, amts = d.date.values, d.key.values, d.abs_amount.values
    for i in range(len(d)):
        for j in range(i):
            if keys[i] == keys[j] and abs(amts[i] - amts[j]) < 0.01 and (dates[i] - dates[j]) <= np.timedelta64(3, "D"):
                dup[i] = 1
                break
    d["dup"] = dup
    d["gap_cat"] = np.log1p(d.groupby(cat_col)["date"].diff().dt.days.fillna(30))
    d["n_same_day"] = d.groupby("date")["date"].transform("count")
    return d


def detect(df: pd.DataFrame, cat_col="category_pred", contamination=0.04, seed=0) -> pd.DataFrame:
    """Retorna df com colunas anom_score (maior = mais atípico), anom_flag e z_cat/z_key/dup."""
    d = build_features(df, cat_col)
    if len(d) < 20:
        d["anom_score"], d["anom_flag"] = 0.0, 0
        return d
    iso = IsolationForest(n_estimators=300, contamination=contamination, random_state=seed)
    iso.fit(d[FEATS])
    d["anom_score"] = -iso.score_samples(d[FEATS])
    d["anom_flag"] = (iso.predict(d[FEATS]) == -1).astype(int)
    return d


def zscore_baseline(d: pd.DataFrame, thr=3.5) -> pd.Series:
    """Regra estatística simples: valor muito acima do típico da categoria."""
    return (d["z_cat"] > thr).astype(int)
