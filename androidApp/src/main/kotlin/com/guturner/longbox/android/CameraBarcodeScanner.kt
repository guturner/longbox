package com.guturner.longbox.android

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer
import com.guturner.longbox.ui.ScannerContent
import java.util.concurrent.Executors

/**
 * CameraX preview + ZXing. ZXing is used instead of ML Kit because comic barcodes carry the issue,
 * cover and printing in the EAN-5 add-on, which ZXing returns as UPC_EAN_EXTENSION metadata and
 * ML Kit's supported-format list does not include.
 */
object CameraBarcodeScanner : ScannerContent {

    @Composable
    override fun Content(onDetected: (mainCode: String, addOn: String?) -> Boolean, onClose: () -> Unit) {
        val context = LocalContext.current
        var granted by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
        LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

        if (!granted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera to scan barcodes") }
            }
            return
        }

        val haptics = LocalHapticFeedback.current
        val latestOnDetected by rememberUpdatedState(onDetected)
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewView = remember { PreviewView(context) }
        val analyzer = remember {
            ComicBarcodeAnalyzer { main, addOn ->
                // Analyzer runs on a background executor; hop to main for UI callbacks.
                ContextCompat.getMainExecutor(context).execute {
                    if (latestOnDetected(main, addOn)) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }

        DisposableEffect(lifecycleOwner) {
            val executor = Executors.newSingleThreadExecutor()
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                runCatching { providerFuture.get().unbindAll() }
                executor.shutdown()
            }
        }

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Decodes UPC-A/EAN-13 plus the 5-digit add-on. If only the main symbol is read, waits briefly for
 * a frame that includes the add-on (it is small and often clips), then accepts the main code alone
 * so pre-1990s books without a supplement can still be scanned.
 */
private class ComicBarcodeAnalyzer(
    private val onCode: (main: String, addOn: String?) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.UPC_A, BarcodeFormat.EAN_13),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }
    private var mainOnlySince: Pair<String, Long>? = null

    override fun analyze(image: ImageProxy) {
        image.use {
            val (bytes, width, height) = lumaUpright(image)
            val source = PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false)
            val result = try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            } catch (_: NotFoundException) {
                return
            } finally {
                reader.reset()
            }

            val addOn = result.resultMetadata?.get(ResultMetadataType.UPC_EAN_EXTENSION) as? String
            val now = System.currentTimeMillis()
            when {
                addOn?.length == 5 -> { mainOnlySince = null; onCode(result.text, addOn) }
                mainOnlySince?.first != result.text -> mainOnlySince = result.text to now
                now - mainOnlySince!!.second > MAIN_ONLY_GRACE_MS -> { mainOnlySince = null; onCode(result.text, null) }
            }
        }
    }

    /** Y plane, rotated so barcodes held level in portrait are horizontal for the 1D reader. 1D symbols decode
     *  in either direction, so one rotation direction serves both 90° and 270°. */
    private fun lumaUpright(image: ImageProxy): Triple<ByteArray, Int, Int> {
        val plane = image.planes[0]
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val buffer = plane.buffer.also { it.rewind() }
        val luma = ByteArray(width * height)
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(luma, row * width, width)
        }
        return when (image.imageInfo.rotationDegrees) {
            90, 270 -> {
                val rotated = ByteArray(luma.size)
                for (y in 0 until height) for (x in 0 until width) rotated[x * height + (height - 1 - y)] = luma[y * width + x]
                Triple(rotated, height, width)
            }
            else -> Triple(luma, width, height)
        }
    }

    private companion object {
        const val MAIN_ONLY_GRACE_MS = 1_500L
    }
}
