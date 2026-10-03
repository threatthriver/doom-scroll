package com.securemessage.app.data.firebase

/**
 * Firestore decodes all integers as [Long] (and leaves boxed types generic), so mapping a
 * document field with a blind `as? Map<String, Int>` throws ClassCastException at runtime.
 * These coercions normalise raw Firestore data into the app's model types.
 */
internal object FirestoreCoerce {

    @Suppress("UNCHECKED_CAST")
    fun stringList(raw: Any?): List<String> =
        (raw as? List<*>)?.mapNotNull { it as? String }.orEmpty()

    fun stringMap(raw: Any?): Map<String, String> =
        (raw as? Map<*, *>)
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? String ?: return@mapNotNull null
                key to value
            }
            ?.toMap()
            .orEmpty()

    fun booleanMap(raw: Any?): Map<String, Boolean> =
        (raw as? Map<*, *>)
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val flag = v as? Boolean ?: return@mapNotNull null
                key to flag
            }
            ?.toMap()
            .orEmpty()

    /** Accepts Long/Int/Double, as Firestore may hand back any numeric width. */
    fun intMap(raw: Any?): Map<String, Int> =
        (raw as? Map<*, *>)
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val num = v as? Number ?: return@mapNotNull null
                key to num.toInt()
            }
            ?.toMap()
            .orEmpty()

    fun bool(raw: Any?): Boolean = raw as? Boolean ?: false
}