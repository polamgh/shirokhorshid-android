package com.psiphon3.ui.redesign

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.R
import com.psiphon3.azadi.PsiphonRegionList
import com.psiphon3.psiphonlibrary.PsiphonConstants
import com.psiphon3.ui.ALL_REGIONS
import com.psiphon3.ui.RegionItem
import com.psiphon3.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

// region Model

/** Continent buckets shown as sections, in display order. */
private enum class RegionContinent(@StringRes val titleRes: Int) {
    EUROPE(R.string.region2_group_europe),
    AMERICAS(R.string.region2_group_americas),
    ASIA_PACIFIC(R.string.region2_group_asia_pacific),
    MIDEAST_AFRICA(R.string.region2_group_mideast_africa),
    OTHER(R.string.region2_group_other)
}

private val CONTINENT_BY_CODE: Map<String, RegionContinent> = buildMap {
    listOf(
        "AD", "AL", "AT", "BA", "BE", "BG", "BY", "CH", "CY", "CZ", "DE", "DK", "EE", "ES", "FI",
        "FR", "GB", "GR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MD", "ME",
        "MK", "MT", "NL", "NO", "PL", "PT", "RO", "RS", "SE", "SI", "SK", "UA"
    ).forEach { put(it, RegionContinent.EUROPE) }
    listOf(
        "AR", "BO", "BR", "CA", "CL", "CO", "CR", "EC", "GT", "MX", "PA", "PE", "PR", "PY", "US",
        "UY", "VE"
    ).forEach { put(it, RegionContinent.AMERICAS) }
    listOf(
        "AU", "BD", "CN", "HK", "ID", "IN", "JP", "KH", "KR", "KZ", "LK", "MY", "NZ", "PH", "PK",
        "SG", "TH", "TW", "VN"
    ).forEach { put(it, RegionContinent.ASIA_PACIFIC) }
    listOf(
        "AE", "BH", "EG", "GH", "IL", "IQ", "IR", "JO", "KE", "KW", "LB", "MA", "NG", "OM", "QA",
        "SA", "TN", "TR", "ZA"
    ).forEach { put(it, RegionContinent.MIDEAST_AFRICA) }
}

private fun continentOf(code: String): RegionContinent =
    CONTINENT_BY_CODE[code.uppercase(Locale.ROOT)] ?: RegionContinent.OTHER

private class RegionEntry(
    val item: RegionItem,
    val name: String,
    val englishName: String
)

private class RegionSection(
    val continent: RegionContinent,
    val rows: List<List<RegionEntry>>
)

/** Case-insensitive, and treats Arabic yeh/kaf as their Persian forms. */
private fun normalizeForSearch(text: String): String =
    text.trim()
        .lowercase(Locale.ROOT)
        .replace('ي', 'ی')
        .replace('ك', 'ک')

// endregion

// region Palette

private val SheetTop = Color(0xFF0D121F)
private val SheetBottom = Color(0xFF080D17)
private val MarkIdleStroke = Color.White.copy(alpha = 0.25f)
private val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

// endregion

