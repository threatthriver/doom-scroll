package com.securemessage.app.data.repo

interface AuthRepository {
    val currentUserId: String?
    val currentEmail: String?
    suspend fun signUp(email: String, password: String): Result<String>
    suspend fun signIn(email: String, password: String): Result<String>
    fun signOut()
}
