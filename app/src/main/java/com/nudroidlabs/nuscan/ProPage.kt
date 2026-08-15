package com.nudroidlabs.nuscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nudroidlabs.nuscan.monetization.ProBillingController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPage(
    modifier: Modifier,
    billing: ProBillingController,
    onBack: () -> Unit,
    onBuy: () -> String?
) {
    var message by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("NuScan Pro") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(42.dp))
                        Text(
                            if (billing.isPro) "NuScan Pro is active" else "Unlock the complete toolkit",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (billing.isPro) "Thank you for supporting NuScan." else "One lifetime purchase. No subscription."
                        )
                    }
                }
            }
            item {
                listOf(
                    "Remove ads",
                    "Compress PDF",
                    "OCR for images and PDFs",
                    "Sign PDF",
                    "Protect PDF"
                ).forEach { benefit ->
                    ListItem(
                        headlineContent = { Text(benefit) },
                        leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                    )
                }
            }
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(billing.statusText, modifier = Modifier.padding(14.dp))
                }
            }
            if (!billing.isPro) {
                item {
                    Button(
                        onClick = { message = onBuy() },
                        enabled = billing.purchaseReady,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(billing.priceText?.let { "Get Pro · $it" } ?: "Get NuScan Pro")
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        billing.refreshPurchases()
                        message = "Purchase status refreshed from Google Play."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore purchases")
                }
            }
            message?.let { info ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(info, modifier = Modifier.padding(14.dp))
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                item {
                    Text(
                        "Debug builds keep premium tools unlocked for testing. Release builds use Google Play entitlement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
