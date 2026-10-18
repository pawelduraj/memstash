package dev.pawelduraj.memstash

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import dev.pawelduraj.memstash.data.Sender
import dev.pawelduraj.memstash.data.uris
import dev.pawelduraj.memstash.ui.ImportEvent
import dev.pawelduraj.memstash.ui.ImportScreen
import dev.pawelduraj.memstash.ui.ImportViewModel
import dev.pawelduraj.memstash.ui.MemeTheme

class ImportActivity : ComponentActivity() {
    private val viewModel: ImportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The target folder is trusted only from this app, not from the exported share alias.
        val internal = intent.component?.className == ImportActivity::class.java.name
        viewModel.start(
            uris = sharedImages(intent),
            initialTarget = if (internal) intent.getStringExtra(EXTRA_TARGET) else null,
        )

        setContent {
            MemeTheme {
                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is ImportEvent.Done -> {
                                toast(event.message)
                                finish()
                            }
                            is ImportEvent.Failed -> toast(event.message)
                        }
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ImportScreen(vm = viewModel, onCancel = ::finish, onOpenApp = ::openApp)
                }
            }
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun sharedImages(intent: Intent): List<Uri> {
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        val uris = streams.ifEmpty { intent.clipData?.uris().orEmpty() }
        return uris.filter { it.scheme == ContentResolver.SCHEME_CONTENT && it.authority != Sender.fileAuthority(this) }.distinct()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        private const val EXTRA_TARGET = "dev.pawelduraj.memstash.extra.TARGET"

        fun importImages(context: Context, uris: List<Uri>, target: String) {
            context.startActivity(
                Intent(context, ImportActivity::class.java)
                    .setAction(Intent.ACTION_SEND_MULTIPLE)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    .putExtra(EXTRA_TARGET, target)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }
}
