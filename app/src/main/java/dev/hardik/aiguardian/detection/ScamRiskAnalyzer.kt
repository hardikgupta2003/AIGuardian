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
    val isPreparingModel: Boolean = false,
    val modelError: String? = null,
    val activePhoneNumber: String = "No active call",
    val lastTranscript: String = "Waiting for speech…",
    val score: Int = 0,
    val level: ThreatLevel = ThreatLevel.SAFE,
    val reasons: List<String> = emptyList(),
    val severeActionTaken: Boolean = false,
    val lastUpdateMs: Long = System.currentTimeMillis()
)

class ScamRiskAnalyzer {

    // Common Vosk mishearings mapped to actual scam keywords
    // This handles cases where STT produces phonetic spellings
    private val phoneticCorrections = mapOf(
        "oh tee pee" to "otp",
        "o t p" to "otp",
        "o tp" to "otp",
        "ot p" to "otp",
        "see vee vee" to "cvv",
        "c v v" to "cvv",
        "cv v" to "cvv",
        "c vv" to "cvv",
        "you pee eye" to "upi",
        "u p i" to "upi",
        "aye are yes" to "irs",
        "i r s" to "irs",
        "add har" to "aadhar",
        "aadhar card" to "aadhar",
        "adhar" to "aadhar",
        "pan card number" to "pan card",
        "sea bee eye" to "cbi",
        "c b i" to "cbi",
        "any desk" to "anydesk",
        "team viewer" to "teamviewer",
        "quick support" to "quicksupport",
        "pay tm" to "paytm",
        "phone pe" to "phonepe",
        "gee pay" to "gpay",
        "g pay" to "gpay",
        "net ban king" to "net banking",
        "debit card" to "debit card",
        "credit card" to "credit card",
        "gift card" to "gift card",
        "a count number" to "account number",
        "account number" to "account number",
        "bank detail" to "bank details",
        "wire transfer" to "wire transfer",
        "digital arrest" to "digital arrest",
        "bijli ka bill" to "bijli bill",
        "bijlee bill" to "bijli bill"
    )

    private val impersonationSignals = listOf(
        "irs", "income tax", "tax department", "police", "cyber cell", "bank manager",
        "government", "court", "social security", "customs", "federal", "officer",
        "cbi", "crime branch", "trai", "fedex", "kbc", "customer care",
        "reserve bank", "rbi", "ministry", "telecom authority", "regulatory"
    )

    private val urgencySignals = listOf(
        "urgent", "right now", "immediately", "do not hang up", "don't hang up",
        "stay on the line", "within minutes", "final warning", "act now", "turant",
        "jaldi", "abhi", "fauran", "last chance", "time is running out",
        "hurry up", "don't delay"
    )

    private val sensitiveDataSignals = listOf(
        "otp", "one time password", "pin", "cvv", "card number", "debit card", "account details",
        "credit card", "bank details", "account number", "net banking", "upi pin",
        "pan card", "aadhar", "khata", "password", "login details", "verify your identity",
        "confirm your details", "social security number", "mother maiden name"
    )

    private val paymentSignals = listOf(
        "gift card", "wire transfer", "bank transfer", "crypto", "bitcoin",
        "wallet transfer", "send money", "payment app", "paytm", "phonepe", "gpay",
        "google pay", "paisa", "paise", "rupee", "cashback", "refund",
        "recharge", "upi transfer", "neft", "rtgs", "imps"
    )

    private val fearSignals = listOf(
        "arrest", "warrant", "freeze your account", "account blocked", "legal action",
        "jail", "police case", "penalty", "suspended", "digital arrest", "fir",
        "parcel block", "electricity bill", "bijli bill", "court order",
        "imprisonment", "confiscate", "seize", "blacklisted"
    )

    private val familyEmergencySignals = listOf(
        "grandson", "granddaughter", "your grandson", "your granddaughter",
        "accident", "hospital", "bail money", "family emergency", "mom fell",
        "son in trouble", "kidnapped", "ransom"
    )
    
    private val techScamSignals = listOf(
        "download apk", "anydesk", "teamviewer", "quicksupport", "screen share", "link open",
        "install app", "remote access", "download this", "click the link",
        "share screen", "mirror screen"
    )

    fun analyze(windowText: String): ScamAnalysis {
        var normalized = windowText.lowercase().trim()
        if (normalized.isBlank()) {
            return ScamAnalysis(
                score = 0,
                level = ThreatLevel.SAFE,
                reasons = emptyList(),
                windowText = windowText
            )
        }

        // Apply phonetic corrections before analysis
        normalized = applyPhoneticCorrections(normalized)

        var score = 0
        val reasons = linkedSetOf<String>()

        score += applySignals(normalized, impersonationSignals, 18, "Caller is impersonating an authority", reasons)
        score += applySignals(normalized, urgencySignals, 14, "Caller is using high-pressure urgency", reasons)
        score += applySignals(normalized, sensitiveDataSignals, 24, "Caller is asking for sensitive financial data", reasons)
        score += applySignals(normalized, paymentSignals, 20, "Caller is pushing unusual payment methods", reasons)
        score += applySignals(normalized, fearSignals, 16, "Caller is threatening punishment or account loss", reasons)
        score += applySignals(normalized, familyEmergencySignals, 18, "Caller is using a family-emergency scam pattern", reasons)
        score += applySignals(normalized, techScamSignals, 22, "Caller is instructing to download remote-access apps", reasons)

        // Combo bonuses — multiple categories = much more likely scam
        if (containsAny(normalized, impersonationSignals) && containsAny(normalized, sensitiveDataSignals)) {
            score += 20
            reasons += "Authority claim combined with credential request"
        }

        if (containsAny(normalized, urgencySignals) && containsAny(normalized, paymentSignals + sensitiveDataSignals)) {
            score += 14
            reasons += "Urgency combined with money or credential request"
        }

        if (containsAny(normalized, fearSignals) && containsAny(normalized, sensitiveDataSignals)) {
            score += 16
            reasons += "Fear tactics combined with data requests"
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

    /**
     * Apply phonetic corrections to handle common Vosk mishearings.
     * For example, Vosk may transcribe "OTP" as "oh tee pee" — this
     * normalizes those spellings back to the intended keyword.
     */
    private fun applyPhoneticCorrections(text: String): String {
        var corrected = text
        for ((mishearing, correction) in phoneticCorrections) {
            corrected = corrected.replace(mishearing, correction)
        }
        return corrected
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
