"""Baixa binarios e fonte do llama.cpp (tag em VERSAO_LLAMA_CPP), modelos base e professor.

Idempotente: pula o que ja existe. Rodar com o python do venv:
    ia/llm/.venv/Scripts/python ia/llm/preparar_ambiente.py
Opcoes: --so-llama (apenas llama.cpp), --so-modelos (apenas pesos).
"""
import argparse
import io
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent
BIN = RAIZ / "bin"
MODELOS = RAIZ / "modelos"
TAG = (RAIZ / "VERSAO_LLAMA_CPP").read_text().strip()
CUDA = "12.4"
GH = "https://github.com/ggml-org/llama.cpp"
EXES = ["llama-server.exe", "llama-quantize.exe", "llama-gguf-split.exe"]
BASES = ["Qwen3-0.6B", "Qwen3-1.7B", "Qwen3-4B"]
PROFESSOR_REPO = "Qwen/Qwen3-8B-GGUF"
PROFESSOR_ARQ = "Qwen3-8B-Q4_K_M.gguf"


def baixar_zip(url: str, destino: Path) -> None:
    print(f"baixando {url}")
    with urllib.request.urlopen(url) as r:
        dados = r.read()
    with zipfile.ZipFile(io.BytesIO(dados)) as z:
        z.extractall(destino)


def llama_cpp() -> None:
    BIN.mkdir(parents=True, exist_ok=True)
    if all((BIN / e).exists() for e in EXES):
        print("binarios llama.cpp ja existem")
    else:
        for nome in (f"llama-{TAG}-bin-win-cuda-{CUDA}-x64.zip", f"cudart-llama-bin-win-cuda-{CUDA}-x64.zip"):
            baixar_zip(f"{GH}/releases/download/{TAG}/{nome}", BIN)
    conv = BIN / "llama.cpp" / "convert_hf_to_gguf.py"
    if conv.exists():
        print("fonte llama.cpp ja existe")
    else:
        baixar_zip(f"{GH}/archive/refs/tags/{TAG}.zip", BIN)
        (BIN / f"llama.cpp-{TAG}").rename(BIN / "llama.cpp")
    # dependencias do conversor (gguf-py da mesma tag)
    subprocess.check_call([sys.executable, "-m", "pip", "install", "-q", str(BIN / "llama.cpp" / "gguf-py")])


def modelos() -> None:
    from huggingface_hub import hf_hub_download, snapshot_download

    for nome in BASES:
        dest = MODELOS / "base" / nome
        if (dest / "config.json").exists() and list(dest.glob("*.safetensors")):
            print(f"{nome} ja existe")
            continue
        snapshot_download(f"Qwen/{nome}", local_dir=dest, allow_patterns=["*.json", "*.safetensors", "*.txt"])
    prof = MODELOS / "professor" / PROFESSOR_ARQ
    if prof.exists():
        print("professor ja existe")
    else:
        hf_hub_download(PROFESSOR_REPO, PROFESSOR_ARQ, local_dir=prof.parent)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--so-llama", action="store_true")
    ap.add_argument("--so-modelos", action="store_true")
    a = ap.parse_args()
    if not a.so_modelos:
        llama_cpp()
    if not a.so_llama:
        modelos()
