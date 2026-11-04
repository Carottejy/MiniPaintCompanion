package com.carottejy.minipaintcompanion.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Prise de photo avec l'appareil photo du téléphone.
 *
 * L'appli déclare la permission CAMERA (pour le scanner de codes-barres) : dans ce cas Android
 * refuse d'ouvrir l'appareil photo (SecurityException) tant que la permission n'est pas accordée.
 * On la demande donc d'abord, et toute erreur est rattrapée au lieu de faire planter l'appli.
 *
 * Retourne une fonction à appeler avec l'URI (FileProvider) où écrire la photo.
 */
@Composable
fun rememberTakePicture(onResult: (Boolean) -> Unit): (Uri) -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> callback(ok) }

    fun safeLaunch(uri: Uri) {
        try {
            take.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Aucune application appareil photo trouvée", Toast.LENGTH_SHORT).show()
            callback(false)
        } catch (e: SecurityException) {
            Toast.makeText(context, "L'appareil photo a été refusé par Android", Toast.LENGTH_SHORT).show()
            callback(false)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val uri = pendingUri
        pendingUri = null
        if (granted && uri != null) {
            safeLaunch(uri)
        } else {
            Toast.makeText(context, "Autorise l'appareil photo pour prendre une photo", Toast.LENGTH_LONG).show()
            callback(false)
        }
    }

    return { uri ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            safeLaunch(uri)
        } else {
            pendingUri = uri
            permission.launch(Manifest.permission.CAMERA)
        }
    }
}
