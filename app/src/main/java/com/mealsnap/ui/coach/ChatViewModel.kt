package com.mealsnap.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealsnap.AppContainer
import com.mealsnap.ai.AnalysisException
import com.mealsnap.ai.ChatTurn
import com.mealsnap.coach.CoachContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String, val isError: Boolean = false)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val hasGeminiKey: Boolean = true,
    val consent: Boolean = false,
    val needsConsent: Boolean = false,
    val suggestions: List<String> = listOf(
        "Plan my workout for today",
        "What should I eat for my next meal?",
        "How am I doing this week?",
        "Why might my resting heart rate be up?",
    ),
)

class ChatViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var pending: String? = null

    init {
        viewModelScope.launch {
            val key = container.settings.geminiKey.first().isNotBlank()
            val consent = container.settings.coachConsent.first()
            _state.update { it.copy(hasGeminiKey = key, consent = consent) }
        }
    }

    fun send(text: String) {
        val question = text.trim()
        val s = _state.value
        if (question.isEmpty() || s.sending || !s.hasGeminiKey) return
        if (!s.consent) {
            pending = question
            _state.update { it.copy(needsConsent = true) }
            return
        }
        viewModelScope.launch {
            val history = _state.value.messages.filter { !it.isError }.map { ChatTurn(it.fromUser, it.text) }
            _state.update { it.copy(messages = it.messages + ChatMessage(true, question), sending = true, needsConsent = false) }
            val reply = try {
                val context = CoachContext.describe(container.coachData.load().inputs)
                ChatMessage(false, container.coach.chat(context, history, question))
            } catch (e: AnalysisException) {
                ChatMessage(false, e.message ?: "Couldn't reach the trainer. Please try again.", isError = true)
            }
            _state.update { it.copy(messages = it.messages + reply, sending = false) }
        }
    }

    fun acceptConsent() {
        viewModelScope.launch {
            container.settings.setCoachConsent(true)
            _state.update { it.copy(consent = true, needsConsent = false) }
            pending?.let { pending = null; send(it) }
        }
    }

    fun dismissConsent() {
        pending = null
        _state.update { it.copy(needsConsent = false) }
    }
}
