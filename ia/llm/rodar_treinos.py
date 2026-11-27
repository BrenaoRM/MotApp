"""Orquestra, sem supervisão, o treino dos três modos (Rápida 0.6B, Média 1.7B, Inteligente 4B).

    .venv/Scripts/python rodar_treinos.py          (rode com o python do venv; pode ser relançado: retoma de onde parou)

1. espera o gerador de dados terminar (processo gerar_*.py sumiu e o log tem a linha final do gerar_respostas);
2. encerra o llama-server do professor (só o da porta 8081);
3. teste de fumaça (20 passos na 0.6B): se falhar, aborta com uma linha clara no log;
4. por modo: treino -> exportação GGUF -> avaliação (cada etapa é pulada se a saída já existe);
5. por fim `avaliar_llm.py --tabela`.
Tudo registra início/fim/duração em modelos/treinos.log.
"""
import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

AQUI = Path(__file__).resolve().parent
PY = sys.executable
GERADOS = AQUI / "dados" / "gerados"
GERACAO_LOG = GERADOS / "geracao.log"
LOG = AQUI / "modelos" / "treinos.log"
LORA = AQUI / "modelos" / "lora"
GGUF = AQUI / "modelos" / "gguf"
RESULTADOS = AQUI.parent / "resultados" / "llm"
BASES = AQUI / "modelos" / "base"
CONFIG_LOTE = {"0.6B": (8, 2), "1.7B": (4, 4), "4B": (1, 16)}  # espelha treinar_lora.CONFIG (lote, acumulação)
MODOS = [("rapida", "0.6B"), ("media", "1.7B"), ("inteligente", "4B")]
PORTA_PROFESSOR = 8081
LINHA_FINAL = re.compile(r"^\{'tentativas': \d+, 'aprovadas': \d+")


def log(msg):
    linha = "%s %s" % (time.strftime("%Y-%m-%d %H:%M:%S"), msg)
    print(linha, flush=True)
    LOG.parent.mkdir(parents=True, exist_ok=True)
    with open(LOG, "a", encoding="utf-8") as f:
        f.write(linha + "\n")


def _powershell(cmd):
    return subprocess.run(["powershell", "-NoProfile", "-Command", cmd], capture_output=True, text=True, timeout=60).stdout.strip()


def geradores_rodando():
    """PIDs de processos cujo comando cita gerar_planos.py / gerar_respostas.py / rodar.cmd (fora este)."""
    saida = _powershell(
        "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'gerar_(planos|respostas)\\.py|dados\\\\gerados\\\\rodar\\.cmd' "
        "-and $_.CommandLine -notmatch 'Get-CimInstance' } | ForEach-Object { $_.ProcessId }")
    return [int(x) for x in saida.split() if x.isdigit()]


def geracao_terminou():
    if not GERACAO_LOG.exists():
        return False
    linhas = [l.strip() for l in GERACAO_LOG.read_text(encoding="utf-8", errors="replace").splitlines() if l.strip()]
    return bool(linhas) and bool(LINHA_FINAL.match(linhas[-1]))


def esperar_gerador(intervalo=60):
    log("[espera] aguardando o gerador de dados terminar")
    voltas = 0
    while True:
        pids = geradores_rodando()
        if not pids:
            if geracao_terminou():
                break
            log("[espera] ABORTADO: gerador não está rodando e o log não termina com a linha final do gerar_respostas "
                "(gerador caiu? relance rodar.cmd e depois este script)")
            sys.exit(2)
        if voltas % 10 == 0:  # uma linha a cada ~10 min
            ultima = ([""] + GERACAO_LOG.read_text(encoding="utf-8", errors="replace").strip().splitlines())[-1]
            log("[espera] gerador rodando (PIDs %s). Último: %s" % (pids, ultima[:140]))
        voltas += 1
        time.sleep(intervalo)
    for nome in ("plano_treino", "plano_valid", "resposta_treino", "resposta_valid"):
        if not (GERADOS / (nome + ".jsonl")).exists():
            log("[espera] ABORTADO: falta %s.jsonl" % nome)
            sys.exit(2)
    log("[espera] gerador terminou; dados finais")


