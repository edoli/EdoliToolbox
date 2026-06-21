package kr.co.edoli.edolitool.feature.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.edoli.edolitool.util.decodeBitmapBounds
import kr.co.edoli.edolitool.util.decodeBitmapFromUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardAnalyzerRoute(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(readClipboardSnapshot(context)) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "클립보드 분석",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "뒤로",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { snapshot = readClipboardSnapshot(context) }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "새로고침",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (snapshot.clipData == null) {
                    EmptyClipboardCard(snapshot = snapshot)
                } else {
                    ClipboardSummaryCard(snapshot = snapshot)
                    snapshot.items.forEachIndexed { index, item ->
                        ClipboardItemCard(
                            index = index,
                            item = item,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyClipboardCard(snapshot: ClipboardSnapshot) {
    InfoCard(title = "현재 클립보드") {
        Text(
            text = if (snapshot.hasPrimaryClip) {
                "클립보드가 있지만 앱에서 읽을 수 있는 항목이 없습니다."
            } else {
                "현재 클립보드가 비어 있습니다."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Android는 보안상 포그라운드 앱에서만 클립보드 접근을 허용합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClipboardSummaryCard(snapshot: ClipboardSnapshot) {
    InfoCard(title = "요약") {
        DetailRow("항목 수", snapshot.items.size.toString())
        DetailRow("라벨", snapshot.description?.label?.toString().orEmpty().ifBlank { "없음" })
        DetailRow("MIME", snapshot.mimeTypes.ifEmpty { listOf("없음") }.joinToString())
        snapshot.timestampText?.let {
            DetailRow("작성 시각", it)
        }
    }
}

@Composable
private fun ClipboardItemCard(
    index: Int,
    item: ClipboardItemSnapshot,
) {
    InfoCard(title = "Item ${index + 1}") {
        item.text?.let { text ->
            TextSection(title = "Text", value = text)
            TextAnalysisSection(text = text)
        }
        item.htmlText?.let { html ->
            TextSection(title = "HTML", value = html)
            HtmlPreviewSection(html = html)
        }
        item.uri?.let { uri ->
            UriSection(
                uri = uri,
                metadata = item.uriMetadata,
                canPreviewImage = item.canPreviewImage,
            )
        }
        item.intentSummary?.let {
            TextSection(title = "Intent", value = it)
        }
        if (item.text == null && item.htmlText == null && item.uri == null && item.intentSummary == null) {
            Text(
                text = "분석할 수 있는 공개 필드가 없습니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HtmlPreviewSection(html: String) {
    val context = LocalContext.current
    val embeddedContentChecks by produceState<List<HtmlContentUriCheck>>(initialValue = emptyList(), html) {
        value = withContext(Dispatchers.IO) {
            analyzeHtmlContentUris(context.applicationContext, html)
        }
    }
    Text(
        text = "HTML Preview",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 420.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    settings.javaScriptEnabled = false
                    settings.allowContentAccess = true
                    settings.allowFileAccess = false
                    settings.loadsImagesAutomatically = true
                    settings.defaultTextEncodingName = "utf-8"
                    webViewClient = ClipboardHtmlWebViewClient(context)
                    loadDataWithBaseURL(
                        null,
                        html,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(
                    null,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (embeddedContentChecks.isNotEmpty()) {
        HtmlContentUriSection(checks = embeddedContentChecks)
    }
}

@Composable
private fun HtmlContentUriSection(checks: List<HtmlContentUriCheck>) {
    Text(
        text = "HTML content URI",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    checks.forEachIndexed { index, check ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Content ${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            DetailRow("URI", check.uri.toString())
            DetailRow("MIME", check.mimeType ?: "알 수 없음")
            DetailRow("Stream open", if (check.canOpenStream) "가능" else "실패")
            check.errorMessage?.let { error ->
                DetailRow("Error", error)
            }
        }
    }
}

private class ClipboardHtmlWebViewClient(
    private val context: Context,
) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        val uri = request?.url ?: return null
        if (uri.scheme != "content") return null

        return runCatching {
            val mimeType = context.contentResolver.getType(uri) ?: guessMimeType(uri.toString())
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            WebResourceResponse(
                mimeType,
                "UTF-8",
                stream,
            )
        }.getOrNull()
    }

    private fun guessMimeType(value: String): String {
        val lower = value.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            else -> "application/octet-stream"
        }
    }
}

@Composable
private fun UriSection(
    uri: Uri,
    metadata: UriMetadata?,
    canPreviewImage: Boolean,
) {
    Text(
        text = "URI",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = uri.toString(),
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
    )
    DetailRow("Scheme", uri.scheme.orEmpty().ifBlank { "없음" })
    DetailRow("Authority", uri.authority.orEmpty().ifBlank { "없음" })
    DetailRow("Path", uri.path.orEmpty().ifBlank { "없음" })
    DetailRow("Query", uri.query.orEmpty().ifBlank { "없음" })
    metadata?.let {
        DetailRow("Display name", it.displayName ?: "없음")
        DetailRow("Size", it.sizeBytes?.let(::formatBytes) ?: "알 수 없음")
        DetailRow("Image size", it.imageSize ?: "알 수 없음")
        DetailRow("Resolved MIME", it.mimeType ?: "알 수 없음")
    }
    if (canPreviewImage) {
        ClipboardImagePreview(uri = uri)
    }
}

@Composable
private fun ClipboardImagePreview(uri: Uri) {
    val context = LocalContext.current
    val bitmapState by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeBitmapFromUri(context, uri, maxSide = 1280) }.getOrNull()
        }
    }
    val bitmap = bitmapState
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "클립보드 이미지 미리보기",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            text = "이미지를 불러오는 중이거나 미리보기를 열 수 없습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TextAnalysisSection(text: String) {
    val analysis = remember(text) { analyzeText(text) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailRow("문자 수", analysis.charCount.toString())
        DetailRow("줄 수", analysis.lineCount.toString())
        DetailRow("단어 수", analysis.wordCount.toString())
        DetailRow("URL", analysis.urls.ifEmpty { listOf("없음") }.joinToString())
        DetailRow("Email", analysis.emails.ifEmpty { listOf("없음") }.joinToString())
        DetailRow("JSON 후보", if (analysis.looksLikeJson) "예" else "아니오")
        DetailRow("Base64 후보", if (analysis.looksLikeBase64) "예" else "아니오")
    }
}

@Composable
private fun TextSection(
    title: String,
    value: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = if (value.length < 240) FontFamily.Monospace else FontFamily.Default,
    )
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private data class ClipboardSnapshot(
    val hasPrimaryClip: Boolean,
    val clipData: ClipData?,
    val description: ClipDescription?,
    val mimeTypes: List<String>,
    val timestampText: String?,
    val items: List<ClipboardItemSnapshot>,
)

private data class ClipboardItemSnapshot(
    val text: String?,
    val htmlText: String?,
    val uri: Uri?,
    val uriMetadata: UriMetadata?,
    val canPreviewImage: Boolean,
    val intentSummary: String?,
)

private data class HtmlContentUriCheck(
    val uri: Uri,
    val mimeType: String?,
    val canOpenStream: Boolean,
    val errorMessage: String?,
)

private data class UriMetadata(
    val displayName: String?,
    val sizeBytes: Long?,
    val mimeType: String?,
    val imageSize: String?,
)

private data class TextAnalysis(
    val charCount: Int,
    val lineCount: Int,
    val wordCount: Int,
    val urls: List<String>,
    val emails: List<String>,
    val looksLikeJson: Boolean,
    val looksLikeBase64: Boolean,
)

private val HtmlContentUriRegex = Regex("""content://[^\s<>"']+""")

private fun analyzeHtmlContentUris(
    context: Context,
    html: String,
): List<HtmlContentUriCheck> {
    return HtmlContentUriRegex.findAll(html)
        .map { match -> unescapeHtmlAttribute(match.value) }
        .distinct()
        .map { rawUri ->
            val uri = Uri.parse(rawUri)
            val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            val streamResult = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.read()
                } ?: error("ContentResolver returned null stream")
            }

            HtmlContentUriCheck(
                uri = uri,
                mimeType = mimeType,
                canOpenStream = streamResult.isSuccess,
                errorMessage = streamResult.exceptionOrNull()?.let { throwable ->
                    "${throwable::class.java.simpleName}: ${throwable.message.orEmpty()}"
                },
            )
        }
        .toList()
}

private fun unescapeHtmlAttribute(value: String): String {
    return value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
}

private fun readClipboardSnapshot(context: Context): ClipboardSnapshot {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = clipboard.primaryClip
    val description = clipboard.primaryClipDescription
    val mimeTypes = description?.let { desc ->
        (0 until desc.mimeTypeCount).map { desc.getMimeType(it) }
    }.orEmpty()

    return ClipboardSnapshot(
        hasPrimaryClip = clipboard.hasPrimaryClip(),
        clipData = clipData,
        description = description,
        mimeTypes = mimeTypes,
        timestampText = description?.timestamp?.let(::formatTimestamp),
        items = clipData?.let { data ->
            (0 until data.itemCount).map { index ->
                val item = data.getItemAt(index)
                val uri = item.uri
                val resolvedMimeType = uri?.let { context.contentResolver.getType(it) }
                val canPreviewImage = mimeTypes.any { it.startsWith("image/") } ||
                    resolvedMimeType?.startsWith("image/") == true
                ClipboardItemSnapshot(
                    text = item.text?.toString(),
                    htmlText = item.htmlText,
                    uri = uri,
                    uriMetadata = uri?.let { readUriMetadata(context, it, resolvedMimeType) },
                    canPreviewImage = canPreviewImage,
                    intentSummary = item.intent?.toUri(0),
                )
            }
        }.orEmpty(),
    )
}

private fun readUriMetadata(
    context: Context,
    uri: Uri,
    resolvedMimeType: String?,
): UriMetadata {
    var displayName: String? = null
    var sizeBytes: Long? = null

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
        }
    }

    val imageSize = runCatching {
        decodeBitmapBounds(context, uri)?.let { (width, height) -> "${width}x$height" }
    }.getOrNull()

    return UriMetadata(
        displayName = displayName,
        sizeBytes = sizeBytes,
        mimeType = resolvedMimeType,
        imageSize = imageSize,
    )
}

private fun analyzeText(text: String): TextAnalysis {
    val trimmed = text.trim()
    val urls = Regex("""https?://[^\s<>"']+""").findAll(text).map { it.value }.take(8).toList()
    val emails = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
        .findAll(text)
        .map { it.value }
        .take(8)
        .toList()
    val base64Candidate = trimmed.length >= 16 &&
        trimmed.length % 4 == 0 &&
        trimmed.matches(Regex("""[A-Za-z0-9+/]+={0,2}"""))

    return TextAnalysis(
        charCount = text.length,
        lineCount = if (text.isEmpty()) 0 else text.lines().size,
        wordCount = Regex("""\S+""").findAll(text).count(),
        urls = urls,
        emails = emails,
        looksLikeJson = (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]")),
        looksLikeBase64 = base64Candidate,
    )
}

private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index += 1
    }
    return if (index == 0) {
        "${bytes} ${units[index]}"
    } else {
        "%.1f %s".format(value, units[index])
    }
}
