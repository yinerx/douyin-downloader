package app.douyin.save

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.RemoteAction
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Stands in for MediaStore so each test can script how the provider reacts. */
class FakeMediaProvider : ContentProvider() {
    enum class Mode { Owned, Gone, NotOwned, Recoverable, RejectsGone, Broken }

    companion object {
        var mode = Mode.Owned
        var recoverable: RecoverableSecurityException? = null
        val deleted = mutableListOf<Uri>()
    }

    override fun onCreate() = true

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = when (mode) {
        Mode.Owned -> {
            deleted += uri
            1
        }
        Mode.Gone -> 0
        Mode.NotOwned -> throw SecurityException("not owner")
        Mode.Recoverable -> throw recoverable!!
        Mode.RejectsGone -> throw IllegalArgumentException("no such row")
        Mode.Broken -> throw UnsupportedOperationException("read only")
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = when (FakeMediaProvider.mode) {
        Mode.Gone, Mode.RejectsGone -> throw FileNotFoundException("gone")
        Mode.NotOwned, Mode.Recoverable -> throw SecurityException("not owner")
        else -> {
            val tmp = File.createTempFile("media", ".mp4")
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String = "video/mp4"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

@RunWith(RobolectricTestRunner::class)
class AndroidMediaFilesTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val uri = "content://$AUTHORITY/video/1"
    private lateinit var dir: File

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(FakeMediaProvider::class.java, AUTHORITY)
        FakeMediaProvider.mode = FakeMediaProvider.Mode.Owned
        FakeMediaProvider.recoverable = null
        FakeMediaProvider.deleted.clear()
        dir = Files.createTempDirectory("dcim").toFile()
    }

    @After
    fun tearDown() {
        dir.setWritable(true)
        dir.deleteRecursively()
    }

    // ── Android 8–9: file:// ──

    @Test
    @Config(sdk = [28])
    fun legacyDeletesExistingFile() {
        val file = File(dir, "a.mp4").apply { writeText("x") }
        val media = AndroidMediaFiles(context)
        assertEquals(FileDelete.Deleted, media.delete(Uri.fromFile(file).toString()))
        assertFalse(file.exists())
        assertFalse(media.stillExists(Uri.fromFile(file).toString()))
    }

    @Test
    @Config(sdk = [28])
    fun legacyMissingFileIsMissing() {
        val gone = Uri.fromFile(File(dir, "gone.mp4")).toString()
        assertEquals(FileDelete.Missing, AndroidMediaFiles(context).delete(gone))
    }

    @Test
    @Config(sdk = [28])
    fun legacyUndeletableFileFails() {
        val file = File(dir, "locked.mp4").apply { writeText("x") }
        dir.setWritable(false)
        val result = AndroidMediaFiles(context).delete(Uri.fromFile(file).toString())
        assertTrue(result is FileDelete.Failed)
        assertTrue(file.exists())
    }

    @Test
    @Config(sdk = [28])
    fun legacyHasNoBatchDialog() {
        assertNull(AndroidMediaFiles(context).batchConsent(listOf(uri)))
    }

    @Test
    @Config(sdk = [28])
    fun unknownSchemeFailsInsteadOfCrashing() {
        val media = AndroidMediaFiles(context)
        assertTrue(media.delete("https://example.com/a.mp4") is FileDelete.Failed)
        assertTrue(media.delete("") is FileDelete.Failed)
        assertFalse(media.stillExists("https://example.com/a.mp4"))
    }

    // ── content:// ──

    @Test
    @Config(sdk = [34])
    fun ownedContentIsDeletedDirectly() {
        assertEquals(FileDelete.Deleted, AndroidMediaFiles(context).delete(uri))
        assertEquals(listOf(Uri.parse(uri)), FakeMediaProvider.deleted)
    }

    @Test
    @Config(sdk = [34])
    fun goneContentIsMissing() {
        FakeMediaProvider.mode = FakeMediaProvider.Mode.Gone
        val media = AndroidMediaFiles(context)
        assertEquals(FileDelete.Missing, media.delete(uri))
        assertFalse(media.stillExists(uri))
    }

    @Test
    @Config(sdk = [34])
    fun providerRejectingGoneRowIsMissing() {
        FakeMediaProvider.mode = FakeMediaProvider.Mode.RejectsGone
        assertEquals(FileDelete.Missing, AndroidMediaFiles(context).delete(uri))
    }

    @Test
    @Config(sdk = [34])
    fun readOnlyProviderFails() {
        FakeMediaProvider.mode = FakeMediaProvider.Mode.Broken
        assertTrue(AndroidMediaFiles(context).delete(uri) is FileDelete.Failed)
    }

    @Test
    @Config(sdk = [34])
    fun stillExistsSeesUnreadableFileAsPresent() {
        FakeMediaProvider.mode = FakeMediaProvider.Mode.NotOwned
        assertTrue(AndroidMediaFiles(context).stillExists(uri))
    }

    @Test
    @Config(sdk = [30])
    fun android11UnownedNeedsBatchConsent() {
        FakeMediaProvider.mode = FakeMediaProvider.Mode.NotOwned
        assertEquals(FileDelete.NeedsConsent(null), AndroidMediaFiles(context).delete(uri))
    }

    @Test
    @Config(sdk = [34])
    fun android11BatchRequestNeverCrashes() {
        // Robolectric has no real MediaStore; whatever happens must not throw.
        AndroidMediaFiles(context).batchConsent(listOf(uri))
    }

    @Test
    @Config(sdk = [29])
    fun android10RecoverableExceptionBecomesPrompt() {
        FakeMediaProvider.mode = FakeMediaProvider.Mode.Recoverable
        val pending = PendingIntent.getActivity(context, 0, Intent("confirm"), PendingIntent.FLAG_IMMUTABLE)
        val action = RemoteAction(Icon.createWithResource(context, android.R.drawable.ic_delete), "允许", "允许", pending)
        FakeMediaProvider.recoverable = RecoverableSecurityException(SecurityException("x"), "需要确认", action)
        val result = AndroidMediaFiles(context).delete(uri)
        val prompt = (result as FileDelete.NeedsConsent).prompt as IntentSenderPrompt
        assertEquals(pending.intentSender, prompt.sender)
    }

    @Test
    @Config(sdk = [29])
    fun android10PlainSecurityExceptionFails() {
        FakeMediaProvider.mode = FakeMediaProvider.Mode.NotOwned
        assertTrue(AndroidMediaFiles(context).delete(uri) is FileDelete.Failed)
    }

    @Test
    @Config(sdk = [29])
    fun android10HasNoBatchDialog() {
        assertNull(AndroidMediaFiles(context).batchConsent(listOf(uri)))
    }

    companion object {
        private const val AUTHORITY = "app.douyin.save.test.media"
    }
}
