package com.psiphon3.ui.azadi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.MainActivityViewModel
import com.psiphon3.R
import com.psiphon3.azadi.AzadiEventLogger
import com.psiphon3.log.LogEntry
import com.psiphon3.log.LoggingContentProvider
import com.psiphon3.log.MyLog
import com.psiphon3.ui.LogItem
import com.psiphon3.ui.theme.AppColors
import androidx.compose.runtime.rxjava2.subscribeAsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzadiLogsScreen(
    viewModel: MainActivityViewModel,
    onBack: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logsState = viewModel.logsPagedListFlowable().subscribeAsState(initial = emptyList<LogEntry>())
    val statusLogs = logsState.value ?: emptyList<LogEntry>()
    val eventLogs by AzadiEventLogger.lines.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(statusLogs.size, eventLogs.size) {
        val total = statusLogs.size + eventLogs.size
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.logs_tab_name),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dashboard_back),
                            tint = Color.White
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = {
                    copyLogs(context, statusLogs, eventLogs)
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.azadi_copy_logs), tint = Color.White)
                }
                if (onExport != null) {
                    TextButton(onClick = onExport) {
                        Text(stringResource(R.string.azadi_export_debug), color = AppColors.IranGreenBright)
                    }
                }
                IconButton(onClick = {
                    val uri = LoggingContentProvider.CONTENT_URI.buildUpon()
                        .appendPath("status")
                        .appendPath("delete")
                        .build()
                    context.contentResolver.delete(uri, null, null)
                    scope.launch { AzadiEventLogger.clear() }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_logs), tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (eventLogs.isNotEmpty()) {
                item {
                    Text(
                        "Events",
                        color = AppColors.IranGreenBright,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                items(eventLogs) { line ->
                    Text(
                        text = line,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }
            }
            if (statusLogs.isNotEmpty()) {
                item {
                    Text(
                        "Tunnel",
                        color = AppColors.SubtitleText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(statusLogs) { entry -> LogItem(entry) }
            }
        }
    }
}

private fun copyLogs(context: Context, statusLogs: List<LogEntry>, eventLogs: List<String>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = buildString {
        appendLine("=== AzadiTunnel Events ===")
        eventLogs.forEach { appendLine(it) }
        appendLine("=== Tunnel Status ===")
        statusLogs.forEach { entry ->
            appendLine(MyLog.getStatusLogMessageForDisplay(entry.logJson, context))
        }
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("AzadiTunnel logs", text))
}