/**
 * Exit-country picker: searchable, grouped by continent, with "Any (best)" featured.
 * Port of the iOS `RegionPickerSheet`.
 *
 * Selecting a row calls [onRegionSelected] immediately, lets the check mark land, then hides
 * the sheet and calls [onDismiss]. [onDismiss] is also called for swipe-down, scrim tap, back
 * and the close button. It is called at most once per sheet instance.
 *
 * @param regionCodes codes to offer (e.g. `PsiphonRegionList.getAvailable(context)`); defaults to
 * [PsiphonRegionList.allSupported]. The "best" entry ([PsiphonConstants.REGION_CODE_ANY]) is
 * always shown as the hero row; codes with no entry in [ALL_REGIONS] are skipped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionPickerSheetV2(
    selectedRegionCode: String,
    onRegionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    regionCodes: List<String>? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    var query by rememberSaveable { mutableStateOf("") }
    var pendingCode by remember { mutableStateOf<String?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    val currentCode = pendingCode ?: selectedRegionCode

    val dismissOnce: () -> Unit = {
        if (!dismissed) {
            dismissed = true
            onDismiss()
        }
    }

    fun closeAnimated(delayMillis: Long) {
        scope.launch {
            if (delayMillis > 0) delay(delayMillis)
            sheetState.hide()
        }.invokeOnCompletion { dismissOnce() }
    }

    fun choose(code: String) {
        if (pendingCode != null || dismissed) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        pendingCode = code
        onRegionSelected(code)
        closeAnimated(delayMillis = 260)
    }

    // Localized + English names for every offered country (the "best" entry is the hero row).
    val entries: List<RegionEntry> = remember(regionCodes, configuration) {
        val resources = context.resources
        val englishResources = context.createConfigurationContext(
            android.content.res.Configuration(configuration).apply { setLocale(Locale.ENGLISH) }
        ).resources
        (regionCodes ?: PsiphonRegionList.allSupported)
            .asSequence()
            .filter { it != PsiphonConstants.REGION_CODE_ANY }
            .distinct()
            .mapNotNull { code -> ALL_REGIONS.firstOrNull { it.code.equals(code, ignoreCase = true) } }
            .map { item ->
                RegionEntry(
                    item = item,
                    name = resources.getString(item.nameResId),
                    englishName = englishResources.getString(item.nameResId)
                )
            }
            .toList()
    }

    val collator: Collator = remember(configuration) {
        Collator.getInstance(configuration.locales[0] ?: Locale.getDefault())
    }

    val sections: List<RegionSection> = remember(entries, query, collator) {
        val needle = normalizeForSearch(query)
        val visible = if (needle.isEmpty()) {
            entries
        } else {
            entries.filter { entry ->
                normalizeForSearch(entry.item.code).contains(needle) ||
                    normalizeForSearch(entry.name).contains(needle) ||
                    normalizeForSearch(entry.englishName).contains(needle)
            }
        }
        RegionContinent.values().mapNotNull { continent ->
            val inContinent = visible
                .filter { continentOf(it.item.code) == continent }
                .sortedWith(Comparator { a, b -> collator.compare(a.name, b.name) })
            if (inContinent.isEmpty()) null else RegionSection(continent, inContinent.chunked(2))
        }
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    ModalBottomSheet(
        onDismissRequest = dismissOnce,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = SheetBottom,
        contentColor = AppColors.PrimaryText,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = null
    ) {
        // The sheet lives in its own window; re-provide the caller's direction so RTL holds.
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f)
                    .background(Brush.verticalGradient(listOf(SheetTop, SheetBottom)))
                    .border(1.dp, AppColors.GlassStroke, SheetShape)
                    .imePadding()
            ) {
                SheetHeader(onClose = { closeAnimated(delayMillis = 0) })
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (query.isBlank()) {
                        item(key = "best") {
                            StaggeredAppear(index = 0, appeared = appeared) {
                                BestCard(
                                    selected = currentCode == PsiphonConstants.REGION_CODE_ANY,
                                    onClick = { choose(PsiphonConstants.REGION_CODE_ANY) }
                                )
                            }
                        }
                    }

                    if (sections.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.region2_no_results),
                                color = AppColors.SecondaryText,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    var staggerIndex = 1
                    sections.forEach { section ->
                        val headerIndex = staggerIndex
                        item(key = "header_${section.continent.name}") {
                            StaggeredAppear(index = headerIndex, appeared = appeared) {
                                SectionHeader(stringResource(section.continent.titleRes))
                            }
                        }
                        staggerIndex++
                        val firstRowIndex = staggerIndex
                        itemsIndexed(
                            items = section.rows,
                            key = { _, row -> "row_${section.continent.name}_${row.first().item.code}" }
                        ) { indexInSection, row ->
                            val rowIndex = firstRowIndex + indexInSection
                            StaggeredAppear(index = rowIndex, appeared = appeared) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    row.forEach { entry ->
                                        CountryCard(
                                            entry = entry,
                                            selected = currentCode.equals(entry.item.code, ignoreCase = true),
                                            onClick = { choose(entry.item.code) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (row.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        staggerIndex += section.rows.size
                    }
                }
            }
        }
    }
}

// region Pieces

@Composable
private fun SheetHeader(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(AppColors.SecondaryText.copy(alpha = 0.35f))
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.region2_title),
                    color = AppColors.PrimaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.region2_subtitle),
                    color = AppColors.SecondaryText,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppColors.PrimaryText.copy(alpha = 0.08f))
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.region2_close),
                    tint = AppColors.PrimaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(color = AppColors.PrimaryText, fontSize = 15.sp),
        cursorBrush = SolidColor(AppColors.IranGreenBright),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(AppColors.PrimaryText.copy(alpha = 0.06f))
                    .border(1.dp, AppColors.GlassStroke, shape)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = AppColors.SecondaryText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.region2_search),
                            color = AppColors.SecondaryText,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn() + scaleIn(initialScale = 0.6f),
                    exit = fadeOut() + scaleOut(targetScale = 0.6f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cancel,
                        contentDescription = stringResource(R.string.region2_clear_search),
                        tint = AppColors.SecondaryText,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onQueryChange("") }
                    )
                }
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = AppColors.SecondaryText,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 4.dp, end = 4.dp)
            .semantics { heading() }
    )
}

@Composable
private fun BestCard(selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    PressableSelectable(
        selected = selected,
        shape = shape,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        ambientColor = AppColors.IranGreenBright,
                        spotColor = AppColors.IranGreenBright
                    )
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(AppColors.IranGreenBright, AppColors.IranGreen))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.region2_best_title),
                    color = AppColors.PrimaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.region2_best_subtitle),
                    color = AppColors.SecondaryText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            SelectionMark(selected = selected)
        }
    }
}

@Composable
private fun CountryCard(
    entry: RegionEntry,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val codeColor by animateColorAsState(
        targetValue = if (selected) AppColors.IranGreenBright else AppColors.SecondaryText,
        label = "regionCodeColor"
    )
    PressableSelectable(
        selected = selected,
        shape = shape,
        onClick = onClick,
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = entry.item.flagResId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AppColors.PrimaryText.copy(alpha = 0.06f))
                        .border(1.dp, AppColors.GlassStroke, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    FitText(
                        text = entry.name,
                        color = AppColors.PrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entry.item.code,
                        color = codeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1
                    )
                }
            }
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + scaleIn(initialScale = 0.3f),
                exit = fadeOut() + scaleOut(targetScale = 0.3f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = AppColors.IranGreenBright,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Card chrome shared by the hero and country cards: selection tint, stroke, glow, press-sink. */
