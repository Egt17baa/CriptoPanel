package app.criptopanel.portfolio.binance

import app.criptopanel.portfolio.Operation
import app.criptopanel.portfolio.OperationType
import app.criptopanel.portfolio.firstShortfall
import app.criptopanel.portfolio.positions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TRADES = """"Date(UTC)","Pair","Side","Price","Executed","Amount","Fee"
"2024-03-01 10:00:00","BTCUSDT","BUY","60000","0.01000000BTC","600.00000000USDT","0.00001000BTC"
"2024-03-05 12:00:00","ETHBTC","BUY","0.05","1.00000000ETH","0.05000000BTC","0.00075000BNB"
"2024-04-01 09:30:00","BTCUSDT","SELL","70000","0.00500000BTC","350.00000000USDT","0.35000000USDT"
"2024-04-02 09:30:00","1INCHUSDT","BUY","0.5","100.01INCH","50USDT","0.11INCH"
"""

private const val STATEMENT = """User_ID,UTC_Time,Account,Operation,Coin,Change,Remark
1,2024-03-01 10:00:00,Spot,Buy,BTC,0.01,
1,2024-03-01 10:00:00,Spot,Buy,USDT,-600,
1,2024-03-01 10:00:00,Spot,Fee,BTC,-0.00001,
1,2024-03-02 08:00:00,Spot,Binance Convert,ETH,1,
1,2024-03-02 08:00:00,Spot,Binance Convert,BTC,-0.005,
1,2024-03-03 08:00:00,Spot,Deposit,SOL,10,
1,2024-03-04 08:00:00,Earn,Simple Earn Flexible Interest,BNB,0.01,
1,2024-03-05 08:00:00,Spot,Transfer Between Main and Funding Wallet,USDT,-10,
1,2024-03-06 08:00:00,Spot,Buy Crypto,ADA,100,
1,2024-03-07 08:00:00,USDT-Futures,Realized Profit and Loss,USDT,5,
1,2024-03-08 08:00:00,Spot,Transaction Sold,SOL,-5,
1,2024-03-08 08:00:00,Spot,Transaction Revenue,USDT,750,
1,2024-03-08 08:00:00,Spot,Transaction Fee,USDT,-0.75,
1,2024-03-09 08:00:00,Spot,Binance Convert,ETH,1,
1,2024-03-09 08:00:00,Spot,Binance Convert,BTC,-0.01,
1,2024-03-09 08:00:00,Spot,Binance Convert,SOL,-1,
"""

private val COINS = mapOf(
    "BTC" to CoinInfo("bitcoin", "btc", "Bitcoin", null),
    "ETH" to CoinInfo("ethereum", "eth", "Ethereum", null),
    "SOL" to CoinInfo("solana", "sol", "Solana", null),
    "ADA" to CoinInfo("cardano", "ada", "Cardano", null),
    "BNB" to CoinInfo("binancecoin", "bnb", "BNB", null),
    "1INCH" to CoinInfo("1inch", "1inch", "1inch", null),
)

private fun usd(asset: String, day: LocalDate): Double? = when (asset) {
    "USDT", "USDC", "USD" -> 1.0
    "EUR" -> 1.1
    "BTC" -> if (day < LocalDate.of(2024, 3, 15)) 62_000.0 else 70_000.0
    "BNB" -> 400.0
    "ETH" -> 3_100.0
    "SOL" -> 150.0
    "ADA" -> 0.6
    else -> null
}

private fun converter() = BinanceConverter({ COINS[it] }, { a, d -> usd(a, d) }, ZoneId.of("UTC"))

private fun close(a: Double, b: Double) = assertEquals(a, b, 1e-6)

class BinanceImportTest {
    @Test fun `numbers, amounts with the coin attached, pairs and dates`() {
        close(1234.5, parseNumber("1,234.5")!!)
        close(1234.5, parseNumber("1.234,5")!!)
        close(0.5, parseNumber("0,5")!!)
        close(1234.0, parseNumber("1,234")!!)
        close(0.0015, amountOf("0.00150000BTC", "BTC")!!)
        assertEquals(Amount("BNB", 0.75), splitAmount("0.75BNB"))
        assertEquals(Amount("1INCH", 100.0), splitAmount("100.01INCH", listOf("1INCH", "USDT")))
        assertEquals("ETH" to "BTC", splitPair("ETHBTC"))
        assertEquals("BTC" to "FDUSD", splitPair("BTCFDUSD"))
        assertEquals("SOL" to "EUR", splitPair("SOL/EUR"))
        assertEquals(LocalDateTime.of(2024, 3, 1, 10, 0), parseTime("2024-03-01 10:00:00"))
        assertEquals(LocalDateTime.of(2021, 6, 14, 3, 12, 45), parseTime("21-06-14 03:12:45"))
        assertEquals(LocalDateTime.of(2024, 1, 1, 12, 0), parseTime("45292.5")) // Excel serial date.
    }