def _cauda(arquivo, n=15):
    try:
        linhas = Path(arquivo).read_text(encoding="utf-8", errors="replace").strip().splitlines()
    except OSError:
        return "(sem %s)" % arquivo
    return "\n".join(linhas[-n:])


def preflight():
    """Antes de encerrar o professor: CUDA visível, placa de >= 5,5 GiB e dry-run (dados, coletor, argumentos) sem erro."""
    r = subprocess.run([PY, "-c", "import torch,sys; ok=torch.cuda.is_available(); "
                        "print(ok, torch.cuda.mem_get_info()[1]/2**30 if ok else 0)"], capture_output=True, text=True, cwd=str(AQUI))
    partes = (r.stdout.strip().splitlines() or [""])[-1].split()
    if r.returncode != 0 or len(partes) != 2 or partes[0] != "True" or float(partes[1]) < 5.5:
        log("[preflight] ABORTADO (professor intacto): CUDA indisponível ou placa pequena demais. rc=%d saída=%r %s" % (
            r.returncode, r.stdout.strip()[-200:], r.stderr.strip()[-300:]))
        sys.exit(5)
    saida = LORA / "_fumaca"
    saida.mkdir(parents=True, exist_ok=True)
    rc = etapa("dry-run", [PY, "treinar_lora.py", "--base", "0.6B", "--saida", saida, "--dry-run", "--max-exemplos", "400",
                           "--max-valid", "32"], saida / "dry-run.log")
    if rc != 0:
        log("[preflight] ABORTADO (professor intacto): dry-run falhou. Fim do log:\n%s" % _cauda(saida / "dry-run.log"))
        sys.exit(5)
    log("[preflight] ok (CUDA, %s GiB, dry-run)" % partes[1][:4])


def parar_professor():
    pid = _powershell("(Get-NetTCPConnection -LocalPort %d -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1)"
                      ".OwningProcess" % PORTA_PROFESSOR)
    if not pid.isdigit():
        log("[professor] nada escutando na porta %d" % PORTA_PROFESSOR)
        return
    nome = _powershell("(Get-Process -Id %s).ProcessName" % pid)
    if "llama-server" not in nome:
        log("[professor] ABORTADO: o processo na porta %d é %s, não llama-server" % (PORTA_PROFESSOR, nome))
        sys.exit(2)
    subprocess.run(["taskkill", "/PID", pid, "/F"], capture_output=True)
    log("[professor] llama-server (PID %s, porta %d) encerrado" % (pid, PORTA_PROFESSOR))
    for _ in range(24):
        usado = _powershell("nvidia-smi --query-gpu=memory.used --format=csv,noheader,nounits")
        if usado.isdigit() and int(usado) < 1500:
            return
        time.sleep(5)
    log("[professor] aviso: a GPU ainda mostra %s MiB em uso" % usado)


def etapa(nome, cmd, saida_log=None):
    """Roda cmd; registra início/fim/duração. Devolve o código de saída."""
    log("[%s] INÍCIO" % nome)
    t0 = time.time()
    destino = open(saida_log, "a", encoding="utf-8") if saida_log else None
    try:
        rc = subprocess.run([str(c) for c in cmd], cwd=str(AQUI), stdout=destino, stderr=subprocess.STDOUT if destino else None,
                            env=_env()).returncode
    finally:
        if destino:
            destino.close()
    log("[%s] FIM rc=%d em %.1f min" % (nome, rc, (time.time() - t0) / 60))
    return rc


def _env():
    import os
    return {**os.environ, "PYTHONIOENCODING": "utf8", "PYTHONUTF8": "1"}


