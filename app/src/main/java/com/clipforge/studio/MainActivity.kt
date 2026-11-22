package com.clipforge.studio

import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

@UnstableApi
class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(13, 15, 18)
    private val panel = Color.rgb(23, 26, 30)
    private val lime = Color.rgb(185, 255, 57)
    private val white = Color.rgb(244, 245, 246)
    private val muted = Color.rgb(146, 152, 160)
    private var player: ExoPlayer? = null
    private var videoUri: Uri? = null
    private var durationMs = 0L
    private var startMs = 0L
    private var endMs = 0L
    private var updating = false
    private lateinit var playerView: PlayerView
    private lateinit var startBar: SeekBar
    private lateinit var endBar: SeekBar
    private lateinit var playButton: TextView
    private lateinit var startLabel: TextView
    private lateinit var endLabel: TextView
    private lateinit var durationLabel: TextView
    private lateinit var exportButton: Button
    private lateinit var emptyOverlay: LinearLayout

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            loadVideo(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        val d = exo.duration
                        if (d > 0) setDuration(d)
                        emptyOverlay.visibility = View.GONE
                        durationLabel.text = format(exo.currentPosition) + "  /  " + format(durationMs)
                    }
                    if (state == Player.STATE_ENDED) exo.seekTo(startMs)
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playButton.text = if (isPlaying) "Ⅱ" else "▶" }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    durationLabel.text = format(newPosition.positionMs) + "  /  " + format(durationMs)
                }
            })
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(18), dp(10), dp(18), dp(12)) }
        setContentView(root)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(2), 0, dp(10)) }
        val brand = TextView(this).apply { text = "◉  CLIPFORGE"; textSize = 18f; setTextColor(white); letterSpacing = .08f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        top.addView(brand, LinearLayout.LayoutParams(0, dp(44), 1f))
        val save = textButton("Guardar", false).apply { setOnClickListener { toast("El proyecto se conserva en esta sesión. Guardado de proyectos próximamente.") } }
        top.addView(save)
        root.addView(top)

        val title = TextView(this).apply { text = "ESTUDIO DE EDICIÓN"; textSize = 10f; letterSpacing = .18f; setTextColor(muted); setPadding(0, 0, 0, dp(9)) }
        root.addView(title)
        val previewFrame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            setBackgroundColor(Color.BLACK)
        }
        previewFrame.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        emptyOverlay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.rgb(17, 19, 22)) }
        val emptyIcon = TextView(this).apply { text = "▶"; textSize = 29f; setTextColor(lime); gravity = Gravity.CENTER; background = rounded(panel, 100f); layoutParams = LinearLayout.LayoutParams(dp(68), dp(68)).apply { gravity = Gravity.CENTER; bottomMargin = dp(13) } }
        val emptyTitle = TextView(this).apply { text = "Tu próximo video empieza aquí"; textSize = 15f; setTextColor(white); gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val emptySub = TextView(this).apply { text = "Importa un clip para comenzar a editar"; textSize = 12f; setTextColor(muted); gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) }
        emptyOverlay.addView(emptyIcon); emptyOverlay.addView(emptyTitle); emptyOverlay.addView(emptySub)
        previewFrame.addView(emptyOverlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(previewFrame, LinearLayout.LayoutParams(-1, 0, 1.12f).apply { bottomMargin = dp(12) })

        val playRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, dp(4), dp(10)) }
        playButton = TextView(this).apply { text = "▶"; textSize = 20f; setTextColor(bg); gravity = Gravity.CENTER; background = rounded(lime, 50f); setOnClickListener { togglePlayback() } }
        playRow.addView(playButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        durationLabel = TextView(this).apply { text = "00:00.0  /  00:00.0"; textSize = 12f; setTextColor(white); typeface = android.graphics.Typeface.MONOSPACE; gravity = Gravity.CENTER; setPadding(dp(12), 0, 0, 0) }
        playRow.addView(durationLabel, LinearLayout.LayoutParams(0, dp(44), 1f))
        val fit = TextView(this).apply { text = "FIT"; textSize = 10f; setTextColor(muted); gravity = Gravity.CENTER; background = rounded(panel, 12f); setPadding(dp(12), dp(9), dp(12), dp(9)) }
        playRow.addView(fit)
        root.addView(playRow)

        val timelineCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(11), dp(13), dp(10)); background = rounded(panel, 16f) }
        val timeHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val timeTitle = TextView(this).apply { text = "RECORTE"; textSize = 10f; letterSpacing = .15f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(muted) }
        timeHead.addView(timeTitle, LinearLayout.LayoutParams(0, dp(25), 1f))
        val clipLen = TextView(this).apply { text = "Ajusta inicio y fin"; textSize = 11f; setTextColor(muted) }
        timeHead.addView(clipLen)
        timelineCard.addView(timeHead)
        val ruler = TextView(this).apply { text = "▏ ▏ ▏ ▏ ▏ ▏ ▏ ▏ ▏ ▏ ▏ ▏ ▏ ▏ ▏ ▏"; textSize = 12f; letterSpacing = .07f; setTextColor(Color.rgb(81, 94, 68)); gravity = Gravity.CENTER; background = rounded(Color.rgb(31, 38, 29), 8f); setPadding(dp(3), dp(7), dp(3), dp(7)); setOnClickListener { player?.seekTo(startMs) } }
        timelineCard.addView(ruler, LinearLayout.LayoutParams(-1, dp(36)).apply { topMargin = dp(5); bottomMargin = dp(9) })
        val startRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        startLabel = TextView(this).apply { text = "IN   00:00.0"; textSize = 11f; typeface = android.graphics.Typeface.MONOSPACE; setTextColor(lime) }
        startRow.addView(startLabel, LinearLayout.LayoutParams(dp(90), dp(27)))
        startBar = SeekBar(this).apply { max = 1000; progress = 0; progressTintList = android.content.res.ColorStateList.valueOf(lime); thumbTintList = android.content.res.ColorStateList.valueOf(lime); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.DKGRAY) }
        startRow.addView(startBar, LinearLayout.LayoutParams(0, dp(35), 1f))
        timelineCard.addView(startRow)
        val endRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        endLabel = TextView(this).apply { text = "OUT 00:00.0"; textSize = 11f; typeface = android.graphics.Typeface.MONOSPACE; setTextColor(lime) }
        endRow.addView(endLabel, LinearLayout.LayoutParams(dp(90), dp(27)))
        endBar = SeekBar(this).apply { max = 1000; progress = 1000; progressTintList = android.content.res.ColorStateList.valueOf(lime); thumbTintList = android.content.res.ColorStateList.valueOf(lime); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.DKGRAY) }
        endRow.addView(endBar, LinearLayout.LayoutParams(0, dp(35), 1f))
        timelineCard.addView(endRow)
        root.addView(timelineCard, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })

        startBar.setOnSeekBarChangeListener(listener(true))
        endBar.setOnSeekBarChangeListener(listener(false))

        val toolsHeader = TextView(this).apply { text = "HERRAMIENTAS"; textSize = 10f; letterSpacing = .15f; setTextColor(muted); setPadding(0, 0, 0, dp(7)) }
        root.addView(toolsHeader)
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val add = textButton("＋  Importar", true).apply { setOnClickListener { pickVideo.launch(arrayOf("video/*")) } }
        tools.addView(add, LinearLayout.LayoutParams(0, dp(48), 1.12f).apply { marginEnd = dp(7) })
        val split = textButton("✂  Dividir", false).apply { setOnClickListener { splitAtPlayhead() } }
        tools.addView(split, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(7) })
        exportButton = textButton("Exportar  ↑", true).apply { setOnClickListener { exportClip() } }
        tools.addView(exportButton, LinearLayout.LayoutParams(0, dp(48), 1.15f))
        root.addView(tools)
        val footer = TextView(this).apply { text = "1080P  ·  30 FPS  ·  MP4"; textSize = 9f; letterSpacing = .12f; setTextColor(Color.rgb(91, 96, 102)); gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        root.addView(footer)
    }

    private fun listener(isStart: Boolean) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser || updating || durationMs <= 0) return
            val value = durationMs * progress / 1000L
            if (isStart) {
                startMs = value.coerceAtMost((endMs - 250).coerceAtLeast(0))
                updating = true; startBar.progress = (startMs * 1000 / durationMs).toInt(); updating = false
                startLabel.text = "IN   ${format(startMs)}"
                player?.seekTo(startMs)
            } else {
                endMs = value.coerceAtLeast((startMs + 250).coerceAtMost(durationMs))
                updating = true; endBar.progress = (endMs * 1000 / durationMs).toInt(); updating = false
                endLabel.text = "OUT ${format(endMs)}"
            }
        }
        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }

    private fun loadVideo(uri: Uri) {
        videoUri = uri
        durationMs = 0; startMs = 0; endMs = 0
        player?.apply { stop(); clearMediaItems(); setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = false }
        startLabel.text = "IN   00:00.0"; endLabel.text = "OUT 00:00.0"
        durationLabel.text = "Cargando video…"
    }

    private fun setDuration(ms: Long) {
        durationMs = ms; startMs = 0; endMs = ms
        updating = true
        startBar.progress = 0; endBar.progress = 1000
        updating = false
        startLabel.text = "IN   ${format(0)}"; endLabel.text = "OUT ${format(ms)}"
        durationLabel.text = "${format(player?.currentPosition ?: 0)}  /  ${format(ms)}"
    }

    private fun togglePlayback() {
        val p = player ?: return
        if (videoUri == null) { toast("Primero importa un video"); return }
        if (p.isPlaying) p.pause() else {
            if (p.currentPosition < startMs || p.currentPosition >= endMs) p.seekTo(startMs)
            p.play()
        }
    }

    private fun splitAtPlayhead() {
        val p = player ?: return
        if (videoUri == null || durationMs <= 0) { toast("Importa un video para editar"); return }
        val at = p.currentPosition.coerceIn(startMs + 250, endMs - 250)
        if (at <= startMs || at >= endMs) { toast("Mueve el cursor dentro del clip"); return }
        endMs = at
        updating = true; endBar.progress = (endMs * 1000 / durationMs).toInt(); updating = false
        endLabel.text = "OUT ${format(endMs)}"
        toast("Recorte ajustado hasta ${format(at)}. Exporta para guardar el segmento.")
    }

    private fun exportClip() {
        val uri = videoUri
        if (uri == null || durationMs <= 0) { toast("Importa un video primero"); return }
        if (endMs - startMs < 300) { toast("El segmento debe durar al menos 0,3 segundos"); return }
        exportButton.isEnabled = false; exportButton.text = "Exportando…"
        player?.pause()
        val temp = File(cacheDir, "clipforge_${System.currentTimeMillis()}.mp4")
        val clip = MediaItem.Builder().setUri(uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()
        ).build()
        val edited = EditedMediaItem.Builder(clip).build()
        val transformer = Transformer.Builder(this).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                try {
                    val saved = copyToMovies(temp)
                    temp.delete()
                    toast("Video exportado: ${saved ?: temp.name}")
                } catch (e: Exception) {
                    toast("Exportación lista, pero no se pudo copiar a Videos: ${e.message ?: "error"}")
                } finally { exportButton.isEnabled = true; exportButton.text = "Exportar  ↑" }
            }
            override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                temp.delete(); exportButton.isEnabled = true; exportButton.text = "Exportar  ↑"
                toast("No se pudo exportar: ${exportException.errorCodeName}")
            }
        }).build()
        try { transformer.start(edited, temp.absolutePath) }
        catch (e: Exception) { exportButton.isEnabled = true; exportButton.text = "Exportar  ↑"; temp.delete(); toast("Error al iniciar exportación: ${e.message ?: "desconocido"}") }
    }

    private fun copyToMovies(file: File): String? {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "ClipForge_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ClipForge")
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("No se pudo crear el archivo de salida")
            contentResolver.openOutputStream(uri)?.use { output -> FileInputStream(file).use { input -> input.copyTo(output) } } ?: throw IllegalStateException("No se pudo escribir el video")
            return "Videos/ClipForge"
        }
        val folder = getExternalFilesDir("Movies") ?: filesDir
        val destination = File(folder, "ClipForge_${System.currentTimeMillis()}.mp4")
        FileInputStream(file).use { input -> FileOutputStream(destination).use { output -> input.copyTo(output) } }
        return destination.absolutePath
    }

    private fun textButton(label: String, primary: Boolean): Button = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false; typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(if (primary) bg else white)
        background = rounded(if (primary) lime else panel, 12f)
        stateListAnimator = null
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(dp(7), 0, dp(7), 0)
    }

    private fun rounded(color: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat() }
    private fun format(ms: Long): String { val t = (ms.coerceAtLeast(0) / 100L).toInt(); return String.format(Locale.US, "%02d:%02d.%d", t / 600, (t / 10) % 60, t % 10) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
