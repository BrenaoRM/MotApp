package com.example.financacelular.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CustomCredential
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.BackupManager
import com.example.financacelular.data.TemaPreferencia
import com.example.financacelular.data.GoogleDriveService
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val ESCOPO_DRIVE = "https://www.googleapis.com/auth/drive.file"

private enum class AcaoPendenteDrive { BACKUP, RESTAURAR, SINCRONIZAR }

class ConfiguracoesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("financacelular_prefs", Context.MODE_PRIVATE)

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

    private var acaoPendente: AcaoPendenteDrive? = null

    init {
        val context = application.applicationContext
        val emailSalvo = prefs.getString("email_utilizador", "") ?: ""
        val dbExiste = context.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO).exists()

        if (emailSalvo.isNotBlank()) {
            emailUtilizador = emailSalvo
            estaLogadoGoogle = true
            prefs.edit().putBoolean("completou_boas_vindas", true).apply()
        }

        val completouSalvo = prefs.getBoolean("completou_boas_vindas", false)
        completouBoasVindas = completouSalvo && (emailSalvo.isNotBlank() || dbExiste)

        // Carregar última data de backup guardada
        ultimaDataBackup = prefs.getString("ultima_data_backup", "Nunca") ?: "Nunca"

        // Carregar tema guardado anteriormente
        val temaSalvo = prefs.getString("tema", TemaPreferencia.SISTEMA.name)
        temaAtual = try {
            TemaPreferencia.valueOf(temaSalvo ?: TemaPreferencia.SISTEMA.name)
        } catch (_: Exception) {
            TemaPreferencia.SISTEMA
        }
    }

    fun definirTema(tema: TemaPreferencia) {
        temaAtual = tema
        prefs.edit().putString("tema", tema.name).apply()
    }

    fun marcarBoasVindasComoConcluida() {
        prefs.edit().putBoolean("completou_boas_vindas", true).apply()
        completouBoasVindas = true
    }

    private fun registarBackupRealizado() {
        val formato = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
        val dataFormatada = formato.format(Date())
        ultimaDataBackup = dataFormatada
        prefs.edit().putString("ultima_data_backup", dataFormatada).apply()
    }

    fun iniciarLoginGoogle(context: Context, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("264232686854-urrjveem9l9a6da1vnd522f6cgtie2lr.apps.googleusercontent.com")
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val credentialManager = CredentialManager.create(context)
                val result = credentialManager.getCredential(context, request)

                tratarResultadoLogin(result, onResult)
            } catch (e: Exception) {
                e.printStackTrace()
                statusBackupMessage = "Erro no login: ${e.localizedMessage ?: "Desconhecido"}"
                onResult(false, null)
            }
        }
    }

    private fun tratarResultadoLogin(result: GetCredentialResponse, onResult: (Boolean, String?) -> Unit) {
        val credential = result.credential

        val emailObtido = if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                googleIdTokenCredential.id
            } catch (_: Exception) {
                "Conta Conectada"
            }
        } else {
            "Conta Conectada"
        }

        estaLogadoGoogle = true
        emailUtilizador = emailObtido
        prefs.edit().putString("email_utilizador", emailObtido).apply()

        statusBackupMessage = "Conta ligada com sucesso!"
        onResult(true, emailObtido)
    }

    fun realizarBackupNaNuvem(activity: Activity) {
        viewModelScope.launch {
            statusBackupMessage = "A preparar o backup local..."

            val context = getApplication<Application>()
            val ficheiroBackup = BackupManager.exportarBaseDeDadosParaFicheiro(context)

            if (ficheiroBackup == null || !ficheiroBackup.exists()) {
                statusBackupMessage = "Erro: Base de dados não encontrada para backup."
                return@launch
            }

            val accessToken = obterAccessTokenDrive(activity, AcaoPendenteDrive.BACKUP) ?: return@launch
            continuarBackupComToken(accessToken, ficheiroBackup)
        }
    }

    fun sincronizarOuEntrarGoogle(activity: Activity) {
        iniciarLoginGoogle(activity) { sucessoLogin, _ ->
            if (sucessoLogin) {
                viewModelScope.launch {
                    statusBackupMessage = "A verificar o Google Drive..."
                    val accessToken = obterAccessTokenDrive(activity, AcaoPendenteDrive.SINCRONIZAR) ?: return@launch
                    executarFluxoInteligenteDrive(accessToken)
                }
            }
        }
    }

    private suspend fun executarFluxoInteligenteDrive(accessToken: String) {
        statusBackupMessage = "A procurar backup na nuvem..."
        val context = getApplication<Application>()
        val dbFile = context.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO)

        FinancaRepository.destruirInstancia()
        AppDatabase.destruirInstancia()

        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
        File(dbFile.absolutePath + "-journal").delete()
        dbFile.parentFile?.mkdirs()

        val encontrouBackup = GoogleDriveService.restaurarBackup(accessToken, dbFile)

        if (encontrouBackup) {
            statusBackupMessage = "Backup encontrado! A restaurar dados..."
            registarBackupRealizado()
            marcarBoasVindasComoConcluida()
            reiniciarApp(context)
        } else {
            statusBackupMessage = "Conta ligada! A configurar backup automático..."
            val ficheiroBackup = BackupManager.exportarBaseDeDadosParaFicheiro(context)
            if (ficheiroBackup != null && ficheiroBackup.exists()) {
                GoogleDriveService.uploadBackup(accessToken, ficheiroBackup)
            }
            registarBackupRealizado()
            marcarBoasVindasComoConcluida()
            completouBoasVindas = true
        }
    }

    fun restaurarBackupDaNuvem(activity: Activity) {
        viewModelScope.launch {
            statusBackupMessage = "A contactar o Google Drive..."
            val accessToken = obterAccessTokenDrive(activity, AcaoPendenteDrive.RESTAURAR) ?: return@launch
            continuarRestauroComToken(accessToken)
        }
    }

    fun aoReceberResultadoAutorizacaoDrive(activity: Activity, dataIntent: Intent?) {
        pedidoAutorizacaoDrive = null
        val acao = acaoPendente
        acaoPendente = null

        if (dataIntent == null || acao == null) {
            statusBackupMessage = "Permissão do Google Drive não concedida."
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

                when (acao) {
                    AcaoPendenteDrive.BACKUP -> {
                        val context = getApplication<Application>()
                        val ficheiroBackup = BackupManager.exportarBaseDeDadosParaFicheiro(context)
                        if (ficheiroBackup == null || !ficheiroBackup.exists()) {
                            statusBackupMessage = "Erro: Base de dados não encontrada para backup."
                        } else {
                            continuarBackupComToken(token, ficheiroBackup)
                        }
                    }
                    AcaoPendenteDrive.RESTAURAR -> continuarRestauroComToken(token)
                    AcaoPendenteDrive.SINCRONIZAR -> executarFluxoInteligenteDrive(token)
                }
            } catch (e: ApiException) {
                e.printStackTrace()
                statusBackupMessage = "Permissão do Google Drive negada: ${e.localizedMessage}"
            }
        }
    }

    private suspend fun continuarBackupComToken(accessToken: String, ficheiroBackup: File) {
        statusBackupMessage = "A enviar para o Google Drive..."
        val sucesso = GoogleDriveService.uploadBackup(accessToken, ficheiroBackup)
        if (sucesso) {
            registarBackupRealizado()
            marcarBoasVindasComoConcluida()
            statusBackupMessage = "Backup enviado com sucesso!"
        } else {
            statusBackupMessage = "Erro ao enviar o ficheiro para a nuvem."
        }
    }

    private suspend fun continuarRestauroComToken(accessToken: String) {
        statusBackupMessage = "A descarregar backup da nuvem..."

        val context = getApplication<Application>()
        val dbFile = context.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO)

        FinancaRepository.destruirInstancia()
        AppDatabase.destruirInstancia()

        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
        File(dbFile.absolutePath + "-journal").delete()
        dbFile.parentFile?.mkdirs()

        val sucesso = GoogleDriveService.restaurarBackup(accessToken, dbFile)

        if (sucesso) {
            statusBackupMessage = "Backup restaurado com sucesso!"
            registarBackupRealizado()
            marcarBoasVindasComoConcluida()
            reiniciarApp(context)
        } else {
            statusBackupMessage = "Nenhum backup encontrado no Google Drive."
        }
    }

    private fun reiniciarApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private suspend fun obterAccessTokenDrive(activity: Activity, acaoSeNaoAutorizado: AcaoPendenteDrive): String? {
        if (emailUtilizador.isBlank() || emailUtilizador == "Conta Conectada") {
            statusBackupMessage = "Erro: Nenhuma conta Google conectada."
            return null
        }

        statusBackupMessage = "A obter autorização do Google Drive..."

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(ESCOPO_DRIVE)))
            .build()

        return try {
            val resultado = suspendCancellableCoroutine<AuthorizationResult> { continuacao ->
                Identity.getAuthorizationClient(activity)
                    .authorize(request)
                    .addOnSuccessListener { continuacao.resume(it) }
                    .addOnFailureListener { continuacao.resumeWithException(it) }
            }

            if (resultado.hasResolution()) {
                acaoPendente = acaoSeNaoAutorizado
                pedidoAutorizacaoDrive = IntentSenderRequest
                    .Builder(resultado.pendingIntent!!.intentSender)
                    .build()
                null
            } else {
                resultado.accessToken
            }
        } catch (e: Exception) {
            e.printStackTrace()
            statusBackupMessage = "Erro de autorização do Drive: ${e.localizedMessage}"
            null
        }
    }
}