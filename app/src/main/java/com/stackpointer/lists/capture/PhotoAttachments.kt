package com.stackpointer.lists.capture

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.stackpointer.lists.data.repository.AttachmentRepository
import com.stackpointer.lists.ui.theme.ListsCorner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import android.graphics.BitmapFactory
import java.io.File

/** Everything the sheet needs to add a photo, in one remembered bundle. */
class PhotoPickers(
    val pickFromGallery: () -> Unit,
    val takePhoto: () -> Unit
)

/**
 * Gallery and camera launchers.
 *
 * The gallery uses the system **photo picker**, which needs no storage
 * permission at all and shows only what the user chooses to share. The camera
 * uses `TakePicture` with a FileProvider URI — and Lists deliberately does not
 * declare `CAMERA` in its manifest, because declaring it would *require*
 * granting it before the camera app could be used at all.
 */
@Composable
fun rememberPhotoPickers(
    repository: AttachmentRepository,
    onImported: (Uri) -> Unit,
    onCaptured: (String) -> Unit,
    onFailed: () -> Unit
): PhotoPickers {
    val context = LocalContext.current
    // rememberSaveable, and a String rather than a File: the camera app can
    // push this activity out of memory, and a plain remember would come back
    // null — throwing away a photo the user had just successfully taken.
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onImported) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCapturePath?.let(::File)
        pendingCapturePath = null
        when {
            !success -> Unit // Cancelled — nothing to report.
            // A "successful" capture can still leave an empty file if the
            // camera app was killed mid-write. Attaching it would show a
            // permanently blank thumbnail.
            file == null || !file.exists() || file.length() == 0L -> {
                file?.delete()
                onFailed()
            }
            else -> onCaptured(file.name)
        }
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    return remember(galleryLauncher, cameraLauncher) {
        PhotoPickers(
            pickFromGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            takePhoto = {
                // The sheet's own scope is right here: this only builds a path
                // and hands it to the camera, and there is nothing left to
                // finish if the sheet goes away mid-flight.
                scope.launch {
                    val file = repository.newCameraFile()
                    val uri = runCatching {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    }.getOrNull()
                    if (uri == null) {
                        onFailed()
                    } else {
                        pendingCapturePath = file.path
                        cameraLauncher.launch(uri)
                    }
                }
            }
        )
    }
}

/** The "Photo" action's little menu: camera or gallery. */
@Composable
fun PhotoSourceMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    pickers: PhotoPickers
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Take a photo") },
            leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
            onClick = {
                onDismiss()
                pickers.takePhoto()
            }
        )
        DropdownMenuItem(
            text = { Text("Choose from gallery") },
            leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
            onClick = {
                onDismiss()
                pickers.pickFromGallery()
            }
        )
    }
}

/** A scrolling strip of thumbnails, each with a remove button. */
@Composable
fun PhotoStrip(
    photos: List<PhotoDraft>,
    repository: AttachmentRepository,
    onRemove: (PhotoDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        photos.forEach { photo ->
            Box {
                PhotoThumbnail(
                    file = repository.fileFor(photo.fileName),
                    modifier = Modifier
                        .size(72.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(ListsCorner.medium))
                )
                Surface(
                    onClick = { onRemove(photo) },
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp).padding(2.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove photo",
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Decodes a thumbnail off the main thread and at a sane size.
 *
 * A modern phone camera produces images far larger than any thumbnail needs;
 * decoding one at full size to draw it at 72dp is how a list of photos turns
 * into an OutOfMemoryError. `inSampleSize` keeps the decode proportional to
 * what is actually drawn.
 */
@Composable
fun PhotoThumbnail(file: File, modifier: Modifier = Modifier, targetPx: Int = 320) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, file.path, targetPx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > targetPx || bounds.outHeight / sample > targetPx) {
                    sample *= 2
                }
                BitmapFactory.decodeFile(
                    file.path,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "Attached photo",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
