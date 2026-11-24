package com.guturner.longbox.domain.model

/**
 * Issue "numbers" are strings in the wild: "1", "0", "½", "1.MU", "12AU", "1000000",
 * "Annual 1", "-1". This orders them the way a longbox is filed: numerically first,
 * then by any suffix, with purely textual numbers last.
 */
data class IssueNumber(val raw: String) : Comparable<IssueNumber> {

    private val numeric: Double? = parseLeadingNumber(raw)
    private val suffix: String = raw.drop(leadingNumberLength(raw)).trim().lowercase()

    override fun compareTo(other: IssueNumber): Int {
        val a = numeric
        val b = other.numeric
        return when {
            a != null && b != null -> a.compareTo(b).takeIf { it != 0 } ?: suffix.compareTo(other.suffix)
            a != null -> -1
            b != null -> 1
            else -> raw.lowercase().compareTo(other.raw.lowercase())
        }
    }

    override fun toString(): String = raw

    private companion object {

        // Fractions first so "1/2" isn't read as the integer 1 followed by "/2".
        private val number = Regex("""^\s*(-?\d*\s*1/2|½|-?\d+(?:\.\d+)?)""")

        fun leadingNumberLength(raw: String): Int = number.find(raw)?.value?.length ?: 0

        fun parseLeadingNumber(raw: String): Double? {
            val token = number.find(raw)?.groupValues?.get(1)?.replace(" ", "") ?: return null
            return when {
                token == "½" -> 0.5
                token.endsWith("1/2") -> {
                    val whole = token.removeSuffix("1/2")
                    when (whole) {
                        "" -> 0.5
                        "-" -> -0.5
                        else -> whole.toDoubleOrNull()?.let { if (it < 0) it - 0.5 else it + 0.5 }
                    }
                }
                else -> token.toDoubleOrNull()
            }
        }
    }
}
