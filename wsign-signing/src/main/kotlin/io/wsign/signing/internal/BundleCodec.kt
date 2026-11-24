package io.wsign.signing.internal

import android.os.Bundle
import io.wsign.signing.ReturnUrlPattern
import io.wsign.signing.SigningColor
import io.wsign.signing.SigningOutcome
import io.wsign.signing.SigningPageState
import io.wsign.signing.SigningReference
import io.wsign.signing.SigningResult
import io.wsign.signing.UrlValidationError
import io.wsign.signing.WsignSigningConfiguration
import io.wsign.signing.WsignSigningError

/**
 * Writes configurations and results into Bundles (intent extras, fragment arguments, saved state) with plain keys,
 * so the public types stay free of Android interfaces and the stored form is stable across SDK versions.
 */
internal object BundleCodec {
    const val EXTRA_URL: String = "io.wsign.signing.extra.URL"
    const val EXTRA_CONFIGURATION: String = "io.wsign.signing.extra.CONFIGURATION"
    const val EXTRA_RESULT: String = "io.wsign.signing.extra.RESULT"
    const val EXTRA_EVENT_TOKEN: String = "io.wsign.signing.extra.EVENT_TOKEN"

    fun writeConfiguration(configuration: WsignSigningConfiguration): Bundle = Bundle().apply {
        putString("language", configuration.language.name)
        putStringArrayList("allowedHosts", ArrayList(configuration.allowedHosts))
        val patterns = configuration.returnUrls.map { "${it.scheme}\n${it.host.orEmpty()}\n${it.pathPrefix}" }
        putStringArrayList("returnUrls", ArrayList(patterns))
        putBoolean("showsCloseButton", configuration.showsCloseButton)
        val appearance = configuration.appearance
        putInt("tintLight", appearance.tintColor.light)
        putInt("tintDark", appearance.tintColor.dark)
        putString("interfaceStyle", appearance.interfaceStyle.name)
        putString("title", appearance.title)
        putBoolean("showsHeader", appearance.showsHeader)
        putBoolean("showsProgressBar", appearance.showsProgressBar)
        putBoolean("confirmsBeforeClosing", appearance.confirmsBeforeClosing)
        putLong("timeoutMillis", configuration.timeoutMillis)
        when (val dismissal = configuration.dismissal) {
            is WsignSigningConfiguration.Dismissal.Manual -> putLong("dismissalDelay", -1L)
            is WsignSigningConfiguration.Dismissal.Automatic -> putLong("dismissalDelay", dismissal.delayMillis)
        }
        putString("identityVerification", configuration.identityVerification.name)
        putStringArrayList("identityVerificationHosts", ArrayList(configuration.identityVerificationHosts))
        putBoolean("detectsPlatformReturnNavigation", configuration.detectsPlatformReturnNavigation)
        putBoolean("usesEphemeralWebsiteData", configuration.usesEphemeralWebsiteData)
        putBoolean("allowsInsecureLocalHosts", configuration.allowsInsecureLocalHosts)
        putBoolean("isWebContentsDebuggingEnabled", configuration.isWebContentsDebuggingEnabled)
    }

    fun readConfiguration(bundle: Bundle?): WsignSigningConfiguration {
        if (bundle == null) return WsignSigningConfiguration.DEFAULT
        val defaults = WsignSigningConfiguration.DEFAULT
        return WsignSigningConfiguration.Builder().apply {
            language = enumOr(bundle.getString("language"), defaults.language)
            allowedHosts = (bundle.getStringArrayList("allowedHosts") ?: defaults.allowedHosts).toMutableSet()
            returnUrls = bundle.getStringArrayList("returnUrls").orEmpty().mapNotNull { encoded ->
                val parts = encoded.split('\n')
                if (parts.size == 3) ReturnUrlPattern(parts[0], parts[1].ifEmpty { null }, parts[2]) else null
            }.toMutableList()
            showsCloseButton = bundle.getBoolean("showsCloseButton", defaults.showsCloseButton)
            appearance = WsignSigningConfiguration.Appearance.Builder().apply {
                tintColor = SigningColor(
                    bundle.getInt("tintLight", SigningColor.BRAND.light),
                    bundle.getInt("tintDark", SigningColor.BRAND.dark),
                )
                interfaceStyle = enumOr(bundle.getString("interfaceStyle"), WsignSigningConfiguration.InterfaceStyle.SYSTEM)
                title = bundle.getString("title")
                showsHeader = bundle.getBoolean("showsHeader", true)
                showsProgressBar = bundle.getBoolean("showsProgressBar", true)
                confirmsBeforeClosing = bundle.getBoolean("confirmsBeforeClosing", true)
            }.build()
            timeoutMillis = bundle.getLong("timeoutMillis", defaults.timeoutMillis)
            val delay = bundle.getLong("dismissalDelay", WsignSigningConfiguration.DEFAULT_DISMISSAL_DELAY_MILLIS)
            dismissal = if (delay < 0) {
                WsignSigningConfiguration.Dismissal.Manual
            } else {
                WsignSigningConfiguration.Dismissal.Automatic(delay)
            }
            identityVerification = enumOr(bundle.getString("identityVerification"), defaults.identityVerification)
            identityVerificationHosts = bundle.getStringArrayList("identityVerificationHosts").orEmpty().toMutableSet()
            detectsPlatformReturnNavigation = bundle.getBoolean("detectsPlatformReturnNavigation", true)
            usesEphemeralWebsiteData = bundle.getBoolean("usesEphemeralWebsiteData", true)
            allowsInsecureLocalHosts = bundle.getBoolean("allowsInsecureLocalHosts", false)
            isWebContentsDebuggingEnabled = bundle.getBoolean("isWebContentsDebuggingEnabled", false)
        }.build()
    }

