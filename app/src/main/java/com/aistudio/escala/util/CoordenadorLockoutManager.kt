package com.aistudio.escala.util

/**
 * Gerenciador em memória do mecanismo de rate limit e bloqueio contra força bruta
 * no login do coordenador.
 *
 * Mantém o contador de tentativas falhas e o timestamp de desbloqueio em memória
 * compartilhada, garantindo persistência mesmo durante navegação entre abas ou
 * recomposição de telas.
 */
object CoordenadorLockoutManager {
    const val MAX_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 30_000L

    @Volatile
    var failedAttempts: Int = 0
        private set

    @Volatile
    var lockUntilTimestamp: Long = 0L
        private set

    /**
     * Registra uma tentativa de login falha.
     * Se atingir MAX_ATTEMPTS (5), bloqueia por LOCKOUT_DURATION_MS (30s).
     * Retorna o timestamp de término do bloqueio (ou 0L se ainda não bloqueado).
     */
    @Synchronized
    fun recordFailedAttempt(): Long {
        failedAttempts++
        if (failedAttempts >= MAX_ATTEMPTS) {
            lockUntilTimestamp = System.currentTimeMillis() + LOCKOUT_DURATION_MS
        }
        return lockUntilTimestamp
    }

    /**
     * Retorna se o usuário está atualmente bloqueado.
     */
    fun isLockedOut(): Boolean {
        return System.currentTimeMillis() < lockUntilTimestamp
    }

    /**
     * Retorna os segundos restantes de bloqueio (arredondado para cima), ou 0 se não bloqueado.
     */
    fun getRemainingSeconds(): Int {
        val now = System.currentTimeMillis()
        val diff = lockUntilTimestamp - now
        return if (diff > 0L) {
            ((diff + 999L) / 1000L).toInt().coerceAtLeast(1)
        } else {
            0
        }
    }

    /**
     * Reseta as tentativas e o bloqueio após um login bem-sucedido.
     */
    @Synchronized
    fun reset() {
        failedAttempts = 0
        lockUntilTimestamp = 0L
    }
}
