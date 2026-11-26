"""Golden da T3 (tokens e vetores) a partir dos assets finais, com a Viterbi podada em Python.

Uso: `cd ia && python gerar_golden.py`. Grava em `app/src/test/resources/ia/`.
"""
import json

from exportar_modelo import ASSETS, RAIZ, _ler_json, carregar_onnx, descricoes_finia, viterbi_de_json
from texto import normalizar_para_modelo

DESTINO = RAIZ.parent / "app" / "src" / "test" / "resources" / "ia"

DIFICEIS = [
    "", "   ", "123", "0001 2345", "😀", "😀 iFood", "ÇÃO", "AÇÃO", "iFood *Pedido", "IFOOD*PEDIDO 123",
    "Jean-Paul", "Ana-Maria Silva", "São Paulo", "SAO PAULO", "Pão de Açúcar", "padaria pão quente",
    "ﬁ", "① posto", "ＡＢＣ", "Café & Cia", "café", "CAFÉ", "Müller", "naïve", "coração",
    "  dois   espaços  ", "a", "x y z", "PAG*", "PIX ENVIADO", "Pix enviado - João", "pix recebido maria",
    "COMPRA CARTAO DEBITO", "uber*trip", "99 APP", "ÁÉÍÓÚ âêô ãõ à", "ñandú", "ß", "Ελλάδα", "москва",
    "東京", "ඞ", "𓀀𓀁 abc", "tab\tseparado", "linha\nnova", "under_score", "hífen‐unicode",
    "palavra " * 80, "supercalifragilisticexpialidocious", "ACOUGUE BOI DE OURO RIO PARANAIBA",
]


def main():
    dados = _ler_json(ASSETS)
    codificar = viterbi_de_json(dados)
    brutos = descricoes_finia()[:150] + DIFICEIS
    assert len(brutos) == 200
    tokens = []
    for b in brutos:
        n = normalizar_para_modelo(b)
        tokens.append({"bruto": b, "normalizado": n, "ids": codificar(dados["prefixo"] + n, dados["max_tokens"])})
    textos = [normalizar_para_modelo(b) for b in descricoes_finia()[150:190]] + \
        [normalizar_para_modelo(b) for b in DIFICEIS[:10]]
    vetores = carregar_onnx(ASSETS)(textos)
    DESTINO.mkdir(parents=True, exist_ok=True)
    (DESTINO / "golden_tokens.json").write_text(json.dumps(tokens, ensure_ascii=False, indent=1), encoding="utf-8")
    (DESTINO / "golden_vetores.json").write_text(json.dumps(
        [{"texto": t, "vetor": [round(float(x), 7) for x in v]} for t, v in zip(textos, vetores)],
        ensure_ascii=False), encoding="utf-8")
    print(len(tokens), "tokens,", len(textos), "vetores em", DESTINO)


if __name__ == "__main__":
    main()
