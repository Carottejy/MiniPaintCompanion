package com.carottejy.minipaintcompanion.ui

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Paint
import com.carottejy.minipaintcompanion.data.Repository
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Ouvre le scanner de codes-barres de Google Play services (EAN/UPC des pots).
 * Aucune permission caméra n'est nécessaire : c'est Play services qui affiche la caméra.
 */
fun scanBarcode(context: Context, onCode: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            val raw = barcode.rawValue?.filter { it.isDigit() }
            if (raw.isNullOrEmpty()) onError("Code illisible") else onCode(raw)
        }
        .addOnFailureListener { e ->
            onError("Scanner indisponible : ${e.message ?: "Google Play services requis"}")
        }
}

/** Code inconnu : l'utilisateur choisit le pot correspondant, le code est mémorisé. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownBarcodeSheet(
    code: String,
    data: AppData,
    repo: Repository,
    onPicked: (Paint) -> Unit,
    onDismiss: () -> Unit,
) {
    val all = remember(data.customPaints) { repo.allPaints(data) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text("Code-barres inconnu", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            "Code $code. Choisis le pot correspondant : je retiendrai ce code et le prochain scan l'ajoutera directement.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        PaintBrowser(
            paints = all,
            ownedIds = data.ownedPaintIds,
            isChecked = { false },
            onToggle = { _, _ -> },
            onRowClick = onPicked,
            rowTrailing = { },
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
