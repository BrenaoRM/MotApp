"""Junta o adaptador LoRA ao modelo base, converte para GGUF f16, quantiza em Q4_K_M e divide se passar de 1,9 GB.

    python exportar_gguf.py --lora modelos/lora/rapida/final --saida modelos/gguf/motapp-rapida-q4km.gguf [--base DIR]

O arquivo inteiro fica em --saida (usado na avaliação no PC). Se passar de 1,9 GB, as partes de até 1900M (limite de
download do app) vão para `<pasta de --saida>/partes/<nome>/`. Imprime o SHA-256 e o tamanho de cada arquivo.
"""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
BIN = AQUI / "bin"
CONVERSOR = BIN / "llama.cpp" / "convert_hf_to_gguf.py"
LIMITE_PARTE = 1_900_000_000  # 1,9 GB; --split-max-size 1900M


def precisa_dividir(tamanho_bytes):
    return tamanho_bytes > LIMITE_PARTE


def base_do_adaptador(lora):
    return json.loads((Path(lora) / "adapter_config.json").read_text(encoding="utf-8"))["base_model_name_or_path"]


def sha256(caminho):
    h = hashlib.sha256()
    with open(caminho, "rb") as f:
        for bloco in iter(lambda: f.read(1 << 20), b""):
            h.update(bloco)
    return h.hexdigest()


def relatorio(arquivos):
    return ["%s  %.1f MB  sha256 %s" % (Path(a).name, Path(a).stat().st_size / 1e6, sha256(a)) for a in arquivos]


def _rodar(cmd):
    print("+ " + " ".join(str(c) for c in cmd), flush=True)
    subprocess.run([str(c) for c in cmd], check=True)


def juntar(lora, base, destino):
    import torch
    from peft import PeftModel
    from transformers import AutoModelForCausalLM, AutoTokenizer
    modelo = AutoModelForCausalLM.from_pretrained(str(base), torch_dtype=torch.bfloat16, device_map={"": "cpu"})
    modelo = PeftModel.from_pretrained(modelo, str(lora)).merge_and_unload()
    modelo.save_pretrained(str(destino), safe_serialization=True)
    AutoTokenizer.from_pretrained(str(base)).save_pretrained(str(destino))


def exportar(lora, saida, base=None):
    saida = Path(saida)
    saida.parent.mkdir(parents=True, exist_ok=True)
    base = base or base_do_adaptador(lora)
    tmp = saida.parent / ("_tmp-" + saida.stem)
    shutil.rmtree(tmp, ignore_errors=True)
    tmp.mkdir()
    try:
        juntar(lora, base, tmp / "merged")
        f16 = tmp / "f16.gguf"
        _rodar([sys.executable, CONVERSOR, tmp / "merged", "--outtype", "f16", "--outfile", f16])
        parcial = saida.with_suffix(".tmp")
        _rodar([BIN / "llama-quantize.exe", f16, parcial, "Q4_K_M"])
        parcial.replace(saida)  # o arquivo final só existe quando completo (a orquestração pula se ele existir)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    arquivos = [saida]
    if precisa_dividir(saida.stat().st_size):
        pasta = saida.parent / "partes" / saida.stem
        shutil.rmtree(pasta, ignore_errors=True)
        pasta.mkdir(parents=True)
        _rodar([BIN / "llama-gguf-split.exe", "--split", "--split-max-size", "1900M", saida, pasta / saida.stem])
        arquivos += sorted(pasta.glob("*.gguf"))
    for l in relatorio(arquivos):
        print(l, flush=True)
    return arquivos


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lora", required=True)
    ap.add_argument("--saida", required=True)
    ap.add_argument("--base", help="pasta HF do modelo base (padrão: a registrada no adaptador)")
    a = ap.parse_args(argv)
    if sys.stdout.encoding != "utf-8":
        sys.stdout.reconfigure(encoding="utf-8")
    exportar(a.lora, a.saida, a.base)


if __name__ == "__main__":
    main()
