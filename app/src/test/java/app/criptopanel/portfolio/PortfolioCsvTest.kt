package app.criptopanel.portfolio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioCsvTest {
    @Test fun `round trip keeps every field`() {
        val ops = listOf(
            buy(0.12345678, 98123.45, fee = 1.5).copy(note = "Binance, \"DCA\" mensual"),
            sell(0.1, 100000.0).copy(name = "Bitcoin"),
        )
        val back = (parseCsv(ops.toCsv(), emptySet()) as CsvImport.Ok).operations
        assertEquals(ops.chronological(), back.map { it.copy(imageUrl = null) })
    }

    @Test fun `quotes protect commas`() {
        val csv = buy(1.0, 2.0).copy(note = "a,b").let { listOf(it).toCsv() }
        assertTrue(csv.lines()[1].contains("\"a,b\""))
    }

    @Test fun `one bad row imports nothing and names the line`() {
        val csv = listOf(buy(1.0, 2.0), buy(1.0, 3.0)).toCsv().replace(",3.0,", ",abc,")
        assertEquals(CsvImport.Error(3, "Precio no válido"), parseCsv(csv, emptySet()))
    }

    @Test fun `known ids are skipped`() {
        val a = buy(1.0, 2.0)
        val b = buy(1.0, 3.0)
        val result = parseCsv(listOf(a, b).toCsv(), setOf(a.id)) as CsvImport.Ok
        assertEquals(listOf(b.id), result.operations.map { it.id })
        assertEquals(1, result.skipped)
    }

    @Test fun `wrong header is refused`() {
        assertEquals(1, (parseCsv("a,b,c\n1,2,3", emptySet()) as CsvImport.Error).line)
    }

    @Test fun `decimal comma and missing id are accepted`() {
        val csv = "fecha,tipo,moneda_id,simbolo,nombre,cantidad,precio_usd,comision_usd,nota\n" +
            "2026-01-01,compra,bitcoin,btc,Bitcoin,\"0,5\",100,,\n"
        val op = (parseCsv(csv, emptySet()) as CsvImport.Ok).operations.single()
        assertEquals(0.5, op.quantity, 0.0)
        assertEquals("BTC", op.symbol)
        assertEquals(0.0, op.feeUsd, 0.0)
        assertTrue(op.id.isNotBlank())
    }

    @Test fun `future dates are refused`() {
        val csv = listOf(buy(1.0, 1.0, date = "2999-01-01")).toCsv()
        assertEquals("La fecha es futura", (parseCsv(csv, emptySet()) as CsvImport.Error).reason)
    }
}
