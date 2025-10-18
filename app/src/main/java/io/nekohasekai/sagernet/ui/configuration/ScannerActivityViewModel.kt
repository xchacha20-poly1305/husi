package io.nekohasekai.sagernet.ui.configuration

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.StringOrRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
internal data class ScannerUiState(
    val isFlashlightOn: Boolean = false,
    val hasFlashUnit: Boolean = false,
)

@Stable
internal sealed interface ScannerUiEvent {
    class ImportSubscription(val uri: Uri) : ScannerUiEvent
    class Snakebar(val message: StringOrRes) : ScannerUiEvent
    object Finish : ScannerUiEvent
}

@Stable
internal class ScannerActivityViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ScannerUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    @Volatile
    private var isProcessing = false

    suspend fun importFromUri(uri: Uri, contentResolver: ContentResolver) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(contentResolver, uri)
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
                    mapOf(DecodeHintType.TRY_HARDER to true)
                )
            } catch (_: NotFoundException) {
                qrReader.decode(
                    BinaryBitmap(GlobalHistogramBinarizer(source.invert())),
                    mapOf(DecodeHintType.TRY_HARDER to true)
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

        runOnDefaultDispatcher {
            try {
                val results = RawUpdater.parseRaw(value)
                if (results.isNullOrEmpty()) {
                    isProcessing = false
                    onFailure(null)
                } else {
                    val currentGroupId = DataStore.selectedGroupForImport()
                    if (DataStore.selectedGroup != currentGroupId) {
                        DataStore.selectedGroup = currentGroupId
                    }

                    for (profile in results) {
                        ProfileManager.createProfile(currentGroupId, profile)
                    }

                    _uiEvent.emit(ScannerUiEvent.Finish)
                }
            } catch (e: SubscriptionFoundException) {
                _uiEvent.emit(ScannerUiEvent.ImportSubscription(e.link.toUri()))
                _uiEvent.emit(ScannerUiEvent.Finish)
            } catch (e: Exception) {
                isProcessing = false
                onFailure(e)
            }
        }
    }

    fun onFailure(e: Exception?) {
        viewModelScope.launch {
            if (e != null) {
                Logs.w(e)
                _uiEvent.emit(ScannerUiEvent.Snakebar(StringOrRes.Direct(e.readableMessage)))
            } else {
                _uiEvent.emit(ScannerUiEvent.Snakebar(StringOrRes.Res(R.string.action_import_err)))
            }
        }
    }

    fun toggleFlashlight() {
        _uiState.update {
            it.copy(
                isFlashlightOn = !it.isFlashlightOn
            )
        }
    }


    fun setHasFlashUnit(hasFlash: Boolean) {
        _uiState.update {
            it.copy(
                hasFlashUnit = hasFlash,
                isFlashlightOn = if (!hasFlash) false else it.isFlashlightOn,
            )
        }
    }
}
