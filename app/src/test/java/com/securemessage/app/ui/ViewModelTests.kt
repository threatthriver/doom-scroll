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
import com.securemessage.app.ui.profile.ProfileViewModel
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
        email: String = "a@b.co", pw: String = "secret1", name: String = "Alice", user: String = "alice_1",
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
        assertEquals(User("me", "alice_1", "Alice"), users.created.single())
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
        // Email is never written to the public profile doc (privacy); only identity fields are.
        assertEquals(User("me", "me_2", "Me"), users.created.single())
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

    @Test fun archivedChatsAreSplitOutOfTheMainList() = runTest {
        chats.chats.value = listOf(
            Chat("active", listOf("me", "bob"), archived = mapOf("me" to false)),
            Chat("hidden", listOf("me", "cat"), archived = mapOf("me" to true)),
        )
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        advanceUntilIdle()
        assertEquals(listOf("hidden"), v.state.value.archivedChats.map { it.id })
        assertEquals(1, v.state.value.archivedCount)
        assertTrue(v.isArchived(Chat("x", archived = mapOf("me" to true))))
        assertFalse(v.isArchived(Chat("x", archived = mapOf("me" to false))))
    }

    @Test fun archiveCountIsZeroWhenNothingArchived() = runTest {
        chats.chats.value = listOf(Chat("a", listOf("me", "bob")))
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        advanceUntilIdle()
        assertEquals(0, v.state.value.archivedCount)
        assertTrue(v.state.value.archivedChats.isEmpty())
    }

    @Test fun archivedFlagIsPerUser() = runTest {
        chats.chats.value = listOf(Chat("c", listOf("me", "bob"), archived = mapOf("bob" to true)))
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        advanceUntilIdle()
        // Archived by the *other* participant only — must not vanish from my list
        assertFalse(v.isArchived(v.state.value.chats.first()))
        assertEquals(0, v.state.value.archivedCount)
    }

    @Test fun toggleArchiveDelegatesToRepository() = runTest {
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        val chat = Chat("c1", listOf("me", "bob"))
        v.toggleArchive(chat)
        advanceUntilIdle()
        assertEquals(listOf("c1"), chats.archivedToggled)
    }

    @Test fun toggleArchiveSurfacesFailure() = runTest {
        chats.toggleArchiveResult = Result.failure(IOException())
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        v.toggleArchive(Chat("c1"))
        advanceUntilIdle()
        assertNotNull(v.state.value.error)
    }

    @Test fun toggleArchiveIsIgnoredWithoutASignedInUser() = runTest {
        val v = ConversationsViewModel(FakeAuthRepository(currentUserId = null), chats, users)
        advanceUntilIdle()
        v.toggleArchive(Chat("c1"))
        advanceUntilIdle()
        assertTrue(chats.archivedToggled.isEmpty())
    }

    @Test fun sortsNewestFirstNullsLast() {
        val sorted = ConversationsViewModel.sortChats(
            listOf(Chat("n"), Chat("old", lastMessageAt = Timestamp(1, 0)), Chat("new", lastMessageAt = Timestamp(2, 0))),
        )
        assertEquals(listOf("new", "old", "n"), sorted.map { it.id })
    }

    @Test fun pinnedChatsSortFirst() {
        val sorted = ConversationsViewModel.sortChats(
            listOf(
                Chat("normal", lastMessageAt = Timestamp(3, 0)),
                Chat("pinned", lastMessageAt = Timestamp(1, 0), pinned = true),
            ),
        )
        assertEquals(listOf("pinned", "normal"), sorted.map { it.id })
    }

    @Test fun titleUsesOtherParticipant() {
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        assertEquals("Bob", v.titleFor(Chat("c", listOf("bob", "me"), mapOf("me" to "Me", "bob" to "Bob"))))
        assertEquals("Unknown", v.titleFor(Chat("c", listOf("bob", "me"))))
    }

    @Test fun unreadCountForCurrentUser() {
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        val chat = Chat("c", listOf("me", "bob"), unreadCount = mapOf("me" to 5, "bob" to 3))
        assertEquals(5, v.unreadCountFor(chat))
    }

    @Test fun unreadCountDefaultsToZero() {
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        val chat = Chat("c", listOf("me", "bob"))
        assertEquals(0, v.unreadCountFor(chat))
    }

    @Test fun myDisplayNameComesFromFirestoreProfile() = runTest {
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        advanceUntilIdle()
        assertEquals("Me", v.state.value.myDisplayName)
    }

    @Test fun missingProfileLeavesDisplayNameEmpty() = runTest {
        users.getUserResult = Result.failure(NoSuchElementException())
        val v = ConversationsViewModel(FakeAuthRepository(), chats, users)
        advanceUntilIdle()
        assertTrue(v.state.value.myDisplayName.isEmpty())
        assertTrue(v.state.value.needsProfile)
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
class ProfileEditValidationTest {
    @get:Rule val main = MainDispatcherRule()
    private val users = FakeUserRepository()
    private fun vm() = ProfileViewModel(FakeAuthRepository(), users)

    @Test fun blankNameIsRejectedWithoutRepoCall() = runTest {
        var calls = 0
        val counting = object : com.securemessage.app.data.repo.UserRepository by users {
            override suspend fun updateProfile(uid: String, displayName: String, bio: String, photoUrl: String): Result<Unit> {
                calls++
                return Result.success(Unit)
            }
        }
        val v = ProfileViewModel(FakeAuthRepository(), counting)
        v.updateProfile("   ", "bio"); advanceUntilIdle()
        assertEquals(0, calls)
        assertEquals("Enter a name", v.state.value.userNotification)
    }

    @Test fun overlongNameAndBioAreRejected() = runTest {
        val v = vm()
        v.updateProfile("x".repeat(51), ""); advanceUntilIdle()
        assertEquals("Name must be at most 50 characters", v.state.value.userNotification)
        v.updateProfile("Ok", "x".repeat(161)); advanceUntilIdle()
        assertEquals("Bio must be at most 160 characters", v.state.value.userNotification)
    }

    @Test fun validEditTrimsAndSaves() = runTest {
        val v = vm()
        v.updateProfile("  Alice  ", "  hi  "); advanceUntilIdle()
        assertEquals("Alice", v.state.value.displayName)
        assertEquals("hi", v.state.value.bio)
        assertEquals("Profile saved", v.state.value.userNotification)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val chats = FakeChatRepository()
    private fun vm() = ChatViewModel(SavedStateHandle(mapOf("chatId" to "c1")), chats, FakeAuthRepository(), null, null)

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

    @Test fun plainSendCarriesNoReply() = runTest {
        val v = vm()
        v.onDraftChange("hi"); v.send(); advanceUntilIdle()
        assertEquals(listOf(Triple("", "", "")), chats.sentReplies)
    }

    @Test fun replySendQuotesOriginal() = runTest {
        val v = vm()
        v.onDraftChange("answer"); v.send(com.securemessage.app.data.model.Message("m1", "question", "bob"))
        advanceUntilIdle()
        assertEquals(listOf("answer"), chats.sent)
        assertEquals(listOf(Triple("m1", "question", "bob")), chats.sentReplies)
    }

    @Test fun failureRestoresDraft() = runTest {
        chats.sendResult = Result.failure(IOException())
        val v = vm()
        v.onDraftChange("hi"); v.send(); advanceUntilIdle()
        assertEquals("hi", v.state.value.draft)
        assertNotNull(v.state.value.userMessage)
    }

    @Test fun messagesMarkedAsRead() = runTest {
        chats.messages.value = listOf(
            com.securemessage.app.data.model.Message("m1", "hi", "bob")
        )
        val v = vm()
        advanceUntilIdle()
        assertTrue(chats.readMarked.isNotEmpty())
    }

    @Test fun ownMessagesDoNotTriggerReadWrite() = runTest {
        chats.messages.value = listOf(
            com.securemessage.app.data.model.Message("m1", "hi", "me")
        )
        val v = vm()
        advanceUntilIdle()
        assertTrue(chats.readMarked.isEmpty())
    }
}
