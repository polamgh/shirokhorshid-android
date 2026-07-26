package com.psiphon3.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.psiphon3.log.LogEntry
import com.psiphon3.log.LoggingContentProvider
import com.psiphon3.log.MyLog
import com.psiphon3.ui.theme.AppColors
import androidx.compose.runtime.rxjava2.subscribeAsState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: MainActivityViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val logsState = viewModel.logsPagedListFlowable().subscribeAsState(initial = emptyList<LogEntry>())
    val logs = logsState.value ?: emptyList<LogEntry>()
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
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
                    val uri = LoggingContentProvider.CONTENT_URI.buildUpon()
                        .appendPath("status")
                        .appendPath("delete")
                        .build()
                    context.contentResolver.delete(uri, null, null)
                }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.clear_logs),
                        tint = Color.White
                    )
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
            items(logs) { entry ->
                LogItem(entry)
            }
        }
    }
}

@Composable
fun LogItem(entry: LogEntry) {
    val context = LocalContext.current
    val message = remember(entry.logJson) {
        MyLog.getStatusLogMessageForDisplay(entry.logJson, context)
    }
    val timeStr = remember(entry.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = timeStr,
            color = AppColors.SubtitleText,
            fontSize = 11.sp
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(top = 8.dp))
    }
}
