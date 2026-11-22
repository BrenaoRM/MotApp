# Aviso no topo — design

Data: 2026-11-22 · Protótipo aprovado: https://claude.ai/artifact/PsbMhanzc3RxHer3TyiUPp (estilo B)

## Por quê
O feedback de ações ("Fatura paga e descontada do saldo!", resultado de backup etc.) usa o Snackbar padrão do Material, embaixo da tela. O usuário quer algo mais moderno e bonito, descendo do topo, e com textos curtos.

## Objetivos
- Trocar o Snackbar global por um aviso que desce do topo (estilo B do protótipo).
- Diferenciar sucesso (✓ violeta) de erro (! coral, borda e barra coral).
- Encurtar todas as mensagens: no máximo ~1 linha, sem "com sucesso" nem "!" (o ícone já diz que deu certo).

## Fora do escopo
- Botão de ação no aviso (ex.: "Desfazer").
- Fila de avisos: o novo substitui o anterior.
- Mudar a lógica das telas; só muda o texto e a marcação de erro.

## Abordagens consideradas
1. **Estilo único (A):** muda só o host, sem tocar nas chamadas. Descartado: erro parece igual a sucesso.
2. **Ícone e cor, adivinhando pelo texto:** detecta "Erro"/"Não foi possível". Descartado: frágil.
3. **Ícone e cor com marcação explícita (B), escolhido:** `mensagem(texto, erro = true)` nas chamadas de erro.

## Design
- **Componente:** `AvisoNoTopo` em arquivo novo `ui/AvisoDePagina.kt`, desenhado no `Box` raiz do `AppNavigation` (acima de tudo, inclusive sheets), alinhado ao topo com `statusBarsPadding()`.
- **Visual:** cartão `cores` do tema (fundo do cartão + borda), cantos 20dp, sombra leve; círculo de 30dp com ícone (Check em violeta `RoxoHero` / `PriorityHigh` em coral) sobre fundo da mesma cor bem translúcido; texto `bodyMedium` Medium, até 3 linhas; barra de 2dp embaixo encolhendo durante o tempo de exibição.
- **Comportamento:** entra com slide de cima + mola leve; some após 3 s (sucesso) ou 5 s (erro); arrastar para cima fecha; novo aviso substitui o atual. Funciona nos temas Claro, Escuro e Sistema.
- **API:** `LocalMensagem` passa a ser `(String, Boolean) -> Unit` com `erro = false` como padrão, por meio de uma função de extensão, para as chamadas atuais de sucesso continuarem iguais. A tela de boas-vindas usa o mesmo componente (substitui o segundo `SnackbarHost`).
- **Backup:** `ConfiguracoesViewModel` ganha `statusBackupErro: Boolean` ao lado de `statusBackupMessage`; o `LaunchedEffect` de `ConfiguracoesScreen` repassa esse valor.
- **Textos (sugestão; o usuário pode ajustar):**

| Hoje | Novo |
|---|---|
| Fatura paga e descontada do saldo! | Fatura paga |
| Pagamento cancelado com sucesso. | Pagamento cancelado |
| Transação salva com sucesso! | Transação salva |
| Cartão salvo com sucesso! | Cartão salvo |
| Aporte guardado com sucesso! / Resgate efetuado com sucesso! / Aporte removido! | Aporte guardado / Resgate feito / Aporte removido |
| Afazer atualizado! | Afazer atualizado |
| Backup enviado com sucesso! / Conta ligada com sucesso! | Backup enviado / Conta conectada |
| Preencha todos os campos corretamente (dias de 1 a 31). | Dias devem ser de 1 a 31 |
| Valor superior ao saldo do ativo! | Valor maior que o saldo |
| O backup restaurado não abriu neste app. Seus dados anteriores foram recuperados e a conta foi desconectada. | Backup não abriu. Dados mantidos |
| Nenhuma conta Google encontrada. Adicione uma conta nas Configurações do celular. | Adicione uma conta Google no celular |
| Erro no login: … / Login cancelado pelo usuário. | Não foi possível entrar / Login cancelado |
| Não foi possível acessar o Google Drive. Nada foi alterado. / … Seus dados foram mantidos. | Sem acesso ao Google Drive |
| Não foi possível preparar o backup agora. Tente de novo. / Erro ao enviar o backup. Nada foi alterado. / Erro ao enviar o arquivo para a nuvem. | Falha ao enviar o backup |
| O backup da nuvem está inválido ou é de uma versão do app que não dá para abrir. Nada foi alterado. | Backup da nuvem incompatível |
| Erro ao restaurar o backup. Seus dados foram mantidos. | Falha ao restaurar o backup |
| Não foi possível revogar a permissão agora. Verifique a internet e tente de novo, ou desconecte mesmo assim tocando outra vez. | Sem internet. Toque de novo para desconectar |
| Permissão do Google Drive negada: … / não concedida. / Não foi possível obter permissão… | Permissão do Drive negada |
| Erro de autorização do Drive: … / Tente de novo. | Falha na autorização do Drive |
| O Google autorizou outra conta. Tente de novo com {conta}. | Use a conta {conta} |
| Nenhum backup encontrado no Google Drive. / Erro: Nenhuma conta Google conectada. | Nenhum backup no Drive / Nenhuma conta conectada |
| Afazer salvo com sucesso! / Salvo no Google Agenda! | Afazer salvo / Salvo no Google Agenda |
| Evento atualizado! / Evento excluído. | Evento atualizado / Evento excluído |
| Salvo só no app: sem permissão para usar o Google Agenda. *(erro)* | Salvo só no app: sem acesso à Agenda |
| Salvo só no app: conecte sua conta Google (a mesma do Drive) para usar o Google Agenda. *(erro)* | Salvo só no app: conecte sua conta Google |
| Salvo só no app: não foi possível criar o evento no Google Agenda. *(erro)* | Salvo só no app: falha na Agenda |
| Sem permissão para alterar o Google Agenda. *(erro)* | Sem acesso à Agenda |
| Nenhum calendário da conta Google conectada. *(erro)* | Nenhum calendário conectado |
| Não foi possível alterar o evento no Google Agenda. *(erro)* | Falha ao alterar na Agenda |
| Evento recorrente: edite pelo app Google Agenda. *(erro)* | Edite pelo app Google Agenda |
| Este calendário é somente leitura. *(erro)* | Calendário só de leitura |
| Informe o valor. *(erro)* | Informe o valor |
| Cadastre e selecione um cartão para lançar no crédito. *(erro)* | Escolha um cartão |
| O cartão selecionado não foi encontrado. Escolha outro cartão. *(erro)* | Cartão não encontrado |
| Escolha uma categoria. *(erro)* | Escolha uma categoria |
| Cartão excluído. / Categoria salva. / excluída. / criada. / Lançamentos movidos para X. | mesmo texto, sem o ponto final |

Os erros de Nova Transação vêm de `NovaTransacaoViewModel.salvar(aoErro)` e também aparecem no popup de transação detectada (`PopupDeTransacaoUi.kt`, texto na própria tela); o texto curto vale para os dois.
Os avisos da Agenda passam por `CalendarioScreen` (o `when` de `resultado` na linha ~200, `mensagemEdicao()` e o aviso de evento somente leitura).

Detalhes técnicos de exceção (`e.localizedMessage`) saem do aviso e vão só para o `Log`.

## Fica a cargo de quem implementar
- Curva exata da animação e valores de sombra, desde que fiquem próximos do protótipo.
- Ícones Material equivalentes ao ✓ e ao !.
- O texto de status do backup que já aparece na própria tela (Configurações e Boas-vindas) continua; ele passa a mostrar o texto curto.
