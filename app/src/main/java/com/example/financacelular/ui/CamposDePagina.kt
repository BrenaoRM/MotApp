package com.example.financacelular.ui

import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.Coral
import com.example.financacelular.R
import com.example.financacelular.ui.theme.Espaco
import com.example.financacelular.ui.theme.RoxoHero
import com.example.financacelular.ui.theme.Tamanho
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType

/** Campo de texto padrão das folhas: preenchido (onSurface 6%), raio 14, sem borda; o rótulo flutua dentro. */
@Composable
fun CampoApp(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    textoApoio: String? = null,
    leitura: Boolean = false,
    habilitado: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    TextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        readOnly = leitura,
        enabled = habilitado,
        isError = isError,
        supportingText = textoApoio?.let { { Text(it) } },
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            errorContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = RoxoHero
        ),
        modifier = modifier.fillMaxWidth()
    )
}

private fun centavosDe(valor: String): Long =
    valor.replace(",", ".").toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L

/** 123456 -> "1.234,56" */
private fun formatarCentavos(centavos: Long): String {
    val reais = (centavos / 100).toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reais,${(centavos % 100).toString().padStart(2, '0')}"
}

/**
 * Campo de dinheiro em destaque: número grande no centro com cursor violeta.
 * Os dígitos entram da direita para a esquerda (5 -> 0,05; 50 -> 0,50), como na Nova Transação.
 * O texto guardado é "1234,56" (sem milhar); o chamador converte vírgula em ponto, como antes.
 */
@Composable
fun CampoValor(
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    legenda: String? = null,
    destrutivo: Boolean = false,
    foco: FocusRequester? = null
) {
    val cor = if (destrutivo) Coral else MaterialTheme.colorScheme.onSurface
    val mudo = MaterialTheme.colorScheme.onSurfaceVariant
    val estilo = MaterialTheme.typography.displayMedium.copy(
        fontSize = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        color = cor,
        textAlign = TextAlign.Center
    )
    // A área toda do campo (não só o número) foca e abre o teclado ao tocar
    val focoUsado = foco ?: remember { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Digitar o valor"
            ) {
                focoUsado.requestFocus()
                teclado?.show()
            }
            .padding(vertical = Espaco.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            Text(
                "R$ ",
                style = estilo.copy(fontSize = 20.sp, color = mudo),
                modifier = Modifier.padding(bottom = 5.dp)
            )
            val exibido = if (valor.isBlank()) "" else formatarCentavos(centavosDe(valor))
            // Largura medida direto do texto: IntrinsicSize.Min atrasava um quadro ao crescer,
            // cortando o dígito novo e deixando o cursor fora do lugar.
            val medidor = rememberTextMeasurer()
            val larguraTexto = with(LocalDensity.current) {
                medidor.measure(exibido, estilo, maxLines = 1).size.width.toDp()
            }
            // Vazio: "0,00" esmaecido fica fora do campo e o cursor logo depois, onde o 1º dígito entra.
            if (exibido.isEmpty()) Text("0,00", style = estilo.copy(color = mudo.copy(alpha = 0.5f)))
            BasicTextField(
                value = TextFieldValue(
                    text = exibido,
                    selection = TextRange(exibido.length)
                ),
                onValueChange = { novo ->
                    val digitos = novo.text.filter { it.isDigit() }.take(9)
                    val centavos = digitos.toLongOrNull() ?: 0L
                    // Apagar o último dígito de "0,0x" esvazia o campo em vez de travar em 0,00
                    aoMudar(
                        when {
                            digitos.isEmpty() -> ""
                            centavos == 0L && novo.text.length < exibido.length -> ""
                            else -> "%d,%02d".format(Locale.ROOT, centavos / 100, centavos % 100)
                        }
                    )
                },
                singleLine = true,
                textStyle = estilo,
                cursorBrush = SolidColor(RoxoHero),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier
                    .width(if (exibido.isEmpty()) 4.dp else larguraTexto + 4.dp)
                    .focusRequester(focoUsado),
                decorationBox = { campo ->
                    Box(contentAlignment = Alignment.Center) {
                        campo()
                    }
                }
            )
        }
        if (legenda != null) {
            Spacer(Modifier.height(Espaco.xs))
            Text(legenda, style = MaterialTheme.typography.bodySmall, color = mudo)
        }
    }
}

/**
 * Foco do [CampoValor] de um sheet que pede o valor primeiro: abre o teclado sozinho ao entrar (uma vez só).
 * Chamar dentro do `if` que mostra o sheet, antes do [FolhaApp]; passar o resultado em `foco`.
 */
@Composable
fun rememberFocoValorAutomatico(): FocusRequester {
    val foco = remember { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(250) // espera o sheet subir
        runCatching {
            foco.requestFocus()
            teclado?.show()
        }
    }
    return foco
}

/** Fileira de atalhos de valor (25% / 50% / +100…): blocos de raio 12; o escolhido ganha violeta a 16%. */
@Composable
fun AtalhosDeValor(
    opcoes: List<String>,
    selecionado: Int,
    aoSelecionar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.forEachIndexed { i, texto ->
            val ativo = i == selecionado
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (ativo) RoxoHero.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable(role = Role.Button) { aoSelecionar(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    texto,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (ativo) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (ativo) RoxoHero else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

/** Bloco de escolha (raio 14): borda e fundo violeta quando escolhido. Para listas curtas dentro de uma folha. */
@Composable
fun BlocoDeEscolha(
    selecionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    conteudo: @Composable () -> Unit
) {
    val forma = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(forma)
            .background(if (selecionado) RoxoHero.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (selecionado) RoxoHero else MaterialTheme.colorScheme.outline, forma)
            .selectable(selected = selecionado, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) { conteudo() }
}

/** Opção de [SeletorQuadradoIcones]: ícone, descrição para leitor de tela e cor quando ativa. */
data class OpcaoDeIcone(val icone: ImageVector, val descricao: String, val cor: Color)

/**
 * Versão compacta do `SeletorQuadrado` (Análise) só com ícones: mesma trilha cinza de cantos 12dp e
 * bloco ativo com borda fina. A trilha tem 40dp de altura, mas o toque ocupa os 48dp.
 */
@Composable
fun SeletorQuadradoIcones(
    opcoes: List<OpcaoDeIcone>,
    indiceSelecionado: Int,
    aoSelecionar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val formaAtiva = RoundedCornerShape(10.dp)
    Box(modifier = modifier.height(Tamanho.toque)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        )
        Row(modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp).selectableGroup()) {
            opcoes.forEachIndexed { indice, opcao ->
                val ativo = indice == indiceSelecionado
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    // O efeito de toque fica só no bloco visível (40x32dp); o toque continua com 48dp de altura.
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(formaAtiva)
                            .background(if (ativo) MaterialTheme.colorScheme.background else Color.Transparent)
                            .then(if (ativo) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, formaAtiva) else Modifier)
                            .selectable(selected = ativo, role = Role.Tab, onClick = { aoSelecionar(indice) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            opcao.icone,
                            contentDescription = opcao.descricao,
                            tint = if (ativo) opcao.cor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Tamanho.icone)
                        )
                    }
                }
            }
        }
    }
}
