package dev.pawelduraj.memstash.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object LibraryEvents {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}
