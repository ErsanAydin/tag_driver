package com.tagtakip.pro.data.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
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
import com.google.api.services.drive.model.File as DriveFile

/**
 * Room veritabanını Google Drive'ın gizli **appDataFolder** alanına yedekler / geri yükler (Firebase YOK).
 *
 * KURULUM (bir kez, Google Cloud Console):
 *  1. Bir proje oluşturun ve "Google Drive API"yi etkinleştirin.
 *  2. OAuth izin ekranını yapılandırın (kapsam: `.../auth/drive.appdata`).
 *  3. "Android" türünde bir OAuth istemci kimliği oluşturun: paket adı `com.tagtakip.pro`
 *     (debug derlemede `com.tagtakip.pro.debug`) ve imzalama anahtarının SHA-1 parmak izi
 *     (`./gradlew signingReport`).
 *  google-services.json veya sunucu istemci kimliği GEREKMEZ: kimlik doğrulama GoogleSignIn hesabından
 *  oluşturulan [GoogleAccountCredential] ile yapılır. SHA-1 kayıtlı değilse giriş "DEVELOPER_ERROR (10)"
 *  ile başarısız olur; uygulama çökmez, yedekleme sessizce atlanır.
 *
 * DRIVE_APPDATA kapsamı yalnızca uygulamaya özel gizli klasöre erişir; kullanıcının Drive dosyaları görülmez.
 * Tüm Drive hataları loglanır ve [Result.failure] olarak döner — mesai bitirme akışı asla engellenmez.
 */
