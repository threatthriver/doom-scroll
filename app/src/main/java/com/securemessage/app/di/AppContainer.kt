package com.securemessage.app.di

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.securemessage.app.data.firebase.FirebaseAuthRepository
import com.securemessage.app.data.firebase.FirestoreChatRepository
import com.securemessage.app.data.firebase.FirestoreUserRepository
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.ChatRepository
import com.securemessage.app.data.repo.UserRepository

class AppContainer {
    val firebaseAuth: FirebaseAuth by lazy { Firebase.auth }
    val firestore: FirebaseFirestore by lazy { Firebase.firestore }

    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(firebaseAuth) }
    val userRepository: UserRepository by lazy { FirestoreUserRepository(firestore) }
    val chatRepository: ChatRepository by lazy { FirestoreChatRepository(firestore) }
}
