package com.semmyx.ledger.bank

import android.app.Notification
import com.semmyx.ledger.domain.bank.Bank
import com.semmyx.ledger.domain.bank.BankParser

/** No network, no raw message persistence. Handles plain, big, inbox and messaging styles. */
object BankNotificationDecoder {
    data class Message(val text: String, val at: Long)

    fun messages(notification: Notification, bank: Bank, postedAt: Long): List<Message> {
        val extras = notification.extras ?: return emptyList()
        // Some apps re-post an old notification. Preserve its original event time when valid.
        val at = notification.`when`.takeIf { it in 1..(postedAt + 300_000) } ?: postedAt
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim().orEmpty()
        val messaging = runCatching { Notification.MessagingStyle.extractMessagingStyleFromNotification(notification)?.messages }.getOrNull().orEmpty()
        if (messaging.isNotEmpty()) return messaging.mapNotNull { message ->
            val text = message.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) null else Message("$title\n$text", message.timestamp.takeIf { it in 1..(postedAt + 300_000) } ?: at)
        }.distinct()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty()
            .map { it.toString().trim() }.filter { it.isNotEmpty() }
        // Inbox style can contain several independent payments. Do not merge their amounts.
        val payments = lines.map { Message("$title\n$it", at) }
            .filter { BankParser.parse(bank, it.text, it.at) != null }
        if (payments.isNotEmpty()) return payments.distinct()
        val parts = linkedSetOf<String>()
        fun add(value: CharSequence?) { value?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add) }
        add(title)
        add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_TEXT))
        lines.forEach(::add)
        add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        if (parts.size < 2) add(notification.tickerText)
        val text = parts.filter { part -> parts.none { other -> other.length > part.length && other.contains(part) } }
            .joinToString("\n")
        return if (text.isBlank()) emptyList() else listOf(Message(text, at))
    }
}
