package com.samyak.repostore.util

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.samyak.repostore.R
import com.samyak.repostore.data.auth.GitHubAuth
import com.samyak.repostore.ui.activity.GitHubSignInActivity

/**
 * Utility for showing rate limit exceeded dialog with sign-in option.
 * 
 * GitHub API Rate Limits:
 * - Without auth: 60 requests/hour
 * - With auth: 5,000 requests/hour
 */
object RateLimitDialog {

    /**
     * Check if the error message indicates a rate limit issue
     * Matches error messages from GitHubRepository:
     * - "Rate limit exceeded. Please wait a few minutes or add a GitHub token in settings."
     * - "API rate limit reached. Add a GitHub token to increase limit (60 → 5000 requests/hour)."
     */
    fun isRateLimitError(errorMessage: String?): Boolean {
        if (errorMessage == null) return false
        val lowerMessage = errorMessage.lowercase()
        return lowerMessage.contains("rate limit") ||
               lowerMessage.contains("api limit") ||
               lowerMessage.contains("github token") ||
               lowerMessage.contains("requests/hour") ||
               lowerMessage.contains("requests per hour")
    }

    /**
     * Show a rate limit dialog with sign-in option if user is not authenticated.
     * 
     * @param context The context to show the dialog
     * @param errorMessage The error message to display
     * @param onDismiss Optional callback when dialog is dismissed
     * @return true if dialog was shown, false if user is already signed in
     */
    fun showIfNeeded(
        context: Context,
        errorMessage: String?,
        onDismiss: (() -> Unit)? = null
    ): Boolean {
        // Only show if it's a rate limit error and user is not signed in
        if (!isRateLimitError(errorMessage)) {
            return false
        }
        
        if (GitHubAuth.isSignedIn(context)) {
            // User is signed in but still hit rate limit (5000/hr exceeded or other issue)
            return false
        }

        showDialog(context, errorMessage, onDismiss)
        return true
    }

    /**
     * Show the rate limit dialog
     */
    private fun showDialog(
        context: Context,
        errorMessage: String?,
        onDismiss: (() -> Unit)? = null
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.rate_limit_exceeded_title)
            .setMessage(context.getString(R.string.rate_limit_exceeded_message))
            .setIcon(R.drawable.ic_warning)
            .setPositiveButton(R.string.github_sign_in) { dialog, _ ->
                dialog.dismiss()
                // Open GitHub Sign In Activity
                val intent = GitHubSignInActivity.newIntent(context)
                context.startActivity(intent)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                onDismiss?.invoke()
            }
            .show()
    }

    /**
     * Show the dialog unconditionally (for manual trigger)
     */
    fun show(context: Context, onDismiss: (() -> Unit)? = null) {
        if (GitHubAuth.isSignedIn(context)) {
            // Already signed in, show different message
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.rate_limit_exceeded_title)
                .setMessage(R.string.rate_limit_exceeded_signed_in)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        } else {
            showDialog(context, null, onDismiss)
        }
    }
}
