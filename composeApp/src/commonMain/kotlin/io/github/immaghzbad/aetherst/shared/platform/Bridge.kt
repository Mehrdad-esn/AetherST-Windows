package io.github.immaghzbad.aetherst.shared.platform

import io.github.immaghzbad.aetherst.shared.model.ConnectionStatus
import io.github.immaghzbad.aetherst.shared.model.SessionTraffic
import kotlinx.coroutines.flow.MutableStateFlow

interface BridgeContract {
    val statusOverride: MutableStateFlow<ConnectionStatus?>
    val trafficOverride: MutableStateFlow<SessionTraffic?>
    val elapsedOverride: MutableStateFlow<Long?>
}

object Bridge : BridgeContract {
    override val statusOverride = MutableStateFlow<ConnectionStatus?>(null)
    override val trafficOverride = MutableStateFlow<SessionTraffic?>(null)
    override val elapsedOverride = MutableStateFlow<Long?>(null)

    var pickFile: ((onResult: (String?) -> Unit) -> Unit)? = null
    var saveFile: ((fileName: String, content: String, onResult: (Boolean) -> Unit) -> Unit)? = null
    var pickOvpnFile: ((onResult: (String?) -> Unit) -> Unit)? = null
}
