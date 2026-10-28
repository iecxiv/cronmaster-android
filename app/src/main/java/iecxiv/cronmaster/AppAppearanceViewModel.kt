package iecxiv.cronmaster

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class AppAppearanceViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val DEFAULT_NAME = "Cronmaster"
        private const val SHORTCUT_ID = "cronmaster_custom_launcher"
        private const val MAX_BYTES = 10L * 1024 * 1024
    }
    private val context = app.applicationContext
    private val prefs = app.getSharedPreferences("app_identity", 0)
    private val iconFile = AtomicFile(File(app.filesDir, "cronmaster-custom-icon.png"))
    private val shortcutMutex = Mutex()
    var displayName by mutableStateOf(prefs.getString("name", DEFAULT_NAME) ?: DEFAULT_NAME)
        private set
    var hideSettingsIcon by mutableStateOf(prefs.getBoolean("hide_settings", false))
        private set
    var iconBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var busy by mutableStateOf(true)
        private set
    var notice by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            try {
                iconBitmap = withContext(Dispatchers.IO) {
                    if (iconFile.baseFile.exists()) iconFile.openRead().use { BitmapFactory.decodeStream(it) } else null
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { notice = "No se pudo cargar el icono; se utiliza el original." }
            finally { busy = false }
        }
    }
    fun clearNotice() { notice = "" }
    fun saveName(value: String) {
        if (busy) return
        val name = value.replace('\n', ' ').replace('\r', ' ').trim().ifBlank { DEFAULT_NAME }
        if (name.length > 64) { notice = "El nombre no puede superar 64 caracteres."; return }
        displayName = name
        prefs.edit().putString("name", name).apply()
        notice = "Nombre guardado"
        updatePinnedShortcut()
    }
    fun resetName() = saveName(DEFAULT_NAME)
    fun saveHideSettingsIcon(hide: Boolean) {
        hideSettingsIcon = hide
        prefs.edit().putBoolean("hide_settings", hide).apply()
    }
    private fun work(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { notice = e.message ?: "No se pudo guardar la apariencia" }
            finally { busy = false }
        }
    }
    fun importIcon(uri: Uri) = work {
        val bitmap = withContext(Dispatchers.IO) { readAndStoreIcon(uri) }
        iconBitmap = bitmap
        notice = "Icono guardado"
        updatePinnedShortcut()
    }
    fun resetIcon() = work {
        withContext(Dispatchers.IO) { iconFile.delete() }
        iconBitmap = null
        notice = "Icono original restaurado"
        updatePinnedShortcut()
    }
    private fun readAndStoreIcon(uri: Uri): Bitmap {
        val temp = File.createTempFile("cronmaster-icon-", ".image", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("No se pudo abrir la imagen")
            input.use { source ->
                temp.outputStream().use { target ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_BYTES) { "La imagen supera 10 MiB." }
                        target.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            check(bounds.outWidth in 1..100000 && bounds.outHeight in 1..100000) { "Formato de imagen no compatible." }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val decoded = BitmapFactory.decodeFile(temp.absolutePath, options) ?: error("No se pudo decodificar la imagen")
            val normalized = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            try {
                val side = minOf(decoded.width, decoded.height)
                val x = (decoded.width - side) / 2
                val y = (decoded.height - side) / 2
                Canvas(normalized).drawBitmap(decoded, Rect(x, y, x + side, y + side), Rect(0, 0, 512, 512), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            } finally { decoded.recycle() }
            val stream = iconFile.startWrite()
            try {
                check(normalized.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "No se pudo guardar el icono" }
                iconFile.finishWrite(stream)
            } catch (e: Exception) { iconFile.failWrite(stream); throw e }
            return normalized
        } finally { temp.delete() }
    }
    private fun shortcut(name: String, bitmap: Bitmap?): ShortcutInfo {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val icon = if (bitmap != null) Icon.createWithBitmap(bitmap) else Icon.createWithResource(context, R.drawable.ic_launcher)
        return ShortcutInfo.Builder(context, SHORTCUT_ID).setShortLabel(name).setLongLabel(name).setIcon(icon).setIntent(intent).build()
    }
    private fun updatePinnedShortcut() {
        val name = displayName
        val bitmap = iconBitmap
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    shortcutMutex.withLock {
                        val manager = context.getSystemService(ShortcutManager::class.java)
                        if (manager != null && manager.pinnedShortcuts.any { it.id == SHORTCUT_ID }) {
                            check(manager.updateShortcuts(listOf(shortcut(name, bitmap)))) { "El launcher no actualizó el acceso directo." }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { notice = "Apariencia guardada; no se pudo actualizar el acceso del launcher." }
        }
    }
    fun addToLauncher() = work {
        val name = displayName
        val bitmap = iconBitmap
        notice = withContext(Dispatchers.IO) {
            shortcutMutex.withLock {
                val manager = context.getSystemService(ShortcutManager::class.java)
                    ?: return@withLock "Launcher no disponible"
                val info = shortcut(name, bitmap)
                if (manager.pinnedShortcuts.any { it.id == SHORTCUT_ID }) {
                    if (manager.updateShortcuts(listOf(info))) "Acceso del launcher actualizado" else "No se pudo actualizar el acceso"
                } else if (!manager.isRequestPinShortcutSupported) {
                    "Este launcher no permite anclar accesos desde la app."
                } else if (manager.requestPinShortcut(info, null)) {
                    "Solicitud enviada. Confirma el diálogo del launcher."
                } else {
                    "El launcher no aceptó la solicitud."
                }
            }
        }
    }
}
