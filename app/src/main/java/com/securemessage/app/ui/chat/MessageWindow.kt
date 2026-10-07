package com.securemessage.app.ui.chat

import com.securemessage.app.data.model.Message

/**
 * Keeps older pages the user already scrolled back to when the live listener (which only covers
 * the newest messages) delivers a fresh snapshot. Without this, every incoming message reset the
 * list to the newest window and threw away the history the user had loaded.
 */
object MessageWindow {
    /**
     * @param existing everything currently shown, oldest first
     * @param window the live listener's latest snapshot (the newest messages), oldest first
     */
    fun merge(existing: List<Message>, window: List<Message>): List<Message> {
        if (window.isEmpty()) return emptyList()
        val cutoff = window.first().timestamp ?: return window
        val inWindow = window.mapTo(HashSet()) { it.id }
        val older = existing.filter { e ->
            e.id !in inWindow && e.timestamp != null && e.timestamp < cutoff
        }
        return older + window
    }

    /** Puts a freshly loaded older page in front, skipping anything already present. */
    fun prepend(existing: List<Message>, olderPage: List<Message>): List<Message> {
        val have = existing.mapTo(HashSet()) { it.id }
        return olderPage.filter { it.id !in have } + existing
    }
}
