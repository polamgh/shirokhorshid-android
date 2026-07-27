package com.psiphon3.ui.azadi

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.R
import com.psiphon3.azadi.SupportStoreManager
import com.psiphon3.ui.GlassCard
import com.psiphon3.ui.theme.AppColors
import com.android.billingclient.api.ProductDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzadiSupportScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { SupportStoreManager.getInstance(context) }
    val products by store.products.collectAsState()
    val isLoading by store.isLoading.collectAsState()
    val purchaseState by store.purchaseState.collectAsState()
    val uiState by store.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        store.startConnection()
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SupportStoreManager.BillingUiState.Success -> {
                snackbarHostState.showSnackbar("Purchase successful! Thank you for your support.")
            }
            is SupportStoreManager.BillingUiState.Error -> {
                if (!state.isUserCanceled) {
                    snackbarHostState.showSnackbar("Error: ${state.message}")
                }
            }
            is SupportStoreManager.BillingUiState.Pending -> {
                snackbarHostState.showSnackbar(state.message)
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_support)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Background,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = AppColors.Background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                HeroHeader(purchaseState)

                if (isLoading) {
                    LoadingCard()
                } else if (products.isEmpty()) {
                    EmptyProductsCard { store.startConnection() }
                    LegalFooter()
                    RestoreFooter {
                        store.queryPurchases()
                    }
                } else {
                    TipsSection(products) { product ->
                        (context as? Activity)?.let { store.purchase(it, product) }
                    }
                    SubscriptionsSection(products) { product ->
                        (context as? Activity)?.let { store.purchase(it, product) }
                    }
                    LegalFooter()
                    RestoreFooter {
                        store.queryPurchases()
                    }
                }
            }

            if (uiState is SupportStoreManager.BillingUiState.Loading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.IranGreenBright)
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(purchaseState: SupportStoreManager.PurchaseState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AppColors.IranGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = AppColors.IranGreenBright,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.support_app_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.support_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.SubtitleText
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = AppColors.IranGreen.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.IranGreen.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AppColors.IranGreenBright,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.support_free_badge),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (purchaseState != SupportStoreManager.PurchaseState.NOT_PURCHASED) {
            StatusPill(purchaseState)
        }
    }
}

@Composable
private fun StatusPill(state: SupportStoreManager.PurchaseState) {
    val tint = if (state == SupportStoreManager.PurchaseState.EXPIRED) AppColors.IranRed else AppColors.IranGreenBright
    val icon = if (state == SupportStoreManager.PurchaseState.EXPIRED) Icons.Default.History else Icons.Default.Favorite

    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(
                text = when (state) {
                    SupportStoreManager.PurchaseState.PURCHASED -> stringResource(R.string.support_state_purchased)
                    SupportStoreManager.PurchaseState.SUBSCRIBED -> stringResource(R.string.support_state_subscribed)
                    SupportStoreManager.PurchaseState.EXPIRED -> stringResource(R.string.support_state_expired)
                    else -> ""
                },
                color = tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.support_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.SubtitleText
            )
        }
    }
}

@Composable
private fun TipsSection(
    products: List<ProductDetails>,
    onPurchase: (ProductDetails) -> Unit
) {
    val tipProducts = products.filter { it.productId.startsWith("tip") }
        .sortedBy { 
            // Sort by expected value: small, medium, large
            when (it.productId) {
                SupportStoreManager.PRODUCT_TIP_SMALL -> 1
                SupportStoreManager.PRODUCT_TIP_MEDIUM -> 2
                SupportStoreManager.PRODUCT_TIP_LARGE -> 3
                else -> 99
            }
        }

    if (tipProducts.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.support_tips_title), Icons.Default.CardGiftcard)

        GlassCard(elevated = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tipProducts.forEachIndexed { index, product ->
                    TipRow(product, SupportTipStyle.fromId(product.productId), onPurchase)
                    if (index < tipProducts.size - 1) {
                        HorizontalDivider(color = AppColors.CardStroke)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsSection(
    products: List<ProductDetails>,
    onPurchase: (ProductDetails) -> Unit
) {
    val subProducts = products.filter { it.productId.startsWith("support") }
        .sortedBy { it.productId == SupportStoreManager.PRODUCT_SUPPORT_YEARLY } // Monthly first

    if (subProducts.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.support_subscriptions_title), Icons.Default.AutoAwesome)

        subProducts.forEach { product ->
            SubscriptionRow(product, product.productId == SupportStoreManager.PRODUCT_SUPPORT_YEARLY, onPurchase)
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun TipRow(
    product: ProductDetails,
    style: SupportTipStyle,
    onPurchase: (ProductDetails) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onPurchase(product) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(style.gradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(style.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (product.description.isNotEmpty()) {
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.SubtitleText
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = style.priceColor.copy(alpha = 0.9f)
        ) {
            Text(
                text = product.oneTimePurchaseOfferDetails?.formattedPrice ?: "",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SubscriptionRow(
    product: ProductDetails,
    highlight: Boolean,
    onPurchase: (ProductDetails) -> Unit
) {
    // Select active offer returned by Google Play without requiring specific basePlanId
    val offer = product.subscriptionOfferDetails?.firstOrNull()
    val price = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
        ?: offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        ?: ""

    GlassCard(
        elevated = highlight,
        modifier = Modifier.clickable { onPurchase(product) }
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            if (highlight) listOf(Color(0xFFFFD700), Color(0xFFFFA500))
                            else listOf(AppColors.IranGreen.copy(alpha = 0.85f), AppColors.IranGreenBright)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (highlight) Icons.Default.Stars else Icons.Default.Loop,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (product.description.isNotEmpty()) {
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.SubtitleText
                    )
                }
                Text(
                    text = price,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AppColors.SubtitleText,
                modifier = Modifier.padding(top = 4.dp).size(20.dp)
            )
        }
    }
}

@Composable
private fun LegalFooter() {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.support_subscription_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.SubtitleText
            )
        }
    }
}

@Composable
private fun RestoreFooter(onRestore: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onRestore) {
            Text(
                text = stringResource(R.string.support_restore),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = AppColors.NavBlue
            )
        }
    }
}

private enum class SupportTipStyle(val icon: ImageVector, val gradient: List<Color>, val priceColor: Color) {
    SMALL(Icons.Default.Coffee, listOf(Color(0xFF5AB8F2), Color(0xFF3884E0)), Color(0xFF3884E0)),
    MEDIUM(Icons.Default.Favorite, listOf(Color(0xFF9E6BF2), Color(0xFF7347D1)), Color(0xFF7347D1)),
    LARGE(Icons.Default.Star, listOf(Color(0xFFF99E38), Color(0xFFEB612E)), Color(0xFFEB612E));

    companion object {
        fun fromId(id: String) = when {
            id.contains("small") -> SMALL
            id.contains("medium") -> MEDIUM
            else -> LARGE
        }
    }
}

@Composable
private fun EmptyProductsCard(onRetry: () -> Unit) {
    GlassCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AppColors.SubtitleText,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "No products retrieved from Google Play",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "Please verify your Play Store tester account and connection.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.SubtitleText
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onRetry) {
                Text("Retry Fetching Products", color = AppColors.NavBlue)
            }
        }
    }
}
