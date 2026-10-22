package app.criptopanel.core

import android.content.Context
import androidx.core.content.edit
import app.criptopanel.core.format.DisplayCurrency
import app.criptopanel.core.format.Money

private const val PREFS = "criptopanel"
private const val KEY_CODE = "currency"
private const val KEY_RATE = "eur_per_usd"

fun savedCurrencyCode(context: Context): String =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CODE, "USD") ?: "USD"

/** Applies the saved preference with the last known rate; stays in USD if no EUR rate was ever fetched. */
fun applySavedCurrency(context: Context) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val rate = prefs.getFloat(KEY_RATE, 0f).toDouble()
    Money.current = if (savedCurrencyCode(context) == "EUR" && rate > 0) DisplayCurrency.eur(rate) else DisplayCurrency.USD
}

/** Saves the choice (and the fresh EUR rate, when known) and applies it. */
fun saveCurrency(context: Context, code: String, eurPerUsd: Double?) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
        putString(KEY_CODE, code)
        if (eurPerUsd != null && eurPerUsd > 0) putFloat(KEY_RATE, eurPerUsd.toFloat())
    }
    applySavedCurrency(context)
}
