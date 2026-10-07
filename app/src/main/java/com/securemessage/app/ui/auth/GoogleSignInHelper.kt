package com.securemessage.app.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.securemessage.app.R

sealed interface GoogleIdResult {
    data class Success(val idToken: String) : GoogleIdResult
    data object Cancelled : GoogleIdResult
    data class Failure(val message: String) : GoogleIdResult
}

/** Gets a Google ID token via Credential Manager. Needs an Activity context to show the account picker. */
object GoogleSignInHelper {
    private const val TAG = "GoogleSignIn"

    suspend fun getIdToken(activityContext: Context): GoogleIdResult {
        // Public OAuth web client id generated from google-services.json; not a secret.
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(activityContext.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleIdResult.Failure("Unsupported credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleIdResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleIdResult.Failure("No Google account available on this device")
        } catch (e: GetCredentialException) {
            Log.w(TAG, "getCredential failed: ${e.type}")
            GoogleIdResult.Failure("Google sign-in isn't available right now")
        } catch (e: GoogleIdTokenParsingException) {
            Log.w(TAG, "Invalid Google ID token response")
            GoogleIdResult.Failure("Google sign-in failed. Try again.")
        }
    }
}
