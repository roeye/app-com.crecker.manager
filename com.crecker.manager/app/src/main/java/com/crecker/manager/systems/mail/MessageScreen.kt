package com.crecker.manager.systems.mail

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.format.Formatter
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.crecker.manager.R
import com.crecker.manager.core.model.LocalizedException
import com.crecker.manager.core.model.describe
import com.crecker.manager.core.model.formatDateTime
import com.crecker.manager.core.settings.ScopedSettings
import com.crecker.manager.graph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId

/**
 * תצוגת הודעה – קריאה בלבד.
 * HTML מוצג ב-WebView בלי JavaScript, ובלי טעינה מהרשת (תמונות מעקב וכו') עד שהמשתמש מאשר.
 * קישור נפתח רק אחרי אישור, בדפדפן.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(account: MailAccount, ref: MessageRef, header: MailHeader?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val settings = remember { context.graph.settings.forSystem(MailSystem.ID) }
    val snackbar = remember { SnackbarHostState() }

    var content by remember(ref) { mutableStateOf<MailContent?>(null) }
    var error by remember(ref) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(ref, attempt) {
        error = null
        try {
            content = withClient(settings, account) { it.message(ref.folder, ref.uidValidity, ref.uid, MailParser::parse) }
            if (content == null) error = resources.getString(R.string.mail_message_gone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.describe(context)
        }
    }

    var showRemote by rememberSaveable(ref.encode()) { mutableStateOf(false) }
    var linkToOpen by remember { mutableStateOf<Uri?>(null) }
    var pending by remember { mutableStateOf<MailAttachment?>(null) }
    var saving by remember { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(SaveAttachmentContract()) { uri ->
        val attachment = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            saving = true
            val message = try {
                withClient(settings, account) { client ->
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        client.downloadAttachment(ref.folder, ref.uidValidity, ref.uid, attachment.path, out)
                    } ?: throw LocalizedException(R.string.mail_err_save_target)
                }
                resources.getString(R.string.mail_saved, attachment.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                resources.getString(R.string.mail_save_failed, e.describe(context))
            } finally {
                saving = false
            }
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (content?.subject ?: header?.subject).orEmpty().ifBlank { stringResource(R.string.mail_no_subject) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MessageHeader(account, ref, header, content)
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            val loaded = content
            when {
                error != null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✗ $error", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { attempt++ }) { Text(stringResource(R.string.mail_retry)) }
                }
                loaded == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    if (loaded.attachments.isNotEmpty()) {
                        Attachments(loaded.attachments, enabled = !saving) { attachment ->
                            pending = attachment
                            saveLauncher.launch(attachment)
                        }
                    }
                    val html = loaded.html
                    if (html != null) {
                        val hasRemote = remember(html) { MailParser.hasRemoteContent(html) }
                        if (hasRemote && !showRemote) RemoteBlockedBanner { showRemote = true }
                        HorizontalDivider()
                        MailWebView(html, allowRemote = showRemote, onLink = { linkToOpen = it }, Modifier.fillMaxWidth().weight(1f))
                    } else {
                        HorizontalDivider()
                        SelectionContainer(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                            Text(
                                loaded.text?.ifBlank { null } ?: stringResource(R.string.mail_no_body),
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    linkToOpen?.let { uri ->
        AlertDialog(
            onDismissRequest = { linkToOpen = null },
            title = { Text(stringResource(R.string.mail_open_link_title)) },
            text = { Text(uri.toString(), maxLines = 8, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    linkToOpen = null
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: ActivityNotFoundException) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.mail_no_app_for_link)) }
                    }
                }) { Text(stringResource(R.string.mail_open)) }
            },
            dismissButton = { TextButton(onClick = { linkToOpen = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** מתחבר לחשבון (הסיסמה מההגדרות), מריץ את [block] ומתנתק. */
private suspend fun <T> withClient(settings: ScopedSettings, account: MailAccount, block: (ImapClient) -> T): T {
    val password = settings.getSecret(MailAccounts.passwordKey(account.id)) ?: throw LocalizedException(R.string.mail_err_no_password)
    return runInterruptible(Dispatchers.IO) {
        ImapClient(account, password).use { client ->
            client.connect()
            block(client)
        }
    }
}

