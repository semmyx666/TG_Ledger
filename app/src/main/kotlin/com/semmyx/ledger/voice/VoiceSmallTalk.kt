package com.semmyx.ledger.voice

/**
 * A tiny rule-based "small talk" layer for the cat assistant, so an everyday greeting like
 * "hi" / "privit" / "privet" gets a friendly reply back instead of "I did not understand
 * that", the way a real conversation partner would react. This is NOT a language model -- it
 * only recognizes a handful of common social phrases in Ukrainian, Russian, and English (many
 * Ukrainian speakers mix Russian words, so both are always checked together) and maps them to
 * one of a few topics. DashboardScreen.kt turns a topic into the actual reply text (localized
 * to the app's current EN/UK language via string resources) and has the cat speak it.
 *
 * This is checked strictly after VoiceEntryParser (an amount always means "log this entry",
 * never chit-chat) and after the fixed balance/spending/add-entry commands, so a real command
 * phrase is never swallowed by small talk.
 */
object VoiceSmallTalk {

    enum class Topic { GREETING, HOW_ARE_YOU, THANKS, WHO_ARE_YOU, BYE, COMPLIMENT, JOKE }

    fun classify(heard: String): Topic? {
        val text = heard.trim().lowercase()
        if (text.isBlank()) return null
        return when {
            containsAny(text, BYE) -> Topic.BYE
            containsAny(text, HOW_ARE_YOU) -> Topic.HOW_ARE_YOU
            containsAny(text, THANKS) -> Topic.THANKS
            containsAny(text, WHO_ARE_YOU) -> Topic.WHO_ARE_YOU
            containsAny(text, COMPLIMENT) -> Topic.COMPLIMENT
            containsAny(text, JOKE) -> Topic.JOKE
            containsAny(text, GREETING) -> Topic.GREETING
            else -> null
        }
    }

    // Explicit Unicode boundaries avoid matching greetings inside longer words.
    private fun containsAny(text: String, phrases: List<String>): Boolean = phrases.any { phrase ->
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(phrase) + "(?![\\p{L}\\p{N}])").containsMatchIn(text)
    }

    private val GREETING = listOf(
        "привіт", "привет", "вітаю", "здравствуй", "здрастуй", "доброго дня", "добрий день",
        "добрий вечір", "добрый вечер", "доброе утро", "доброго ранку", "hello", "hi", "hey", "yo",
    )
    private val HOW_ARE_YOU = listOf(
        "як справи", "як ти", "как дела", "как ты", "как поживаешь", "як поживаєш",
        "how are you", "whats up",
    )
    private val THANKS = listOf("дякую", "спасибі", "спасибо", "благодарю", "thanks", "thank you", "thx")
    private val WHO_ARE_YOU = listOf(
        "хто ти", "кто ты", "як тебе звати", "как тебя зовут", "твоє ім'я", "твое имя",
        "who are you", "what are you", "whats your name",
    )
    private val BYE = listOf("бувай", "до побачення", "пока", "прощай", "до зустрічі", "goodbye", "bye", "see you")
    private val COMPLIMENT = listOf(
        "гарний кіт", "хороший кот", "милий кіт", "милый кот", "люблю тебе", "люблю тебя",
        "good cat", "cute cat", "i love you", "good kitty",
    )
    private val JOKE = listOf("жарт", "анекдот", "розкажи щось", "расскажи что", "joke", "tell me a joke")
}
