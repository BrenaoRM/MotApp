"""QLoRA (4 bits) de um Qwen3 para o assistente do MotApp: um modelo só, treinado com plano + resposta misturados.

    python treinar_lora.py --base 0.6B --saida modelos/lora/rapida [--passos N --max-exemplos N]

- Os `prompt` dos dados já são ChatML cru (idêntico ao do app, sem bloco think): nenhum chat template é aplicado.
- Texto = prompt + saida + "<|im_end|>"; a perda só conta em saida + <|im_end|> (o prompt fica com label -100).
- Retomável: se existir `checkpoint-*` em --saida o treino continua do último. Guarda o melhor pela perda de validação.
- Resultado final: `<saida>/final` (adaptador LoRA do melhor checkpoint). Se já existir, não faz nada.
- Log: `<saida>/treino.log`.
"""
import argparse
import json
import random
import re
import sys
import time
from pathlib import Path

AQUI = Path(__file__).resolve().parent
GERADOS = AQUI / "dados" / "gerados"
BASES = AQUI / "modelos" / "base"
FIM_DE_TURNO = "<|im_end|>"
CONTEXTO = 1024
SEMENTE = 42
EPOCAS = 2
# base -> (lote por passo, acumulação, gradient checkpointing). Lote efetivo 16 em todos; ajustado à VRAM de 6 GB.
CONFIG = {"0.6B": (8, 2, False), "1.7B": (4, 4, True), "4B": (1, 16, True)}


def ler_jsonl(caminho):
    linhas = []
    with open(caminho, encoding="utf-8") as f:
        for l in f:
            if l.strip():
                linhas.append(json.loads(l))
    return linhas


def misturar(*conjuntos, semente=SEMENTE):
    """Junta os conjuntos e embaralha de forma determinística."""
    todos = [x for c in conjuntos for x in c]
    random.Random(semente).shuffle(todos)
    return todos


def carregar_dados(max_exemplos=None, max_valid=None, dir_dados=GERADOS):
    dir_dados = Path(dir_dados)
    treino = misturar(ler_jsonl(dir_dados / "plano_treino.jsonl"), ler_jsonl(dir_dados / "resposta_treino.jsonl"))
    valid = misturar(ler_jsonl(dir_dados / "plano_valid.jsonl"), ler_jsonl(dir_dados / "resposta_valid.jsonl"))
    return treino[:max_exemplos], valid[:max_valid]


def tokenizar_exemplo(tokenizer, prompt, saida, contexto=CONTEXTO):
    """(input_ids, labels) com labels = -100 no prompt; None se não couber no contexto (nunca corta a saída)."""
    ids_prompt = tokenizer(prompt, add_special_tokens=False)["input_ids"]
    ids_saida = tokenizer(saida + FIM_DE_TURNO, add_special_tokens=False)["input_ids"]
    if len(ids_prompt) + len(ids_saida) > contexto:
        return None
    return ids_prompt + ids_saida, [-100] * len(ids_prompt) + ids_saida


def tokenizar_conjunto(tokenizer, exemplos, contexto=CONTEXTO):
    """Lista de {"input_ids","labels"} e quantos exemplos ficaram de fora por passar do contexto."""
    out, descartados = [], 0
    for e in exemplos:
        r = tokenizar_exemplo(tokenizer, e["prompt"], e["saida"], contexto)
        if r is None:
            descartados += 1
        else:
            out.append({"input_ids": r[0], "labels": r[1]})
    return out, descartados


def juntar_lote(lote, pad_id):
    """Padding à direita: input_ids com pad_id, labels com -100, attention_mask 1 nos tokens reais."""
    m = max(len(x["input_ids"]) for x in lote)
    return {
        "input_ids": [x["input_ids"] + [pad_id] * (m - len(x["input_ids"])) for x in lote],
        "labels": [x["labels"] + [-100] * (m - len(x["labels"])) for x in lote],
        "attention_mask": [[1] * len(x["input_ids"]) + [0] * (m - len(x["input_ids"])) for x in lote],
    }


