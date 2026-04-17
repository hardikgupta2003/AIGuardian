package dev.hardik.aiguardian.detection

enum class ThreatLevel {
    SAFE,
    CAUTION,
    HIGH,
    SEVERE
}

data class TranscriptSegment(
    val text: String,
    val isFinal: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

data class ScamAnalysis(
    val score: Int,
    val level: ThreatLevel,
    val reasons: List<String>,
    val windowText: String
)

data class ScamProtectionState(
    val isMonitoring: Boolean = false,
    val modelReady: Boolean = false,
    val activePhoneNumber: String = "No active call",
    val lastTranscript: String = "Waiting for speech…",
    val score: Int = 0,
    val level: ThreatLevel = ThreatLevel.SAFE,
    val reasons: List<String> = emptyList(),
    val severeActionTaken: Boolean = false,
    val lastUpdateMs: Long = System.currentTimeMillis()
)

class ScamRiskAnalyzer {

    private val impersonationSignals = listOf(
        "irs", "income tax", "tax department", "police", "cyber cell", "bank manager",
        "government", "court", "social security", "customs", "federal", "officer"
    )

    private val urgencySignals = listOf(
        "urgent", "right now", "immediately", "do not hang up", "don't hang up",
        "stay on the line", "within minutes", "final warning", "act now"
    )

    private val sensitiveDataSignals = listOf(
        "otp", "one time password", "pin", "cvv", "card number", "debit card",
        "credit card", "bank details", "account number", "net banking", "upi pin"
    )

    private val paymentSignals = listOf(
        "gift card", "wire transfer", "bank transfer", "crypto", "bitcoin",
        "wallet transfer", "send money", "payment app"
    )

    private val fearSignals = listOf(
        "arrest", "warrant", "freeze your account", "account blocked", "legal action",
        "jail", "police case", "penalty", "suspended"
    )

    private val familyEmergencySignals = listOf(
        "grandson", "granddaughter", "your grandson", "your granddaughter",
        "accident", "hospital", "bail money", "family emergency", "mom fell"
    )

    fun analyze(windowText: String): ScamAnalysis {
        val normalized = windowText.lowercase().trim()
        if (normalized.isBlank()) {
            return ScamAnalysis(
                score = 0,
                level = ThreatLevel.SAFE,
                reasons = emptyList(),
                windowText = windowText
            )
        }

        var score = 0
        val reasons = linkedSetOf<String>()

        score += applySignals(normalized, impersonationSignals, 18, "Caller is impersonating an authority", reasons)
        score += applySignals(normalized, urgencySignals, 14, "Caller is using high-pressure urgency", reasons)
        score += applySignals(normalized, sensitiveDataSignals, 24, "Caller is asking for sensitive financial data", reasons)
        score += applySignals(normalized, paymentSignals, 20, "Caller is pushing unusual payment methods", reasons)
        score += applySignals(normalized, fearSignals, 16, "Caller is threatening punishment or account loss", reasons)
        score += applySignals(normalized, familyEmergencySignals, 18, "Caller is using a family-emergency scam pattern", reasons)

        if (containsAny(normalized, impersonationSignals) && containsAny(normalized, sensitiveDataSignals)) {
            score += 20
            reasons += "Authority claim combined with credential request"
        }

        if (containsAny(normalized, urgencySignals) && containsAny(normalized, paymentSignals + sensitiveDataSignals)) {
            score += 14
            reasons += "Urgency combined with money or credential request"
        }

        val level = when {
            score >= 70 -> ThreatLevel.SEVERE
            score >= 45 -> ThreatLevel.HIGH
            score >= 20 -> ThreatLevel.CAUTION
            else -> ThreatLevel.SAFE
        }

        return ScamAnalysis(
            score = score.coerceAtMost(100),
            level = level,
            reasons = reasons.toList(),
            windowText = windowText
        )
    }

    private fun applySignals(
        text: String,
        signals: List<String>,
        points: Int,
        reason: String,
        reasons: LinkedHashSet<String>
    ): Int {
        val hits = signals.count { text.contains(it) }
        if (hits == 0) return 0
        reasons += reason
        return points + ((hits - 1) * 4)
    }

    private fun containsAny(text: String, signals: List<String>): Boolean {
        return signals.any(text::contains)
    }
}
