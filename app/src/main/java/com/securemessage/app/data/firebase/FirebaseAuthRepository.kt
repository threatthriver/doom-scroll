package com.securemessage.app.data.firebase

import android.util.Log
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.securemessage.app.data.repo.AccountCollisionException
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.TooManyRequestsException
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {
    override val currentUserId: String? get() = auth.currentUser?.uid
    override val currentEmail: String? get() = auth.currentUser?.email
    override val isEmailVerified: Boolean get() = auth.currentUser?.isEmailVerified == true

    override suspend fun signUp(email: String, password: String): Result<String> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        checkNotNull(result.user?.uid) { "Sign up returned no user" }
    }.onFailure { Log.w(TAG, "signUp failed", it) }

    override suspend fun signIn(email: String, password: String): Result<String> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        checkNotNull(result.user?.uid) { "Sign in returned no user" }
    }.onFailure { Log.w(TAG, "signIn failed", it) }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<String> = runCatching {
        try {
            val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            checkNotNull(result.user?.uid) { "Google sign in returned no user" }
        } catch (e: FirebaseAuthUserCollisionException) {
            throw AccountCollisionException()
        }
    }.onFailure { Log.w(TAG, "Google signIn failed: ${it.javaClass.simpleName}") }

    override suspend fun sendEmailVerification(): Result<Unit> = runCatching {
        val user = checkNotNull(auth.currentUser) { "Not signed in" }
        try {
            user.sendEmailVerification().await()
        } catch (e: FirebaseTooManyRequestsException) {
            throw TooManyRequestsException()
        }
        Unit
    }.onFailure { Log.w(TAG, "sendEmailVerification failed: ${it.javaClass.simpleName}") }

    override suspend fun reloadUser(): Result<Boolean> = runCatching {
        val user = checkNotNull(auth.currentUser) { "Not signed in" }
        user.reload().await()
        val verified = auth.currentUser?.isEmailVerified == true
        // Force a fresh ID token so Firestore rules see the new email_verified claim.
        if (verified) auth.currentUser?.getIdToken(true)?.await()
        verified
    }.onFailure { Log.w(TAG, "reloadUser failed: ${it.javaClass.simpleName}") }

    override fun signOut() = auth.signOut()

    private companion object {
        const val TAG = "AuthRepo"
    }
}
