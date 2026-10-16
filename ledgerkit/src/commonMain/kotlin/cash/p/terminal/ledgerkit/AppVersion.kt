package cash.p.terminal.ledgerkit

/** Compares dotted numeric versions part by part; suffixes such as "-rc" are ignored. */
internal fun isVersionAtLeast(actual: String, required: String): Boolean {
    val actualParts = versionParts(actual)
    val requiredParts = versionParts(required)
    for (index in 0 until maxOf(actualParts.size, requiredParts.size)) {
        val comparison = actualParts.getOrElse(index) { 0 }.compareTo(requiredParts.getOrElse(index) { 0 })
        if (comparison != 0) return comparison > 0
    }
    return true
}

private fun versionParts(version: String): List<Int> =
    version.substringBefore('-').substringBefore('+').split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
