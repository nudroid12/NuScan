package com.nudroidlabs.nuscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class IntroStep(
    val title: String,
    val body: String,
    val icon: ImageVector
)

@Composable
fun OnboardingPage(onFinish: () -> Unit) {
    val steps = remember {
        listOf(
            IntroStep(
                "Scan and build PDFs",
                "Capture paper, import images, merge, split, compress and export documents from one app.",
                Icons.Default.DocumentScanner
            ),
            IntroStep(
                "Privacy first",
                "NuScan keeps document processing on your device wherever the selected tool allows it.",
                Icons.Default.Lock
            ),
            IntroStep(
                "Fast offline tools",
                "Most PDF, OCR and document scanning work does not need an account. QR scanning uses Google Play services.",
                Icons.Default.OfflineBolt
            )
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val current = steps[index]

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("NuScan", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Your pocket document toolkit", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Icon(current.icon, contentDescription = null, modifier = Modifier.size(64.dp))
                Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(current.body, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    steps.indices.forEach { step ->
                        Text(if (step == index) "●" else "○", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (index > 0) {
                FilledTonalButton(onClick = { index-- }, modifier = Modifier.weight(1f)) { Text("Back") }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Button(
                onClick = {
                    if (index < steps.lastIndex) index++ else onFinish()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (index == steps.lastIndex) "Start NuScan" else "Next")
            }
        }
    }
}
