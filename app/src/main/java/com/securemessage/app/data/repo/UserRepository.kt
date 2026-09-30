package com.securemessage.app.data.repo

import com.securemessage.app.data.model.User

class UsernameTakenException : Exception("Username already taken")

interface UserRepository {
    /** Atomically writes users/{uid} and usernames/{username}. */
    suspend fun createProfile(user: User): Result<Unit>

    /** Fails with [NoSuchElementException] if the profile does not exist. */
    suspend fun getUser(uid: String): Result<User>

    /** Exact email match if the query contains '@', otherwise username prefix match. */
    suspend fun searchUsers(query: String, excludeUid: String): Result<List<User>>
}