def passos_por_epoca(n_exemplos, lote, acumulacao):
    return max(1, n_exemplos // (lote * acumulacao))


def passos_de_avaliacao(n_exemplos, lote, acumulacao):
    """Avalia/salva 4 vezes por época (mínimo 10 passos)."""
    return max(10, passos_por_epoca(n_exemplos, lote, acumulacao) // 4)


def ultimo_checkpoint(saida):
    cps = []
    for p in Path(saida).glob("checkpoint-*"):
        m = re.fullmatch(r"checkpoint-(\d+)", p.name)
        if m and (p / "trainer_state.json").exists():
            cps.append((int(m.group(1)), p))
    return str(max(cps)[1]) if cps else None


def _log_em(arquivo):
    def log(msg):
        linha = "%s %s" % (time.strftime("%Y-%m-%d %H:%M:%S"), msg)
        print(linha, flush=True)
        with open(arquivo, "a", encoding="utf-8") as f:
            f.write(linha + "\n")
    return log


def treinar(base, saida, passos=None, max_exemplos=None, max_valid=400, log=None):
    saida = Path(saida)
    saida.mkdir(parents=True, exist_ok=True)
    log = log or _log_em(saida / "treino.log")
    if (saida / "final" / "adapter_config.json").exists() and passos is None:
        log("[treino] %s já treinado (%s), nada a fazer" % (base, saida / "final"))
        return
    import unsloth  # noqa: F401  (precisa vir antes de transformers/peft)
    from unsloth import FastLanguageModel
    import torch
    from transformers import Trainer, TrainerCallback, TrainingArguments

    lote, acum, checkpointing = CONFIG[base]
    caminho_base = BASES / ("Qwen3-" + base)
    log("[treino] base %s (%s), lote %d x acumulação %d, checkpointing %s, contexto %d, épocas %d" % (
        base, caminho_base, lote, acum, checkpointing, CONTEXTO, EPOCAS))
    modelo, tokenizer = FastLanguageModel.from_pretrained(
        model_name=str(caminho_base), max_seq_length=CONTEXTO, load_in_4bit=True, dtype=None)
    modelo = FastLanguageModel.get_peft_model(
        modelo, r=16, lora_alpha=32, lora_dropout=0.0, bias="none",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        use_gradient_checkpointing="unsloth" if checkpointing else False, random_state=SEMENTE)

    treino_cru, valid_cru = carregar_dados(max_exemplos, max_valid)
    treino, d1 = tokenizar_conjunto(tokenizer, treino_cru)
    valid, d2 = tokenizar_conjunto(tokenizer, valid_cru)
    log("[treino] exemplos treino %d (descartados por contexto %d), validação %d (descartados %d)" % (
        len(treino), d1, len(valid), d2))
    pad_id = tokenizer.pad_token_id if tokenizer.pad_token_id is not None else tokenizer.eos_token_id

    def coletor(lote_):
        return {k: torch.tensor(v) for k, v in juntar_lote(lote_, pad_id).items()}

    cada = passos_de_avaliacao(len(treino), lote, acum) if passos is None else max(1, passos // 2)
    args = TrainingArguments(
        output_dir=str(saida), per_device_train_batch_size=lote, per_device_eval_batch_size=max(1, lote),
        gradient_accumulation_steps=acum, num_train_epochs=EPOCAS, max_steps=passos if passos else -1,
        learning_rate=2e-4, lr_scheduler_type="cosine", warmup_ratio=0.03, weight_decay=0.0, optim="adamw_8bit",
        bf16=True, logging_steps=10, eval_strategy="steps", eval_steps=cada, save_strategy="steps", save_steps=cada,
        save_total_limit=2, load_best_model_at_end=True, metric_for_best_model="eval_loss", greater_is_better=False,
        group_by_length=True, dataloader_num_workers=0, report_to="none", seed=SEMENTE, remove_unused_columns=False)

    class Registro(TrainerCallback):
        def on_log(self, a, estado, controle, logs=None, **kw):
            if logs:
                log("[passo %d/%d] %s" % (estado.global_step, estado.max_steps, json.dumps(logs)))

    trainer = Trainer(model=modelo, args=args, train_dataset=treino, eval_dataset=valid, data_collator=coletor,
                      callbacks=[Registro()])
    retomar = ultimo_checkpoint(saida)
    log("[treino] %s" % ("retomando de " + retomar if retomar else "começando do zero"))
    t0 = time.time()
    try:
        trainer.train(resume_from_checkpoint=retomar)
    except torch.cuda.OutOfMemoryError:
        log("[treino] ERRO: sem memória na GPU (OOM) com base %s" % base)
        sys.exit(3)
    destino = saida / ("final" if passos is None else "final-smoke")
    modelo.save_pretrained(str(destino))
    tokenizer.save_pretrained(str(destino))
    log("[treino] concluído em %.0f min, melhor checkpoint %s (eval_loss %s), adaptador em %s" % (
        (time.time() - t0) / 60, trainer.state.best_model_checkpoint, trainer.state.best_metric, destino))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", required=True, choices=sorted(CONFIG))
    ap.add_argument("--saida", required=True)
    ap.add_argument("--passos", type=int, help="só para teste de fumaça: para após N passos")
    ap.add_argument("--max-exemplos", type=int, help="limita os exemplos de treino (teste)")
    ap.add_argument("--max-valid", type=int, default=400, help="exemplos de validação usados (padrão 400)")
    a = ap.parse_args(argv)
    if sys.stdout.encoding != "utf-8":
        sys.stdout.reconfigure(encoding="utf-8")
    treinar(a.base, a.saida, a.passos, a.max_exemplos, a.max_valid)


if __name__ == "__main__":
    main()
