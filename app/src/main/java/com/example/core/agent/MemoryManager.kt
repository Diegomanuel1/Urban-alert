package com.example.core.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Gerenciador de memória conversacional da sessão atual do agente.
 * No M6 será conectado ao Room SQLite (conversations / conversation_messages / drafts).
 */
class MemoryManager {

    private val _messages = MutableStateFlow<List<AgentChatMessage>>(emptyList())
    val messages: StateFlow<List<AgentChatMessage>> = _messages.asStateFlow()

    private val _draft = MutableStateFlow(CitizenIncidentDraft())
    val draft: StateFlow<CitizenIncidentDraft> = _draft.asStateFlow()

    private val _step = MutableStateFlow(AgentStep.COLLECTING_INPUT)
    val step: StateFlow<AgentStep> = _step.asStateFlow()

    fun appendMessage(message: AgentChatMessage) {
        _messages.update { current ->
            (current + message).takeLast(MAX_SESSION_MESSAGES)
        }
    }

    fun updateDraft(transform: (CitizenIncidentDraft) -> CitizenIncidentDraft) {
        _draft.update(transform)
    }

    fun setStep(next: AgentStep) {
        _step.value = next
    }

    fun recentNarrativeSummary(): String {
        val currentDraft = _draft.value
        val userNotes = _messages.value
            .filter { it.role == AgentMessageRole.CITIZEN }
            .joinToString("\n") { it.text }
            .trim()
        return listOf(currentDraft.userNarrative.trim(), userNotes)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    fun resetSession(welcomeMessage: String) {
        _draft.value = CitizenIncidentDraft()
        _step.value = AgentStep.COLLECTING_INPUT
        _messages.value = listOf(
            AgentChatMessage(
                role = AgentMessageRole.AGENT,
                text = welcomeMessage,
            ),
        )
    }

    fun restoreSession(
        draft: CitizenIncidentDraft,
        messages: List<AgentChatMessage>,
        fallbackWelcome: String,
    ) {
        _draft.value = draft
        _step.value = AgentStep.COLLECTING_INPUT
        _messages.value = messages.ifEmpty {
            listOf(
                AgentChatMessage(
                    role = AgentMessageRole.AGENT,
                    text = fallbackWelcome,
                ),
            )
        }
    }

    private companion object {
        const val MAX_SESSION_MESSAGES = 40
    }
}
