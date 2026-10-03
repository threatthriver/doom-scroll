package com.securemessage.app.data.model

/**
 * Public profile. This document (users/{uid}) is readable by any signed-in user, so it must
 * NOT contain private data. The account email is intentionally absent: it lives only in
 * Firebase Auth (readable solely by its owner) and is never written to a world-readable doc.
 */
data class User(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val bio: String = "",
)
