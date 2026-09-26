package com.aistudio.escala

import com.aistudio.escala.data.DatabaseHelper
import com.aistudio.escala.util.CoordenadorLockoutManager
import com.aistudio.escala.util.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAndValidationTest {

    @Test
    fun testHashAccessCode_generatesUniqueSaltedHash() {
        val code = "COORD2026"
        val hash1 = SecurityUtils.hashAccessCode(code)
        val hash2 = SecurityUtils.hashAccessCode(code)

        assertTrue("Hash 1 deve iniciar com sha256:", hash1.startsWith("sha256:"))
        assertTrue("Hash 2 deve iniciar com sha256:", hash2.startsWith("sha256:"))
        assertNotEquals("Dois hashes do mesmo código devem ser diferentes devido ao salt aleatório", hash1, hash2)
    }

    @Test
    fun testVerifyAccessCode_successAndFailure() {
        val code = "JFYLQP"
        val hash = SecurityUtils.hashAccessCode(code)

        assertTrue("Código correto deve ser validado com sucesso", SecurityUtils.verifyAccessCode("JFYLQP", hash))
        assertTrue("Código correto em minúsculas deve ser aceito", SecurityUtils.verifyAccessCode("jfylqp", hash))
        assertFalse("Código errado deve ser rejeitado", SecurityUtils.verifyAccessCode("ERRADO123", hash))
        assertFalse("Código em branco deve ser rejeitado", SecurityUtils.verifyAccessCode("", hash))
    }

    @Test
    fun testSanitizeInput_removesControlCharsAndEnforcesLimit() {
        val inputWithControl = "João\u0000\u0007 da Silva  \r\n"
        val sanitized = DatabaseHelper.sanitizeInput(inputWithControl, 20)
        assertEquals("João da Silva", sanitized)

        val longInput = "A".repeat(150)
        val truncated = DatabaseHelper.sanitizeInput(longInput, 100)
        assertEquals(100, truncated.length)

        val emptyInput = "     "
        val emptyResult = DatabaseHelper.sanitizeInput(emptyInput, 50)
        assertEquals("", emptyResult)
    }

    @Test
    fun testCoordenadorLockoutManager_locksOutAfter5AttemptsFor30Seconds() {
        CoordenadorLockoutManager.reset()
        assertFalse("Não deve estar bloqueado no início", CoordenadorLockoutManager.isLockedOut())
        assertEquals(0, CoordenadorLockoutManager.failedAttempts)
        assertEquals(0, CoordenadorLockoutManager.getRemainingSeconds())

        // 4 tentativas falhas - ainda não bloqueado
        for (i in 1..4) {
            CoordenadorLockoutManager.recordFailedAttempt()
            assertEquals(i, CoordenadorLockoutManager.failedAttempts)
            assertFalse("Não deve estar bloqueado com $i tentativas", CoordenadorLockoutManager.isLockedOut())
        }

        // 5ª tentativa falha - bloqueio ativado por 30 segundos
        CoordenadorLockoutManager.recordFailedAttempt()
        assertEquals(5, CoordenadorLockoutManager.failedAttempts)
        assertTrue("Deve estar bloqueado após 5 tentativas", CoordenadorLockoutManager.isLockedOut())
        val remaining = CoordenadorLockoutManager.getRemainingSeconds()
        assertTrue("Segundos restantes devem ser entre 1 e 30 (obtido $remaining)", remaining in 1..30)

        // Reset limpa tentativas e bloqueio
        CoordenadorLockoutManager.reset()
        assertFalse("Não deve estar bloqueado após reset", CoordenadorLockoutManager.isLockedOut())
        assertEquals(0, CoordenadorLockoutManager.failedAttempts)
        assertEquals(0, CoordenadorLockoutManager.getRemainingSeconds())
    }
}