def fumaca():
    saida = LORA / "_fumaca"
    if (saida / "final-smoke" / "adapter_config.json").exists():
        log("[fumaça] já feita, pulando")
        return
    saida.mkdir(parents=True, exist_ok=True)
    rc = etapa("fumaça", [PY, "treinar_lora.py", "--base", "0.6B", "--saida", saida, "--passos", "20", "--max-exemplos", "400",
                          "--max-valid", "32"], saida / "stdout.log")
    if rc != 0 or not (saida / "final-smoke" / "adapter_config.json").exists():
        log("[fumaça] FALHOU (rc=%d): abortando todo o treino. Veja %s e %s. Fim do stdout:\n%s" % (
            rc, saida / "stdout.log", saida / "treino.log", _cauda(saida / "stdout.log")))
        sys.exit(4)
    log("[fumaça] ok")


def modo(nome, base):
    lora = LORA / nome
    gguf = GGUF / ("motapp-%s-q4km.gguf" % nome)
    lora.mkdir(parents=True, exist_ok=True)
    if (lora / "final" / "adapter_config.json").exists():
        log("[%s] treino já feito, pulando" % nome)
    else:
        lote_padrao, acum_padrao = CONFIG_LOTE[base]
        extra, metade = [], False
        for tentativa in (1, 2, 3):  # retomadas continuam do último checkpoint
            rc = etapa("%s treino (%s)%s%s" % (nome, base, "" if tentativa == 1 else " retomada %d" % tentativa,
                                                " lote/2" if metade else ""),
                       [PY, "treinar_lora.py", "--base", base, "--saida", lora] + extra, lora / "stdout.log")
            if rc == 0:
                break
            if rc == 3:
                if base == "4B" or metade or lote_padrao < 2:
                    log("[%s] OOM na GPU com %s (lote %s): registrado, seguindo sem este modo" % (
                        nome, base, "reduzido" if metade else "mínimo"))
                    return
                metade = True  # mesmo lote efetivo: metade do lote, o dobro da acumulação
                extra = ["--lote", str(lote_padrao // 2), "--acum", str(acum_padrao * 2)]
                log("[%s] OOM na GPU: nova tentativa com lote %d x acumulação %d" % (nome, lote_padrao // 2, acum_padrao * 2))
        else:
            log("[%s] treino FALHOU 3 vezes, seguindo para o próximo modo. Fim do stdout:\n%s" % (nome, _cauda(lora / "stdout.log")))
            return
    if gguf.exists():
        log("[%s] GGUF já existe, pulando exportação" % nome)
    elif etapa("%s exportação" % nome, [PY, "exportar_gguf.py", "--lora", lora / "final", "--saida", gguf, "--base", BASES / ("Qwen3-" + base)],
               lora / "exportar.log") != 0:
        log("[%s] exportação FALHOU (veja %s)" % (nome, lora / "exportar.log"))
        return
    if (RESULTADOS / (nome + ".json")).exists():
        log("[%s] avaliação já existe, pulando" % nome)
    elif etapa("%s avaliação" % nome, [PY, "avaliar_llm.py", "--modelo", gguf, "--nome", nome], lora / "avaliacao.log") != 0:
        log("[%s] avaliação FALHOU (veja %s)" % (nome, lora / "avaliacao.log"))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sem-espera", action="store_true", help="não espera o gerador nem encerra o professor (só se já terminou)")
    a = ap.parse_args(argv)
    log("[orquestração] iniciada (PID %d)" % __import__("os").getpid())
    if not a.sem_espera:
        esperar_gerador()
        preflight()
        parar_professor()
    fumaca()
    for nome, base in MODOS:
        modo(nome, base)
    etapa("tabela", [PY, "avaliar_llm.py", "--tabela"], AQUI / "modelos" / "tabela.log")
    log("[orquestração] concluída; tabela em ia/resultados/llm.md")


if __name__ == "__main__":
    main()
