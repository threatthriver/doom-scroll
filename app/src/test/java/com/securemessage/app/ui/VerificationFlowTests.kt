package com.securemessage.app.ui

import androidx.lifecycle.SavedStateHandle
import com.securemessage.app.MainDispatcherRule
import com.securemessage.app.data.repo.AccountCollisionException
import com.securemessage.app.fakes.FakeAuthRepository
import com.securemessage.app.fakes.FakeChatRepository
import com.securemessage.app.fakes.FakePushTokenRepository
import com.securemessage.app.fakes.FakeUserRepository
import com.securemessage.app.ui.auth.AuthViewModel
import com.securemessage.app.ui.auth.MSG_ACCOUNT_COLLISION
import com.securemessage.app.ui.conversations.ConversationsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Email verification gate, Google sign-in and push-token cleanup on sign-out. */
@OptIn(ExperimentalCoroutinesApi::class)
class VerificationFlowTests {
    @get:Rule val main = MainDispatcherRule()
    private val auth = FakeAuthRepository().apply { verified = false }
    private val users = FakeUserRepository()
    private val push = FakePushTokenRepository()

    private fun authVm() =
        AuthViewModel(SavedStateHandle(), auth, users, push) { it.contains("@") && it.contains(".") }

    @Test fun signUpSendsVerificationAndRoutesToVerifyScreen() = runTest {
        val v = authVm()
        v.onEmailChange("a@b.co"); v.onPasswordChange("secret1")
        v.onDisplayNameChange("Alice"); v.onUsernameChange("alice_1")
        v.signUp(); advanceUntilIdle()
        assertEquals(1, auth.verificationSent)
        assertTrue(v.state.value.needsVerification)
        assertFalse(v.state.value.isAuthenticated)
    }

    @Test fun unverifiedSignInGoesToVerifyNotChats() = runTest {
        val v = authVm()
        v.onEmailChange("a@b.co"); v.onPasswordChange("secret1")
        v.signIn(); advanceUntilIdle()
        assertTrue(v.state.value.needsVerification)
        assertFalse(v.state.value.isAuthenticated)
    }

    @Test fun verifiedSignInGoesToChats() = runTest {
        auth.verified = true
        val v = authVm()
        v.onEmailChange("a@b.co"); v.onPasswordChange("secret1")
        v.signIn(); advanceUntilIdle()
        assertTrue(v.state.value.isAuthenticated)
        assertFalse(v.state.value.needsVerification)
    }

    @Test fun googleCollisionShowsClearMessage() = runTest {
        auth.googleResult = Result.failure(AccountCollisionException())
        val v = authVm()
        v.signInWithGoogle("token"); advanceUntilIdle()
        assertEquals(MSG_ACCOUNT_COLLISION, v.state.value.error)
        assertFalse(v.state.value.isAuthenticated)
    }

    @Test fun googleSignInWithProfileGoesToChats() = runTest {
        auth.verified = true
        val v = authVm()
        v.signInWithGoogle("token"); advanceUntilIdle()
        assertTrue(v.state.value.isAuthenticated)
    }

    @Test fun unverifiedUserNeverStartsChatListener() = runTest {
        val chats = FakeChatRepository()
        val v = ConversationsViewModel(auth, chats, users, push)
        advanceUntilIdle()
        assertTrue(v.state.value.needsVerification)
        assertEquals(0, push.registered)
        assertEquals(0, users.getUserCalls)
    }

    @Test fun verifiedUserRegistersPushToken() = runTest {
        auth.verified = true
        val v = ConversationsViewModel(auth, FakeChatRepository(), users, push)
        advanceUntilIdle()
        assertFalse(v.state.value.needsVerification)
        assertEquals(1, push.registered)
    }

    @Test fun signOutRemovesPushTokenThenSignsOut() = runTest {
        auth.verified = true
        val v = ConversationsViewModel(auth, FakeChatRepository(), users, push)
        var done = false
        v.signOut { done = true }
        advanceUntilIdle()
        assertEquals(1, push.unregistered)
        assertTrue(auth.signedOut)
        assertTrue(done)
    }
}
