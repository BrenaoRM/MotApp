"""Golden da T5: entradas pequenas (vetores sintéticos de dimensão 8) → saída do modelo do app (`modelo_app`).

O Kotlin deve reproduzir `esperado` com tolerância 1e-4. Os vetores `x` são dados prontos (não vêm do ONNX); as
descrições só entram no léxico (`sem_acento(normalizar_para_modelo(...))`, por palavra inteira) e na chave da memória
exata. Uso: `cd ia && python gerar_golden_classificador.py`.
"""
import json

import numpy as np

from avaliar_categorias import chave_do_estabelecimento, modelo_app
from exportar_modelo import RAIZ
from usuarios_sinteticos import GENERICAS

SAIDA = RAIZ.parent / "app" / "src" / "test" / "resources" / "ia" / "golden_classificador.json"
D = 8

CLASSIFICADOR = {"epocas": 300, "taxa": 0.5, "lambdaL2": 1e-4, "pesoPrototipo": 1.0, "alfaK": 10.0,
                 "temperaturaPrior": 0.05, "limiarSugestao": 0.42, "minimoExemplos": 3}
LEXICO = {"uber": "Transporte", "prime": "Lazer e Assinaturas", "smart fit": "Saúde",
          "ifood": "Restaurantes e Delivery"}


def _unit(v):
    v = np.asarray(v, dtype=np.float64)
    return v / np.linalg.norm(v)


def _r(m):
    """Arredonda a 6 casas: o JSON guarda exatamente o que entra no cálculo."""
    m = np.round(np.asarray(m, dtype=np.float64), 6)
    return m


def montar():
    rng = np.random.default_rng(7)
    vg = _r([_unit(rng.normal(size=D)) for _ in GENERICAS])
    generico = {"nomes": GENERICAS, "pesos": _r(rng.normal(size=(9, D))).tolist(),
                "vieses": _r(rng.normal(scale=0.1, size=9)).tolist(),
                "vetoresNomes": vg.tolist(), "similaridadeMinimaMapeamento": 0.5, "confiancaLexico": 0.9}
    # categorias: 0 ≈ Transporte, 1 ≈ Restaurantes, 2 e 3 longe das genéricas (não mapeadas)
    nomes = ["Carro", "Comida fora", "Trabalho", "Pet"]
    V = _r([_unit(vg[2] + 0.2 * rng.normal(size=D)), _unit(vg[1] + 0.2 * rng.normal(size=D)),
            _unit(-vg.sum(axis=0) + 0.1 * rng.normal(size=D)),
            _unit(-vg[[0, 3, 4]].sum(axis=0) + 0.1 * rng.normal(size=D))])

    def perto(c, ruido=0.3):
        return _r(_unit(V[c] + ruido * rng.normal(size=D))).tolist()

    # a pessoa rotula "UBER TRIP" como Trabalho; o léxico ("uber") diria Transporte
    exemplos = [("UBER TRIP", 2), ("POSTO SHELL", 0), ("RESTAURANTE SABOR", 1), ("UBER TRIP", 2), ("RACAO TOTAL", 3)]
    ex_X = [perto(c) for _, c in exemplos]
    consultas = [("UBER TRIP", perto(0)),           # memória exata: Trabalho vence léxico e vetor
                 ("Uber  Trip", perto(0)),          # mesma chave (maiúsculas/espaços)
                 ("UBER EATS", perto(1)),           # outra chave: léxico "uber" → Transporte no prior
                 ("AMAZON PRIME VIDEO", perto(2)),  # "prime" casa como palavra
                 ("PRIMEIRO MERCADO", perto(3)),    # "prime" não casa "primeiro"
                 ("SMART FIT ACADEMIA", perto(1)),  # termo de duas palavras
                 ("LOJA QUALQUER", perto(3))]       # sem léxico, sem memória
    casos = []
    for n in (0, 5):
        ex = exemplos[:n]
        out = modelo_app(CLASSIFICADOR, generico, LEXICO, V,
                         np.array(ex_X[:n], dtype=np.float64).reshape(n, D), np.array([c for _, c in ex], dtype=int),
                         [chave_do_estabelecimento(d) for d, _ in ex], np.array([x for _, x in consultas]),
                         [d for d, _ in consultas], [chave_do_estabelecimento(d) for d, _ in consultas])
        casos.append({"n": n,
                      "exemplos": [{"descricao": d, "categoria": c, "x": x} for (d, c), x in zip(ex, ex_X[:n])],
                      "consultas": [{"descricao": d, "x": x} for d, x in consultas],
                      "esperado": {k: (v.tolist() if isinstance(v, np.ndarray) else v) for k, v in out.items()}})
    return {"descricao": "Saída do modelo do app (spec em ia/resultados/categorias_personalizadas.md) para vetores "
                         "sintéticos; comparar com tolerância 1e-4. Índice -1 = nenhum (não mapeada, sem léxico, "
                         "sem memória, sem sugestão).",
            "dimensao": D, "classificador": CLASSIFICADOR, "generico": generico, "lexico": LEXICO,
            "categorias": [{"nome": nm, "vetorNome": v} for nm, v in zip(nomes, V.tolist())], "casos": casos}


def main():
    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    SAIDA.write_text(json.dumps(montar(), ensure_ascii=False, indent=1), encoding="utf-8")
    print(SAIDA)


if __name__ == "__main__":
    main()
