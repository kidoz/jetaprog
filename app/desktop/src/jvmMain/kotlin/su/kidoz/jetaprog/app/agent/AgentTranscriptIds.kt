package su.kidoz.jetaprog.app.agent

/** Keeps valid saved identities, repairing duplicates without stealing another message's saved ID. */
internal fun uniqueTranscriptIds(
    preferredIds: List<String?>,
    fallbackPrefix: String,
): List<String> {
    val reserved = preferredIds.filterNotNull().filter { it.isNotBlank() }.toSet()
    val used = mutableSetOf<String>()
    return preferredIds.mapIndexed { index, preferred ->
        if (!preferred.isNullOrBlank() && used.add(preferred)) {
            preferred
        } else {
            val base = "$fallbackPrefix-$index"
            var candidate = base
            var suffix = 0
            while (candidate in reserved || candidate in used) candidate = "$base-${++suffix}"
            used += candidate
            candidate
        }
    }
}
