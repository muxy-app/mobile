package com.muxy.app.core

import com.muxy.app.core.validation.ConnectionInputError
import com.muxy.app.core.validation.ConnectionInputValidator
import com.muxy.app.core.validation.InputValidation
import com.muxy.app.core.validation.ValidatedConnectionInput
import com.muxy.app.core.validation.ValidatedSshInput
import com.muxy.app.models.SshAuthMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionInputValidatorTest {
    private val validator = ConnectionInputValidator()

    @Test
    fun acceptsValidInput() {
        assertEquals(ValidatedConnectionInput("Studio", "studio.local", 4865), valid(validator.validate("Studio", "studio.local", "4865")))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals(
            ValidatedConnectionInput("Studio", "studio.local", 4865),
            valid(validator.validate("  Studio  ", "  studio.local ", " 4865 ")),
        )
    }

    @Test
    fun rejectsEmptyName() {
        assertEquals(invalid(ConnectionInputError.EMPTY_NAME), validator.validate("   ", "studio.local", "4865"))
    }

    @Test
    fun rejectsEmptyHost() {
        assertEquals(invalid(ConnectionInputError.EMPTY_HOST), validator.validate("Studio", "  ", "4865"))
    }

    @Test
    fun rejectsHostWithSpaces() {
        assertEquals(invalid(ConnectionInputError.INVALID_HOST), validator.validate("Studio", "stud io.local", "4865"))
    }

    @Test
    fun rejectsMissingPort() {
        assertEquals(invalid(ConnectionInputError.MISSING_PORT), validator.validate("Studio", "studio.local", ""))
    }

    @Test
    fun rejectsInvalidPorts() {
        listOf("0", "65536", "abc", "-1", "70000").forEach { port ->
            assertEquals(port, invalid(ConnectionInputError.INVALID_PORT), validator.validate("Studio", "studio.local", port))
        }
    }

    @Test
    fun acceptsBoundaryPortsAndHostShapes() {
        listOf("1", "65535").forEach { assertTrue(it, validator.validate("N", "studio.local", it) is InputValidation.Valid) }
        listOf("192.168.1.10", "host-name.local", "fd7a:115c:a1e0::1").forEach {
            assertTrue(it, validator.validate("N", it, "4865") is InputValidation.Valid)
        }
    }

    @Test
    fun acceptsIpv4Host() {
        assertEquals("192.168.1.10", valid(validator.validate("N", "192.168.1.10", "4865")).host)
    }

    @Test
    fun validateSshAcceptsPasswordInput() {
        val input = valid(validator.validateSsh("  Box  ", " box.local ", " 22 ", "  root  ", SshAuthMethod.PASSWORD, "hunter2", ""))
        assertEquals(ValidatedSshInput("Box", "box.local", 22, "root", SshAuthMethod.PASSWORD, "hunter2", null), input)
    }

    @Test
    fun validateSshPreservesSecretAndPassphraseVerbatim() {
        val key = "-----BEGIN KEY-----\nline\n-----END KEY-----\n"
        val input = valid(validator.validateSsh("Box", "box.local", "22", "root", SshAuthMethod.PRIVATE_KEY, key, " pass phrase "))
        assertEquals(key, input.secret)
        assertEquals(" pass phrase ", input.passphrase)
    }

    @Test
    fun validateSshTreatsWhitespacePassphraseAsMissing() {
        assertNull(valid(validator.validateSsh("Box", "box.local", "22", "root", SshAuthMethod.PRIVATE_KEY, "key", "   ")).passphrase)
    }

    @Test
    fun validateSshRejectsEmptyUsername() {
        assertEquals(
            invalid(ConnectionInputError.EMPTY_USERNAME),
            validator.validateSsh("Box", "box.local", "22", "   ", SshAuthMethod.PASSWORD, "pw", ""),
        )
    }

    @Test
    fun validateSshRejectsWhitespacePassword() {
        assertEquals(
            invalid(ConnectionInputError.EMPTY_PASSWORD),
            validator.validateSsh("Box", "box.local", "22", "root", SshAuthMethod.PASSWORD, "   ", ""),
        )
    }

    @Test
    fun validateSshRejectsWhitespacePrivateKey() {
        assertEquals(
            invalid(ConnectionInputError.EMPTY_PRIVATE_KEY),
            validator.validateSsh("Box", "box.local", "22", "root", SshAuthMethod.PRIVATE_KEY, "  \n  ", ""),
        )
    }

    @Test
    fun validateSshReportsEndpointErrorsBeforeCredentials() {
        assertEquals(
            invalid(ConnectionInputError.INVALID_HOST),
            validator.validateSsh("Box", "bad host", "22", "", SshAuthMethod.PASSWORD, "", ""),
        )
    }

    private fun <T> valid(result: InputValidation<T>): T = (result as InputValidation.Valid).value

    private fun invalid(error: ConnectionInputError) = InputValidation.Invalid(error)
}
