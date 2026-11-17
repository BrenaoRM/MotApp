package com.example.financacelular.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalMovies
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.Tamanho
import java.text.Normalizer

/** Um ícone que o usuário pode escolher para uma categoria; [chave] é o que fica guardado no banco. */
data class IconeDeCategoria(val chave: String, val icone: ImageVector, val rotulo: String)

val IconesDeCategoria = listOf(
    IconeDeCategoria("alimentacao", Icons.Outlined.Restaurant, "Alimentação"),
    IconeDeCategoria("mercado", Icons.Outlined.ShoppingCart, "Mercado"),
    IconeDeCategoria("cafe", Icons.Outlined.Coffee, "Café"),
    IconeDeCategoria("transporte", Icons.Outlined.DirectionsCar, "Transporte"),
    IconeDeCategoria("combustivel", Icons.Outlined.LocalGasStation, "Combustível"),
    IconeDeCategoria("moradia", Icons.Outlined.Home, "Moradia"),
    IconeDeCategoria("contas", Icons.AutoMirrored.Outlined.ReceiptLong, "Contas"),
    IconeDeCategoria("energia", Icons.Outlined.Bolt, "Energia"),
    IconeDeCategoria("saude", Icons.Outlined.MedicalServices, "Saúde"),
    IconeDeCategoria("educacao", Icons.Outlined.School, "Educação"),
    IconeDeCategoria("lazer", Icons.Outlined.LocalMovies, "Lazer"),
    IconeDeCategoria("jogos", Icons.Outlined.SportsEsports, "Jogos"),
    IconeDeCategoria("viagem", Icons.Outlined.Flight, "Viagem"),
    IconeDeCategoria("compras", Icons.Outlined.ShoppingBag, "Compras"),
    IconeDeCategoria("roupas", Icons.Outlined.Checkroom, "Roupas"),
    IconeDeCategoria("beleza", Icons.Outlined.Spa, "Beleza"),
    IconeDeCategoria("pets", Icons.Outlined.Pets, "Pets"),
    IconeDeCategoria("presentes", Icons.Outlined.CardGiftcard, "Presentes"),
    IconeDeCategoria("filhos", Icons.Outlined.ChildCare, "Filhos"),
    IconeDeCategoria("esporte", Icons.Outlined.FitnessCenter, "Esporte"),
    IconeDeCategoria("telefone", Icons.Outlined.PhoneIphone, "Telefone"),
    IconeDeCategoria("internet", Icons.Outlined.Wifi, "Internet"),
    IconeDeCategoria("assinaturas", Icons.Outlined.Subscriptions, "Assinaturas"),
    IconeDeCategoria("manutencao", Icons.Outlined.Build, "Manutenção"),
    IconeDeCategoria("trabalho", Icons.Outlined.Work, "Trabalho"),
    IconeDeCategoria("salario", Icons.Outlined.AttachMoney, "Salário e renda"),
    IconeDeCategoria("investimentos", Icons.Outlined.Savings, "Investimentos"),
    IconeDeCategoria("cartao", Icons.Outlined.CreditCard, "Cartão"),
    IconeDeCategoria("outros", Icons.Outlined.Category, "Outros")
)

/** Palavras do nome (sem acento) que levam a um ícone: [prefixos] casam o começo da palavra, [exatos] a palavra inteira. */
private class RegraDeIcone(val chave: String, val prefixos: List<String>, val exatos: List<String> = emptyList())

