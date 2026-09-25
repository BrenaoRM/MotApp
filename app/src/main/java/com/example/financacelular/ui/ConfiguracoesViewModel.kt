package com.example.financacelular.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.financacelular.data.PreferenciasTema
import com.example.financacelular.data.TemaPreferencia

class ConfiguracoesViewModel(application: Application) : AndroidViewModel(application) {

    var temaAtual by mutableStateOf(PreferenciasTema.obter(application))
        private set

    fun definirTema(tema: TemaPreferencia) {
        temaAtual = tema
        PreferenciasTema.salvar(getApplication(), tema)
    }
}
