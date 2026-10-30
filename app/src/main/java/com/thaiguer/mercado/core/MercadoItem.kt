package com.thaiguer.mercado.core

data class MercadoItem(
    val id: String,
    val name: String,
    val ok: Boolean = false,
    val count: Int = 1,
    val price: Double = 0.0,
    val modifiedAt: Long = System.currentTimeMillis()
) {
    val total: Double
        get() = count * price
}