# Redesign do FinanceAPP para Estilo "Paguei"

O objetivo é transformar a interface atual do aplicativo em uma experiência intuitiva, moderna e visualmente similar ao aplicativo "Paguei" mostrado nas imagens de referência. As mudanças focarão em componentes arredondados, tipografia clara, e um fluxo de navegação centrado em um botão de ação rápida (FAB).

## User Review Required

> [!IMPORTANT]
> A navegação será alterada para incluir um botão central flutuante "+". Algumas telas atuais (como "Categorias") podem ser movidas para dentro de menus de configuração para simplificar a barra inferior.

> [!NOTE]
> O recurso de "Leitura Automática de PDF" mencionado na imagem não será implementado funcionalmente nesta fase, apenas o elemento visual será adicionado.

## Proposed Changes

### 🎨 Identidade Visual e Tematização

#### [MODIFY] [Color.kt](file:///C:/Users/breno/AndroidStudioProjects/FinanceAPP/app/src/main/java/com/example/financacelular/ui/theme/Color.kt)
*   Ajustar a paleta para tons de cinza muito suaves e brancos puros.
*   Refinar o `VerdeMenta` e `Coral` para tons mais pastéis e modernos.

#### [MODIFY] [Type.kt](file:///C:/Users/breno/AndroidStudioProjects/FinanceAPP/app/src/main/java/com/example/financacelular/ui/theme/Type.kt)
*   Atualizar a tipografia para usar fontes sem serifa com pesos variados (Bold para valores, Medium para títulos).

---

### 🧱 Componentes Reutilizáveis (UI Kit)

#### [NEW] [PagueiComponents.kt](file:///C:/Users/breno/AndroidStudioProjects/FinanceAPP/app/src/main/java/com/example/financacelular/ui/components/PagueiComponents.kt)
*   `PagueiSummaryCard`: Card principal com Projeção, Saldo, Barra de Metas e Entradas/Saídas.
*   `PagueiTransactionItem`: Item de lista com ícone circular, descrição, data, valor colorido e indicador de status.
*   `PagueiBottomNav`: Barra de navegação customizada com entalhe para o FAB central.
*   `PagueiSegmentedControl`: Para seleção entre "Entrada" e "Gasto".

---

### 📱 Telas Principais

#### [MODIFY] [DashboardScreen.kt](file:///C:/Users/breno/AndroidStudioProjects/FinanceAPP/app/src/main/java/com/example/financacelular/ui/DashboardScreen.kt)
*   Redesenhar o cabeçalho com o mês atual e ícone de configurações.
*   Implementar o `PagueiSummaryCard` com suporte a paginação (dots).
*   Implementar a seção "CONTAS DO MÊS" usando `PagueiTransactionItem`.

#### [MODIFY] [NovaTransacaoScreen.kt](file:///C:/Users/breno/AndroidStudioProjects/FinanceAPP/app/src/main/java/com/example/financacelular/ui/NovaTransacaoScreen.kt)
*   Adaptar para uma interface de "página cheia" com cabeçalho (Fechar, Salvar).
*   Incluir o botão visual de "Leitura de PDF".
*   Campos de input com design limpo e switches para "Pago" e "Repetir".

#### [NEW] [CalendarioScreen.kt](file:///C:/Users/breno/AndroidStudioProjects/FinanceAPP/app/src/main/java/com/example/financacelular/ui/CalendarioScreen.kt)
*   Criar tela de calendário com resumo de status (Pagar, Atrasado, Pago) no topo.
*   Lista de transações do dia selecionado abaixo do calendário.

---

### 🚦 Navegação e Lógica

#### [MODIFY] [AppNavigation.kt](file:///C:/Users/breno/AndroidStudioProjects/FinanceAPP/app/src/main/java/com/example/financacelular/ui/AppNavigation.kt)
*   Atualizar rotas para: Início, Análise, Calendário, Conquistas.
*   Adicionar lógica para o BottomSheet de "Adicionar" ao clicar no FAB.

#### [MODIFY] [DashboardViewModel.kt](file:///C:/Users/breno/AndroidStudioProjects/FinanceAPP/app/src/main/java/com/example/financacelular/ui/DashboardViewModel.kt)
*   Adicionar fluxo de dados para as "metas do mês" (cálculo de percentual).
*   Buscar lista de transações recentes/pendentes para a Home.

## Verification Plan

### Manual Verification
1.  **Home Screen:** Verificar se o card de resumo mostra o saldo correto e a barra de progresso das metas.
2.  **Transações:** Confirmar se os itens de transação exibem as cores corretas (Verde para entrada, Preto/Vermelho para saída).
3.  **Adicionar:** Abrir o BottomSheet através do botão "+" e verificar se as opções levam à tela de cadastro.
4.  **Cadastro:** Salvar uma nova transação e verificar se ela aparece na Home e no Extrato.
5.  **Calendário:** Navegar entre os meses e verificar se as marcações nos dias correspondem às transações.