@Composable
private fun PressableSelectable(
    selected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "regionCardPress"
    )
    val fill by animateColorAsState(
        targetValue = if (selected) {
            AppColors.IranGreenBright.copy(alpha = 0.16f)
        } else {
            AppColors.PrimaryText.copy(alpha = 0.05f)
        },
        label = "regionCardFill"
    )
    val stroke by animateColorAsState(
        targetValue = if (selected) AppColors.IranGreenBright else AppColors.GlassStroke,
        label = "regionCardStroke"
    )
    val strokeWidth: Dp = if (selected) 1.6.dp else 1.dp
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // No elevation shadow: under a translucent fill it shows through as a dark box.
            .clip(shape)
            .background(fill)
            .border(strokeWidth, stroke, shape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        content()
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(
                width = 1.5.dp,
                color = if (selected) AppColors.IranGreenBright else MarkIdleStroke,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(initialScale = 0.3f),
            exit = fadeOut() + scaleOut(targetScale = 0.3f)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AppColors.IranGreenBright),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/** Items rise into place one after another when the sheet opens (first frame only). */
@Composable
private fun StaggeredAppear(
    index: Int,
    appeared: Boolean,
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(
            durationMillis = 420,
            delayMillis = index.coerceIn(0, 14) * 35,
            easing = FastOutSlowInEasing
        ),
        label = "regionAppear"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 14.dp.toPx()
        }
    ) {
        content()
    }
}

// endregion