@Composable
private fun MessageHeader(account: MailAccount, ref: MessageRef, header: MailHeader?, content: MailContent?) {
    val date = content?.date ?: header?.date
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SelectionContainer {
            Text(
                (content?.subject ?: header?.subject).orEmpty().ifBlank { stringResource(R.string.mail_no_subject) },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        HeaderLine(R.string.mail_from, content?.from ?: header?.let { listOf(it.from, "<${it.fromAddress}>").joinToString(" ") })
        HeaderLine(R.string.mail_to, content?.to?.joinToString())
        HeaderLine(R.string.mail_cc, content?.cc?.takeIf { it.isNotEmpty() }?.joinToString())
        HeaderLine(R.string.mail_date, date?.takeIf { it > 0 }?.let { formatDateTime(Instant.ofEpochMilli(it), ZoneId.systemDefault()) })
        Text(
            listOf(account.label, folderLabel(ref.folder)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeaderLine(label: Int, value: String?) {
    if (value.isNullOrBlank()) return
    SelectionContainer {
        Text(
            "${stringResource(label)}: $value",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Attachments(attachments: List<MailAttachment>, enabled: Boolean, onSave: (MailAttachment) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            AssistChip(
                enabled = enabled,
                onClick = { onSave(attachment) },
                leadingIcon = { Icon(Icons.Default.Attachment, stringResource(R.string.mail_save_attachment), Modifier.size(18.dp)) },
                label = {
                    val size = attachment.size.takeIf { it > 0 }?.let { " · " + Formatter.formatShortFileSize(context, it.toLong()) }.orEmpty()
                    Text(attachment.name + size, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
private fun RemoteBlockedBanner(onShow: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.mail_remote_blocked), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onShow) { Text(stringResource(R.string.mail_show_remote)) }
        }
    }
}

/** תצוגת HTML מוגנת: בלי JavaScript, בלי גישה לקבצים, וכל ניווט עובר דרך [onLink] (אישור לפני פתיחה). */
@Composable
private fun MailWebView(html: String, allowRemote: Boolean, onLink: (Uri) -> Unit, modifier: Modifier) {
    val currentOnLink by rememberUpdatedState(onLink)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = false
                    javaScriptCanOpenWindowsAutomatically = false
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = false
                    builtInZoomControls = true
                    displayZoomControls = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportMultipleWindows(false)
                }
                setBackgroundColor(Color.WHITE)
                webViewClient = MailWebViewClient { currentOnLink(it) }
            }
        },
        update = { view ->
            val key = html.hashCode() to allowRemote
            if (view.tag != key) {
                view.tag = key
                (view.webViewClient as MailWebViewClient).allowRemote = allowRemote
                view.settings.blockNetworkLoads = !allowRemote
                view.loadDataWithBaseURL(null, wrapHtml(html), "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private class MailWebViewClient(private val onLink: (Uri) -> Unit) : WebViewClient() {
    /** נקרא גם מחוט רקע ([shouldInterceptRequest]). */
    @Volatile
    var allowRemote = false

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (request.isForMainFrame && request.hasGesture()) onLink(request.url)
        return true // אף פעם לא מנווטים בתוך ה-WebView
    }

    /** הגנה נוספת מעבר ל-blockNetworkLoads: כל בקשת http(s) נענית בתשובה ריקה. */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val scheme = request.url.scheme?.lowercase()
        if (allowRemote || (scheme != "http" && scheme != "https")) return null
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}

/** התאמה למסך טלפון: רוחב לפי המסך, ותמונות לא חורגות מהרוחב. */
private fun wrapHtml(html: String) =
    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>img{max-width:100%;height:auto}body{margin:8px;word-wrap:break-word}</style>" + html

/** "שמירה בשם" (Storage Access Framework) – בלי הרשאות אחסון. */
private class SaveAttachmentContract : ActivityResultContract<MailAttachment, Uri?>() {
    override fun createIntent(context: Context, input: MailAttachment): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.name)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
