package com.example.kanak.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX preview with the bundled ML Kit barcode model; no dynamic module download. */
@Composable
fun CameraBarcodeScannerDialog(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = KanakSurface) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Scan barcode", style = MaterialTheme.typography.titleLarge,
                    color = KanakPrimaryText)
                Text("Point the camera at a product barcode.",
                    style = MaterialTheme.typography.bodySmall, color = KanakSecondaryText)
                if (hasPermission) {
                    BarcodeCameraPreview(onScanned = onScanned, modifier = Modifier
                        .fillMaxWidth().height(300.dp).testTag("barcode_camera_preview"))
                } else {
                    Text("Camera access is required for scanning. You can enter the barcode manually.",
                        color = KanakSecondaryText)
                    OutlinedButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Allow camera")
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Enter manually")
                }
            }
        }
    }
}

@Composable
private fun BarcodeCameraPreview(onScanned: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestCallback by rememberUpdatedState(onScanned)
    val previewView = remember(context) { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    val delivered = remember { AtomicBoolean(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(previewView, owner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val scanner = BarcodeScanning.getClient()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
            val mediaImage = proxy.image
            if (mediaImage == null || delivered.get()) {
                proxy.close()
            } else {
                try {
                    scanner.process(InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes.firstNotNullOfOrNull {
                                it.rawValue?.trim()?.takeIf(String::isNotEmpty)
                            }
                            if (value != null && delivered.compareAndSet(false, true)) {
                                latestCallback(value)
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                } catch (e: Exception) {
                    proxy.close()
                    cameraError = e.message ?: "Unable to analyze the camera image"
                }
            }
        }
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (!disposed) {
                try {
                    provider = providerFuture.get()
                    provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    cameraError = e.message ?: "Camera unavailable"
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            scanner.close()
        }
    }
    Column {
        AndroidView(factory = { previewView }, modifier = modifier)
        if (cameraError != null) Text(cameraError.orEmpty(), color = MaterialTheme.colorScheme.error)
    }
}
