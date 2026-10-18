package dev.pawelduraj.memstash

import android.app.Application
import dev.pawelduraj.memstash.data.SettingsStore

class MemStashApp : Application() {
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
}
