package app.criptopanel.learn

enum class Level(val label: String) { BASIC("Básico"), INTERMEDIATE("Intermedio") }

sealed interface Block {
    data class Heading(val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Bullets(val items: List<String>) : Block
    /** A highlighted note; [warning] for safety advice. */
    data class Callout(val text: String, val warning: Boolean = false) : Block
    /** A small comparison: first row is the header. */
    data class Table(val rows: List<List<String>>) : Block
}

data class Question(val text: String, val options: List<String>, val answer: Int, val why: String)

data class Lesson(
    val id: String,
    val title: String,
    val subtitle: String,
    val level: Level,
    val minutes: Int,
    val blocks: List<Block>,
    val summary: List<String>,
    val quiz: List<Question>,
)

data class Term(val name: String, val definition: String)

private fun h(t: String) = Block.Heading(t)
private fun p(t: String) = Block.Paragraph(t)
private fun list(vararg items: String) = Block.Bullets(items.toList())
private fun tip(t: String) = Block.Callout(t)
private fun warn(t: String) = Block.Callout(t, warning = true)

val LESSONS: List<Lesson> = listOf(
    Lesson(
        id = "que-es",
        title = "Qué es una criptomoneda",
        subtitle = "Dinero digital sin banco central",
        level = Level.BASIC,
        minutes = 4,
        blocks = listOf(
            p(
                "Una criptomoneda es un activo digital que se puede enviar de persona a persona por Internet sin que un banco o una empresa " +
                    "tenga que aprobar la operación. Las reglas las aplica un programa que ejecutan miles de ordenadores a la vez, y el " +
                    "registro de quién tiene qué es público.",
            ),
            h("¿Qué la hace diferente del dinero del banco?"),
            Block.Table(
                listOf(
                    listOf("", "Euro en tu banco", "Bitcoin"),
                    listOf("Quién lleva el registro", "Tu banco", "Miles de nodos a la vez"),
                    listOf("Quién crea más", "El banco central", "Un calendario fijo en el código"),
                    listOf("Horario", "Laborables, con límites", "24 h, 365 días"),
                    listOf("Si pierdes el acceso", "El banco te ayuda", "Nadie puede recuperarlo"),
                ),
            ),
            h("Monedas y tokens"),
            p(
                "Una «moneda» tiene su propia blockchain (Bitcoin, Ether en Ethereum, SOL en Solana). Un «token» vive dentro de otra " +
                    "blockchain: por ejemplo, USDC o UNI funcionan sobre Ethereum y otras redes.",
            ),
            h("¿Por qué su precio sube y baja tanto?"),
            p(
                "Su precio lo fija solo la oferta y la demanda en los mercados. No hay beneficios de una empresa detrás ni un banco central " +
                    "que lo estabilice, y el mercado es pequeño comparado con las bolsas, así que las noticias y el sentimiento lo mueven mucho.",
            ),
            warn("Movimientos del 10 % o más en un día son normales. No inviertas dinero que puedas necesitar."),
        ),
        summary = listOf(
            "Es un activo digital que se transfiere sin intermediarios.",
            "El registro es público y lo mantienen miles de ordenadores.",
            "Moneda = blockchain propia; token = vive en otra blockchain.",
            "Es muy volátil: el precio solo depende de oferta y demanda.",
        ),
        quiz = listOf(
            Question(
                "¿Quién aprueba una transferencia de bitcoin?",
                listOf("Un banco", "La red de nodos siguiendo las reglas del programa", "El gobierno de cada país"),
                1,
                "La red comprueba que la operación cumple las reglas; no hay un intermediario que la autorice.",
            ),
            Question(
                "USDC funciona sobre Ethereum y otras redes. ¿Qué es?",
                listOf("Una moneda con blockchain propia", "Un token", "Un exchange"),
                1,
                "Los tokens viven dentro de otra blockchain.",
            ),
            Question(
                "Si pierdes el acceso a tus bitcoins en tu propia wallet…",
                listOf("El banco te lo devuelve", "Puedes pedirlo a la red", "Nadie puede recuperarlos"),
                2,
                "Sin intermediario tampoco hay nadie que pueda restaurar el acceso.",
            ),
        ),
    ),
    Lesson(
        id = "origenes",
        title = "Los orígenes: de los cypherpunks a Bitcoin",
        subtitle = "1983–2010: cómo empezó todo",
        level = Level.BASIC,
        minutes = 5,
        blocks = listOf(
            h("Antes de Bitcoin"),
            p(
                "Desde los años 80, criptógrafos y activistas («cypherpunks») buscaban un dinero digital privado. David Chaum diseñó " +
                    "en 1983 el dinero electrónico anónimo y fundó DigiCash, que cerró a finales de los 90. Otros propusieron ideas como " +
                    "b-money (Wei Dai, 1998) o bit gold (Nick Szabo). Todas tropezaban con el mismo problema: ¿cómo evitar que alguien " +
                    "gaste dos veces el mismo dinero digital sin una autoridad central que lo vigile?",
            ),
            h("2008: el documento de Satoshi"),
            p(
                "El 31 de octubre de 2008, alguien con el seudónimo Satoshi Nakamoto publicó «Bitcoin: A Peer-to-Peer Electronic Cash " +
                    "System», un documento de 9 páginas. Su solución al doble gasto: un registro público encadenado (la blockchain) que los " +
                    "propios participantes mantienen compitiendo con potencia de cálculo (prueba de trabajo).",
            ),
            h("2009: el bloque génesis"),
            p(
                "El 3 de enero de 2009 se minó el primer bloque. Satoshi incluyó en él el titular de The Times de ese día sobre un " +
                    "segundo rescate a los bancos, que se interpreta como una crítica al sistema financiero. Días después, Hal Finney " +
                    "recibió la primera transferencia de bitcoin.",
            ),
            h("2010: la primera compra"),
            p(
                "El 22 de mayo de 2010, Laszlo Hanyecz pagó 10.000 BTC por dos pizzas. Ese día se celebra como el «Bitcoin Pizza Day». " +
                    "En 2010 Satoshi dejó de participar en público y su identidad sigue sin conocerse.",
            ),
            h("Después"),
            list(
                "2015: nace Ethereum, que añade programas (contratos inteligentes) a la blockchain.",
                "2017 y 2021: grandes subidas y caídas que llevan las criptomonedas a los medios.",
                "2024: se aprueban en EE. UU. los primeros fondos cotizados (ETF) de bitcoin al contado.",
            ),
            tip("El número total de bitcoins está limitado a 21 millones por el propio código."),
        ),
        summary = listOf(
            "Décadas de intentos previos chocaron con el problema del doble gasto.",
            "Satoshi Nakamoto publicó el documento de Bitcoin el 31-10-2008.",
            "El bloque génesis se minó el 3-1-2009.",
            "Habrá como máximo 21 millones de bitcoins.",
        ),
        quiz = listOf(
            Question(
                "¿Qué problema resolvió Bitcoin que los intentos anteriores no lograron?",
                listOf("El doble gasto sin autoridad central", "Hacer pagos con tarjeta", "Imprimir billetes"),
                0,
                "La blockchain con prueba de trabajo impide gastar dos veces sin necesitar a nadie que lo vigile.",
            ),
            Question(
                "¿Cuándo se minó el bloque génesis?",
                listOf("31 de octubre de 2008", "3 de enero de 2009", "22 de mayo de 2010"),
                1,
                "El documento es de 2008; la red arrancó el 3 de enero de 2009.",
            ),
            Question(
                "¿Cuántos bitcoins habrá como máximo?",
                listOf("21 millones", "100 millones", "No hay límite"),
                0,
                "El límite de 21 millones está fijado en el código.",
            ),
        ),
    ),
    Lesson(
        id = "blockchain",
        title = "Blockchain y consenso",
        subtitle = "Prueba de trabajo y prueba de participación",
        level = Level.BASIC,
        minutes = 5,
        blocks = listOf(
            p(
                "Una blockchain es un libro de cuentas dividido en bloques. Cada bloque guarda un grupo de operaciones y la «huella» " +
                    "(hash) del bloque anterior, así que forman una cadena: cambiar un bloque antiguo rompería todas las huellas siguientes " +
                    "y la red lo rechazaría.",
            ),
            h("¿Quién añade el siguiente bloque?"),
            p("Hace falta un método para que miles de ordenadores que no se conocen se pongan de acuerdo. A eso se le llama consenso."),
            Block.Table(
                listOf(
                    listOf("", "Prueba de trabajo (PoW)", "Prueba de participación (PoS)"),
                    listOf("Quién propone bloques", "Mineros con máquinas", "Validadores con monedas bloqueadas"),
                    listOf("Qué ponen en juego", "Electricidad y equipos", "Sus propias monedas"),
                    listOf("Si hacen trampa", "Pierden lo gastado", "Pueden perder parte de lo bloqueado"),
                    listOf("Ejemplos", "Bitcoin", "Ethereum (desde 2022), Solana, Cardano"),
                ),
            ),
            h("Nodos"),
            p(
                "Un nodo es un ordenador que guarda una copia de la blockchain y comprueba que cada bloque cumple las reglas. Cualquiera " +
                    "puede tener uno: por eso no hace falta confiar en una sola empresa.",
            ),
            tip("Ethereum pasó de prueba de trabajo a prueba de participación en septiembre de 2022 («The Merge») y redujo su consumo eléctrico más de un 99 %."),
        ),
        summary = listOf(
            "Cada bloque incluye la huella del anterior: cambiar el pasado es casi imposible.",
            "El consenso decide quién añade el siguiente bloque.",
            "PoW usa potencia de cálculo; PoS usa monedas bloqueadas como garantía.",
        ),
        quiz = listOf(
            Question(
                "¿Por qué es tan difícil cambiar un bloque antiguo?",
                listOf("Porque está en papel", "Porque cada bloque contiene la huella del anterior", "Porque lo prohíbe la ley"),
                1,
                "Cambiar un bloque cambia su huella y rompe toda la cadena posterior.",
            ),
            Question(
                "En prueba de participación, ¿qué arriesga un validador tramposo?",
                listOf("Su electricidad", "Parte de las monedas que bloqueó", "Nada"),
                1,
                "Las monedas bloqueadas sirven de garantía y pueden recortarse («slashing»).",
            ),
            Question(
                "¿Qué consenso usa Bitcoin?",
                listOf("Prueba de trabajo", "Prueba de participación", "Votación de bancos"),
                0,
                "Bitcoin sigue usando prueba de trabajo con minería.",
            ),
        ),
    ),
    Lesson(
        id = "criptografia",
        title = "Criptografía sin fórmulas",
        subtitle = "Hashes, claves y firmas",
        level = Level.INTERMEDIATE,
        minutes = 6,
        blocks = listOf(
            h("1. El hash: una huella digital"),
            p(
                "Una función hash convierte cualquier dato en una huella de longitud fija. Bitcoin usa SHA-256, que da siempre 256 bits " +
                    "(64 caracteres en hexadecimal). Si cambias una sola letra del dato, la huella cambia por completo, y no hay forma " +
                    "práctica de volver de la huella al dato original.",
            ),
            h("2. Clave privada y clave pública"),
            p(
                "Tu clave privada es un número secreto enorme, elegido al azar. De ella se calcula tu clave pública, y de esta tu " +
                    "dirección (a la que te envían dinero). El camino solo funciona en un sentido: con la dirección nadie puede averiguar " +
                    "tu clave privada.",
            ),
            Block.Table(
                listOf(
                    listOf("", "¿Se puede compartir?", "Para qué sirve"),
                    listOf("Dirección", "Sí", "Recibir dinero"),
                    listOf("Clave pública", "Sí", "Comprobar tus firmas"),
                    listOf("Clave privada", "Nunca", "Firmar y gastar"),
                ),
            ),
            h("3. La firma digital"),
            p(
                "Para enviar dinero, tu wallet firma la operación con la clave privada. Cualquiera puede comprobar con tu clave pública " +
                    "que la firma es tuya, pero nadie puede copiarla para otra operación. Bitcoin y Ethereum usan curvas elípticas " +
                    "(secp256k1) para estas firmas.",
            ),
            h("4. La frase semilla"),
            p(
                "Casi todas las wallets generan una frase de 12 o 24 palabras (estándar BIP-39). Con ella se recrean todas tus claves " +
                    "privadas: quien tenga la frase tiene tu dinero.",
            ),
            warn("Nunca escribas tu frase semilla en una web, un correo, una foto o la nube. Ningún soporte técnico legítimo te la pedirá."),
        ),
        summary = listOf(
            "El hash es una huella: un cambio mínimo la cambia por completo.",
            "Clave privada → clave pública → dirección; nunca al revés.",
            "La firma demuestra que eres el dueño sin revelar la clave privada.",
            "La frase semilla es la llave maestra de todas tus claves.",
        ),
        quiz = listOf(
            Question(
                "¿Qué puedes compartir sin riesgo?",
                listOf("Tu dirección", "Tu clave privada", "Tu frase semilla"),
                0,
                "La dirección sirve para recibir; la clave privada y la frase semilla dan acceso a tus fondos.",
            ),
            Question(
                "Si cambias una letra de un documento, su hash SHA-256…",
                listOf("Cambia una letra", "Cambia por completo", "No cambia"),
                1,
                "Es una propiedad clave de los hashes criptográficos.",
            ),
            Question(
                "Alguien de «soporte» te pide tu frase semilla para ayudarte. ¿Qué haces?",
                listOf("Se la doy si parece oficial", "Le doy solo la mitad", "No se la doy nunca: es una estafa"),
                2,
                "Nadie legítimo necesita tu frase semilla.",
            ),
        ),
    ),
    Lesson(
        id = "wallets",
        title = "Wallets: calientes frente a frías",
        subtitle = "Dónde guardar tus criptomonedas",
        level = Level.BASIC,
        minutes = 6,
        blocks = listOf(
            p(
                "Una wallet no guarda monedas: las monedas siempre están en la blockchain. La wallet guarda tus claves privadas, que " +
                    "son las que permiten moverlas. Por eso la pregunta importante es dónde viven esas claves.",
            ),
            h("Caliente frente a fría"),
            Block.Table(
                listOf(
                    listOf("", "Caliente", "Fría"),
                    listOf("Conectada a Internet", "Sí", "No (o casi nunca)"),
                    listOf("Ejemplos", "Apps de móvil, extensiones del navegador", "Dispositivos hardware, papel"),
                    listOf("Comodidad", "Alta: pagos al momento", "Menor: hay que conectarla y confirmar"),
                    listOf("Riesgo principal", "Malware, phishing, robo del móvil", "Perderla o perder la frase semilla"),
                    listOf("Ideal para", "Cantidades pequeñas y uso diario", "Ahorros a largo plazo"),
                ),
            ),
            h("Custodia: ¿quién tiene las claves?"),
            list(
                "Exchange (custodial): el exchange guarda las claves por ti. Es cómodo, pero dependes de él: si quiebra o te bloquea la cuenta, puedes perder el acceso.",
                "Wallet propia (no custodial): las claves son tuyas. Tienes el control total y también toda la responsabilidad.",
            ),
            tip("Hay un dicho en cripto: «not your keys, not your coins» (si no son tus claves, no son tus monedas)."),
            h("Buenas prácticas"),
            list(
                "Apunta la frase semilla en papel o metal y guárdala en un lugar seguro, sin fotos ni copias digitales.",
                "Compra los dispositivos hardware solo en la web oficial del fabricante.",
                "Antes de enviar mucho dinero, prueba con una cantidad pequeña.",
                "Comprueba la dirección completa antes de confirmar: hay malware que la cambia al copiar y pegar.",
            ),
        ),
        summary = listOf(
            "La wallet guarda claves, no monedas.",
            "Caliente: cómoda y conectada; fría: desconectada y más segura.",
            "En un exchange no controlas las claves.",
            "Usa caliente para el día a día y fría para ahorrar.",
        ),
        quiz = listOf(
            Question(
                "¿Qué guarda realmente una wallet?",
                listOf("Las monedas", "Las claves privadas", "Una copia de la blockchain"),
                1,
                "Las monedas están en la blockchain; la wallet guarda las claves que las mueven.",
            ),
            Question(
                "Para ahorros a largo plazo, lo más seguro suele ser…",
                listOf("Una wallet fría", "Dejarlo en un exchange", "Una extensión del navegador"),
                0,
                "Al no estar conectada, una wallet fría está fuera del alcance del malware.",
            ),
            Question(
                "Si dejas tus monedas en un exchange…",
                listOf("Controlas las claves", "El exchange controla las claves", "Nadie controla las claves"),
                1,
                "Es una wallet custodial: dependes del exchange.",
            ),
        ),
    ),
    Lesson(
        id = "ethereum",
        title = "Ethereum y los contratos inteligentes",
        subtitle = "Programas que viven en la blockchain",
        level = Level.INTERMEDIATE,
        minutes = 5,
        blocks = listOf(
            p(
                "Ethereum, propuesto por Vitalik Buterin y lanzado en julio de 2015, añadió a la idea de Bitcoin la posibilidad de " +
                    "ejecutar programas en la blockchain: los contratos inteligentes. Su moneda es el ether (ETH).",
            ),
            h("¿Qué es un contrato inteligente?"),
            p(
                "Es un programa público que se ejecuta exactamente como está escrito, sin que nadie pueda pararlo o cambiarlo a su antojo. " +
                    "Por ejemplo: «si alguien deposita X, entrégale Y». Sobre ellos funcionan los tokens, los exchanges descentralizados, " +
                    "los préstamos DeFi o los NFT.",
            ),
            h("El gas"),
            p(
                "Cada operación consume «gas», una comisión que se paga en ETH a quienes validan la red. Cuando hay mucha demanda, " +
                    "el gas sube. Por eso nacieron las redes de capa 2 (Arbitrum, Optimism, Base…), que agrupan muchas operaciones y las " +
                    "anotan en Ethereum, con comisiones mucho más bajas.",
            ),
            warn("Un contrato inteligente hace lo que dice su código, no lo que promete su web. Un error o una trampa en el código puede hacerte perder fondos."),
        ),
        summary = listOf(
            "Ethereum ejecuta programas: los contratos inteligentes.",
            "El gas es la comisión de cada operación, en ETH.",
            "Las capas 2 abaratan las operaciones.",
        ),
        quiz = listOf(
            Question("¿Cómo se llama la comisión de Ethereum?", listOf("Gas", "Spread", "Royalty"), 0, "Cada operación consume gas, pagado en ETH."),
            Question(
                "¿Para qué sirven las redes de capa 2?",
                listOf("Para minar bitcoin", "Para abaratar y acelerar operaciones", "Para guardar claves"),
                1,
                "Agrupan operaciones y las anotan en Ethereum.",
            ),
            Question(
                "Un contrato inteligente…",
                listOf("Se puede cambiar cuando quiera su autor", "Se ejecuta tal como está escrito su código", "Lo aprueba un banco"),
                1,
                "Por eso un error en el código es tan peligroso.",
            ),
        ),
    ),
    Lesson(
        id = "stablecoins",
        title = "Stablecoins",
        subtitle = "Criptomonedas que intentan valer 1 dólar",
        level = Level.BASIC,
        minutes = 4,
        blocks = listOf(
            p(
                "Una stablecoin es un token que intenta mantener un precio fijo, casi siempre 1 dólar. Sirven para moverse entre " +
                    "criptomonedas sin pasar por el banco, y son la base de casi todo DeFi.",
            ),
            Block.Table(
                listOf(
                    listOf("Tipo", "Cómo mantiene el precio", "Ejemplos"),
                    listOf("Respaldo en dinero", "Reservas en dólares y bonos", "USDT, USDC"),
                    listOf("Respaldo en cripto", "Más cripto bloqueada que la emitida", "DAI"),
                    listOf("Algorítmica", "Reglas de oferta y demanda", "TerraUSD (UST), que colapsó en 2022"),
                ),
            ),
            h("Perder la paridad"),
            p(
                "Si el mercado deja de confiar en el respaldo, el precio puede caer por debajo de 1 $ («depeg»). En mayo de 2022 UST " +
                    "perdió casi todo su valor en pocos días. En la pestaña DeFi → Stablecoins, CriptoPanel marca en rojo las que se " +
                    "alejan más de un 1 % de su paridad.",
            ),
            tip("Una stablecoin solo es tan sólida como su respaldo y quien lo custodia."),
        ),
        summary = listOf(
            "Intentan valer siempre lo mismo, casi siempre 1 $.",
            "El respaldo puede ser dinero, cripto o un algoritmo.",
            "Pueden perder la paridad si falla la confianza.",
        ),
        quiz = listOf(
            Question("¿Qué stablecoin colapsó en 2022?", listOf("USDC", "TerraUSD (UST)", "DAI"), 1, "UST era algorítmica y perdió la paridad en mayo de 2022."),
            Question("¿Cómo se respalda DAI?", listOf("Con dólares en un banco", "Con cripto bloqueada en exceso", "No tiene respaldo"), 1, "Se emite contra más cripto de la que vale."),
            Question("¿Qué significa «depeg»?", listOf("Que sube mucho", "Que pierde su paridad", "Que cambia de nombre"), 1, "Es cuando deja de valer lo que pretende."),
        ),
    ),
    Lesson(
        id = "defi",
        title = "DeFi: TVL, DEX y rendimientos",
        subtitle = "Finanzas sin intermediarios… y sus riesgos",
        level = Level.INTERMEDIATE,
        minutes = 6,
        blocks = listOf(
            p(
                "DeFi (finanzas descentralizadas) son servicios financieros hechos con contratos inteligentes: intercambiar, prestar, " +
                    "pedir prestado o ganar intereses sin un banco de por medio.",
            ),
            h("TVL"),
            p(
                "El TVL (valor total bloqueado) suma lo que los usuarios han depositado en un protocolo o en una red. Es la cifra " +
                    "principal de la pestaña DeFi. Ojo: sube y baja también con el precio de las monedas depositadas, no solo con depósitos " +
                    "y retiradas.",
            ),
            h("DEX"),
            p(
                "Un exchange descentralizado (DEX) como Uniswap no tiene libro de órdenes: usa fondos comunes de liquidez. Quien aporta " +
                    "liquidez cobra parte de las comisiones, pero sufre la «pérdida impermanente» si los precios de las dos monedas se separan.",
            ),
            h("Rendimientos (APY)"),
            list(
                "Base: lo que paga el protocolo con su propia actividad (intereses, comisiones).",
                "Recompensas: tokens extra repartidos para atraer depósitos; suelen bajar con el tiempo.",
                "Un APY muy alto suele indicar mucho riesgo o recompensas temporales.",
            ),
            warn("Riesgos de DeFi: errores en contratos, hackeos, «rug pulls» (los creadores se llevan el dinero), liquidaciones y caída del token de recompensa."),
        ),
        summary = listOf(
            "DeFi son servicios financieros hechos con contratos inteligentes.",
            "TVL = valor depositado; también se mueve con los precios.",
            "Los DEX usan fondos de liquidez.",
            "APY alto suele significar riesgo alto.",
        ),
        quiz = listOf(
            Question("Si ETH baja un 20 % y nadie retira nada, el TVL en dólares de un protocolo con ETH…", listOf("Sube", "Baja", "No cambia"), 1, "El TVL se mide en dólares: baja con el precio."),
            Question("¿Qué es la pérdida impermanente?", listOf("Una comisión fija", "La desventaja de aportar liquidez si los precios se separan", "Un hackeo"), 1, "Afecta a quien aporta liquidez a un DEX."),
            Question("Un pool promete un 900 % APY. Lo más probable es que…", listOf("Sea seguro", "Tenga mucho riesgo o recompensas temporales", "Sea un depósito bancario"), 1, "Rendimientos así no se sostienen sin riesgo."),
        ),
    ),
    Lesson(
        id = "seguridad",
        title = "Seguridad: estafas habituales",
        subtitle = "Cómo reconocerlas y protegerte",
        level = Level.BASIC,
        minutes = 5,
        blocks = listOf(
            h("Las más comunes"),
            list(
                "Phishing: webs, correos o anuncios que imitan un exchange o una wallet para robarte la contraseña o la frase semilla.",
                "Falso soporte: alguien te escribe por redes sociales ofreciendo ayuda y acaba pidiéndote la frase semilla o que conectes tu wallet.",
                "Sorteos falsos: «envía 1 ETH y te devolvemos 2». Nadie devuelve nada.",
                "Inversiones con beneficios garantizados o esquemas piramidales.",
                "Tokens trampa: se pueden comprar pero el contrato impide venderlos.",
                "Romance o «pig butchering»: alguien gana tu confianza durante semanas y te lleva a una plataforma falsa.",
            ),
            h("Reglas de oro"),
            list(
                "Nunca compartas tu frase semilla ni tu clave privada.",
                "Escribe tú la dirección de la web o usa un marcador; no entres desde anuncios.",
                "Activa la verificación en dos pasos con una app (no por SMS) en los exchanges.",
                "Desconfía de cualquier prisa, presión o rentabilidad garantizada.",
                "Revisa qué permisos das al conectar tu wallet a una web y retíralos cuando no los uses.",
            ),
            warn("Las operaciones en blockchain no se pueden deshacer. Si envías dinero a un estafador, normalmente no hay forma de recuperarlo."),
            tip("Si sufres una estafa, denúnciala a la policía y avisa al exchange cuanto antes."),
        ),
        summary = listOf(
            "Nadie legítimo te pedirá tu frase semilla.",
            "Rentabilidad garantizada = señal de alarma.",
            "Las transferencias no se pueden revertir.",
        ),
        quiz = listOf(
            Question("«Envía 1 ETH y te devolvemos 2». Es…", listOf("Una promoción", "Una estafa", "Un airdrop"), 1, "Es uno de los fraudes más antiguos."),
            Question("¿Qué verificación en dos pasos es más segura?", listOf("SMS", "App de autenticación", "Ninguna"), 1, "Los SMS se pueden interceptar con un duplicado de SIM."),
            Question("Si envías cripto a un estafador…", listOf("El banco lo devuelve", "Se puede anular en 24 h", "Normalmente se pierde"), 2, "Las operaciones en blockchain son irreversibles."),
        ),
    ),
    Lesson(
        id = "leer-criptopanel",
        title = "Cómo leer CriptoPanel",
        subtitle = "TOTAL2, dominancia, miedo y codicia…",
        level = Level.INTERMEDIATE,
        minutes = 5,
        blocks = listOf(
            h("Capitalización y TOTAL"),
            p(
                "La capitalización de una moneda es su precio por las unidades en circulación. TOTAL es la suma de todas. TOTAL2 es " +
                    "TOTAL sin bitcoin, y TOTAL3 es TOTAL sin bitcoin ni ether: se usan para ver si el dinero entra en las demás monedas " +
                    "(«altcoins»).",
            ),
            h("Dominancia"),
            p(
                "La dominancia de BTC es el porcentaje de TOTAL que corresponde a bitcoin. Si sube, bitcoin lo está haciendo mejor que " +
                    "el resto; si baja, las altcoins ganan peso.",
            ),
            h("Volumen"),
            p("El volumen de 24 h es cuánto se ha negociado. Un movimiento de precio con mucho volumen suele ser más significativo."),
            h("Miedo y codicia"),
            p(
                "El índice de Alternative.me va de 0 (miedo extremo) a 100 (codicia extrema) y combina volatilidad, volumen, redes " +
                    "sociales y otras señales. Resume el ánimo del mercado; no predice el precio.",
            ),
            h("En tu cartera"),
            p(
                "La ganancia se calcula por precio medio: cada compra suma su coste con comisiones, y cada venta compara lo cobrado con " +
                    "ese precio medio. «Hoy» es cuánto ha cambiado el valor en las últimas 24 h. Más → Cómo calculamos lo explica con detalle.",
            ),
            tip("Ningún indicador predice el futuro: sirven para entender qué está pasando, no para saber qué pasará."),
        ),
        summary = listOf(
            "TOTAL2 y TOTAL3 aíslan las altcoins.",
            "La dominancia compara bitcoin con el resto.",
            "Miedo y codicia mide el ánimo, no el futuro.",
        ),
        quiz = listOf(
            Question("TOTAL3 es…", listOf("TOTAL sin BTC ni ETH", "Las 3 mayores monedas", "TOTAL × 3"), 0, "Excluye bitcoin y ether."),
            Question("Si la dominancia de BTC baja…", listOf("Bitcoin gana peso", "Las demás monedas ganan peso", "No significa nada"), 1, "Bitcoin pesa menos en el total."),
            Question("Un índice de miedo y codicia de 85 indica…", listOf("Miedo extremo", "Codicia extrema", "Que el precio subirá seguro"), 1, "Y no predice nada: solo resume el ánimo."),
        ),
    ),
)

val GLOSSARY: List<Term> = listOf(
    Term("Airdrop", "Reparto gratuito de tokens a ciertas direcciones, a menudo para promocionar un proyecto. Cuidado con los falsos que piden conectar tu wallet."),
    Term("Altcoin", "Cualquier criptomoneda distinta de bitcoin."),
    Term("APY", "Rentabilidad anual con interés compuesto. En DeFi es variable."),
    Term("ATH", "Máximo histórico (all-time high) del precio."),
    Term("Blockchain", "Registro público dividido en bloques encadenados por sus hashes."),
    Term("Bloque", "Grupo de operaciones que se añade a la blockchain de una vez."),
    Term("Capa 2", "Red que agrupa operaciones y las anota en otra blockchain (como Ethereum) para abaratarlas."),
    Term("Capitalización", "Precio por unidades en circulación."),
    Term("Clave privada", "Número secreto que permite firmar y gastar. Nunca se comparte."),
    Term("Clave pública", "Se calcula a partir de la privada y sirve para comprobar firmas."),
    Term("Cold wallet", "Wallet fría: guarda las claves sin conexión a Internet."),
    Term("Consenso", "Método por el que los nodos se ponen de acuerdo en el siguiente bloque."),
    Term("Contrato inteligente", "Programa que se ejecuta en la blockchain tal como está escrito."),
    Term("Custodial", "Servicio que guarda tus claves por ti, como un exchange."),
    Term("DCA", "Invertir la misma cantidad cada cierto tiempo, sin importar el precio."),
    Term("DeFi", "Finanzas descentralizadas: servicios financieros hechos con contratos inteligentes."),
    Term("Depeg", "Cuando una stablecoin pierde su paridad."),
    Term("DEX", "Exchange descentralizado que funciona con contratos y fondos de liquidez."),
    Term("Dirección", "Identificador público para recibir criptomonedas."),
    Term("Dominancia", "Porcentaje de la capitalización total que corresponde a una moneda."),
    Term("Exchange", "Plataforma para comprar y vender criptomonedas."),
    Term("Frase semilla", "12 o 24 palabras que recrean todas las claves de una wallet."),
    Term("Gas", "Comisión por operación en Ethereum y redes parecidas."),
    Term("Halving", "Reducción a la mitad de la recompensa por bloque de bitcoin, cada 210.000 bloques (unos 4 años)."),
    Term("Hash", "Huella digital de longitud fija de cualquier dato."),
    Term("Hot wallet", "Wallet caliente: conectada a Internet, cómoda pero más expuesta."),
    Term("Liquidez", "Facilidad para comprar o vender sin mover mucho el precio."),
    Term("Minería", "Proceso de prueba de trabajo para proponer bloques y ganar recompensas."),
    Term("NFT", "Token no fungible: un token único, a menudo ligado a una obra o un objeto digital."),
    Term("Nodo", "Ordenador que guarda la blockchain y comprueba sus reglas."),
    Term("Pérdida impermanente", "Desventaja de aportar liquidez a un DEX cuando los precios se separan."),
    Term("Phishing", "Engaño que imita una web o un servicio real para robarte datos."),
    Term("Prueba de participación", "Consenso en el que validadores bloquean monedas como garantía."),
    Term("Prueba de trabajo", "Consenso en el que mineros compiten con potencia de cálculo."),
    Term("Rug pull", "Estafa en la que los creadores de un proyecto se llevan el dinero de los inversores."),
    Term("Satoshi", "La unidad más pequeña de bitcoin: 0,00000001 BTC."),
    Term("Stablecoin", "Token que intenta mantener un precio fijo, normalmente 1 $."),
    Term("Staking", "Bloquear monedas para participar en la validación y recibir recompensas."),
    Term("Token", "Activo que vive dentro de otra blockchain."),
    Term("TVL", "Valor total bloqueado en un protocolo o red DeFi."),
    Term("Volatilidad", "Cuánto y qué tan rápido varía un precio."),
    Term("Wallet", "Aplicación o dispositivo que guarda tus claves privadas."),
)

fun lessonById(id: String): Lesson? = LESSONS.firstOrNull { it.id == id }

fun searchGlossary(query: String): List<Term> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return GLOSSARY
    return GLOSSARY.filter { q in it.name.lowercase() || q in it.definition.lowercase() }
}
