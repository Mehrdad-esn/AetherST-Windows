package io.github.immaghzbad.aetherst.core

import androidx.annotation.Keep
import io.github.immaghzbad.aetherst.data.LogRepository

@Keep
object HevTun2SocksNative {
    private val loaded: Boolean
    private val failure: Throwable?

    init {
        var nativeLoaded = false
        var nativeFailure: Throwable? = null
        try {
            System.loadLibrary("hev-tun2socks-jni")
            nativeLoaded = true
        } catch (throwable: Throwable) {
            nativeFailure = throwable
            LogRepository.e("[Hev] Native library load failed: ${throwable.localizedMessage}")
        }
        loaded = nativeLoaded
        failure = nativeFailure
    }

    val isAvailable: Boolean
        get() = loaded

    external fun nativeStart(configStr: String, tunFd: Int): Int
    external fun nativeStop()
    external fun nativeGetStats(): LongArray?
    external fun nativeGetVersion(): Int
}
