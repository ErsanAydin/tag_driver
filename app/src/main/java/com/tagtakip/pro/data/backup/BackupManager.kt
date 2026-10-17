package com.tagtakip.pro.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.tagtakip.pro.core.common.Constants
import com.tagtakip.pro.core.di.IoDispatcher
import com.tagtakip.pro.data.local.db.TagTakipDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room veritabanını kullanıcının sistem dosya seçicisinde seçtiği bir belgeye (ör. Google Drive) yedekler.
 * Storage Access Framework kullanılır: Google Cloud kurulumu, OAuth istemcisi veya SHA-1 GEREKMEZ.
 * Seçilen belgenin URI izni kalıcı olarak saklanır; sonraki yedeklemeler aynı dosyanın üzerine yazar.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: TagTakipDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val resolver get() = appContext.contentResolver

    private val _backupUri = MutableStateFlow(prefs.getString(KEY_URI, null)?.let(Uri::parse))
    val backupUri: StateFlow<Uri?> = _backupUri.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.getString(KEY_NAME, null))
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    private val _lastBackupTime = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 })
    val lastBackupTime: StateFlow<Long?> = _lastBackupTime.asStateFlow()

    val hasTarget: Boolean get() = _backupUri.value != null

    /** Seçilen belgeyi yedek hedefi yapar ve kalıcı okuma/yazma izni alır. */
    fun setTarget(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure {
            // Bazı sağlayıcılar yalnızca okuma izni verir
            runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            Log.w(TAG, "Kalıcı yazma izni alınamadı", it)
        }
        val name = queryName(uri)
        prefs.edit().putString(KEY_URI, uri.toString()).putString(KEY_NAME, name).remove(KEY_DISMISSED).apply()
        _backupUri.value = uri
        _displayName.value = name
    }

    fun clear() {
        _backupUri.value?.let { uri ->
            runCatching {
                resolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        prefs.edit().remove(KEY_URI).remove(KEY_NAME).remove(KEY_DISMISSED).apply()
        _backupUri.value = null
        _displayName.value = null
    }

    /** WAL checkpoint sonrası .db dosyasını seçilen belgenin üzerine yazar. */
    suspend fun backup(): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                val uri = _backupUri.value ?: error("Yedek konumu seçilmedi")
                guard {
                    checkpoint()
                    val dbFile = appContext.getDatabasePath(Constants.DATABASE_NAME)
                    val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
                        ?: resolver.openOutputStream(uri, "w")
                        ?: error("Yedek dosyası açılamadı")
                    out.use { o -> dbFile.inputStream().use { it.copyTo(o) } }
                    // Sağlayıcının kaydettiği zaman cihaz saatinden ileride olabilir; kendi yedeğimizi "daha yeni" sanmayalım
                    val now = maxOf(System.currentTimeMillis(), remoteLastModified() ?: 0L)
                    prefs.edit().putLong(KEY_LAST_BACKUP, now).apply()
                    _lastBackupTime.value = now
                    Log.i(TAG, "Yedek yazıldı (${dbFile.length()} bayt)")
                    Unit
                }
            }.onFailure { Log.w(TAG, "Yedekleme başarısız", it) }
        }
    }

    /** Yedeği doğrular ve yerel veritabanının yerine koyar. Başarılıysa çağıran [restartApp] çağırmalıdır. */
    suspend fun restore(): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                val uri = _backupUri.value ?: error("Yedek konumu seçilmedi")
                val tmp = File(appContext.cacheDir, "restore_tmp.db")
                try {
                    guard {
                        val input = resolver.openInputStream(uri) ?: error("Yedek dosyası açılamadı")
                        input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                    }
                    check(tmp.length() > 0) { "Yedek dosyası boş" }
                    val backupVersion = validateSqlite(tmp)
                    val currentVersion = database.openHelper.readableDatabase.version
                    check(backupVersion <= currentVersion) {
                        "Yedek daha yeni bir uygulama sürümüne ait; önce uygulamayı güncelleyin"
                    }
                    val remote = remoteLastModified()
                    database.close()
                    val dbFile = appContext.getDatabasePath(Constants.DATABASE_NAME)
                    File(dbFile.path + "-wal").delete()
                    File(dbFile.path + "-shm").delete()
                    tmp.copyTo(dbFile, overwrite = true)
                    val stamp = remote ?: System.currentTimeMillis()
                    prefs.edit().putLong(KEY_LAST_BACKUP, stamp).apply()
                    _lastBackupTime.value = stamp
                    Log.i(TAG, "Yedek geri yüklendi")
                    Unit
                } finally {
                    tmp.delete()
                }
            }.onFailure { Log.w(TAG, "Geri yükleme başarısız", it) }
        }
    }

    /** Yedek dosyası bu cihazın son yedeğinden belirgin şekilde yeniyse değiştirilme zamanını döner. */
    suspend fun newerRemote(): Long? = withContext(ioDispatcher) {
        runCatching {
            if (!hasTarget) return@runCatching null
            val remote = remoteLastModified() ?: return@runCatching null
            val last = prefs.getLong(KEY_LAST_BACKUP, 0L)
            val dismissed = prefs.getLong(KEY_DISMISSED, 0L)
            remote.takeIf { it > last + 60_000 && it != dismissed }
        }.getOrNull()
    }

    fun dismiss(remoteTime: Long) = prefs.edit().putLong(KEY_DISMISSED, remoteTime).apply()

    fun hasLocalData(): Boolean = runCatching {
        database.openHelper.readableDatabase
            .query("SELECT (SELECT COUNT(*) FROM shifts) + (SELECT COUNT(*) FROM earnings)")
            .use { it.moveToFirst() && it.getLong(0) > 0 }
    }.getOrDefault(false)

    /** Room tekil örneği kapatıldığı için süreç yeniden başlatılır. */
    fun restartApp() {
        val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) appContext.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private fun remoteLastModified(): Long? {
        val uri = _backupUri.value ?: return null
        return runCatching {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
            }
        }.getOrNull()
    }

    private fun queryName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** İzin kaybolduysa hedefi temizler ve anlaşılır bir hata verir. */
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        clear()
        throw IllegalStateException("Yedek dosyasına erişilemiyor, konumu yeniden seçin", e)
    } catch (e: java.io.FileNotFoundException) {
        clear()
        throw IllegalStateException("Yedek dosyası bulunamadı, konumu yeniden seçin", e)
    }

    private fun checkpoint() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
    }

    private fun validateSqlite(file: File): Int {
        val header = ByteArray(16)
        file.inputStream().use { check(it.read(header) == 16) { "Yedek dosyası bozuk" } }
        check(String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) { "Seçilen dosya geçerli bir yedek değil" }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    }

    companion object {
        private const val TAG = "Backup"
        const val DEFAULT_FILE_NAME = "tag_takip_yedek.db"
        private const val PREFS_NAME = "drive_backup_prefs"
        private const val KEY_URI = "backup_uri"
        private const val KEY_NAME = "backup_name"
        private const val KEY_LAST_BACKUP = "last_backup_time"
        private const val KEY_DISMISSED = "dismissed_remote"
    }
}