    @Test fun `CSV with quotes, semicolons and line breaks inside quotes`() {
        assertEquals(listOf(listOf("a", "b;c"), listOf("1", "x\ny")), readCsv("a;\"b;c\"\r\n1;\"x\ny\"\n\n"))
        assertEquals(listOf(listOf("a", "b \"q\"")), readCsv("a,\"b \"\"q\"\"\""))
    }

    @Test fun `trade history - buys, sells, a crypto pair and fees`() {
        val p = parseBinance(readCsv(TRADES))
        assertEquals(BinanceFormat.TRADES, p.format)
        assertEquals(4, p.swaps.size)
        assertEquals(Amount("BTC", 0.01), p.swaps[0].received)
        assertEquals(Amount("USDT", 600.0), p.swaps[0].spent)
        assertEquals(Amount("BTC", 0.00001), p.swaps[0].fee)
        assertEquals(Amount("BTC", 0.05), p.swaps[1].spent)
        assertEquals(Amount("USDT", 350.0), p.swaps[2].received) // A sell receives the quote.
        assertEquals(Amount("1INCH", 100.0), p.swaps[3].received)
        assertEquals(Amount("1INCH", 0.1), p.swaps[3].fee)
    }

    @Test fun `spanish headers and the old format are recognised`() {
        val es = "Fecha(UTC);Par;Lado;Precio;Ejecutado;Importe;Comisión\n2024-03-01 10:00:00;BTCEUR;COMPRA;55000;0,01BTC;550EUR;0,55EUR\n"
        val p = parseBinance(readCsv(es))
        assertEquals(Swap(LocalDateTime.of(2024, 3, 1, 10, 0), Amount("BTC", 0.01), Amount("EUR", 550.0), Amount("EUR", 0.55), p.swaps[0].ref), p.swaps[0])
        val old = "Date(UTC),Market,Type,Price,Amount,Total,Fee,Fee Coin\n2019-01-02 03:04:05,ETHBTC,BUY,0.03,2,0.06,0.0015,BNB\n"
        val o = parseBinance(readCsv(old))
        assertEquals(BinanceFormat.TRADES_OLD, o.format)
        assertEquals(Amount("ETH", 2.0), o.swaps[0].received)
        assertEquals(Amount("BTC", 0.06), o.swaps[0].spent)
        assertEquals(Amount("BNB", 0.0015), o.swaps[0].fee)
    }

    @Test fun `statement - trades grouped by second, rewards, card buys, ignored and ambiguous rows`() {
        val p = parseBinance(readCsv(STATEMENT))
        assertEquals(BinanceFormat.STATEMENT, p.format)
        assertEquals(3, p.swaps.size)
        assertEquals(Amount("BTC", 0.01), p.swaps[0].received)
        assertEquals(Amount("BTC", 0.00001), p.swaps[0].fee)
        assertEquals(Amount("ETH", 1.0), p.swaps[1].received) // Convert BTC → ETH.
        assertEquals(Amount("USDT", 750.0), p.swaps[2].received) // SOL sold for USDT.
        assertEquals(Amount("USDT", 0.75), p.swaps[2].fee)
        assertEquals(listOf(Amount("ADA", 100.0)), p.marketBuys.map { it.amount })
        assertEquals(listOf(Amount("BNB", 0.01)), p.rewards.map { it.amount })
        assertEquals(mapOf("Depósitos y retiradas" to 1, "Transferencias entre cuentas" to 1, "Futuros y margen" to 1), p.ignored)
        assertEquals(3, p.unrecognized) // Three coins in one second: not split by guessing.
    }

    @Test fun `files that are not Binance exports are refused clearly`() {
        try {
            parseBinance(readCsv("fecha,tipo,moneda_id\n2024-01-01,compra,bitcoin\n"))
            fail()
        } catch (e: BinanceFormatException) {
            // expected
        }
    }

