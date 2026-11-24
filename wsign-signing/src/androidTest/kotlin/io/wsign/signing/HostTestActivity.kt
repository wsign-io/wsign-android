package io.wsign.signing

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.CopyOnWriteArrayList

/** Hosts [WsignSigningFragment] the way an app would, recording what the fragment reports. */
class HostTestActivity : FragmentActivity(), WsignSigningFragment.Listener {
    val results: MutableList<SigningResult> = CopyOnWriteArrayList()
    val fragmentResults: MutableList<SigningResult> = CopyOnWriteArrayList()
    val events: MutableList<SigningEvent> = CopyOnWriteArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = CONTAINER })
        supportFragmentManager.setFragmentResultListener(WsignSigningFragment.REQUEST_KEY, this) { _, bundle ->
            WsignSigningFragment.resultFrom(bundle)?.let(fragmentResults::add)
        }
    }

    fun show(url: String, configuration: WsignSigningConfiguration) {
        supportFragmentManager.beginTransaction()
            .replace(CONTAINER, WsignSigningFragment.newInstance(url, configuration))
            .addToBackStack(WsignSigningFragment.BACK_STACK_NAME)
            .commit()
    }

    override fun onSigningResult(fragment: WsignSigningFragment, result: SigningResult) {
        results.add(result)
    }

    override fun onSigningEvent(fragment: WsignSigningFragment, event: SigningEvent) {
        events.add(event)
    }

    companion object {
        const val CONTAINER: Int = 0x7f0c0101
    }
}