@Singleton
class DriveBackupManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: TagTakipDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _account = MutableStateFlow(currentAccount()?.email)
    /** Giriş yapılmış Google hesabının e-postası (yoksa null). */
    val signedInEmail: StateFlow<String?> = _account.asStateFlow()

    private val _lastBackupTime = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 })
    val lastBackupTime: StateFlow<Long?> = _lastBackupTime.asStateFlow()

    val isSignedIn: Boolean get() = currentAccount() != null

    private val signInOptions: GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()

    private fun client(context: Context): GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    /** Compose'da `rememberLauncherForActivityResult` ile başlatılacak giriş intent'i. */
    fun signInIntent(): Intent = client(appContext).signInIntent

    /** Klasik Activity akışı: sonucu onActivityResult'ta [handleSignInResult]'a verin. */
    @Suppress("DEPRECATION")
    fun signIn(activity: Activity) = activity.startActivityForResult(client(activity).signInIntent, RC_SIGN_IN)

    /** Giriş sonucunu işler; başarılı ve Drive kapsamı verilmişse true. */
    fun handleSignInResult(data: Intent?): Boolean = try {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        val granted = account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))
        _account.value = if (granted) account.email else null
        granted
    } catch (e: ApiException) {
        Log.w(TAG, "Google girişi başarısız (kod ${e.statusCode})", e)
        false
    }

    suspend fun signOut() = withContext(ioDispatcher) {
        runCatching { com.google.android.gms.tasks.Tasks.await(client(appContext).signOut()) }
            .onFailure { Log.w(TAG, "Çıkış başarısız", it) }
        _account.value = null
    }

    /** WAL checkpoint sonrası .db dosyasını appDataFolder'a "tag_takip_backup.db" olarak yükler (varsa üzerine yazar). */
    suspend fun backupDatabase(context: Context = appContext): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                val drive = driveService(context) ?: error("Google hesabı ile giriş yapılmadı")
                checkpoint()
                val tmp = File(context.cacheDir, "drive_upload.db")
                context.getDatabasePath(Constants.DATABASE_NAME).copyTo(tmp, overwrite = true)
                try {
                    val content = FileContent(MIME_TYPE, tmp)
                    val existing = findBackup(drive)
                    val uploaded = if (existing != null) {
                        drive.files().update(existing.id, null, content).setFields(FIELDS).execute()
                    } else {
                        val meta = DriveFile().setName(BACKUP_NAME).setParents(listOf(APP_DATA_FOLDER))
                        drive.files().create(meta, content).setFields(FIELDS).execute()
                    }
                    val remoteTime = uploaded.modifiedTime?.value ?: System.currentTimeMillis()
                    markSynced(remoteTime)
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong(KEY_LAST_BACKUP, now).apply()
                    _lastBackupTime.value = now
                    Log.i(TAG, "Drive yedeği yüklendi (${tmp.length()} bayt)")
                    Unit
                } finally {
                    tmp.delete()
                }
            }.onFailure { Log.w(TAG, "Drive yedekleme başarısız", it) }
        }
    }

    /**
     * Drive'daki yedek hem yerel veritabanından hem de bu cihazın son eşitlediği sürümden yeniyse
     * yedeğin değiştirilme zamanını döner; aksi halde (veya hata/giriş yoksa) null.
     */
    suspend fun checkAndPromptRestore(context: Context = appContext): Long? = withContext(ioDispatcher) {
        runCatching {
            val drive = driveService(context) ?: return@runCatching null
            val remote = findBackup(drive)?.modifiedTime?.value ?: return@runCatching null
            val dbFile = context.getDatabasePath(Constants.DATABASE_NAME)
            // Yeni kurulumda (boş veritabanı) dosya zamanı yedekten yeni olsa da geri yükleme önerilir
            val localTime = if (hasLocalData()) {
                maxOf(dbFile.lastModified(), File(dbFile.path + "-wal").lastModified())
            } else 0L
            val lastSynced = prefs.getLong(KEY_LAST_SYNCED_REMOTE, 0L)
            remote.takeIf { it > lastSynced && it > localTime }
        }.onFailure { Log.w(TAG, "Drive yedek kontrolü başarısız", it) }.getOrNull()
    }

    /**
     * Yedeği indirir, doğrular ve yerel veritabanının yerine koyar. Room kapatılır; başarılı olursa
     * çağıran taraf [restartApp] ile uygulamayı yeniden başlatmalıdır.
     */
    suspend fun restoreDatabase(context: Context = appContext): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                val drive = driveService(context) ?: error("Google hesabı ile giriş yapılmadı")
                val remote = findBackup(drive) ?: error("Drive'da yedek bulunamadı")
                val tmp = File(context.cacheDir, "drive_download.db")
                try {
                    tmp.outputStream().use { drive.files().get(remote.id).executeMediaAndDownloadTo(it) }
                    val backupVersion = validateSqlite(tmp)
                    val currentVersion = database.openHelper.readableDatabase.version
                    check(backupVersion <= currentVersion) {
                        "Yedek daha yeni bir uygulama sürümüne ait; önce uygulamayı güncelleyin"
                    }
                    database.close()
                    val dbFile = context.getDatabasePath(Constants.DATABASE_NAME)
                    File(dbFile.path + "-wal").delete()
                    File(dbFile.path + "-shm").delete()
                    tmp.copyTo(dbFile, overwrite = true)
                    markSynced(remote.modifiedTime?.value ?: System.currentTimeMillis())
                    Log.i(TAG, "Drive yedeği geri yüklendi")
                    Unit
                } finally {
                    tmp.delete()
                }
            }.onFailure { Log.w(TAG, "Drive geri yükleme başarısız", it) }
        }
    }

    /** Room tekil örneği kapatıldığı için süreç yeniden başlatılır. */
    fun restartApp(context: Context = appContext) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private fun currentAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(appContext)
            ?.takeIf { GoogleSignIn.hasPermissions(it, Scope(DriveScopes.DRIVE_APPDATA)) }

    private fun driveService(context: Context): Drive? {
        val account = currentAccount()?.account ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
            .setSelectedAccount(account)
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(APP_NAME)
            .build()
    }

    private fun findBackup(drive: Drive): DriveFile? =
        drive.files().list()
            .setSpaces(APP_DATA_FOLDER)
            .setQ("name = '$BACKUP_NAME' and trashed = false")
            .setOrderBy("modifiedTime desc")
            .setFields("files($FIELDS)")
            .execute()
            .files
            ?.firstOrNull()

    private fun hasLocalData(): Boolean =
        database.openHelper.readableDatabase
            .query("SELECT (SELECT COUNT(*) FROM shifts) + (SELECT COUNT(*) FROM earnings)")
            .use { it.moveToFirst() && it.getLong(0) > 0 }

    /** WAL içeriğini ana .db dosyasına yazar (PRAGMA satır döndürdüğü için query ile çalıştırılır). */
    private fun checkpoint() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
    }

    /** Geçerli bir SQLite dosyası olduğunu doğrular ve şema sürümünü (user_version) döner. */
    private fun validateSqlite(file: File): Int {
        val header = ByteArray(16)
        file.inputStream().use { check(it.read(header) == 16) { "Yedek dosyası bozuk" } }
        check(String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) { "Yedek dosyası geçerli değil" }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    }

    private fun markSynced(remoteTime: Long) = prefs.edit().putLong(KEY_LAST_SYNCED_REMOTE, remoteTime).apply()

    companion object {
        private const val TAG = "DriveBackup"
        const val RC_SIGN_IN = 4711
        const val BACKUP_NAME = "tag_takip_backup.db"
        private const val APP_DATA_FOLDER = "appDataFolder"
        private const val MIME_TYPE = "application/x-sqlite3"
        private const val FIELDS = "id, name, modifiedTime"
        private const val APP_NAME = "TAG Takip"
        private const val PREFS_NAME = "drive_backup_prefs"
        private const val KEY_LAST_BACKUP = "last_backup_time"
        private const val KEY_LAST_SYNCED_REMOTE = "last_synced_remote_modified"
    }
}
