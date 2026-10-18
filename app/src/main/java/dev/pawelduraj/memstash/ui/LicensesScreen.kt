package dev.pawelduraj.memstash.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.pawelduraj.memstash.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full texts are bundled, so they can be read offline. */
private enum class License(val title: String, @param:RawRes val text: Int) {
    GPL_3_0("GNU General Public License v3.0", R.raw.license_gpl_3_0),
    APACHE_2_0("Apache License 2.0", R.raw.license_apache_2_0),
    /** OFL files open with the font's own notice, so each font has its own copy. */
    FIGTREE_OFL_1_1("SIL Open Font License 1.1", R.raw.figtree_ofl_1_1),
}

private class LicensedPart(val name: String, val copyright: String, val license: License)

private val APP = LicensedPart("MemStash", COPYRIGHT, License.GPL_3_0)

/**
 * What ends up in the APK. Libraries R8 removes entirely, or that only point to AndroidX, aren't listed. The notices
 * are the libraries' own for the versions used; where every file has its own, the years span all of them.
 */
private val LIBRARIES = listOf(
    LicensedPart("Accompanist", "Copyright © 2021 The Android Open Source Project", License.APACHE_2_0),
    LicensedPart("AndroidX", "Copyright © 2009-2026 The Android Open Source Project", License.APACHE_2_0),
    LicensedPart("Coil", "Copyright © 2026 Coil Contributors", License.APACHE_2_0),
    LicensedPart("Guava ListenableFuture", "Copyright © 2007 The Guava Authors", License.APACHE_2_0),
    LicensedPart(
        "Kotlin",
        "Copyright © 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors",
        License.APACHE_2_0,
    ),
    LicensedPart(
        "kotlinx.coroutines",
        "Copyright © 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors",
        License.APACHE_2_0,
    ),
    LicensedPart("Okio", "Copyright © 2014-2025 Square, Inc.", License.APACHE_2_0),
)

private val FONTS = listOf(LicensedPart("Figtree", "Copyright © 2022 The Figtree Project Authors", License.FIGTREE_OFL_1_1))

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val resources = LocalResources.current
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val license = (listOf(APP) + LIBRARIES + FONTS).find { it.name == expanded }?.license
    val loaded by produceState<Pair<License, List<String>>?>(null, license) {
        if (license == null) return@produceState
        value = license to withContext(Dispatchers.IO) {
            resources.openRawResource(license.text).bufferedReader().use { paragraphs(it.readText()) }
        }
    }
    val text = loaded?.takeIf { it.first == license }?.second.orEmpty()
    val librariesTitle = stringResource(R.string.licenses_libraries)
    val fontTitle = stringResource(R.string.licenses_font)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        ScreenBar(stringResource(R.string.licenses), onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            listOf(null to listOf(APP), librariesTitle to LIBRARIES, fontTitle to FONTS).forEach { (title, parts) ->
                if (title != null) item(key = "section $title") { Section(title) }
                parts.forEach { part ->
                    val open = part.name == expanded
                    item(key = part.name) {
                        PartRow(part, open) { expanded = if (open) null else part.name }
                    }
                    if (open) {
                        items(text) { paragraph ->
                            Text(
                                paragraph,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartRow(part: LicensedPart, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(part.name)
            Text(
                "${part.copyright}\n${part.license.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(AppIcons.ChevronDown, contentDescription = null, modifier = Modifier.rotate(if (open) 180f else 0f))
    }
}

/**
 * License files are wrapped at about 70 characters, which leaves ragged lines on a phone, so the lines of each
 * paragraph are joined. A line without lowercase letters is a heading and keeps its line break.
 */
private fun paragraphs(text: String): List<String> =
    text.trim().split(Regex("\n\\s*\n")).map { paragraph ->
        val lines = paragraph.lines().map { it.trim() }
        buildString {
            lines.forEachIndexed { i, line ->
                if (i > 0) append(if (lines[i - 1].none { it.isLowerCase() }) '\n' else ' ')
                append(line)
            }
        }
    }
