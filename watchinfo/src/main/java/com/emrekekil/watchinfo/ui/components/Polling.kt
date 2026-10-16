package com.emrekekil.watchinfo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Yükleme durumu: ilk veri gelene kadar [Loading], hata olursa [Failed]. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val error: Throwable) : LoadState<Nothing>
}

/**
 * [producer]'ı çalıştırır ve [intervalMillis] verilmişse ekran görünür kaldığı sürece tekrarlar.
 * Saat ekranı kapandığında veya uygulama arka plana geçtiğinde yoklama durur; pil harcanmaz.
 */
@Composable
fun <T> rememberPolled(key: Any?, intervalMillis: Long?, producer: suspend () -> T): State<LoadState<T>> {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState<LoadState<T>>(LoadState.Loading, key, intervalMillis) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = try {
                    LoadState.Ready(producer())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Önceki veri varsa onu koru; geçici bir okuma hatası ekranı boşaltmasın.
                    if (value is LoadState.Ready) value else LoadState.Failed(e)
                }
                if (intervalMillis == null) break
                delay(intervalMillis)
            }
        }
    }
}
