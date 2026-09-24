package com.arjun.absolutra.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

// GeckoRuntime can be created only once per process.
object GeckoRuntimeProvider {
    private var runtime: GeckoRuntime? = null

    fun getRuntime(context: Context): GeckoRuntime {
        if (runtime == null) {
            runtime = GeckoRuntime.create(context.applicationContext)
        }
        return runtime!!
    }
}

@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val geckoRuntime = remember { GeckoRuntimeProvider.getRuntime(context) }
    val geckoSession = remember {
        GeckoSession().apply {
            open(geckoRuntime)
            loadUri("https://www.mozilla.org")
        }
    }

    var urlInput by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            geckoSession.close()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search or type URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = { loadUrl(geckoSession, urlInput) }
                )
            )

            Button(
                onClick = { loadUrl(geckoSession, urlInput) },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Go")
            }
        }

        AndroidView(
            factory = { ctx ->
                GeckoView(ctx).apply {
                    setSession(geckoSession)
                }
            },
            onRelease = { view ->
                view.releaseSession()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

private fun loadUrl(session: GeckoSession, input: String) {
    var url = input.trim()
    if (url.isNotEmpty()) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        session.loadUri(url)
    }
}
