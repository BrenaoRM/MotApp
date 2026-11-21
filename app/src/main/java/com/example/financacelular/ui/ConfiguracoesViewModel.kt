package com.example.financacelular.ui

import android.accounts.Account
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.BackupManager
import com.example.financacelular.data.ContaDrive
import com.example.financacelular.data.RestauroBanco
import com.example.financacelular.data.GoogleDriveService
import com.example.financacelular.data.TemaPreferencia
import com.example.financacelular.deteccao.BancosMonitorados
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val ESCOPO_DRIVE = "https://www.googleapis.com/auth/drive.file"
private const val MENSAGEM_EXPORT_FALHOU = "Não foi possível preparar o backup agora. Tente de novo."

private enum class AcaoPendenteDrive { BACKUP, RESTAURAR, SINCRONIZAR }

/** Conta nova que já tem backup no Drive enquanto este aparelho também tem dados: o usuário escolhe qual vale. */
class EscolhaBackup(val conta: String, val token: String, val arquivo: File)

class ConfiguracoesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("financacelular_prefs", Context.MODE_PRIVATE)
    /** E-mail da conta, data do último backup e pendências do Drive: fora do backup do Android (ver ContaDrive). */
    private val contaPrefs: SharedPreferences = ContaDrive.prefs(application)

    var temaAtual: TemaPreferencia by mutableStateOf(TemaPreferencia.SISTEMA)
        private set
    var estaLogadoGoogle: Boolean by mutableStateOf(false)
        private set
    var statusBackupMessage: String by mutableStateOf("")
        private set
    var emailUtilizador: String by mutableStateOf("")
        private set
    var completouBoasVindas: Boolean by mutableStateOf(false)
        private set
    var ultimaDataBackup: String by mutableStateOf("Nunca")
        private set
    var pedidoAutorizacaoDrive: IntentSenderRequest? by mutableStateOf(null)
        private set
    var nomeUtilizador: String by mutableStateOf("")
        private set
    var escolhaBackup: EscolhaBackup? by mutableStateOf(null)
        private set

    var bancosAtivos: Set<String> by mutableStateOf(BancosMonitorados.ativados(prefs))
        private set

    fun alternarBanco(pacote: String, ativo: Boolean) {
        BancosMonitorados.definirAtivo(prefs, pacote, ativo)
        bancosAtivos = BancosMonitorados.ativados(prefs)
    }

    /** O cartão "Detectar gastos" do Início foi dispensado, ou a detecção foi tratada no onboarding. */
    var dicaDeteccaoDispensada: Boolean by mutableStateOf(prefs.getBoolean("dica_deteccao_dispensada", false))
        private set

    /** Passo de onboarding da detecção, mostrado uma vez logo depois das boas-vindas (não é salvo: no restart vale o cartão). */
    var mostrarPassoDeteccao: Boolean by mutableStateOf(false)
        private set

    fun dispensarDicaDeteccao() {
        prefs.edit { putBoolean("dica_deteccao_dispensada", true) }
        dicaDeteccaoDispensada = true
    }

    fun concluirPassoDeteccao() {
        dispensarDicaDeteccao()
        mostrarPassoDeteccao = false
    }

    private var acaoPendente: AcaoPendenteDrive? = null
    /** Conta para a qual o consentimento do Drive foi pedido (ainda não gravada como conectada). */
    private var contaPendente: String? = null
    /** Há conectar/trocar/desconectar em andamento. */
    private var processando = false
    /** A tela de consentimento do Google está aberta: a trava só solta quando o resultado chegar. */
    private var aguardandoConsentimento = false
    /** A última tentativa de desconectar falhou ao revogar; o próximo toque desconecta mesmo assim. */
    private var desconexaoForcavel = false

    init {
        val context = application.applicationContext
        val emailSalvo = contaPrefs.getString(ContaDrive.EMAIL, "") ?: ""
        val dbExiste = context.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO).exists()
        val nomeSalvo = prefs.getString("nome_utilizador", "") ?: ""
        nomeUtilizador = nomeSalvo

        if (emailSalvo.isNotBlank()) {
            emailUtilizador = emailSalvo
            estaLogadoGoogle = true
            prefs.edit { putBoolean("completou_boas_vindas", true) }
        }

        val completouSalvo = prefs.getBoolean("completou_boas_vindas", false)
        completouBoasVindas = completouSalvo && (emailSalvo.isNotBlank() || dbExiste)

        ultimaDataBackup = contaPrefs.getString(ContaDrive.ULTIMA_DATA_BACKUP, "Nunca") ?: "Nunca"

        if (contaPrefs.getBoolean(ContaDrive.RESTAURO_REVERTIDO, false)) {
            contaPrefs.edit { putBoolean(ContaDrive.RESTAURO_REVERTIDO, false) }
            statusBackupMessage = "O backup restaurado não abriu neste app. Seus dados anteriores foram recuperados e a conta foi desconectada."
        }
        revogarPendentes()

        val temaSalvo = prefs.getString("tema", TemaPreferencia.SISTEMA.name)
        temaAtual = try {
            // O antigo AMOLED virou o Escuro
            TemaPreferencia.valueOf(if (temaSalvo == "AMOLED") TemaPreferencia.ESCURO.name else temaSalvo ?: TemaPreferencia.SISTEMA.name)
        } catch (_: Exception) {
            TemaPreferencia.SISTEMA
        }
    }

    fun definirTema(tema: TemaPreferencia) {
        temaAtual = tema
        prefs.edit { putString("tema", tema.name) }
    }

    fun marcarBoasVindasComoConcluida() {
        if (!completouBoasVindas && !dicaDeteccaoDispensada) mostrarPassoDeteccao = true
        prefs.edit { putBoolean("completou_boas_vindas", true) }
        completouBoasVindas = true
    }

    fun limparPedidoAutorizacao() {
        pedidoAutorizacaoDrive = null
    }

    private fun registarBackupRealizado() {
        val formato = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
        val dataFormatada = formato.format(Date())
        ultimaDataBackup = dataFormatada
        contaPrefs.edit { putString(ContaDrive.ULTIMA_DATA_BACKUP, dataFormatada) }
    }

    fun iniciarLoginGoogle(context: Context, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("264232686854-22i568l7q1nop0tfr72i1vks9e699g0c.apps.googleusercontent.com")
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val credentialManager = CredentialManager.create(context)
                val result = credentialManager.getCredential(context, request)

                tratarResultadoLogin(result, onResult)
            } catch (_: androidx.credentials.exceptions.GetCredentialCancellationException) {
                statusBackupMessage = "Login cancelado pelo usuário."
                onResult(false, null)
            } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                e.printStackTrace()
                statusBackupMessage = "Nenhuma conta Google encontrada. Adicione uma conta nas Configurações do celular."
                onResult(false, null)
            } catch (e: Exception) {
                e.printStackTrace()
                statusBackupMessage = "Erro no login: ${e.localizedMessage ?: "Desconhecido"}"
                onResult(false, null)
            }
        }
    }

    private fun tratarResultadoLogin(result: GetCredentialResponse, onResult: (Boolean, String?) -> Unit) {
        val credential = result.credential

        // A conta só é gravada como conectada depois que o Drive dela for autorizado (ver confirmarConta)
        val emailObtido = if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                GoogleIdTokenCredential.createFrom(credential.data).id
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        if (emailObtido.isNullOrBlank()) {
            statusBackupMessage = "Não foi possível identificar a conta Google."
            onResult(false, null)
        } else {
            onResult(true, emailObtido)
        }
    }

    /**
     * Roda uma ação de Drive com a trava de conta. Se ela pediu consentimento ao usuário, a trava
     * continua ligada até aoReceberResultadoAutorizacaoDrive terminar o fluxo.
     */
    private fun comTrava(bloco: suspend () -> Unit) {
        if (processando || escolhaBackup != null) return
        processando = true
        viewModelScope.launch {
            try {
                bloco()
            } finally {
                if (!aguardandoConsentimento) processando = false
            }
        }
    }

    fun realizarBackupNaNuvem(activity: Activity) = comTrava {
        statusBackupMessage = "Preparando o backup local..."
        val context = getApplication<Application>()
        val ficheiroBackup = BackupManager.exportarBaseDeDadosParaFicheiro(context)

        if (ficheiroBackup == null) {
            statusBackupMessage = MENSAGEM_EXPORT_FALHOU
            return@comTrava
        }

        val accessToken = obterAccessTokenDrive(activity, AcaoPendenteDrive.BACKUP)
        if (accessToken == null) {
            ficheiroBackup.delete()
            return@comTrava
        }
        continuarBackupComToken(accessToken, ficheiroBackup)
    }

    fun sincronizarOuEntrarGoogle(activity: Activity) {
        // Um fluxo de conta por vez (toque duplo, sheet de escolha aberto, desconexão em andamento)
        if (processando || escolhaBackup != null) return
        processando = true
        iniciarLoginGoogle(activity) { sucessoLogin, conta ->
            if (!sucessoLogin || conta == null) {
                processando = false
                return@iniciarLoginGoogle
            }
            if (conta.equals(emailUtilizador, ignoreCase = true)) {
                statusBackupMessage = "Essa conta já está conectada."
                processando = false
                return@iniciarLoginGoogle
            }
            viewModelScope.launch {
                try {
                    statusBackupMessage = "Verificando o Google Drive..."
                    val accessToken = obterAccessTokenDrive(activity, AcaoPendenteDrive.SINCRONIZAR, conta) ?: return@launch
                    executarFluxoInteligenteDrive(accessToken, conta)
                } finally {
                    if (!aguardandoConsentimento) processando = false
                }
            }
        }
    }

    /**
     * Conecta [conta] sem apagar nada antes da hora: o backup dela é baixado para um arquivo
     * temporário e, se este aparelho também tem dados, quem decide é o usuário (EscolhaBackup).
     */
    private suspend fun executarFluxoInteligenteDrive(accessToken: String, conta: String) {
        statusBackupMessage = "Procurando backup na nuvem..."
        val context = getApplication<Application>()
        val temporario = File.createTempFile("backup_nuvem_", ".db", context.cacheDir)

        when (GoogleDriveService.baixarBackup(accessToken, temporario)) {
            GoogleDriveService.ResultadoDownload.ERRO -> {
                temporario.delete()
                statusBackupMessage = "Não foi possível acessar o Google Drive. Nada foi alterado."
                abandonarConta(conta)
            }
            GoogleDriveService.ResultadoDownload.SEM_BACKUP -> {
                temporario.delete()
                conectarEnviandoDadosDoAparelho(accessToken, conta)
            }
            GoogleDriveService.ResultadoDownload.BAIXADO -> {
                if (temDadosLocais(context)) {
                    statusBackupMessage = ""
                    escolhaBackup = EscolhaBackup(conta, accessToken, temporario)
                } else {
                    aplicarBackupDaNuvem(conta, temporario)
                }
            }
        }
    }

    /**
     * O banco existe desde a primeira abertura; "tem dados" é qualquer tabela do usuário com linhas.
     * `categorias` fica de fora porque o app semeia as padrão sozinho.
     */
    private suspend fun temDadosLocais(context: Context): Boolean = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO)
        if (!dbFile.exists()) return@withContext false
        try {
            val db = AppDatabase.getInstance(context).openHelper.readableDatabase
            val tabelas = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT IN ('android_metadata', 'room_master_table', 'categorias')"
            ).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }
            tabelas.any { tabela ->
                db.query("SELECT COUNT(*) FROM `$tabela`").use { it.moveToFirst() && it.getInt(0) > 0 }
            }
        } catch (_: Exception) {
            true // na dúvida, pergunta em vez de sobrescrever
        }
    }

    private suspend fun conectarEnviandoDadosDoAparelho(accessToken: String, conta: String) {
        val context = getApplication<Application>()
        statusBackupMessage = "A configurar backup automático..."
        val bancoExiste = context.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO).exists()
        val ficheiroBackup = BackupManager.exportarBaseDeDadosParaFicheiro(context)
        if (bancoExiste && ficheiroBackup == null) {
            statusBackupMessage = "$MENSAGEM_EXPORT_FALHOU Nada foi alterado."
            abandonarConta(conta)
            return
        }
        if (ficheiroBackup != null) {
            val enviou = try {
                GoogleDriveService.uploadBackup(accessToken, ficheiroBackup)
            } finally {
                ficheiroBackup.delete()
            }
            if (!enviou) {
                statusBackupMessage = "Erro ao enviar o backup. Nada foi alterado."
                abandonarConta(conta)
                return
            }
        }
        confirmarConta(conta)
        if (ficheiroBackup != null) registarBackupRealizado()
        marcarBoasVindasComoConcluida()
        statusBackupMessage = "Conta ligada com sucesso!"
    }

    /**
     * Troca o banco local pelo backup baixado (validação, troca sob trava e `.bak` ficam em
     * [RestauroBanco]). Nada de rede aqui: o app reinicia logo em seguida e a revogação da conta
     * antiga fica para depois do reinício (revogarPendentes).
     */
    private suspend fun aplicarBackupDaNuvem(conta: String, baixado: File) {
        val context = getApplication<Application>()
        statusBackupMessage = "Backup encontrado! Restaurando dados..."

        when (withContext(Dispatchers.IO) { RestauroBanco.restaurar(context, baixado) }) {
            RestauroBanco.Resultado.INVALIDO -> {
                statusBackupMessage = "O backup da nuvem está inválido ou é de uma versão do app que não dá para abrir. Nada foi alterado."
                abandonarConta(conta)
            }
            RestauroBanco.Resultado.FALHOU -> {
                statusBackupMessage = "Erro ao restaurar o backup. Seus dados foram mantidos."
                abandonarConta(conta)
            }
            RestauroBanco.Resultado.OK -> {
                confirmarConta(conta, revogarAnteriorAgora = false)
                registarBackupRealizado()
                marcarBoasVindasComoConcluida()
                reiniciarApp(context)
            }
        }
    }

    /**
     * Grava [conta] como a conectada. A gravação vem antes da revogação da antiga (que depende de
     * rede), para o app nunca ficar com o banco de uma conta e o e-mail de outra. Revogação que
     * falha (ou adiada) vai para a lista de pendentes e é tentada de novo ao abrir o app.
     */
    private suspend fun confirmarConta(conta: String, revogarAnteriorAgora: Boolean = true) {
        val anterior = emailUtilizador
        val mudou = !anterior.equals(conta, ignoreCase = true)
        contaPrefs.edit(commit = true) {
            putString(ContaDrive.EMAIL, conta)
            if (mudou) remove(ContaDrive.ULTIMA_DATA_BACKUP)
        }
        // A conta que acabou de ser adotada não pode ficar na lista de revogações pendentes
        removerRevogacaoPendente(conta)
        emailUtilizador = conta
        estaLogadoGoogle = true
        if (mudou) ultimaDataBackup = "Nunca"
        if (mudou && anterior.isNotBlank()) {
            if (revogarAnteriorAgora) revogarOuAdiar(anterior) else adiarRevogacao(anterior)
        }
    }

    /** A conta nova não foi adotada: tira a permissão que acabou de ganhar (a conectada atual não mexe). */
    private suspend fun abandonarConta(conta: String) {
        if (!conta.equals(emailUtilizador, ignoreCase = true)) revogarOuAdiar(conta)
    }

    private suspend fun revogarOuAdiar(conta: String) {
        if (!revogarAcessoDrive(getApplication(), conta)) adiarRevogacao(conta)
    }

    private fun adiarRevogacao(conta: String) {
        val pendentes = contaPrefs.getStringSet(ContaDrive.CONTAS_A_REVOGAR, emptySet()).orEmpty().toMutableSet()
        pendentes.add(conta)
        contaPrefs.edit(commit = true) { putStringSet(ContaDrive.CONTAS_A_REVOGAR, pendentes) }
    }

    /** Ao abrir o app: tenta de novo as revogações que falharam ou ficaram para depois do reinício. */
    private fun revogarPendentes() {
        val pendentes = contaPrefs.getStringSet(ContaDrive.CONTAS_A_REVOGAR, emptySet()).orEmpty().toSet()
        if (pendentes.isEmpty()) return
        viewModelScope.launch {
            for (conta in pendentes) {
                // Se a pessoa reconectou essa conta nesse meio-tempo, a permissão é dela de novo
                // Relê a lista: a conta pode ter sido adotada (e tirada da lista) enquanto este laço rodava
                val aindaPendente = conta in contaPrefs.getStringSet(ContaDrive.CONTAS_A_REVOGAR, emptySet()).orEmpty()
                if (!aindaPendente) continue
                if (conta.equals(emailUtilizador, ignoreCase = true) || revogarAcessoDrive(getApplication(), conta)) {
                    removerRevogacaoPendente(conta)
                }
            }
        }
    }

    private fun removerRevogacaoPendente(conta: String) {
        val restantes = contaPrefs.getStringSet(ContaDrive.CONTAS_A_REVOGAR, emptySet()).orEmpty().toMutableSet()
        if (restantes.remove(conta)) {
            contaPrefs.edit(commit = true) { putStringSet(ContaDrive.CONTAS_A_REVOGAR, restantes) }
        }
    }

    fun manterDadosDoAparelho() {
        val escolha = escolhaBackup ?: return
        escolhaBackup = null
        processando = true
        viewModelScope.launch {
            try {
                escolha.arquivo.delete()
                conectarEnviandoDadosDoAparelho(escolha.token, escolha.conta)
            } finally {
                processando = false
            }
        }
    }

    fun restaurarBackupDaContaNova() {
        val escolha = escolhaBackup ?: return
        escolhaBackup = null
        processando = true
        viewModelScope.launch {
            try {
                aplicarBackupDaNuvem(escolha.conta, escolha.arquivo)
            } finally {
                processando = false
            }
        }
    }

    fun cancelarEscolhaBackup() {
        val escolha = escolhaBackup ?: return
        escolhaBackup = null
        escolha.arquivo.delete()
        statusBackupMessage = "Nada foi alterado."
        viewModelScope.launch { abandonarConta(escolha.conta) }
    }

    /**
     * Desconecta a conta: tira a permissão no Google e só então limpa o app. Os dados do aparelho
     * e o backup que já está no Drive não são tocados. Sem rede, nada muda.
     */
    fun desconectarGoogle() {
        val conta = emailUtilizador
        if (conta.isBlank() || processando) return
        processando = true
        viewModelScope.launch {
            try {
                statusBackupMessage = "A desconectar..."
                val context = getApplication<Application>()
                // Se a permissão já foi retirada pela conta Google, não há o que revogar e o app não pode ficar preso
                val revogada = revogarAcessoDrive(context, conta) || permissaoJaRetirada(context, conta)
                var permissaoOrfa = false
                if (!revogada) {
                    if (!desconexaoForcavel) {
                        desconexaoForcavel = true
                        statusBackupMessage = "Não foi possível revogar a permissão agora. Verifique a internet e tente de novo, ou desconecte mesmo assim tocando outra vez."
                        return@launch
                    }
                    permissaoOrfa = true
                }
                desconexaoForcavel = false
                try {
                    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                } catch (_: Exception) {
                }
                contaPrefs.edit(commit = true) {
                    remove(ContaDrive.EMAIL)
                    remove(ContaDrive.ULTIMA_DATA_BACKUP)
                }
                if (permissaoOrfa) adiarRevogacao(conta)
                emailUtilizador = ""
                estaLogadoGoogle = false
                ultimaDataBackup = "Nunca"
                statusBackupMessage = if (permissaoOrfa) {
                    "Desconectado. A permissão será retirada do Google assim que houver conexão."
                } else {
                    "Conta desconectada. Seus dados continuam neste aparelho."
                }
            } finally {
                processando = false
            }
        }
    }

    private fun pedidoDrive(conta: String): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(ESCOPO_DRIVE)))
            .setAccount(Account(conta, "com.google"))
            .build()

    private suspend fun revogarAcessoDrive(context: Context, conta: String): Boolean = try {
        val pedido = RevokeAccessRequest.builder()
            .setAccount(Account(conta, "com.google"))
            .setScopes(listOf(Scope(ESCOPO_DRIVE)))
            .build()
        suspendCancellableCoroutine<Unit> { continuacao ->
            Identity.getAuthorizationClient(context)
                .revokeAccess(pedido)
                .addOnSuccessListener { continuacao.resume(Unit) }
                .addOnFailureListener { continuacao.resumeWithException(it) }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    /** true só se o Google confirmar que o app não tem mais permissão (pede consentimento de novo). */
    private suspend fun permissaoJaRetirada(context: Context, conta: String): Boolean = try {
        val resultado = suspendCancellableCoroutine<AuthorizationResult> { continuacao ->
            Identity.getAuthorizationClient(context)
                .authorize(pedidoDrive(conta))
                .addOnSuccessListener { continuacao.resume(it) }
                .addOnFailureListener { continuacao.resumeWithException(it) }
        }
        resultado.hasResolution()
    } catch (_: Exception) {
        false
    }

    /**
     * O token precisa ser da conta pedida: confere pelo resultado do Google e, se ele não traz
     * e-mail, pelo dono do token no Drive. Se nada diz de quem é, vale o que foi fixado em setAccount.
     */
    private suspend fun donoDoToken(resultado: AuthorizationResult): String? =
        resultado.toGoogleSignInAccount()?.email
            ?: resultado.accessToken?.let { GoogleDriveService.emailDoToken(it) }

    private fun contaConfere(dono: String?, conta: String): Boolean =
        dono == null || normalizarEmail(dono) == normalizarEmail(conta)

    /** googlemail.com é o mesmo endereço que gmail.com: não pode virar "conta diferente" (e revogar a própria conta). */
    private fun normalizarEmail(email: String): String =
        email.trim().lowercase().replace("@googlemail.com", "@gmail.com")

    fun restaurarBackupDaNuvem(activity: Activity) = comTrava {
        statusBackupMessage = "A contactar o Google Drive..."
        val accessToken = obterAccessTokenDrive(activity, AcaoPendenteDrive.RESTAURAR) ?: return@comTrava
        continuarRestauroComToken(accessToken)
    }

    fun definirNomeUtilizador(nome: String) {
        nomeUtilizador = nome.trim()
        prefs.edit { putString("nome_utilizador", nomeUtilizador) }
    }

    fun aoReceberResultadoAutorizacaoDrive(activity: Activity, dataIntent: Intent?) {
        pedidoAutorizacaoDrive = null
        val acao = acaoPendente
        acaoPendente = null
        val conta = contaPendente ?: emailUtilizador
        contaPendente = null
        aguardandoConsentimento = false

        if (dataIntent == null || acao == null || conta.isBlank()) {
            statusBackupMessage = "Permissão do Google Drive não concedida."
            processando = false
            return
        }

        viewModelScope.launch {
            try {
                val resultado = Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(dataIntent)

                val token = resultado.accessToken
                if (token == null) {
                    statusBackupMessage = "Não foi possível obter permissão do Google Drive."
                    return@launch
                }
                val dono = donoDoToken(resultado)
                if (!contaConfere(dono, conta)) {
                    statusBackupMessage = "O Google autorizou outra conta. Tente de novo com $conta."
                    dono?.let { abandonarConta(it) }
                    return@launch
                }

                when (acao) {
                    AcaoPendenteDrive.BACKUP -> {
                        val context = getApplication<Application>()
                        val ficheiroBackup = BackupManager.exportarBaseDeDadosParaFicheiro(context)
                        if (ficheiroBackup == null) {
                            statusBackupMessage = MENSAGEM_EXPORT_FALHOU
                        } else {
                            continuarBackupComToken(token, ficheiroBackup)
                        }
                    }
                    AcaoPendenteDrive.RESTAURAR -> continuarRestauroComToken(token)
                    AcaoPendenteDrive.SINCRONIZAR -> executarFluxoInteligenteDrive(token, conta)
                }
            } catch (e: ApiException) {
                e.printStackTrace()
                statusBackupMessage = "Permissão do Google Drive negada: ${e.localizedMessage}"
            } finally {
                // Se o fluxo abriu o sheet de escolha, escolhaBackup já impede outra ação
                processando = false
            }
        }
    }

    private suspend fun continuarBackupComToken(accessToken: String, ficheiroBackup: File) {
        statusBackupMessage = "Enviando para o Google Drive..."
        val sucesso = try {
            GoogleDriveService.uploadBackup(accessToken, ficheiroBackup)
        } finally {
            ficheiroBackup.delete()
        }
        if (sucesso) {
            registarBackupRealizado()
            marcarBoasVindasComoConcluida()
            statusBackupMessage = "Backup enviado com sucesso!"
        } else {
            statusBackupMessage = "Erro ao enviar o arquivo para a nuvem."
        }
    }

    private suspend fun continuarRestauroComToken(accessToken: String) {
        statusBackupMessage = "Baixando backup da nuvem..."
        val context = getApplication<Application>()
        // Baixa para um temporário: o banco local só é trocado depois de validar o arquivo
        val temporario = File.createTempFile("backup_nuvem_", ".db", context.cacheDir)

        when (GoogleDriveService.baixarBackup(accessToken, temporario)) {
            GoogleDriveService.ResultadoDownload.BAIXADO -> aplicarBackupDaNuvem(emailUtilizador, temporario)
            GoogleDriveService.ResultadoDownload.SEM_BACKUP -> {
                temporario.delete()
                statusBackupMessage = "Nenhum backup encontrado no Google Drive."
            }
            GoogleDriveService.ResultadoDownload.ERRO -> {
                temporario.delete()
                statusBackupMessage = "Não foi possível acessar o Google Drive. Seus dados foram mantidos."
            }
        }
    }

    private fun reiniciarApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
    }

    private suspend fun obterAccessTokenDrive(
        activity: Activity,
        acaoSeNaoAutorizado: AcaoPendenteDrive,
        conta: String = emailUtilizador
    ): String? {
        if (conta.isBlank()) {
            statusBackupMessage = "Erro: Nenhuma conta Google conectada."
            return null
        }

        statusBackupMessage = "A obter autorização do Google Drive..."

        return try {
            val resultado = suspendCancellableCoroutine<AuthorizationResult> { continuacao ->
                Identity.getAuthorizationClient(activity)
                    .authorize(pedidoDrive(conta))
                    .addOnSuccessListener { continuacao.resume(it) }
                    .addOnFailureListener { continuacao.resumeWithException(it) }
            }

            if (resultado.hasResolution()) {
                acaoPendente = acaoSeNaoAutorizado
                contaPendente = conta
                aguardandoConsentimento = true
                pedidoAutorizacaoDrive = IntentSenderRequest
                    .Builder(resultado.pendingIntent!!.intentSender)
                    .build()
                null
            } else {
                val dono = donoDoToken(resultado)
                if (!contaConfere(dono, conta)) {
                    statusBackupMessage = "O Google autorizou outra conta. Tente de novo com $conta."
                    dono?.let { abandonarConta(it) }
                    null
                } else {
                    resultado.accessToken
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            statusBackupMessage = "Erro de autorização do Drive: ${e.localizedMessage}"
            null
        }
    }
}