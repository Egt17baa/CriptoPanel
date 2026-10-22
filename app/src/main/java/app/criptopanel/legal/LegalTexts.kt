package app.criptopanel.legal

/** Bump when the terms change: everyone is asked to accept them again. */
const val TERMS_VERSION = "2026-10-22 (rev. 2)"

const val DISCLAIMER = "Datos informativos, no asesoramiento financiero."

/** The four points shown before the full terms. */
val TERMS_SUMMARY = listOf(
    "CriptoPanel es una herramienta informativa. No es asesoramiento financiero, de inversión ni fiscal.",
    "Los datos vienen de servicios públicos de terceros y pueden tener errores, retrasos o cortes.",
    "La app no guarda tu dinero ni compra o vende nada: no hay custodia ni trading.",
    "Tu cartera, favoritos y alertas se guardan solo en tu móvil. Una alerta puede no llegar si el sistema cierra la app o no hay red.",
)

data class LegalSection(val title: String, val body: String)

enum class LegalDoc(val title: String) {
    TERMS("Términos y condiciones"),
    PRIVACY("Privacidad"),
    SOURCES("Fuentes de datos"),
    METHOD("Cómo calculamos"),
}

val TERMS = listOf(
    LegalSection(
        "1. Qué es CriptoPanel",
        "CriptoPanel es una aplicación gratuita que muestra información pública sobre criptomonedas y finanzas descentralizadas (DeFi), " +
            "y herramientas personales (cartera, favoritos, alertas, calculadoras). Al usarla aceptas estos términos.",
    ),
    LegalSection(
        "2. No es asesoramiento financiero",
        "Todo lo que muestra la app es información general con fines informativos y educativos. No es una recomendación de compra, " +
            "venta o mantenimiento de ningún activo ni asesoramiento financiero, de inversión, legal o fiscal. Las criptomonedas son " +
            "muy volátiles y puedes perder todo lo invertido. Rentabilidades pasadas o simuladas no garantizan resultados futuros. " +
            "Antes de invertir, infórmate por tu cuenta y, si lo necesitas, consulta a un profesional autorizado.",
    ),
    LegalSection(
        "3. Datos de terceros",
        "Los precios, índices, TVL, rendimientos y demás datos proceden de servicios de terceros (CoinGecko, CoinLore, DefiLlama, " +
            "Binance, Alternative.me, el BCE a través de Frankfurter y TradingView). No garantizamos que sean exactos, completos ni que estén disponibles en todo momento. " +
            "Pueden llegar con retraso o contener errores; cuando una fuente falla, la app muestra el último dato válido o «sin datos».",
    ),
    LegalSection(
        "4. Cartera, alertas y calculadoras",
        "La cartera, los favoritos y las alertas son herramientas personales: tú introduces los datos y eres responsable de ellos. " +
            "Los cálculos de ganancias son aproximados y no sirven como documento fiscal. Las alertas dependen de la conexión, de la " +
            "batería y de que el sistema permita a la app seguir en segundo plano; pueden llegar tarde o no llegar.",
    ),
    LegalSection(
        "4 bis. Herramientas de seguridad",
        "Las herramientas de Seguridad (cifrado de texto y archivos, generador y comprobadores) usan algoritmos estándar " +
            "(AES-256-GCM, ChaCha20-Poly1305, Argon2id) de librerías reconocidas, pero no han pasado una auditoría independiente. " +
            "Si olvidas una contraseña de cifrado, nadie puede recuperar los datos. Para proteger fondos importantes usa una wallet hardware.",
    ),
    LegalSection(
        "5. Sin custodia ni operaciones",
        "CriptoPanel no guarda fondos, no se conecta a tus wallets ni a tus cuentas de exchange y no ejecuta compras ni ventas.",
    ),
    LegalSection(
        "6. Responsabilidad",
        "La app se ofrece «tal cual», sin garantías. En la medida en que lo permita la ley, no respondemos de pérdidas o daños " +
            "derivados del uso de la app o de decisiones tomadas a partir de su información, ni de fallos de servicios de terceros. " +
            "Nada de esto limita los derechos que te reconozca la normativa de consumidores aplicable.",
    ),
    LegalSection(
        "7. Privacidad",
        "No pedimos registro ni datos personales y no usamos publicidad ni analítica. Los detalles están en la sección Privacidad.",
    ),
    LegalSection(
        "8. Marcas y contenidos",
        "Los nombres, logotipos y marcas de monedas, proyectos y proveedores de datos pertenecen a sus titulares y se muestran solo " +
            "para identificarlos.",
    ),
    LegalSection(
        "9. Cambios",
        "Podemos actualizar estos términos. Si cambian, la app te pedirá aceptarlos de nuevo antes de seguir usándola.",
    ),
    LegalSection(
        "10. Ley aplicable y contacto",
        "Estos términos se rigen por la ley española. Para cualquier duda, escribe al contacto que figura en la ficha de la app en Google Play.",
    ),
)

