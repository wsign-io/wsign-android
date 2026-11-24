package io.wsign.signing

import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.wsign.signing.internal.BundleCodec
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundleCodecTest {

    /** Writes the bundle into a Parcel and back, as saved state and intents do. */
    private fun roundTrip(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            return parcel.readBundle(javaClass.classLoader)!!
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun configurationRoundTrips() {
        val configuration = WsignSigningConfiguration {
            language = WsignSigningConfiguration.Language.ARABIC
            addAllowedHost("sign.example.com")
            addReturnUrl("https://example.com/done")
            addReturnUrl(ReturnUrlPattern("myapp"))
            showsCloseButton = false
            appearance {
                tintColor = SigningColor(0x112233, 0x445566)
                interfaceStyle = WsignSigningConfiguration.InterfaceStyle.DARK
                title = "توقيع العقد"
                showsHeader = false
                showsProgressBar = false
                confirmsBeforeClosing = false
            }
            timeoutMillis = 45_000
            dismissal = WsignSigningConfiguration.Dismissal.Manual
            identityVerification = WsignSigningConfiguration.IdentityVerificationPolicy.LISTED_HOSTS_ONLY
            addIdentityVerificationHost("*.provider.example")
            detectsPlatformReturnNavigation = false
            usesEphemeralWebsiteData = false
            allowsInsecureLocalHosts = true
            isWebContentsDebuggingEnabled = true
        }
        assertEquals(configuration, BundleCodec.readConfiguration(roundTrip(BundleCodec.writeConfiguration(configuration))))
        assertEquals(
            WsignSigningConfiguration.DEFAULT,
            BundleCodec.readConfiguration(roundTrip(BundleCodec.writeConfiguration(WsignSigningConfiguration.DEFAULT))),
        )
        assertEquals(WsignSigningConfiguration.DEFAULT, BundleCodec.readConfiguration(null))
    }

    @Test
    fun everyResultRoundTrips() {
        val outcome = SigningOutcome(SigningReference("sr_1", "sgr_1"), SigningOutcome.Detection.RETURN_URL, "https://e.com/?x", true, "r")
        val results = listOf(
            SigningResult.Signed(outcome),
            SigningResult.Declined(SigningOutcome(detection = SigningOutcome.Detection.PAGE_CONTENT)),
            SigningResult.Canceled,
            SigningResult.Expired,
            SigningResult.Failed(WsignSigningError.InvalidUrl(UrlValidationError.MissingUrl)),
            SigningResult.Failed(WsignSigningError.InvalidUrl(UrlValidationError.Malformed)),
            SigningResult.Failed(WsignSigningError.InvalidUrl(UrlValidationError.UnsupportedScheme("http"))),
            SigningResult.Failed(WsignSigningError.InvalidUrl(UrlValidationError.MissingHost)),
            SigningResult.Failed(WsignSigningError.InvalidUrl(UrlValidationError.HostNotAllowed("evil.com"))),
            SigningResult.Failed(WsignSigningError.InvalidUrl(UrlValidationError.ContainsCredentials)),
            SigningResult.Failed(WsignSigningError.InvalidUrl(UrlValidationError.MissingToken)),
            SigningResult.Failed(WsignSigningError.Network(-2, "net::ERR_NAME_NOT_RESOLVED")),
            SigningResult.Failed(WsignSigningError.TimedOut),
            SigningResult.Failed(WsignSigningError.HttpStatus(503)),
            SigningResult.Failed(WsignSigningError.LinkUnavailable(SigningPageState.NOT_FOUND)),
            SigningResult.Failed(WsignSigningError.PageError("boom")),
            SigningResult.Failed(WsignSigningError.WebContentTerminated),
            SigningResult.Failed(WsignSigningError.UnexpectedReturnStatus("odd")),
        )
        for (result in results) {
            assertEquals(result, BundleCodec.readResult(roundTrip(BundleCodec.writeResult(result))))
        }
    }

    @Test
    fun contractTreatsMissingResultAsCanceled() {
        val contract = WsignSigning.Contract()
        assertEquals(SigningResult.Canceled, contract.parseResult(android.app.Activity.RESULT_CANCELED, null))
        assertEquals(SigningResult.Canceled, contract.parseResult(android.app.Activity.RESULT_OK, null))
    }
}