    fun writeResult(result: SigningResult): Bundle = Bundle().apply {
        when (result) {
            is SigningResult.Signed -> {
                putString("type", "signed")
                writeOutcome(this, result.outcome)
            }
            is SigningResult.Declined -> {
                putString("type", "declined")
                writeOutcome(this, result.outcome)
            }
            SigningResult.Canceled -> putString("type", "canceled")
            SigningResult.Expired -> putString("type", "expired")
            is SigningResult.Failed -> {
                putString("type", "failed")
                putBundle("error", writeError(result.error))
            }
        }
    }

    fun readResult(bundle: Bundle?): SigningResult? {
        if (bundle == null) return null
        return when (bundle.getString("type")) {
            "signed" -> readOutcome(bundle)?.let { SigningResult.Signed(it) }
            "declined" -> readOutcome(bundle)?.let { SigningResult.Declined(it) }
            "canceled" -> SigningResult.Canceled
            "expired" -> SigningResult.Expired
            "failed" -> readError(bundle.getBundle("error"))?.let { SigningResult.Failed(it) }
            else -> null
        }
    }

    private fun writeOutcome(bundle: Bundle, outcome: SigningOutcome) {
        bundle.putString("signatureRequestId", outcome.reference.signatureRequestId)
        bundle.putString("signerId", outcome.reference.signerId)
        bundle.putString("detection", outcome.detection.name)
        bundle.putString("returnUrl", outcome.returnUrl)
        bundle.putBoolean("wasAlreadyFinal", outcome.wasAlreadyFinal)
        bundle.putString("reason", outcome.reason)
    }

    private fun readOutcome(bundle: Bundle): SigningOutcome? {
        val detection = enumOrNull<SigningOutcome.Detection>(bundle.getString("detection")) ?: return null
        return SigningOutcome(
            reference = SigningReference(bundle.getString("signatureRequestId"), bundle.getString("signerId")),
            detection = detection,
            returnUrl = bundle.getString("returnUrl"),
            wasAlreadyFinal = bundle.getBoolean("wasAlreadyFinal"),
            reason = bundle.getString("reason"),
        )
    }

    fun writeError(error: WsignSigningError): Bundle = Bundle().apply {
        when (error) {
            is WsignSigningError.InvalidUrl -> {
                putString("type", "invalid_url")
                val reason = error.reason
                val name = when (reason) {
                    UrlValidationError.MissingUrl -> "MissingUrl"
                    UrlValidationError.Malformed -> "Malformed"
                    is UrlValidationError.UnsupportedScheme -> "UnsupportedScheme".also { putString("value", reason.scheme) }
                    UrlValidationError.MissingHost -> "MissingHost"
                    is UrlValidationError.HostNotAllowed -> "HostNotAllowed".also { putString("value", reason.host) }
                    UrlValidationError.ContainsCredentials -> "ContainsCredentials"
                    UrlValidationError.MissingToken -> "MissingToken"
                }
                putString("reason", name)
            }
            is WsignSigningError.Network -> {
                putString("type", "network")
                putInt("code", error.code)
                putString("value", error.message)
            }
            WsignSigningError.TimedOut -> putString("type", "timed_out")
            is WsignSigningError.HttpStatus -> {
                putString("type", "http_status")
                putInt("code", error.status)
            }
            is WsignSigningError.LinkUnavailable -> {
                putString("type", "link_unavailable")
                putString("value", error.state.value)
            }
            is WsignSigningError.PageError -> {
                putString("type", "page_error")
                putString("value", error.reason)
            }
            WsignSigningError.WebContentTerminated -> putString("type", "web_content_terminated")
            is WsignSigningError.UnexpectedReturnStatus -> {
                putString("type", "unexpected_return_status")
                putString("value", error.status)
            }
        }
    }

    fun readError(bundle: Bundle?): WsignSigningError? {
        if (bundle == null) return null
        val value = bundle.getString("value").orEmpty()
        return when (bundle.getString("type")) {
            "invalid_url" -> WsignSigningError.InvalidUrl(readValidationError(bundle.getString("reason"), value))
            "network" -> WsignSigningError.Network(bundle.getInt("code"), value)
            "timed_out" -> WsignSigningError.TimedOut
            "http_status" -> WsignSigningError.HttpStatus(bundle.getInt("code"))
            "link_unavailable" -> SigningPageState.fromValue(value)?.let { WsignSigningError.LinkUnavailable(it) }
            "page_error" -> WsignSigningError.PageError(value)
            "web_content_terminated" -> WsignSigningError.WebContentTerminated
            "unexpected_return_status" -> WsignSigningError.UnexpectedReturnStatus(value)
            else -> null
        }
    }

    private fun readValidationError(name: String?, value: String): UrlValidationError = when (name) {
        "Malformed" -> UrlValidationError.Malformed
        "UnsupportedScheme" -> UrlValidationError.UnsupportedScheme(value)
        "MissingHost" -> UrlValidationError.MissingHost
        "HostNotAllowed" -> UrlValidationError.HostNotAllowed(value)
        "ContainsCredentials" -> UrlValidationError.ContainsCredentials
        "MissingToken" -> UrlValidationError.MissingToken
        else -> UrlValidationError.MissingUrl
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T = enumOrNull<T>(name) ?: fallback
}
