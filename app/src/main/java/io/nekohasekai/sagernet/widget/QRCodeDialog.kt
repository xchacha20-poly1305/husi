package io.nekohasekai.sagernet.widget

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ui.MainActivity
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap
import io.nekohasekai.sagernet.ktx.closeQuietly
import io.nekohasekai.sagernet.ktx.hasPermission
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class QRCodeDialog() : DialogFragment() {

    companion object {
        private const val KEY_URL = "io.nekohasekai.sagernet.QRCodeDialog.KEY_URL"
        private const val KEY_NAME = "io.nekohasekai.sagernet.QRCodeDialog.KEY_NAME"
    }

    constructor(url: String, displayName: String) : this() {
        arguments = bundleOf(
            Pair(KEY_URL, url), Pair(KEY_NAME, displayName)
        )
    }

    private lateinit var bitmap: Bitmap

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            saveQrCode()
        } else {
            (activity as? MainActivity)?.snackbar(R.string.permission_denied)?.show()
        }
    }

    /**
     * Based on:
     *
     * https://android.googlesource.com/platform/packages/apps/Settings/+/0d706f0/src/com/android/settings/wifi/qrcode/QrCodeGenerator.java
     *
     * https://android.googlesource.com/platform/packages/apps/Settings/+/8a9ccfd/src/com/android/settings/wifi/dpp/WifiDppQrCodeGeneratorFragment.java#153
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = try {
        // get display size
        var pixelMin = 0

        try {
            val displayMetrics: DisplayMetrics = requireContext().resources.displayMetrics
            val height: Int = displayMetrics.heightPixels
            val width: Int = displayMetrics.widthPixels
            pixelMin = if (height > width) width else height
            pixelMin = (pixelMin * 0.8).roundToInt()
        } catch (_: Exception) {
        }

        val size = if (pixelMin > 0) {
            pixelMin
        } else {
            resources.getDimensionPixelSize(R.dimen.qrcode_size)
        }

        // draw QR Code
        val url = arguments?.getString(KEY_URL)!!
        val displayName = arguments?.getString(KEY_NAME)!!

        val hints = mutableMapOf<EncodeHintType, Any>()
        val iso88591 = StandardCharsets.ISO_8859_1.newEncoder()
        if (!iso88591.canEncode(url)) hints[EncodeHintType.CHARACTER_SET] =
            StandardCharsets.UTF_8.name()
        val qrBits = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, size, size, hints)
        LinearLayout(context).apply {
            // Layout
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            // QR Code Image View
            addView(ImageView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                bitmap = createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                    for (x in 0 until size) for (y in 0 until size) {
                        setPixel(x, y, if (qrBits.get(x, y)) Color.BLACK else Color.WHITE)
                    }
                }
                setImageBitmap(bitmap)
                isLongClickable = true
                val context = requireContext()
                setOnLongClickListener {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.save)
                        .setMessage(R.string.save_to_system)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                                context.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            ) {
                                saveQrCode()
                            } else {
                                requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
            })

            // Text View
            addView(TextView(context).apply {
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = displayName
            })
        }
    } catch (e: WriterException) {
        Logs.w(e)
        (activity as MainActivity).snackbar(e.readableMessage).show()
        dismiss()
        null
    }

    private fun saveQrCode() {
        val displayName = arguments?.getString(KEY_NAME)!!
        val filename = "$displayName.png"

        var outputStream: java.io.OutputStream? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = requireContext().contentResolver
                val contentUri =
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (contentUri != null) {
                    outputStream = resolver.openOutputStream(contentUri)
                } else {
                    (activity as? MainActivity)?.snackbar(R.string.error_title)?.show()
                    return
                }

            } else {
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val imageFile = File(downloadsDir, filename)
                outputStream = FileOutputStream(imageFile)
            }

            outputStream?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            (activity as? MainActivity)?.snackbar(R.string.saved_to_download)?.show()
        } catch (e: IOException) {
            val message = e.readableMessage
            Logs.e(message)
            (activity as? MainActivity)?.snackbar(message)?.show()
        } finally {
            outputStream?.closeQuietly()
        }
    }
}