// A ordem importa: a primeira regra que casar vence
private val RegrasDeIcone = listOf(
    RegraDeIcone("cartao", listOf("fatura", "cartao")),
    RegraDeIcone("mercado", listOf("mercado", "supermercado", "feira", "hortifruti")),
    RegraDeIcone("cafe", listOf("cafe", "cafeteria")),
    RegraDeIcone("alimentacao", listOf("aliment", "comida", "restaurante", "lanche", "ifood", "padaria", "refeic")),
    RegraDeIcone("combustivel", listOf("combustivel", "gasolina", "etanol", "posto")),
    RegraDeIcone("transporte", listOf("transporte", "uber", "taxi", "onibus", "metro", "carro", "estacionamento", "pedagio"), listOf("99")),
    RegraDeIcone("energia", listOf("luz", "energia", "eletric")),
    RegraDeIcone("contas", listOf("conta", "boleto", "imposto", "taxa", "iptu", "ipva")),
    RegraDeIcone("moradia", listOf("moradia", "aluguel", "casa", "condominio")),
    RegraDeIcone("saude", listOf("saude", "farmacia", "medic", "hospital", "dentista", "consulta", "plano")),
    RegraDeIcone("educacao", listOf("educa", "escola", "curso", "faculdade", "livro")),
    RegraDeIcone("jogos", listOf("jogo", "game", "steam", "playstation", "xbox")),
    RegraDeIcone("lazer", listOf("lazer", "cinema", "streaming", "show", "festa"), listOf("bar")),
    RegraDeIcone("viagem", listOf("viagem", "hotel", "passagem", "voo", "ferias")),
    RegraDeIcone("roupas", listOf("roupa", "vestuario", "moda", "calcado", "sapato")),
    RegraDeIcone("beleza", listOf("beleza", "cabelo", "salao", "estetica", "barbearia")),
    RegraDeIcone("pets", listOf("pets", "petshop", "racao", "veterin", "cachorro", "gato")),
    RegraDeIcone("presentes", listOf("presente")),
    RegraDeIcone("filhos", listOf("filho", "crianca", "bebe", "creche")),
    RegraDeIcone("esporte", listOf("academia", "esporte", "fitness", "treino")),
    RegraDeIcone("telefone", listOf("telefone", "celular", "chip")),
    RegraDeIcone("internet", listOf("internet", "wifi", "fibra")),
    RegraDeIcone("assinaturas", listOf("assinatura", "netflix", "spotify")),
    RegraDeIcone("manutencao", listOf("manutencao", "reforma", "conserto", "oficina")),
    RegraDeIcone("investimentos", listOf("invest", "poupanca", "reserva", "aporte")),
    RegraDeIcone("salario", listOf("salario", "receita", "renda", "freela", "bonus", "ganho")),
    RegraDeIcone("trabalho", listOf("trabalho", "empresa", "projeto")),
    RegraDeIcone("compras", listOf("compra", "shopping", "loja", "amazon"))
)

private fun semAcento(texto: String): String =
    Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/**
 * Ícone da categoria: o escolhido pelo usuário ([chave], guardado na categoria) quando existe; senão, o que
 * as palavras do [nome] sugerem; senão, o ícone genérico de categoria.
 */
fun iconeParaCategoria(nome: String, chave: String? = null): ImageVector {
    if (chave != null) IconesDeCategoria.firstOrNull { it.chave == chave }?.let { return it.icone }
    val palavras = semAcento(nome).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    val regra = RegrasDeIcone.firstOrNull { r ->
        palavras.any { p -> p in r.exatos || r.prefixos.any { p.startsWith(it) } }
    }
    val chaveFinal = regra?.chave ?: "outros"
    return IconesDeCategoria.first { it.chave == chaveFinal }.icone
}

/** Fileira rolável para escolher o ícone de uma categoria nova; tocar no ícone escolhido de novo desfaz. */
@Composable
fun SeletorDeIconeCategoria(
    selecionada: String?,
    aoSelecionar: (String?) -> Unit,
    cor: Color,
    modifier: Modifier = Modifier
) {
    val forma = RoundedCornerShape(12.dp)
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(IconesDeCategoria, key = { it.chave }) { opcao ->
            val ativo = opcao.chave == selecionada
            Box(
                modifier = Modifier
                    .size(Tamanho.toque)
                    .clip(forma)
                    .then(if (ativo) Modifier.border(1.dp, cor, forma) else Modifier)
                    .selectable(
                        selected = ativo,
                        role = Role.RadioButton,
                        onClick = { aoSelecionar(if (ativo) null else opcao.chave) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    opcao.icone,
                    contentDescription = opcao.rotulo,
                    tint = if (ativo) cor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Tamanho.iconeGrande)
                )
            }
        }
    }
}
