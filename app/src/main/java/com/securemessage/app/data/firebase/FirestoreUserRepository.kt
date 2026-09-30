package com.securemessage.app.data.firebase

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.securemessage.app.data.model.User
import com.securemessage.app.data.repo.UserRepository
import com.securemessage.app.data.repo.UsernameTakenException
import kotlinx.coroutines.tasks.await

class FirestoreUserRepository(private val db: FirebaseFirestore) : UserRepository {

    private val users get() = db.collection("users")
    private val usernames get() = db.collection("usernames")

    override suspend fun createProfile(user: User): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.set(
            users.document(user.uid),
            mapOf(
                "uid" to user.uid,
                "username" to user.username,
                "displayName" to user.displayName,
                "emailLower" to user.emailLower,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(usernames.document(user.username), mapOf("uid" to user.uid))
        try {
            batch.commit().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && isTakenByOther(user)) {
                Log.i(TAG, "username '${user.username}' already taken")
                throw UsernameTakenException()
            }
            throw e
        }
        Unit
    }.onFailure { if (it !is UsernameTakenException) Log.w(TAG, "createProfile failed", it) }

    /** Follow-up read to tell a taken username apart from other rule failures. */
    private suspend fun isTakenByOther(user: User): Boolean = try {
        val doc = usernames.document(user.username).get().await()
        doc.exists() && doc.getString("uid") != user.uid
    } catch (e: Exception) {
        Log.w(TAG, "username follow-up read failed", e)
        false
    }

    override suspend fun getUser(uid: String): Result<User> = runCatching {
        val doc = users.document(uid).get().await()
        if (!doc.exists()) throw NoSuchElementException("No profile for $uid")
        doc.toUser()
    }.onFailure { if (it !is NoSuchElementException) Log.w(TAG, "getUser failed", it) }

    override suspend fun searchUsers(query: String, excludeUid: String): Result<List<User>> = runCatching {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@runCatching emptyList()
        val firestoreQuery = if ('@' in q) {
            users.whereEqualTo("emailLower", q).limit(20)
        } else {
            users.orderBy("username").startAt(q).endAt(q + "\uf8ff").limit(20)
        }
        firestoreQuery.get().await().documents
            .map { it.toUser() }
            .filter { it.uid != excludeUid }
    }.onFailure { Log.w(TAG, "searchUsers failed", it) }

    private fun DocumentSnapshot.toUser() = User(
        uid = getString("uid") ?: id,
        username = getString("username").orEmpty(),
        displayName = getString("displayName").orEmpty(),
        emailLower = getString("emailLower").orEmpty(),
    )

    private companion object {
        const val TAG = "UserRepo"
    }
}
