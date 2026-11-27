# MotApp

App Android de finanças pessoais, feito em Kotlin com Jetpack Compose. Os dados ficam no aparelho (Room); o backup opcional vai para a pasta privada do app no Google Drive. A IA Financeira (sugestão de categoria, gastos fora do padrão, recomendações e perguntas) roda 100% offline no aparelho.

Projeto acadêmico: a IA Financeira é o trabalho de SIN 323 (Inteligência Artificial) da UFV.

## Funcionalidades

- Lançamentos de despesas e receitas, incluindo compras parceladas e despesas recorrentes (assinaturas).
- Cartões e faturas, com lembrete de fatura em segundo plano (WorkManager). Pagar a fatura não conta como gasto novo.
- Orçamentos por categoria, metas, investimentos e categorias personalizadas.
- Dashboard e Análise por mês ou por período personalizado; Extrato com exportação em CSV.
- Calendário e agenda, com integração ao Google Calendar.
- Detecção de transações a partir de notificações dos bancos: um popup pré-preenche a Nova Transação. O texto da notificação não é gravado.
- Backup no Google Drive (AppData), manual e automático, com restauração.
- Bloqueio do app por biometria.
- Atualização do app pelos APKs do GitHub Releases.
- **Assistente (IA Financeira, offline):** sugere a categoria enquanto você digita a descrição, aponta gastos fora do padrão, dá recomendações e responde perguntas sobre os próprios lançamentos.
- Tema claro, escuro ou do sistema, com o mesmo layout nos três.

## Stack

| Item | Versão |
|---|---|
| Kotlin / KSP | 2.2.10 / 2.2.10-2.0.2 |
| Android Gradle Plugin / Gradle | 9.3.2 / 9.5.0 |
| Jetpack Compose (BOM) + Material 3 | 2026.02.01 |
| Room | 2.8.5, schema versão 17 (`app/schemas/`) |
| Navigation Compose / WorkManager | 2.9.8 / 2.9.0 |
| ONNX Runtime Android | 1.31.0 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 |
| Java alvo | 11 |
| Testes | JUnit, Robolectric 4.16, Room testing, Compose UI test |

Integrações Google: Sign-In e Credential Manager, Drive API (AppData) e Calendar. A IA usa um MiniLM-L12 multilíngue quantizado (int8, ONNX) para vetorizar textos, só em `arm64-v8a`; os modelos e tabelas são gerados pelos scripts de `ia/`.

## Arquitetura

MVVM com Room e Compose. Pacote base: `com.example.financacelular`.

```
app/src/main/java/com/example/financacelular/
├── MainActivity.kt
├── data/       entidades, DAOs, AppDatabase e migrações, FinancaRepository,
│               backup (Drive), calendário, atualização do app
├── ui/         uma *Screen + *ViewModel por tela, AppNavigation, tema
├── deteccao/   NotificacaoListener e leitura das notificações dos bancos
├── ia/         IA Financeira: vetorizador ONNX, classificador de categoria,
│               Isolation Forest, recomendador, respondedor de perguntas
└── worker/     AutoBackupWorker, LembreteFaturaWorker
```

Fluxo de dados: a tela (Compose) observa o estado do ViewModel; o ViewModel lê e grava pelo `FinancaRepository`, que usa os DAOs do Room. Valores em dinheiro são guardados como `Long` em centavos. A regra de quando um lançamento conta (por exemplo, compra no cartão só com a fatura paga) fica num lugar só, `data/RegraDeRealizacao.kt`. Na IA, os textos viram vetores no aparelho (cache na tabela `ia_vetor`) e o classificador é treinado com os lançamentos da própria pessoa; qualquer falha da IA vira "sem sugestão".

Os scripts Python que geram o modelo, os arquivos de teste e as métricas estão em `ia/` (veja `ia/README.md`).

## Como compilar e rodar

Requisitos: Android Studio recente com Android SDK 36 e 37, e JDK 17 ou superior (o CI usa JDK 17; o desenvolvimento local usa JDK 21).

```
git clone https://github.com/BrenaoRM/MotApp.git
cd MotApp
./gradlew assembleDebug            # APK em app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest   # testes de unidade
./gradlew lintDebug                # lint (o CI roda os dois)
```

No Windows use `gradlew.bat`. Também dá para abrir a pasta no Android Studio e rodar em um aparelho arm64 ou em um emulador arm64 (o ONNX Runtime só está empacotado para `arm64-v8a`; em outras ABIs a IA se desliga sozinha). O login com Google exige cadastrar o SHA-1 da chave de debug no projeto do Google Cloud.

Ao subir a versão do banco, o build gera o novo `app/schemas/.../<versão>.json`; versione o arquivo e adicione a migração em `MigracaoBancoTest`.

## Releases e CI

O workflow `.github/workflows/build_apk.yml` roda no GitHub Actions:

- **Pull request para `master`/`main`:** só testes de unidade e lint.
- **Push em `master`/`main`:** lê a versão do título do commit mais recente do push (`^[0-9]+(\.[0-9]+)+`, por exemplo `1.21 - descrição`), falha se não houver número ou se a tag `v<versão>` já existir, roda testes e lint, compila o `assembleRelease` assinado e publica um GitHub Release `v<versão>` com o `MotApp.apk`. As notas do release são os títulos dos commits desde a tag anterior.

A chave de assinatura e suas senhas são secrets do repositório no GitHub Actions. Todas as releases precisam da mesma chave, senão a atualização pelo próprio app falha. O `versionCode` deriva do `versionName` (`maior*10000 + menor*100 + correção`) e nunca pode diminuir.

## Versionamento e commits

O título do commit começa com a versão: `<versão> - descrição`.

- Commit sem push usa `1.0.N` (`1.0.169`, `1.0.170`...).
- O commit mais recente de um push leva o número de release `1.N` (`1.20`, `1.21`...), porque o CI publica a tag a partir dele.

## Em desenvolvimento

IA avançada com LLM local, rodando no aparelho e treinada no PC (branch `ia-llm`).
