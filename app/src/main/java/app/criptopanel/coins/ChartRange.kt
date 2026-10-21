package app.criptopanel.coins

enum class ChartRange(val days: String, val label: String) {
    D1("1", "24 h"),
    W1("7", "7 d"),
    M1("30", "30 d"),
    Y1("365", "1 año"),
}
