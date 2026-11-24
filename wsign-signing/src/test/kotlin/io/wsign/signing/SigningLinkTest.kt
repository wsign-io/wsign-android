package io.wsign.signing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SigningLinkTest {

    private fun reason(url: String?, configuration: WsignSigningConfiguration = WsignSigningConfiguration.DEFAULT) =
        (SigningLink.check(url, configuration) as SigningLink.Check.Invalid).reason

    @Test
    fun signingPageUrlIsValid() {
        val link = SigningLink.validate("https://wsign.io/sign.php?token=abc123")
        assertEquals(SigningLink.Kind.SIGNING_PAGE, link.kind)
        assertEquals("wsign.io", link.host)
        assertEquals("https://wsign.io/sign.php?token=abc123", link.url)
    }

    @Test
    fun embeddedSessionUrlIsValid() {
        val link = SigningLink.validate("https://www.wsign.io/embed/sign/0a1B2c3D/")
        assertEquals(SigningLink.Kind.EMBEDDED_SESSION, link.kind)
    }

    @Test
    fun englishPrefixIsAccepted() {
        assertEquals(SigningLink.Kind.SIGNING_PAGE, SigningLink.validate("https://wsign.io/en/sign.php?token=x").kind)
        assertEquals(SigningLink.Kind.EMBEDDED_SESSION, SigningLink.validate("https://wsign.io/EN/embed/sign/abc").kind)
    }

    @Test
    fun otherPathOnAllowedHostIsAccepted() {
        assertEquals(SigningLink.Kind.OTHER, SigningLink.validate("https://wsign.io/verify").kind)
        assertEquals(SigningLink.Kind.OTHER.value, "other")
    }

    @Test
    fun plainHttpIsRefused() {
        assertEquals(UrlValidationError.UnsupportedScheme("http"), reason("http://wsign.io/sign.php?token=x"))
    }

    @Test
    fun otherSchemesAreRefused() {
        assertEquals(UrlValidationError.UnsupportedScheme("ftp"), reason("ftp://wsign.io/sign.php?token=x"))
        assertEquals(UrlValidationError.UnsupportedScheme("javascript"), reason("javascript:alert(1)"))
        assertEquals(UrlValidationError.UnsupportedScheme(""), reason("/sign.php?token=x"))
    }

    @Test
    fun missingAndMalformedUrls() {
        assertEquals(UrlValidationError.MissingUrl, reason(null))
        assertEquals(UrlValidationError.MissingUrl, reason("  "))
        assertEquals(UrlValidationError.Malformed, reason("https://wsign.io/sign.php?token=a b"))
        assertEquals(UrlValidationError.MissingHost, reason("https:///sign.php?token=x"))
    }

    @Test
    fun unknownHostIsRefused() {
        assertEquals(UrlValidationError.HostNotAllowed("evil.com"), reason("https://evil.com/sign.php?token=x"))
        assertEquals(UrlValidationError.HostNotAllowed("wsign.io.evil.com"), reason("https://wsign.io.evil.com/embed/sign/a"))
    }

    @Test
    fun customDomainNeedsToBeAllowed() {
        val url = "https://sign.example.com/sign.php?token=x"
        assertFalse(SigningLink.isValid(url))
        val configuration = WsignSigningConfiguration { addAllowedHost("Sign.Example.com") }
        assertTrue(SigningLink.isValid(url, configuration))
        val wildcard = WsignSigningConfiguration { addAllowedHost("*.example.com") }
        assertTrue(SigningLink.isValid(url, wildcard))
        assertFalse(SigningLink.isValid("https://example.com/sign.php?token=x", wildcard))
    }

    @Test
    fun credentialsAreRefused() {
        assertEquals(UrlValidationError.ContainsCredentials, reason("https://user@wsign.io/sign.php?token=x"))
        assertEquals(UrlValidationError.ContainsCredentials, reason("https://evil.com@wsign.io/sign.php?token=x"))
    }

    @Test
    fun missingTokenIsRefused() {
        assertEquals(UrlValidationError.MissingToken, reason("https://wsign.io/sign.php"))
        assertEquals(UrlValidationError.MissingToken, reason("https://wsign.io/sign.php?token=%20"))
        assertEquals(UrlValidationError.MissingToken, reason("https://wsign.io/embed/sign/"))
        assertEquals(UrlValidationError.MissingToken, reason("https://wsign.io/embed/sign/abc-def"))
    }

    @Test
    fun localDevelopmentNeedsTheFlag() {
        val url = "http://10.0.2.2:8782/sign.php?token=x"
        assertEquals(UrlValidationError.UnsupportedScheme("http"), reason(url))
        val dev = WsignSigningConfiguration { allowsInsecureLocalHosts = true }
        assertTrue(SigningLink.isValid(url, dev))
        assertTrue(SigningLink.isValid("http://localhost/embed/sign/abc", dev))
        assertEquals(UrlValidationError.UnsupportedScheme("http"), reason("http://wsign.io/sign.php?token=x", dev))
    }

    @Test
    fun languageParameterIsAdded() {
        val link = SigningLink.validate("https://wsign.io/sign.php?token=x#top")
        assertEquals("https://wsign.io/sign.php?token=x&lang=ar#top", link.urlFor(WsignSigningConfiguration.Language.ARABIC))
        assertEquals("https://wsign.io/sign.php?token=x&lang=en#top", link.urlFor(WsignSigningConfiguration.Language.ENGLISH))
        assertEquals(link.url, link.urlFor(WsignSigningConfiguration.Language.AUTOMATIC))
    }

    @Test
    fun languageParameterIsAddedToPathOnlyUrl() {
        val link = SigningLink.validate("https://wsign.io/embed/sign/abc")
        assertEquals("https://wsign.io/embed/sign/abc?lang=en", link.urlFor(WsignSigningConfiguration.Language.ENGLISH))
    }

    @Test
    fun existingLanguageParameterIsKept() {
        val link = SigningLink.validate("https://wsign.io/embed/sign/abc?lang=ar")
        assertEquals(link.url, link.urlFor(WsignSigningConfiguration.Language.ENGLISH))
    }

    @Test
    fun validateThrowsWithTheReason() {
        try {
            SigningLink.validate("https://evil.com/sign.php?token=x")
            fail("expected an exception")
        } catch (error: InvalidSigningUrlException) {
            assertEquals(UrlValidationError.HostNotAllowed("evil.com"), error.reason)
            assertTrue(error.message!!.contains("allowedHosts"))
        }
    }

    @Test
    fun errorsHaveDescriptions() {
        listOf(
            UrlValidationError.MissingUrl,
            UrlValidationError.Malformed,
            UrlValidationError.UnsupportedScheme("http"),
            UrlValidationError.MissingHost,
            UrlValidationError.HostNotAllowed("x"),
            UrlValidationError.ContainsCredentials,
            UrlValidationError.MissingToken,
        ).forEach { assertTrue(it.description.isNotBlank()) }
    }
}
