package com.example.financacelular.deteccao

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.financacelular.R
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TemaPreferencia
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ui.PopupDeTransacaoConteudo
import com.example.financacelular.ui.theme.FinanceAPPTheme
import com.example.financacelular.ui.theme.ProvideResponsiveDensity

/**
 * Popup que desce do topo sobre qualquer app quando uma transação é detectada (janela flutuante do Android).
 * Precisa da liberação "Exibir sobre outros apps"; sem ela quem chama cai na notificação.
 * Só existe um por vez: um aviso novo troca o que ainda estiver aberto.
 */
object PopupDeTransacao {
    private val principal = Handler(Looper.getMainLooper())
    // Seguro: Aberto só guarda o applicationContext e a ComposeView, e ambos são soltos em remover() (aberto = null).
    @SuppressLint("StaticFieldLeak")
    private var aberto: Aberto? = null
    @SuppressLint("StaticFieldLeak")
    private var aquecendo: Aberto? = null

    fun podeMostrar(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Devolve false se o Android não liberou a janela flutuante (nada é mostrado). */
    fun mostrar(context: Context, detectada: TransacaoDetectada, nomeBanco: String): Boolean {
        val app = context.applicationContext
        if (!podeMostrar(app)) return false
        principal.post {
            aquecendo?.remover()
            // Um aviso novo troca o popup aberto; se aquele ainda não foi salvo nem fechado, vira notificação
            // para a transação não se perder
            aberto?.let {
                if (!it.resolvido) notificarTransacao(app, it.detectada, it.nomeBanco)
                it.remover()
            }
            val novo = Aberto(app, detectada, nomeBanco)
            // A liberação pode ter sido retirada entre o podeMostrar e agora: vira notificação em vez de fechar o app
            aberto = if (novo.adicionar()) novo else {
                notificarTransacao(app, detectada, nomeBanco)
                null
            }
            // Sem isso o Android pode congelar o app antes de o popup terminar de aparecer (ver ServicoDoPopup)
            if (aberto != null) ServicoDoPopup.iniciar(app)
        }
        return true
    }

    /**
     * Monta o popup uma vez numa janela invisível, que não recebe toques, e tira logo depois. Com o app fechado o
     * código do Compose ainda não foi carregado, e a primeira montagem levava segundos; assim o aviso de verdade já
     * o encontra pronto. Nada é salvo: o popup só é desenhado com um valor de exemplo.
     */
    fun aquecer(context: Context) {
        val app = context.applicationContext
        if (!podeMostrar(app)) return
        principal.post {
            if (aberto != null || aquecendo != null) return@post
            val exemplo = TransacaoDetectada(1.0, TipoTransacao.DESPESA, null, FormaPagamento.DEBITO)
            val oculto = Aberto(app, exemplo, "", invisivel = true)
            if (!oculto.adicionar()) return@post
            aquecendo = oculto
            principal.postDelayed({ oculto.remover() }, TEMPO_DE_AQUECIMENTO_MS)
        }
    }

    private class Aberto(
        private val app: Context,
        val detectada: TransacaoDetectada,
        val nomeBanco: String,
        private val invisivel: Boolean = false
    ) {
        /** Verdadeiro quando a pessoa salvou ou fechou o popup. */
        var resolvido = false
            private set

        private val gerenciador = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        private val dono = DonoDaJanela()
        private var visao: View? = null
        /** O que o voltar do sistema faz na etapa atual (null = nada; o evento segue adiante). */
        private var voltar: (() -> Unit)? = null
        private val parametros = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            FLAGS_SEM_TECLADO or extras(),
            PixelFormat.TRANSLUCENT
        ).apply {
            if (invisivel) alpha = 0f
            gravity = Gravity.TOP
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        /** Devolve false se o Android recusou a janela. */
        fun adicionar(): Boolean {
            dono.iniciar()
            val tela = ComposeView(ContextThemeWrapper(app, R.style.Theme_FinanceAPP)).apply {
                setContent {
                    FinanceAPPTheme(darkTheme = temaEscuro()) {
                        ProvideResponsiveDensity {
                            PopupDeTransacaoConteudo(
                                detectada = detectada,
                                nomeBanco = nomeBanco,
                                aoPrecisarTeclado = ::alternarTeclado,
                                aoResolver = { resolvido = true },
                                aoRemover = ::remover,
                                aoExpirar = { if (!invisivel) notificarTransacao(app, detectada, nomeBanco) },
                                aoMudarVoltar = { voltar = it }
                            )
                        }
                    }
                }
            }
            // O Compose só recebe teclas num campo com foco; o voltar é tratado antes, na raiz da janela
            val raiz = object : FrameLayout(app) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    val acao = voltar
                    if (event.keyCode == KeyEvent.KEYCODE_BACK && acao != null) {
                        if (event.action == KeyEvent.ACTION_UP) acao()
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }
            raiz.addView(tela)
            // Os donos ficam na raiz: o Compose procura o recompositor a partir da raiz da janela
            raiz.setViewTreeLifecycleOwner(dono)
            raiz.setViewTreeViewModelStoreOwner(dono)
            raiz.setViewTreeSavedStateRegistryOwner(dono)
            visao = raiz
            return try {
                gerenciador.addView(raiz, parametros)
                true
            } catch (e: RuntimeException) { // WindowManager.BadTokenException, SecurityException
                Log.w("PopupDeTransacao", "Janela flutuante recusada", e)
                visao = null
                dono.encerrar()
                false
            }
        }

        private fun extras() = if (invisivel) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0

        /** Sem teclado a janela não rouba o foco do app de baixo; com teclado precisa tê-lo. */
        private fun alternarTeclado(precisa: Boolean) {
            val tela = visao ?: return
            if (invisivel) return
            parametros.flags = if (precisa) FLAGS_COM_TECLADO else FLAGS_SEM_TECLADO
            runCatching { gerenciador.updateViewLayout(tela, parametros) }
        }

        fun remover() {
            val tela = visao ?: return
            visao = null
            runCatching { gerenciador.removeView(tela) }
            dono.encerrar()
            if (aberto === this) {
                aberto = null
                ServicoDoPopup.parar(app)
            }
            if (aquecendo === this) aquecendo = null
        }

        private fun temaEscuro(): Boolean {
            val prefs = app.getSharedPreferences("financacelular_prefs", Context.MODE_PRIVATE)
            val salvo = prefs.getString("tema", TemaPreferencia.SISTEMA.name)
            return when (salvo) {
                TemaPreferencia.CLARO.name -> false
                TemaPreferencia.SISTEMA.name -> sistemaEscuro()
                else -> true // ESCURO e o antigo AMOLED
            }
        }

        private fun sistemaEscuro(): Boolean =
            app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private const val TEMPO_DE_AQUECIMENTO_MS = 1500L
    private const val FLAGS_SEM_TECLADO = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    private const val FLAGS_COM_TECLADO = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    /** O Compose precisa de ciclo de vida, estado salvo e loja de ViewModels mesmo fora de uma Activity. */
    private class DonoDaJanela : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registro = LifecycleRegistry(this)
        private val loja = ViewModelStore()
        private val controlador = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registro
        override val viewModelStore: ViewModelStore get() = loja
        override val savedStateRegistry: SavedStateRegistry get() = controlador.savedStateRegistry

        fun iniciar() {
            controlador.performAttach()
            controlador.performRestore(null)
            registro.currentState = Lifecycle.State.RESUMED
        }

        fun encerrar() {
            registro.currentState = Lifecycle.State.DESTROYED
            loja.clear()
        }
    }
}
