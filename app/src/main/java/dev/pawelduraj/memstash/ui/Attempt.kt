package dev.pawelduraj.memstash.ui

import android.util.Log
import kotlinx.coroutines.CancellationException

internal const val TAG = "MemStash"

internal inline fun <T> attempt(log: String, onFailure: (Exception) -> T, block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, log, e)
        onFailure(e)
    }
