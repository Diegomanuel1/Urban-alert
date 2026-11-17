package com.example.core.security

import java.util.UUID

/**
 * Gerador e validador de chaves de idempotência para evitar duplicidade
 * em envios de ocorrências e fila de sincronização offline.
 */
object IdempotencyKeyGenerator {

    fun generate(): String = UUID.randomUUID().toString()

    fun isValid(key: String): Boolean =
        try {
            key.isNotBlank() && UUID.fromString(key).toString().equals(key, ignoreCase = true)
        } catch (_: IllegalArgumentException) {
            false
        }
}
