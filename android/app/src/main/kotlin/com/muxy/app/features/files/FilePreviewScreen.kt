package com.muxy.app.features.files

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.models.FileLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FilePreviewScreen(
    state: FileManagerState,
    onDraft: (String) -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
) {
    val preview = state.preview ?: return
    Column(Modifier.fillMaxSize()) {
        if (preview.hasExternalChanges) {
            Text("Changed on ${state.location.host.shortName}", Modifier.padding(horizontal = 16.dp), color = LocalAppTheme.current.yellow)
            Text(state.location.host.draftReplacementMessage, Modifier.padding(horizontal = 16.dp))
            TextButton(onClick = onReload, enabled = state.canMutate) { Text("Reload") }
        }
        if (preview.isPreviewShortened && !preview.isEditing) {
            Text(
                "Preview shortened. Tap Edit file to load the complete text.",
                Modifier.padding(16.dp),
                color = LocalAppTheme.current.secondaryForeground,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoadingPreview -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                preview.isEditing -> FileTextEditor(preview.draft, onDraft, preview.wrapsLines, !state.isBusy)
                preview.text != null -> FileTextPreview(preview.displayText, preview.wrapsLines)
                preview.image != null -> FileImagePreview(preview.image, preview.entry.name)
                else -> Text("Preview unavailable", Modifier.align(Alignment.Center), color = LocalAppTheme.current.secondaryForeground)
            }
        }
        if (preview.text != null) {
            TextButton(
                onClick = if (preview.isEditing) onSave else onEdit,
                enabled = state.canMutate && !state.isLoadingPreview,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (preview.isEditing) "Save changes" else "Edit file") }
        }
    }
}

@Composable
private fun FileTextPreview(
    text: String,
    wrap: Boolean,
) {
    val horizontal = if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()).widthIn(min = 760.dp)
    SelectionContainer {
        Text(
            text,
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(horizontal)
                .padding(16.dp),
            color = LocalAppTheme.current.foreground,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            softWrap = wrap,
        )
    }
}

@Composable
private fun FileTextEditor(
    text: String,
    onChange: (String) -> Unit,
    wrap: Boolean,
    enabled: Boolean,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val theme = LocalAppTheme.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            EditText(context).apply {
                typeface = Typeface.MONOSPACE
                textSize = 14f
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int,
                        ) = Unit

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int,
                        ) = Unit

                        override fun afterTextChanged(s: Editable?) {
                            currentOnChange(s?.toString().orEmpty())
                        }
                    },
                )
            }
        },
        update = { editor ->
            if (editor.text.toString() != text) editor.setText(text)
            editor.setHorizontallyScrolling(!wrap)
            editor.isEnabled = enabled
            editor.setTextColor(theme.foreground.toArgb())
            editor.setHintTextColor(theme.secondaryForeground.toArgb())
        },
    )
}

@Composable
private fun FileImagePreview(
    data: ByteArray,
    name: String,
) {
    val image by produceState<ImageBitmap?>(null, data) {
        value = withContext(Dispatchers.Default) { decodeImage(data) }
    }
    val loaded = image
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loaded == null) {
            Text("Preview unavailable")
        } else {
            Image(loaded, name, Modifier.fillMaxSize().padding(16.dp))
        }
    }
}

internal fun decodeImage(data: ByteArray): ImageBitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    var sample = 1
    val longest = maxOf(options.outWidth, options.outHeight).toLong()
    while ((longest + sample - 1) / sample > FileLimits.IMAGE_PIXELS) sample *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sample
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)?.asImageBitmap()
}
