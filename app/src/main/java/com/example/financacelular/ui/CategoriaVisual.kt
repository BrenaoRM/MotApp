package com.example.financacelular.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector

fun iconeParaCategoria(nome: String): ImageVector {
    val chave = nome.lowercase()
    return when {
        "fatura" in chave || "cartao" in chave -> Icons.Filled.CreditCard
        "aliment" in chave || "mercado" in chave || "comida" in chave -> Icons.Filled.Restaurant
        "transporte" in chave || "uber" in chave || "carro" in chave -> Icons.Filled.DirectionsCar
        "lazer" in chave || "cinema" in chave -> Icons.Filled.LocalMovies
        "moradia" in chave || "conta" in chave || "aluguel" in chave -> Icons.Filled.Home
        "sal" in chave || "receita" in chave || "renda" in chave -> Icons.Filled.AttachMoney
        else -> Icons.Filled.ShoppingBag
    }
}