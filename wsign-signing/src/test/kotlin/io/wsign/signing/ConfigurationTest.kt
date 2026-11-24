package io.wsign.signing

import io.wsign.signing.internal.BridgeScript
import io.wsign.signing.internal.ErrorIcon
import io.wsign.signing.internal.ResolvedLanguage
import io.wsign.signing.internal.SigningStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationTest {

    @Test
    fun defaults() {
        val configuration = WsignSigningConfiguration.DEFAULT
        assertEquals(WsignSigningConfiguration.Language.AUTOMATIC, configuration.language)
        assertEquals(setOf("wsign.io", "www.wsign.io"), configuration.allowedHosts)
        assertTrue(configuration.returnUrls.isEmpty())
        assertTrue(configuration.showsCloseButton)
        assertEquals(SigningColor.BRAND, configuration.appearance.tintColor)
        assertEquals(WsignSigningConfiguration.InterfaceStyle.SYSTEM, configuration.appearance.interfaceStyle)
        assertNull(configuration.appearance.title)
        assertTrue(configuration.appearance.showsHeader)
        assertTrue(configuration.appearance.showsProgressBar)
        assertTrue(configuration.appearance.confirmsBeforeClosing)
        assertEquals(30_000L, configuration.timeoutMillis)
        assertEquals(WsignSigningConfiguration.Dismissal.Automatic(1_500), configuration.dismissal)
        assertEquals(WsignSigningConfiguration.IdentityVerificationPolicy.FOLLOW_PLATFORM, configuration.identityVerification)
        assertTrue(configuration.identityVerificationHosts.isEmpty())
        assertTrue(configuration.detectsPlatformReturnNavigation)
        assertTrue(configuration.usesEphemeralWebsiteData)
        assertFalse(configuration.allowsInsecureLocalHosts)
        assertFalse(configuration.isWebContentsDebuggingEnabled)
    }

    @Test
    fun timeoutIsClamped() {
        assertEquals(5_000L, WsignSigningConfiguration { timeoutMillis = 1 }.effectiveTimeoutMillis)
        assertEquals(120_000L, WsignSigningConfiguration { timeoutMillis = 999_999 }.effectiveTimeoutMillis)
        assertEquals(45_000L, WsignSigningConfiguration { timeoutMillis = 45_000 }.effectiveTimeoutMillis)
        assertEquals(5_000L, WsignSigningConfiguration { timeoutMillis = -5 }.effectiveTimeoutMillis)
    }

    @Test
    fun dismissalDelay() {
        assertEquals(1_500L, WsignSigningConfiguration.DEFAULT.dismissalDelayMillis)
        assertNull(WsignSigningConfiguration { dismissal = WsignSigningConfiguration.Dismissal.Manual }.dismissalDelayMillis)
        assertEquals(0L, WsignSigningConfiguration { dismissal = WsignSigningConfiguration.Dismissal.Automatic(-3) }.dismissalDelayMillis)
        assertEquals(
            30_000L,
            WsignSigningConfiguration { dismissal = WsignSigningConfiguration.Dismissal.Automatic(90_000) }.dismissalDelayMillis,
        )
    }

    @Test
    fun addAllowedHostNormalizes() {
        val configuration = WsignSigningConfiguration {
            addAllowedHost("  Sign.Example.COM. ")
            addAllowedHost("   ")
            addIdentityVerificationHost("ID.Example.com")
        }
        assertTrue("sign.example.com" in configuration.allowedHosts)
        assertEquals(3, configuration.allowedHosts.size)
        assertEquals(setOf("id.example.com"), configuration.identityVerificationHosts)
    }

    @Test
    fun builderRoundTripsAndEquality() {
        val original = WsignSigningConfiguration {
            language = WsignSigningConfiguration.Language.ENGLISH
            addReturnUrl("https://example.com/done")
            appearance { title = "Sign"; confirmsBeforeClosing = false }
        }
        val copy = original.newBuilder().build()
        assertEquals(original, copy)
        assertEquals(original.hashCode(), copy.hashCode())
        assertNotEquals(original, WsignSigningConfiguration.DEFAULT)
        assertEquals("Sign", original.appearance.title)
        assertFalse(original.appearance.confirmsBeforeClosing)
        assertTrue(original.toString().contains("ENGLISH"))
    }

    @Test
    fun languageResolution() {
        val auto = WsignSigningConfiguration.Language.AUTOMATIC
        assertEquals(ResolvedLanguage.ARABIC, ResolvedLanguage.resolve(auto, listOf("ar-EG", "en")))
        assertEquals(ResolvedLanguage.ENGLISH, ResolvedLanguage.resolve(auto, listOf("en-US", "ar")))
        assertEquals(ResolvedLanguage.ENGLISH, ResolvedLanguage.resolve(auto, emptyList()))
        assertEquals(ResolvedLanguage.ARABIC, ResolvedLanguage.resolve(WsignSigningConfiguration.Language.ARABIC, listOf("en")))
        assertEquals(ResolvedLanguage.ENGLISH, ResolvedLanguage.resolve(WsignSigningConfiguration.Language.ENGLISH, listOf("ar")))
        assertTrue(ResolvedLanguage.ARABIC.isRightToLeft)
        assertFalse(ResolvedLanguage.ENGLISH.isRightToLeft)
        assertEquals("ar", WsignSigningConfiguration.Language.ARABIC.code)
        assertNull(WsignSigningConfiguration.Language.AUTOMATIC.code)
    }

    @Test
    fun versionConstants() {
        assertEquals("1.0.0", WsignSigning.VERSION)
        assertEquals(System.getProperty("wsign.version"), WsignSigning.VERSION)
        assertEquals("2026-07-01", WsignSigning.API_VERSION)
        assertEquals(setOf("wsign.io", "www.wsign.io"), WsignSigning.PLATFORM_HOSTS)
    }

    @Test
    fun brandColor() {
        assertEquals(0x121AB0, SigningColor.BRAND.light)
        assertEquals(0x8B91F7, SigningColor.BRAND.dark)
        assertEquals(0xFF121AB0.toInt(), SigningColor.BRAND.argb(darkMode = false))
        assertEquals(0xFF8B91F7.toInt(), SigningColor.BRAND.argb(darkMode = true))
        assertEquals(SigningColor(0x123456), SigningColor(0xFF123456.toInt(), 0x123456))
    }

    @Test
    fun brandContrast() {
        assertTrue(SigningColor.contrastRatio(0x121AB0, 0xFFFFFF) >= 11.0)
        assertTrue(SigningColor.contrastRatio(0x8B91F7, 0x000000) >= 7.0)
        assertEquals(21.0, SigningColor.contrastRatio(0x000000, 0xFFFFFF), 0.01)
    }

    @Test
    fun foregroundChoice() {
        assertEquals(0xFFFFFF, SigningColor.foreground(0x121AB0))
        assertEquals(0x0B0B0B, SigningColor.foreground(0xFFE066))
        assertEquals(0x0B0B0B, SigningColor.foreground(0x8B91F7))
    }

    @Test
    fun everyStringExistsInBothLanguages() {
        val arabic = SigningStrings.make(ResolvedLanguage.ARABIC)
        val english = SigningStrings.make(ResolvedLanguage.ENGLISH)
        val fields = SigningStrings::class.java.declaredFields.filter { it.type == String::class.java }
        assertTrue(fields.size >= 25)
        for (field in fields) {
            field.isAccessible = true
            val ar = field.get(arabic) as String
            val en = field.get(english) as String
            assertTrue(field.name, ar.isNotBlank())
            assertTrue(field.name, en.isNotBlank())
            assertNotEquals(field.name, ar, en)
        }
        assertTrue(arabic.isRightToLeft)
        assertEquals("توقيع المستند", arabic.title)
        assertEquals("Sign document", english.title)
    }

    @Test
    fun errorTextMapping() {
        val strings = SigningStrings.make(ResolvedLanguage.ENGLISH)
        assertEquals(strings.offlineTitle, strings.errorText(WsignSigningError.Network(-2, "x")).first)
        assertEquals(strings.timeoutTitle, strings.errorText(WsignSigningError.TimedOut).first)
        assertEquals(strings.serverTitle, strings.errorText(WsignSigningError.HttpStatus(503)).first)
        assertEquals(strings.invalidLinkTitle, strings.errorText(WsignSigningError.InvalidUrl(UrlValidationError.MissingUrl)).first)
        assertEquals(strings.invalidLinkTitle, strings.errorText(WsignSigningError.LinkUnavailable(SigningPageState.NOT_FOUND)).first)
        assertEquals(strings.crashTitle, strings.errorText(WsignSigningError.WebContentTerminated).first)
        assertEquals(strings.genericTitle, strings.errorText(WsignSigningError.Network(-6, "refused")).first)
        assertEquals(strings.genericTitle, strings.errorText(WsignSigningError.PageError("x")).first)
    }

    @Test
    fun errorIcons() {
        assertEquals(ErrorIcon.OFFLINE, SigningStrings.icon(WsignSigningError.Network(-2, "x")))
        assertEquals(ErrorIcon.CLOCK, SigningStrings.icon(WsignSigningError.TimedOut))
        assertEquals(ErrorIcon.LINK, SigningStrings.icon(WsignSigningError.UnexpectedReturnStatus("x")))
        assertEquals(ErrorIcon.WARNING, SigningStrings.icon(WsignSigningError.HttpStatus(500)))
    }

    @Test
    fun bridgeScriptUsesTheWebMessageObjectAndNoJavascriptInterface() {
        val source = BridgeScript.source
        assertTrue(source.contains("window.wsignSigning"))
        assertTrue(source.contains("JSON.stringify(body)"))
        assertTrue(source.contains("/\\/sign\\.php$/i"))
        assertTrue(source.contains("'fa-id-card', 'identity_unavailable'"))
        assertTrue(source.contains("pades=auto"))
        assertFalse(source.contains("$" + "OBJECT_NAME"))
        assertFalse(source.contains("removeAttribute('capture')"))
    }
}
