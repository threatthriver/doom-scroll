package com.securemessage.app.data.repo

import com.securemessage.app.data.model.User

class UsernameTakenException : Exception("Username already taken")

interface UserRepository {
    /** Atomically writes users/{uid} and usernames/{username}. */
    suspend fun createProfile(user: User): Result<Unit>

    /** Fails with [NoSuchElementException] if the profile does not exist. */
    suspend fun getUser(uid: String): Result<User>

    /** Prefix match on username. Email lookup is intentionally unsupported (privacy). */
    suspend fun searchUsers(query: String, excludeUid: String): Result<List<User>>

    /** Update profile fields (displayName, bio, photoUrl). */
    suspend fun updateProfile(uid: String, displayName: String, bio: String, photoUrl: String): Result<Unit>
}
