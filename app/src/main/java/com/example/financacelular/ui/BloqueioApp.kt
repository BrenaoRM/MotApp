package com.example.financacelular.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.example.financacelular.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.example.financacelular.ui.theme.RoxoHero

/**
 * Bloqueio do app com a digital ou a senha do próprio celular (opcional, em Configurações > Segurança).
 * Tranca ao abrir o app e ao voltar depois de [CARENCIA_MS] fora dele; a carência evita pedir de novo
 * na volta rápida do compartilhar, do login do Google ou das telas de permissão.
 */
object BloqueioApp {
    private const val PREFS = "financacelular_prefs"
    private const val CHAVE = "bloquear_app"
    private const val CARENCIA_MS = 30_000L
    private const val AUTENTICADORES = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    var bloqueado by mutableStateOf(false)
        private set

    private var iniciado = false
    private var saiuEm = 0L
    private var autenticando = false
    private var pedidoAutomaticoFeito = false

    fun ativo(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CHAVE, false)

    fun definirAtivo(context: Context, ativo: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(CHAVE, ativo) }
    }

    /**
     * Esconde o conteúdo do app na lista de recentes. Android 13+: só a miniatura some, sempre (prints
     * continuam). Antes do 13 o único jeito é FLAG_SECURE, que também bloqueia prints: só com o bloqueio ligado.
     */
    fun protegerTela(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        } else if (ativo(activity)) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Android 9 ou mais novo: no 8 a janela de digital da biblioteca usa diálogo do AppCompat, e o tema
     * do app não é AppCompat (fecharia o app ao pedir a digital).
     */
    fun suportado(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** O celular tem digital, rosto ou senha de tela que o Android aceite para confirmar. */
    fun disponivel(context: Context): Boolean = suportado() &&
        BiometricManager.from(context).canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS

    /** Chamado no onCreate: a primeira vez no processo tranca (girar a tela não tranca de novo). */
    fun aoCriar(context: Context) {
        autenticando = false // um pedido aberto antes de recriar a activity perdeu o retorno
        if (iniciado) return
        iniciado = true
        if (ativo(context)) trancar()
    }

    fun aoSair() {
        if (!autenticando) saiuEm = SystemClock.elapsedRealtime()
    }

    fun aoVoltar(context: Context) {
        val fora = if (saiuEm == 0L) 0L else SystemClock.elapsedRealtime() - saiuEm
        saiuEm = 0L
        if (!autenticando && fora > CARENCIA_MS && ativo(context)) trancar()
    }

    /** Ao aparecer a tela bloqueada, o pedido de digital abre sozinho uma vez; depois só pelo botão. */
    fun aoRetomar(activity: FragmentActivity) {
        if (bloqueado && !pedidoAutomaticoFeito) {
            pedidoAutomaticoFeito = true
            desbloquear(activity)
        }
    }

    fun desbloquear(activity: FragmentActivity) {
        // Sem senha de tela no celular não há como confirmar: o bloqueio perde o sentido e não prende o usuário
        if (!disponivel(activity)) {
            bloqueado = false
            return
        }
        confirmar(activity, "Desbloquear o MotApp") { bloqueado = false }
    }

    /** Abre a janela de digital/senha do Android e chama [aoConseguir] se confirmar. */
    fun confirmar(activity: FragmentActivity, titulo: String, aoConseguir: () -> Unit) {
        if (autenticando) return
        autenticando = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    autenticando = false
                    aoConseguir()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    autenticando = false
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(titulo)
                .setAllowedAuthenticators(AUTENTICADORES)
                .build()
        )
    }

    private fun trancar() {
        bloqueado = true
        pedidoAutomaticoFeito = false
    }
}

/** Tela por cima do app enquanto [BloqueioApp.bloqueado]: logo e o botão Desbloquear. Voltar sai do app. */
@Composable
fun TelaBloqueada(activity: FragmentActivity) {
    BackHandler { activity.moveTaskToBack(true) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo do app (a mesma do ícone), igual à da tela de boas-vindas
                val formaLogo = RoundedCornerShape(21.dp)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(formaLogo)
                        .border(1.dp, MaterialTheme.colorScheme.outline, formaLogo)
                ) {
                    Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize())
                    Image(
                        painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().scale(1.3f)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "MotApp bloqueado",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            BotaoPrincipal(
                texto = "Desbloquear",
                onClick = { BloqueioApp.desbloquear(activity) },
                modifier = Modifier.fillMaxWidth(),
                cor = RoxoHero,
                corConteudo = Color.White,
                icone = Icons.Outlined.Fingerprint
            )
        }
    }
}
