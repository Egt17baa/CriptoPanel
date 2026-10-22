package app.criptopanel.market

import app.criptopanel.decodeFixture

class FakeSentimentApi : SentimentApi {
    var calls = 0

    override suspend fun fearGreed(limit: Int): FearGreedResponseDto {
        calls++
        return decodeFixture("fng.json")
    }
}
