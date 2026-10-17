package com.mediagrab

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private data class FormatOption(val label: String, val args: List<String>)

    private val formats = listOf(
        FormatOption(
            "Video - best quality (MP4)",
            listOf(
                "-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b",
                "--merge-output-format", "mp4"
            )
        ),
        FormatOption(
            "Video - up to 720p (MP4)",
            listOf(
                "-f", "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b[height<=720]",
                "--merge-output-format", "mp4"
            )
        ),
        FormatOption(
            "Video - up to 480p (MP4)",
            listOf(
                "-f", "bv*[height<=480][ext=mp4]+ba[ext=m4a]/b[height<=480][ext=mp4]/bv*[height<=480]+ba/b[height<=480]",
                "--merge-output-format", "mp4"
            )
        ),
        FormatOption(
            "Audio only - MP3",
            listOf("-f", "ba/b", "-x", "--audio-format", "mp3", "--audio-quality", "0")
        ),
        FormatOption(
            "Audio only - M4A",
            listOf("-f", "ba/b", "-x", "--audio-format", "m4a")
        )
    )

    private lateinit var urlInput: EditText
    private lateinit var formatSpinner: Spinner
    private lateinit var downloadButton: Button
    private lateinit var cancelButton: Button
    private lateinit var updateButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var engineReady = false
    private var downloadJob: Job? = null
    private var currentProcessId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        formatSpinner = findViewById(R.id.formatSpinner)
        downloadButton = findViewById(R.id.downloadButton)
        cancelButton = findViewById(R.id.cancelButton)
        updateButton = findViewById(R.id.updateButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        formatSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            formats.map { it.label }
        )

        downloadButton.setOnClickListener { startDownload() }
        cancelButton.setOnClickListener { cancelDownload() }
        updateButton.setOnClickListener { updateEngine() }

        handleSharedText(intent)
        initEngine()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedText(intent)
    }

    private fun handleSharedText(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank()) {
                // Shared text often contains a title plus the link; pull out the first URL.
                val match = Regex("https?://\\S+").find(shared)
                urlInput.setText(match?.value ?: shared.trim())
            }
        }
    }

    private fun initEngine() {
        setBusy(true)
        statusText.text = "Starting engine…"
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().init(application)
                    FFmpeg.getInstance().init(application)
                }
                engineReady = true
                statusText.text = "Ready"
            } catch (e: Exception) {
                statusText.text = "Engine failed to start: ${e.message}"
            } finally {
                setBusy(false)
            }
        }
    }

    private fun updateEngine() {
        if (!engineReady) return
        setBusy(true)
        statusText.text = "Updating download engine…"
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        application,
                        YoutubeDL.UpdateChannel.STABLE
                    )
                }
                statusText.text = "Engine update: ${result ?: "already up to date"}"
            } catch (e: Exception) {
                statusText.text = "Update failed: ${e.message}"
            } finally {
                setBusy(false)
            }
        }
    }

    private fun startDownload() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            statusText.text = "Paste a link first."
            return
        }
        if (!engineReady) {
            statusText.text = "Engine is not ready yet."
            return
        }

        val option = formats[formatSpinner.selectedItemPosition]
        val workDir = File(cacheDir, "downloads").apply {
            deleteRecursively()
            mkdirs()
        }
        val processId = UUID.randomUUID().toString()
        currentProcessId = processId

        setBusy(true)
        cancelButton.isEnabled = true
        progressBar.progress = 0
        statusText.text = "Starting…"

        downloadJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val request = YoutubeDLRequest(url).apply {
                        addOption("-o", File(workDir, "%(title).80s.%(ext)s").absolutePath)
                        addOption("--no-playlist")
                        addOption("--restrict-filenames")
                        // Flags start with "-"; a following non-flag item is that flag's value.
                        var i = 0
                        while (i < option.args.size) {
                            val flag = option.args[i]
                            val next = option.args.getOrNull(i + 1)
                            if (next != null && !next.startsWith("-")) {
                                addOption(flag, next)
                                i += 2
                            } else {
                                addOption(flag)
                                i += 1
                            }
                        }
                    }
                    YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ ->
                        runOnUiThread {
                            progressBar.progress = progress.toInt().coerceIn(0, 100)
                            statusText.text = "Downloading… ${progress.toInt()}%  (about ${eta}s left)"
                        }
                    }
                }

                val files = workDir.listFiles()?.filter { it.isFile } ?: emptyList()
                if (files.isEmpty()) {
                    statusText.text = "Nothing was downloaded."
                } else {
                    withContext(Dispatchers.IO) { files.forEach { saveToDownloads(it) } }
                    progressBar.progress = 100
                    statusText.text = "Saved to Downloads/MediaGrab:\n" +
                        files.joinToString("\n") { it.name }
                }
            } catch (e: Exception) {
                statusText.text = "Download failed: ${e.message?.take(300)}"
            } finally {
                workDir.deleteRecursively()
                currentProcessId = null
                cancelButton.isEnabled = false
                setBusy(false)
            }
        }
    }

    private fun cancelDownload() {
        currentProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        downloadJob?.cancel()
        statusText.text = "Cancelled."
    }

    private fun saveToDownloads(file: File) {
        val mime = when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/MediaGrab")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create file in Downloads")
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun setBusy(busy: Boolean) {
        downloadButton.isEnabled = !busy
        updateButton.isEnabled = !busy
        formatSpinner.isEnabled = !busy
        urlInput.isEnabled = !busy
        progressBar.visibility = View.VISIBLE
    }
}
