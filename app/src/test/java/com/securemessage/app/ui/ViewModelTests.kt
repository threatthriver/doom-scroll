package com.securemessage.app.ui

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.Timestamp
import com.securemessage.app.MainDispatcherRule
import com.securemessage.app.data.model.Chat
import com.securemessage.app.data.model.User
import com.securemessage.app.data.repo.UsernameTakenException
import com.securemessage.app.fakes.FakeAuthRepository
import com.securemessage.app.fakes.FakeChatRepository
import com.securemessage.app.fakes.FakeUserRepository
import com.securemessage.app.ui.auth.AuthViewModel
import com.securemessage.app.ui.auth.MSG_PROFILE_FAILED
import com.securemessage.app.ui.auth.MSG_USERNAME_TAKEN
import com.securemessage.app.ui.auth.REASON_PROFILE_FAILED
import com.securemessage.app.ui.auth.REASON_USERNAME_TAKEN
import com.securemessage.app.ui.chat.ChatViewModel
import com.securemessage.app.ui.conversations.ConversationsViewModel
import com.securemessage.app.ui.users.UserSearchViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val auth = FakeAuthRepository()
    private val users = FakeUserRepository()

    private fun vm(reason: String? = null) =
        AuthViewModel(SavedStateHandle(mapOf("reason" to reason)), auth, users) { it.contains("@") && it.contains(".") }

    private fun AuthViewModel.fillSignUp(
        email: String = "a@b.co", pw: String = "secret1", name: String = "Alice", user: String = "Alice_1",
    ) { onEmailChange(email); onPasswordChange(pw); onDisplayNameChange(name); onUsernameChange(user) }

    @Test fun invalidInputsNeverCallRepos() = runTest {
        listOf(
            vm().apply { fillSignUp(email = "bad") },
            vm().apply { fillSignUp(pw = "12345") },
            vm().apply { fillSignUp(name = "  ") },
            vm().apply { fillSignUp(name = "x".repeat(51)) },
            vm().apply { fillSignUp(user = "a!") },
        ).forEach { v ->
            v.signUp(); advanceUntilIdle()
            assertNotNull(v.state.value.error)
        }
        assertEquals(0, auth.calls)
        assertTrue(users.created.isEmpty())
    }

    @Test fun signUpSuccessNormalizes() = runTest {
        val v = vm().apply { fillSignUp(email = " A@B.co ") }
        v.signUp(); advanceUntilIdle()
        assertTrue(v.state.value.isAuthenticated)
        assertEquals(User("me", "alice_1", "Alice", "a@b.co"), users.created.single())
    }

    @Test fun usernameTakenRoutesWithReason() = runTest {
        users.createResult = Result.failure(UsernameTakenException())
        val v = vm().apply { fillSignUp() }
        v.signUp(); advanceUntilIdle()
        assertTrue(v.state.value.needsProfile)
        assertEquals(REASON_USERNAME_TAKEN, v.state.value.profileReason)
    }

    @Test fun otherProfileFailureRoutesWithProfileFailed() = runTest {
        users.createResult = Result.failure(IOException("net"))
        val v = vm().apply { fillSignUp() }
        v.signUp(); advanceUntilIdle()
        assertEquals(REASON_PROFILE_FAILED, v.state.value.profileReason)
    }

    @Test fun signInMissingProfileNeedsProfile() = runTest {
        users.getUserResult = Result.failure(NoSuchElementException())
        val v = vm().apply { onEmailChange("a@b.co"); onPasswordChange("secret1") }
        v.signIn(); advanceUntilIdle()
        assertTrue(v.state.value.needsProfile)
        assertFalse(v.state.value.isAuthenticated)
    }

    @Test fun signInNetworkProfileErrorStillAuthenticates() = runTest {
        users.getUserResult = Result.failure(IOException())
        val v = vm().apply { onEmailChange("a@b.co"); onPasswordChange("secret1") }
        v.signIn(); advanceUntilIdle()
        assertTrue(v.state.value.isAuthenticated)
    }

    @Test fun reasonSeedsError() {
        assertEquals(MSG_USERNAME_TAKEN, vm(REASON_USERNAME_TAKEN).state.value.error)
        assertEquals(MSG_PROFILE_FAILED, vm(REASON_PROFILE_FAILED).state.value.error)
        assertNull(vm("other").state.value.error)
        assertNull(vm().state.value.error)
    }

    @Test fun completeProfileTakenStaysWithError() = runTest {
        users.createResult = Result.failure(UsernameTakenException())
        val v = vm().apply { onDisplayNameChange("Me"); onUsernameChange("me_2") }
        v.completeProfile(); advanceUntilIdle()
        assertEquals(MSG_USERNAME_TAKEN, v.state.value.error)
        assertFalse(v.state.value.isAuthenticated)
        assertEquals("me@example.com", users.created.single().emailLower)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val users = FakeUserRepository()
    private val chats = FakeChatRepository()

    @Test fun missingProfileSetsNeedsProfile() = runTest {
        users.getUserResult = Result.failure(NoSuchElementException())
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        advanceUntilIdle()
        assertTrue(v.state.value.needsProfile)
    }

    @Test fun otherFailureIgnored() = runTest {
        users.getUserResult = Result.failure(IOException())
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        advanceUntilIdle()
        assertFalse(v.state.value.needsProfile)
        assertFalse(v.state.value.isLoading)
    }

    @Test fun sortsNewestFirstNullsLast() {
        val sorted = ConversationsViewModel.sortChats(
            listOf(Chat("n"), Chat("old", lastMessageAt = Timestamp(1, 0)), Chat("new", lastMessageAt = Timestamp(2, 0))),
        )
        assertEquals(listOf("new", "old", "n"), sorted.map { it.id })
    }

    @Test fun titleUsesOtherParticipant() {
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        assertEquals("Bob", v.titleFor(Chat("c", listOf("bob", "me"), mapOf("me" to "Me", "bob" to "Bob"))))
        assertEquals("Unknown", v.titleFor(Chat("c", listOf("bob", "me"))))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UserSearchViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val users = FakeUserRepository()
    private val chats = FakeChatRepository()
    private fun vm() = UserSearchViewModel(FakeAuthRepository(), users, chats)

    @Test fun blankQueryMakesNoCall() = runTest {
        val v = vm()
        v.onQueryChange("   "); advanceUntilIdle()
        assertTrue(users.searches.isEmpty())
        assertFalse(v.state.value.hasSearched)
    }

    @Test fun debounces() = runTest {
        val v = vm()
        v.onQueryChange("a"); advanceTimeBy(100)
        v.onQueryChange("al"); advanceTimeBy(100)
        v.onQueryChange("ali"); advanceTimeBy(299); runCurrent()
        assertTrue(users.searches.isEmpty())
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf("ali"), users.searches)
    }

    @Test fun excludesSelfAndShowsEmpty() = runTest {
        users.searchResult = Result.success(listOf(User("me", "me")))
        val v = vm()
        v.onQueryChange("me"); advanceUntilIdle()
        assertTrue(v.state.value.hasSearched)
        assertTrue(v.state.value.results.isEmpty())
        assertNull(v.state.value.searchError)
    }

    @Test fun errorState() = runTest {
        users.searchResult = Result.failure(IOException())
        val v = vm()
        v.onQueryChange("ali"); advanceUntilIdle()
        assertNotNull(v.state.value.searchError)
    }

    @Test fun profileLoadFailureBlocksOpenChat() = runTest {
        users.getUserResult = Result.failure(IOException())
        val v = vm()
        v.onUserClick(User("bob")); advanceUntilIdle()
        assertEquals("Couldn't load your profile", v.state.value.userMessage)
        assertNull(v.state.value.navigateToChatId)
    }

    @Test fun openChatNavigatesAndCachesMe() = runTest {
        val v = vm()
        v.onUserClick(User("bob")); advanceUntilIdle()
        v.onUserClick(User("bob")); advanceUntilIdle()
        assertEquals("chat1", v.state.value.navigateToChatId)
        assertEquals(1, users.getUserCalls)
        v.navigationHandled()
        assertNull(v.state.value.navigateToChatId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val chats = FakeChatRepository()
    private fun vm() = ChatViewModel(SavedStateHandle(mapOf("chatId" to "c1")), chats, FakeAuthRepository())

    @Test fun titleFromOtherParticipant() = runTest {
        chats.getChatResult = Result.success(Chat("c1", listOf("bob", "me"), mapOf("bob" to "Bob", "me" to "Me")))
        val v = vm(); advanceUntilIdle()
        assertEquals("Bob", v.state.value.title)
    }

    @Test fun titleFallback() = runTest {
        val v = vm(); advanceUntilIdle()
        assertEquals("Chat", v.state.value.title)
    }

    @Test fun blankAndTooLongRejected() = runTest {
        val v = vm()
        v.onDraftChange("   "); v.send(); advanceUntilIdle()
        v.onDraftChange("x".repeat(2001)); v.send(); advanceUntilIdle()
        assertTrue(chats.sent.isEmpty())
    }

    @Test fun sendTrimsAndClears() = runTest {
        val v = vm()
        v.onDraftChange(" hi "); v.send(); advanceUntilIdle()
        assertEquals(listOf("hi"), chats.sent)
        assertEquals("", v.state.value.draft)
    }

    @Test fun failureRestoresDraft() = runTest {
        chats.sendResult = Result.failure(IOException())
        val v = vm()
        v.onDraftChange("hi"); v.send(); advanceUntilIdle()
        assertEquals("hi", v.state.value.draft)
        assertNotNull(v.state.value.userMessage)
    }
}
