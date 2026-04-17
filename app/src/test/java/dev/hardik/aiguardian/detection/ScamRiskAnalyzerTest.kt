package dev.hardik.aiguardian.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamRiskAnalyzerTest {

    private val analyzer = ScamRiskAnalyzer()

    @Test
    fun `flags irs style pressure and otp demand as severe`() {
        val analysis = analyzer.analyze(
            "This is officer Martin from the IRS. Do not hang up. " +
                "Verify your account number and OTP immediately or police will arrest you."
        )

        assertEquals(ThreatLevel.SEVERE, analysis.level)
        assertTrue(analysis.score >= 70)
        assertTrue(analysis.reasons.isNotEmpty())
    }

    @Test
    fun `keeps normal family conversation safe`() {
        val analysis = analyzer.analyze(
            "Hi grandma, I reached home safely and will call you after dinner."
        )

        assertEquals(ThreatLevel.SAFE, analysis.level)
        assertTrue(analysis.score < 20)
    }
}