val PRIVACY = listOf(
    LegalSection("Qué datos recogemos", "Ninguno. No hay cuentas, ni publicidad, ni herramientas de analítica, ni servidores propios."),
    LegalSection(
        "Qué se guarda en tu móvil",
        "Tu cartera (operaciones), tus favoritos, tus alertas y la fecha en que aceptaste los términos. Se guardan en el almacenamiento " +
            "privado de la app. Si tienes activada la copia de seguridad de Google, pueden incluirse en ella. Puedes exportar la cartera " +
            "en CSV y se borra todo al desinstalar.",
    ),
    LegalSection(
        "Conexiones a terceros",
        "Para mostrar datos, tu móvil se conecta directamente a los servicios de la sección Fuentes. Como en cualquier conexión a " +
            "Internet, reciben datos técnicos (dirección IP, tipo de dispositivo) y los tratan según su propia política de privacidad. " +
            "El widget de TradingView puede usar cookies propias.",
    ),
    LegalSection(
        "Seguridad",
        "Lo que escribes o eliges en las herramientas de Seguridad (textos, archivos, contraseñas) se procesa solo en tu móvil y no se " +
            "guarda. El comprobador de filtraciones envía a Have I Been Pwned únicamente los 5 primeros caracteres de la huella SHA-1 de la " +
            "contraseña, que no permiten conocerla. En las pantallas de Seguridad se bloquean las capturas de pantalla.",
    ),
    LegalSection(
        "Permisos",
        "Internet y estado de la red (descargar datos), notificaciones (avisarte de tus alertas), servicio en primer plano e inicio al " +
            "arrancar (vigilar tus alertas en directo, solo mientras tengas alguna activa).",
    ),
)

data class Source(val name: String, val gives: String, val url: String)

val SOURCES = listOf(
    Source("CoinGecko", "Top 100, detalle y gráficos de monedas, búsqueda, tendencias, sectores, exchanges y precios de la cartera.", "https://www.coingecko.com/en/api"),
    Source("CoinLore", "Capitalización total, volumen y dominancias (TOTAL, TOTAL2, TOTAL3). Respaldo del Top 100.", "https://www.coinlore.com/cryptocurrency-data-api"),
    Source("DefiLlama", "TVL por red y protocolo, stablecoins, volumen de DEX, comisiones e ingresos, rendimientos.", "https://defillama.com/docs/api"),
    Source("Binance (datos de mercado)", "Precios en directo para las alertas y precios antiguos de la cartera.", "https://developers.binance.com/docs/binance-spot-api-docs"),
    Source("Alternative.me", "Índice de miedo y codicia.", "https://alternative.me/crypto/fear-and-greed-index/"),
    Source("BCE vía Frankfurter", "Tipo de cambio euro/dólar, de hoy y de días pasados, para ver la app en euros.", "https://frankfurter.dev"),
    Source("Have I Been Pwned", "Comprobar si una contraseña aparece en filtraciones (solo se envían 5 caracteres de su huella).", "https://haveibeenpwned.com/Passwords"),
    Source("EFF", "Lista de 7.776 palabras para generar frases de contraseña (CC BY 3.0).", "https://www.eff.org/dice"),
    Source("TradingView", "Gráficos de velas de TOTAL, TOTAL2, TOTAL3 y dominancias.", "https://www.tradingview.com/widget-docs/"),
)

val METHOD = listOf(
    LegalSection(
        "TOTAL2 y TOTAL3",
        "TOTAL2 = capitalización total × (1 − dominancia de BTC). TOTAL3 = capitalización total × (1 − dominancia de BTC − dominancia de ETH). " +
            "Se calculan con los datos de CoinLore, así que pueden diferir algo de los índices de TradingView, que tienen su propia metodología.",
    ),
    LegalSection(
        "Ganancia de la cartera",
        "Método del precio medio: cada compra suma su coste (incluida la comisión); al vender, la ganancia realizada es lo cobrado menos " +
            "la comisión menos el precio medio por la cantidad vendida. La ganancia total suma la realizada y la pendiente (valor actual " +
            "menos coste). El porcentaje se calcula sobre todo lo invertido en compras.",
    ),
    LegalSection(
        "Cambio de hoy",
        "Suma, para cada moneda, valor × cambio24h / (100 + cambio24h): lo que ha variado su valor en las últimas 24 h.",
    ),
    LegalSection(
        "Rendimientos (APY)",
        "Se ocultan los pools marcados como atípicos por DefiLlama, los que no tienen APY, los de APY mayor de 1.000 % y los de menos de 1 M$ de TVL. " +
            "El APY es variable y puede cambiar en cualquier momento.",
    ),
    LegalSection(
        "Alertas en directo",
        "Las monedas que cotizan en Binance (par con USDT y precio a menos de un 3 % del de CoinGecko) se siguen en directo, una " +
            "actualización por segundo; el resto se comprueba cada minuto con CoinGecko. Una alerta que salta se desactiva sola.",
    ),
    LegalSection(
        "En euros",
        "Los datos llegan en dólares y se convierten con el cambio oficial del BCE. Los precios de mercado usan el cambio de hoy. " +
            "En tu cartera, cada compra y venta se convierte con el cambio de su propio día, así que el coste en euros es lo que pagaste " +
            "en euros y la ganancia incluye la variación del euro frente al dólar. Las alertas se vigilan en dólares al cambio del día en que las creas.",
    ),
    LegalSection(
        "¿Y si hubiera invertido?",
        "Usa el precio diario de CoinGecko del último año. La compra única se hace el primer día del periodo; en DCA se compra el mismo " +
            "importe cada mes. No incluye comisiones ni impuestos.",
    ),
)
