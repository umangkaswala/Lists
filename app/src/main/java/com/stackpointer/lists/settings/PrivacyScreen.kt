package com.stackpointer.lists.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The privacy page PLAN.md's Phase 9 calls for, and the home for the dictation
 * disclosure Phase 8 deliberately deferred here.
 *
 * Written as plain sentences about what this build actually does, not as legal
 * boilerplate. Every claim below is checkable against the code — if one of them
 * stops being true, this page is wrong and has to change with it. The real
 * Play-store Privacy Policy is Phase 12's job; this is the honest placeholder.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Privacy",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            item {
                Text(
                    text = "Lists keeps your reminders on this phone.",
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            SECTIONS.forEach { section ->
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = section.title,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = section.body,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "This page is the plain-English version. A formal privacy " +
                        "policy will be published before Lists goes on the Play Store.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class PrivacySection(val title: String, val body: String)

private val SECTIONS = listOf(
    PrivacySection(
        "No account, no server",
        "There is nothing to sign in to. Your reminders, lists, notes, checklists, " +
            "photos and saved places are stored in a database on this phone and are " +
            "not sent anywhere. Uninstalling Lists deletes them."
    ),
    PrivacySection(
        "Reading dates from what you type",
        "When you type \"call the bank tomorrow at 6\", Lists works out the date and " +
            "time on the phone itself. Your text is never uploaded to do it. You can " +
            "turn this off in Settings › Capture."
    ),
    PrivacySection(
        "Dictation",
        "The microphone button hands the job to whichever speech app your phone " +
            "already has — Lists never records audio itself and does not ask for " +
            "microphone permission. Lists asks that app to work offline where it can, " +
            "but that is a request, not a guarantee: depending on your phone, what " +
            "you say may be sent to Google to be turned into text, under Google's own " +
            "privacy policy. If that matters to you, type instead of speaking."
    ),
    PrivacySection(
        "Photos",
        "A photo you attach is copied into Lists' own private storage, so it keeps " +
            "working if the original is moved or deleted. Lists does not ask for " +
            "access to your photo library; the system picker shows Lists only the " +
            "pictures you choose."
    ),
    PrivacySection(
        "Places and location",
        "Place reminders are handled by Android's geofencing, which watches for you " +
            "arriving at or leaving a place you saved. The coordinates stay on the " +
            "phone. Lists does not track where you are, keep a location history, or " +
            "use location for anything but the reminders you set. Location permission " +
            "is only asked for the first time you make a place reminder, and place " +
            "reminders are the only thing that stops working if you refuse."
    ),
    PrivacySection(
        "No analytics, no ads",
        "Lists contains no advertising, no tracking and no crash or usage reporting. " +
            "Nothing about how you use it is collected."
    ),
    PrivacySection(
        "Getting your data out",
        "Settings › Data › Export my reminders writes everything except photos to a " +
            "JSON or plain-text file and hands it to whichever app you choose. Nothing " +
            "leaves the phone unless you pick something that sends it."
    )
)
