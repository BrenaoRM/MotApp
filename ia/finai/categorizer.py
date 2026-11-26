"""Classificação supervisionada de transações em categorias de gasto."""
import joblib
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.naive_bayes import MultinomialNB
from sklearn.pipeline import Pipeline, FeatureUnion
from sklearn.base import BaseEstimator, TransformerMixin
from scipy import sparse
from text_utils import normalize, clean_text

KEYWORDS = [  # baseline: regras de palavras-chave (ordem importa; 1º acerto vence)
    ("Alimentação", ["mercado", "supermercado", "padaria", "acougue", "hortifruti", "sacolao", "atacado", "atacarejo", "mercearia"]),
    ("Restaurantes e Delivery", ["ifood", "restaurante", "lanchonete", "pizzaria", "hamburgueria", "sorveteria", "churrascaria", "sushi", "acai", "cafe"]),
    ("Transporte", ["uber", "posto", "estacionamento", "pedagio", "combustiveis", "oficina", "borracharia", "viacao"]),
    ("Moradia", ["aluguel", "condominio", "energia", "agua", "internet", "gas butano"]),
    ("Saúde", ["farmacia", "drogaria", "clinica", "laboratorio", "dentista", "otica"]),
    ("Lazer e Assinaturas", ["netflix", "spotify", "cinema", "prime", "disney", "hbo", "youtube", "smart fit", "steam"]),
    ("Educação", ["faculdade", "curso", "livraria", "papelaria", "escola", "colegio"]),
    ("Compras", ["loja", "shopee", "amazon", "aliexpress"]),
]


class KeywordBaseline:
    def fit(self, X, y=None):
        return self

    def predict(self, X):
        out = []
        for d in X:
            t = normalize(d)
            cat = "Outros"
            for c, kws in KEYWORDS:
                if any(k in t for k in kws):
                    cat = c
                    break
            out.append(cat)
        return np.array(out)


class LexiconFeatures(BaseEstimator, TransformerMixin):
    """Indicadores multi-hot de palavras genéricas do domínio (supermercado, farmácia, posto...).
    Dão ao modelo uma 'dica' para estabelecimentos NUNCA vistos no treino."""
    def __init__(self, weight=2.0):
        self.weight = weight

    def fit(self, X, y=None):
        return self

    def transform(self, X):
        rows = [[1.0 if any(k in t for k in kws) else 0.0 for _, kws in KEYWORDS] for t in map(normalize, X)]
        return sparse.csr_matrix(np.array(rows) * self.weight)


def make_model(kind="hibrido"):
    """kind: 'nb' (Naive Bayes), 'lr' (Reg. Logística só n-gramas), 'hibrido' (n-gramas + léxico + RL)."""
    vec = TfidfVectorizer(preprocessor=clean_text, analyzer="char_wb", ngram_range=(2, 5), sublinear_tf=True, min_df=2)
    if kind == "nb":
        return Pipeline([("tfidf", vec), ("clf", MultinomialNB(alpha=0.1))])
    if kind == "lr":
        return Pipeline([("tfidf", vec), ("clf", LogisticRegression(C=5, max_iter=3000))])
    feats = FeatureUnion([("tfidf", vec), ("lex", LexiconFeatures())])
    return Pipeline([("feats", feats), ("clf", LogisticRegression(C=5, max_iter=3000))])


def save(model, path):
    joblib.dump(model, path)


def load(path):
    return joblib.load(path)
