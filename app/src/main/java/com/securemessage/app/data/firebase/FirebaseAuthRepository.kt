package com.securemessage.app.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.securemessage.app.data.repo.AuthRepository
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {
    override val currentUserId: String? get() = auth.currentUser?.uid
    override val currentEmail: String? get() = auth.currentUser?.email

    override suspend fun signUp(email: String, password: String): Result<String> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        checkNotNull(result.user?.uid) { "Sign up returned no user" }
    }.onFailure { Log.w(TAG, "signUp failed", it) }

    override suspend fun signIn(email: String, password: String): Result<String> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        checkNotNull(result.user?.uid) { "Sign in returned no user" }
    }.onFailure { Log.w(TAG, "signIn failed", it) }

    override fun signOut() = auth.signOut()

    private companion object {
        const val TAG = "AuthRepo"
    }
}