    @Test fun `conversion - cost with the fee, crypto pairs as two legs and opening balances`() = runTest {
        val plan = converter().convert(parseBinance(readCsv(TRADES)), emptyList())
        val ops = plan.operations
        val btcBuy = ops.first { it.coinId == "bitcoin" && it.type == OperationType.BUY && it.note.startsWith("Binance BTC") }
        close(0.00999, btcBuy.quantity) // The fee in BTC was taken from what arrived.
        close(600.0, btcBuy.quantity * btcBuy.priceUsd + btcBuy.feeUsd) // But the full 600 $ is the cost.
        val ethBuy = ops.first { it.coinId == "ethereum" }
        close(1.0, ethBuy.quantity)
        close(3_100.0, ethBuy.priceUsd) // 0.05 BTC at 62 000 $.
        close(0.3, ethBuy.feeUsd) // 0.00075 BNB at 400 $.
        val btcForEth = ops.first { it.coinId == "bitcoin" && it.type == OperationType.SELL && it.date == "2024-03-05" && !it.id.endsWith("saldo") }
        close(0.05, btcForEth.quantity)
        close(62_000.0, btcForEth.priceUsd)
        val sell = ops.first { it.type == OperationType.SELL && it.date == "2024-04-01" }
        close(70_000.0, sell.priceUsd)
        close(0.35, sell.feeUsd)
        val oneInch = ops.first { it.coinId == "1inch" }
        close(99.9, oneInch.quantity)
        // More BTC was spent than bought in the file: opening balances fill the gap, so nothing goes negative.
        assertEquals(2, plan.openingBalances)
        assertNull(firstShortfall(ops))
        assertTrue(positions(ops).none { it.quantity < 0 })
        assertEquals(3, plan.buys) // BTC, ETH and 1INCH (the opening balances are counted apart).
        assertEquals(2, plan.sells)
        assertEquals(3, plan.coins)
    }

    @Test fun `statement conversion - rewards free, card buys at market price, euros`() = runTest {
        val plan = converter().convert(parseBinance(readCsv(STATEMENT)), emptyList())
        val reward = plan.operations.first { it.coinId == "binancecoin" }
        close(0.0, reward.priceUsd)
        assertTrue(reward.note.contains("recompensa"))
        val ada = plan.operations.first { it.coinId == "cardano" }
        close(0.6, ada.priceUsd)
        assertEquals(1, plan.rewards)
        val eur = "Date(UTC),Pair,Side,Price,Executed,Amount,Fee\n2024-03-01 10:00:00,BTCEUR,BUY,55000,0.01BTC,550EUR,0.55EUR\n"
        val e = converter().convert(parseBinance(readCsv(eur)), emptyList()).operations.single()
        close(55_000 * 1.1, e.priceUsd)
        close(0.55 * 1.1, e.feeUsd)
    }

    @Test fun `importing the same file again adds nothing, and unknown coins are reported`() = runTest {
        val parse = parseBinance(readCsv(TRADES + "\"2024-04-03 09:30:00\",\"XYZUSDT\",\"BUY\",\"1\",\"5XYZ\",\"5USDT\",\"\"\n"))
        val first = converter().convert(parse, emptyList())
        assertEquals(setOf("XYZ"), first.unknownSymbols)
        val again = converter().convert(parse, first.operations)
        assertEquals(emptyList<Operation>(), again.operations)
        assertEquals(first.operations.map { it.id }, converter().convert(parse, emptyList()).operations.map { it.id })
    }

    @Test fun `an Excel export is read like the CSV`() {
        val sheet = """<?xml version="1.0"?><worksheet><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="inlineStr"><is><t>Side</t></is></c>
            <c r="D1" t="s"><v>2</v></c><c r="E1" t="s"><v>3</v></c><c r="F1" t="s"><v>4</v></c><c r="G1" t="s"><v>5</v></c></row>
            <row r="2"><c r="A2"><v>45352.41666666666</v></c><c r="B2" t="inlineStr"><is><t>BTCUSDT</t></is></c><c r="C2" t="inlineStr"><is><t>BUY</t></is></c>
            <c r="D2"><v>60000</v></c><c r="E2" t="inlineStr"><is><t>0.01BTC</t></is></c><c r="F2" t="inlineStr"><is><t>600USDT</t></is></c>
            <c r="G2" t="inlineStr"><is><t>0.6USDT</t></is></c></row></sheetData></worksheet>"""
        val strings = """<?xml version="1.0"?><sst><si><t>Date(UTC)</t></si><si><t>Pair</t></si><si><t>Price</t></si>
            <si><t>Executed</t></si><si><t>Amount</t></si><si><t>Fee</t></si></sst>"""
        val p = parseBinance(readSheet(xlsx(sheet, strings)))
        assertEquals(LocalDateTime.of(2024, 3, 1, 10, 0), p.swaps.single().time)
        assertEquals(Amount("BTC", 0.01), p.swaps.single().received)
        assertEquals(Amount("USDT", 0.6), p.swaps.single().fee)
    }

    @Test fun `Excel files with a DTD are refused (no external entities)`() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><worksheet><sheetData><row><c t="inlineStr"><is><t>&e;</t></is></c></row></sheetData></worksheet>"""
        try {
            readSheet(xlsx(evil, null))
            fail()
        } catch (e: SheetException) {
            // expected
        }
    }

    private fun xlsx(sheet: String, strings: String?): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheet.toByteArray())
            if (strings != null) {
                zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
                zip.write(strings.toByteArray())
            }
        }
        return out.toByteArray()
    }
}
