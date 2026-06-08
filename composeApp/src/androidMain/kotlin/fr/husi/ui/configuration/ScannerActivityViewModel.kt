package fr.husi.ui.configuration

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader
import fr.husi.group.RawUpdater
import fr.husi.ktx.SubscriptionFoundException
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.resources.*
import fr.husi.ui.ImportLinkInteractor
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class ScannerUiState(
    val isFlashlightOn: Boolean = false,
    val hasFlashUnit: Boolean = false,
)

@Immutable
internal sealed interface ScannerUiEvent {
    class ImportSubscription(val uri: Uri) : ScannerUiEvent
    class Snakebar(val message: StringOrRes) : ScannerUiEvent
    class AskSubscriptionOrProfile(val url: String) : ScannerUiEvent
    object Finish : ScannerUiEvent
}

@Stable
internal class ScannerActivityViewModel(
    private val importLinkInteractor: ImportLinkInteractor = ImportLinkInteractor(),
) : ViewModel() {

    val uiState: StateFlow<ScannerUiState>
        field = MutableStateFlow(ScannerUiState())

    val uiEvent: SharedFlow<ScannerUiEvent>
        field = MutableSharedFlow<ScannerUiEvent>()

    @Volatile
    private var isProcessing = false

    suspend fun importFromUri(uri: Uri, contentResolver: ContentResolver) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(contentResolver, uri),
            ) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
        val intArray = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(
            intArray,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height,
        )

        val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
        val qrReader = QRCodeReader()
        try {
            val result = try {
                qrReader.decode(
                    BinaryBitmap(GlobalHistogramBinarizer(source)),
                    mapOf(DecodeHintType.TRY_HARDER to true),
                )
            } catch (_: NotFoundException) {
                qrReader.decode(
                    BinaryBitmap(GlobalHistogramBinarizer(source.invert())),
                    mapOf(DecodeHintType.TRY_HARDER to true),
                )
            }

            if (!result.text.isNullOrEmpty()) {
                onSuccess(result.text)
            } else {
                onFailure(null)
            }
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    fun onSuccess(value: String) {
        if (isProcessing) return
        isProcessing = true

        val uri = try {
            value.toUri()
        } catch (_: Exception) {
            null
        }

        when (uri?.scheme) {
            "http", "https" -> viewModelScope.launch {
                uiEvent.emit(ScannerUiEvent.AskSubscriptionOrProfile(value))
            }

            "sing-box" -> importSubscription(value)
            "husi" -> importSubscription(value)
            else -> parseAndImportProfile(value)
        }
    }

    fun parseAndImportProfile(text: String) = runOnDefaultDispatcher {
        try {
            val results = RawUpdater.parseRaw(text)
            if (results.isNullOrEmpty()) {
                isProcessing = false
                onFailure(null)
            } else {
                uiEvent.emit(ScannerUiEvent.Finish)
                onIoDispatcher {
                    importLinkInteractor.importProfiles(results)
                }
            }
        } catch (e: SubscriptionFoundException) {
            uiEvent.emit(ScannerUiEvent.ImportSubscription(e.link.toUri()))
            uiEvent.emit(ScannerUiEvent.Finish)
        } catch (e: Exception) {
            isProcessing = false
            onFailure(e)
        }
    }

    fun importSubscription(url: String) = runOnDefaultDispatcher {
        try {
            val group = importLinkInteractor.parseSubscription(url)
            if (group == null) {
                isProcessing = false
                viewModelScope.launch {
                    uiEvent.emit(ScannerUiEvent.Snakebar(StringOrRes.Res(Res.string.action_import_err)))
                }
                return@runOnDefaultDispatcher
            }

            uiEvent.emit(ScannerUiEvent.Finish)
            onIoDispatcher {
                importLinkInteractor.importSubscription(group)
            }
        } catch (e: Exception) {
            isProcessing = false
            onFailure(e)
        }
    }

    fun resetProcessing() {
        isProcessing = false
    }

    fun onFailure(e: Exception?) {
        // Ignore it because they are too much and common.
        /*viewModelScope.launch {
            if (e != null) {
                Logs.w(e)
                uiEvent.emit(ScannerUiEvent.Snakebar(StringOrRes.Direct(e.readableMessage)))
            } else {
                uiEvent.emit(ScannerUiEvent.Snakebar(StringOrRes.Res(Res.string.action_import_err)))
            }
        }*/
    }

    fun toggleFlashlight() {
        uiState.update {
            it.copy(
                isFlashlightOn = !it.isFlashlightOn,
            )
        }
    }


    fun setHasFlashUnit(hasFlash: Boolean) {
        uiState.update {
            it.copy(
                hasFlashUnit = hasFlash,
                isFlashlightOn = if (!hasFlash) false else it.isFlashlightOn,
            )
        }
    }
}
