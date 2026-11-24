package io.wsign.signing.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.wsign.signing.SigningEventListener
import io.wsign.signing.SigningRequest
import io.wsign.signing.SigningResult
import io.wsign.signing.UrlValidationError
import io.wsign.signing.WsignSigning
import io.wsign.signing.WsignSigningConfiguration
import io.wsign.signing.WsignSigningError

/**
 * Starts the full-screen signing activity from Compose. Create it with [rememberWsignSigningLauncher].
 */
@Stable
public class WsignSigningLauncher internal constructor(
    private val launcher: ActivityResultLauncher<SigningRequest>,
) {
    /** Presents the signing page for [url]. The result arrives in the `onResult` callback given at creation. */
    public fun launch(
        url: String,
        configuration: WsignSigningConfiguration = WsignSigningConfiguration.DEFAULT,
    ) {
        launcher.launch(SigningRequest(url, configuration))
    }

    /** Presents the signing page for [request]. */
    public fun launch(request: SigningRequest) {
        launcher.launch(request)
    }
}

/**
 * Remembers a [WsignSigningLauncher]. [onResult] is called once per launch, after the signing screen has closed,
 * and is redelivered after the app's process is recreated (Activity Result API).
 *
 * ```kotlin
 * val signing = rememberWsignSigningLauncher { result ->
 *     if (result.isSigned) viewModel.refreshOrder()
 * }
 * Button(onClick = { signing.launch(signingUrl) }) { Text("Sign") }
 * ```
 *
 * @param onEvent informational events while the signing screen is open.
 */
@Composable
public fun rememberWsignSigningLauncher(
    onEvent: SigningEventListener? = null,
    onResult: (SigningResult) -> Unit,
): WsignSigningLauncher {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val contract = remember {
        WsignSigning.Contract { event -> currentOnEvent?.onSigningEvent(event) }
    }
    val launcher = rememberLauncherForActivityResult(contract, onResult)
    return remember(launcher) { WsignSigningLauncher(launcher) }
}

/**
 * Presents the signing page full screen while [isPresented] is `true`, the declarative twin of the iOS
 * `.wsignSigning(isPresented:url:)` modifier.
 *
 * [onResult] is called once per presentation, after the signing screen has closed; set [isPresented] back to `false`
 * there. A `null` [url] reports `Failed(InvalidUrl(MissingUrl))` without opening anything. The presentation survives
 * configuration changes and process recreation.
 *
 * ```kotlin
 * var signing by rememberSaveable { mutableStateOf(false) }
 * WsignSigningSheet(isPresented = signing, url = signingUrl) { result ->
 *     signing = false
 *     if (result.isSigned) viewModel.refreshOrder()
 * }
 * ```
 */
@Composable
public fun WsignSigningSheet(
    isPresented: Boolean,
    url: String?,
    configuration: WsignSigningConfiguration = WsignSigningConfiguration.DEFAULT,
    onEvent: SigningEventListener? = null,
    onResult: (SigningResult) -> Unit,
) {
    val currentOnResult by rememberUpdatedState(onResult)
    var launched by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberWsignSigningLauncher(onEvent) { result ->
        launched = false
        currentOnResult(result)
    }
    LaunchedEffect(isPresented, url) {
        if (!isPresented) {
            launched = false
            return@LaunchedEffect
        }
        if (launched) return@LaunchedEffect
        if (url == null) {
            currentOnResult(SigningResult.Failed(WsignSigningError.InvalidUrl(UrlValidationError.MissingUrl)))
            return@LaunchedEffect
        }
        launched = true
        launcher.launch(url, configuration)
    }
}
