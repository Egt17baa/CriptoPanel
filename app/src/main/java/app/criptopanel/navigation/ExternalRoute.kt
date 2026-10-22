package app.criptopanel.navigation

/** Screens a notification may open. MainActivity is exported, so any other app can send it an intent. */
private val COIN_ROUTE = Regex("coin/[A-Za-z0-9%._-]{1,120}")
private val FIXED_ROUTES = setOf("portfolio?tab=2")

/** [route] if it is one our own notifications send, else null (anything else is ignored, never navigated to). */
fun allowedExternalRoute(route: String?): String? = route?.takeIf { it in FIXED_ROUTES || COIN_ROUTE.matches(it) }
