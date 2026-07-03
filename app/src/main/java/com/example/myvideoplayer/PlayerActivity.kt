package com.example.myvideoplayer

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.myvideoplayer.databinding.ActivityPlayerBinding
import com.example.myvideoplayer.model.VideoItem
import kotlin.math.abs
import kotlin.math.roundToInt

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEOS = "extra_videos"
        const val EXTRA_INDEX = "extra_index"
        private val SPEED_OPTIONS = floatArrayOf(0.5f, 1f, 1.25f, 1.5f, 2f)
        private const val HIDE_DELAY_MS = 3500L
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    private lateinit var videos: List<VideoItem>
    private var currentIndex = 0
    private var speedIndex = 1 // يبدأ من 1x

    private var isLocked = false
    private var isControlsVisible = true
    private var isSeekBarTracking = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var gestureDetector: GestureDetector

    // متغيرات تتبع السحب (السطوع / الصوت)
    private var downX = 0f
    private var downY = 0f
    private var isVerticalDrag = false
    private var startVolume = 0
    private var startBrightness = 0.5f

    private val hideControlsRunnable = Runnable { hideControls() }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            player?.let { p ->
                val duration = p.duration.coerceAtLeast(0)
                val position = p.currentPosition.coerceAtLeast(0)
                if (!isSeekBarTracking) {
                    binding.seekBar.max = if (duration > 0) duration.toInt() else 0
                    binding.seekBar.progress = position.toInt()
                    binding.currentTimeText.text = formatTime(position)
                    binding.totalTimeText.text = formatTime(duration)
                }
            }
            mainHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        videos = intent.getParcelableArrayListExtra(EXTRA_VIDEOS) ?: emptyList()
        currentIndex = intent.getIntExtra(EXTRA_INDEX, 0)

        if (videos.isEmpty()) {
            finish()
            return
        }

        setupGestureDetector()
        setupTouchOverlay()
        setupControlButtons()
        setupSeekBar()
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    // ---------- إعداد المشغّل ----------

    private fun initializePlayer() {
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        binding.playerView.player = exoPlayer

        val mediaItems = videos.map { MediaItem.fromUri(it.uri) }
        exoPlayer.setMediaItems(mediaItems, currentIndex, 0L)
        exoPlayer.playWhenReady = true
        exoPlayer.setPlaybackSpeed(SPEED_OPTIONS[speedIndex])
        exoPlayer.prepare()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.playPauseButton.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                binding.bufferingProgress.visibility =
                    if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = exoPlayer.currentMediaItemIndex
                updateTitle()
            }

            override fun onPlayerError(error: PlaybackException) {
                binding.bufferingProgress.visibility = View.GONE
            }
        })

        updateTitle()
        mainHandler.post(updateProgressRunnable)
        scheduleHideControls()
    }

    private fun releasePlayer() {
        player?.let {
            currentIndex = it.currentMediaItemIndex
        }
        mainHandler.removeCallbacks(updateProgressRunnable)
        player?.release()
        player = null
    }

    private fun updateTitle() {
        if (currentIndex in videos.indices) {
            binding.videoTitleText.text = videos[currentIndex].title
        }
    }

    // ---------- أزرار التحكم ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControlButtons() {
        binding.backButton.setOnClickListener { finish() }

        binding.playPauseButton.setOnClickListener {
            togglePlayPause()
            scheduleHideControls()
        }

        binding.rewindButton.setOnClickListener {
            seekRelative(-10_000)
            scheduleHideControls()
        }

        binding.forwardButton.setOnClickListener {
            seekRelative(10_000)
            scheduleHideControls()
        }

        binding.nextButton.setOnClickListener {
            player?.let {
                if (it.hasNextMediaItem()) it.seekToNextMediaItem()
            }
            scheduleHideControls()
        }

        binding.previousButton.setOnClickListener {
            player?.let {
                if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem()
            }
            scheduleHideControls()
        }

        binding.speedButton.setOnClickListener {
            speedIndex = (speedIndex + 1) % SPEED_OPTIONS.size
            player?.setPlaybackSpeed(SPEED_OPTIONS[speedIndex])
            showGestureIndicator(GestureType.SPEED, "×${SPEED_OPTIONS[speedIndex]}")
            scheduleHideControls()
        }

        binding.lockButton.setOnClickListener { setLocked(true) }
        binding.unlockOnlyButton.setOnClickListener { setLocked(false) }
    }

    private fun togglePlayPause() {
        player?.let {
            it.playWhenReady = !it.playWhenReady
        }
    }

    private fun seekRelative(deltaMs: Long) {
        player?.let {
            val newPos = (it.currentPosition + deltaMs).coerceIn(0, it.duration.coerceAtLeast(0))
            it.seekTo(newPos)
        }
    }

    private fun setLocked(locked: Boolean) {
        isLocked = locked
        if (locked) {
            hideControls()
            binding.unlockOnlyButton.visibility = View.VISIBLE
        } else {
            binding.unlockOnlyButton.visibility = View.GONE
            showControls()
        }
    }

    // ---------- شريط التقديم ----------

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.currentTimeText.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeekBarTracking = true
                mainHandler.removeCallbacks(hideControlsRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeekBarTracking = false
                player?.seekTo(seekBar?.progress?.toLong() ?: 0L)
                scheduleHideControls()
            }
        })
    }

    // ---------- الإيماءات (دبل تاب / سحب) ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchOverlay() {
        binding.gestureOverlay.setOnTouchListener { view, event ->
            if (isLocked) return@setOnTouchListener true

            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    isVerticalDrag = false
                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    startBrightness = currentBrightness()
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY

                    if (!isVerticalDrag && abs(dy) > 35 && abs(dy) > abs(dx)) {
                        isVerticalDrag = true
                    }

                    if (isVerticalDrag) {
                        val screenWidth = view.width
                        val change = -dy / 500f // نسبة التغيير بناءً على مسافة السحب

                        if (downX < screenWidth / 2f) {
                            // النصف الأيسر: التحكم بالسطوع
                            val newBrightness = (startBrightness + change).coerceIn(0.02f, 1f)
                            setBrightness(newBrightness)
                            showGestureIndicator(
                                GestureType.BRIGHTNESS,
                                "${(newBrightness * 100).roundToInt()}%"
                            )
                        } else {
                            // النصف الأيمن: التحكم بالصوت
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val newVol = (startVolume + change * maxVol).roundToInt()
                                .coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            showGestureIndicator(
                                GestureType.VOLUME,
                                "${(newVol * 100 / maxVol)}%"
                            )
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isVerticalDrag) {
                        isVerticalDrag = false
                        mainHandler.postDelayed({ binding.gestureIndicatorContainer.visibility = View.GONE }, 500)
                    }
                }
            }
            true
        }
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isLocked) return true
                toggleControlsVisibility()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isLocked) return true
                val width = binding.gestureOverlay.width
                when {
                    e.x < width / 3f -> {
                        seekRelative(-10_000)
                        showGestureIndicator(GestureType.SEEK_BACK, "-10 ثواني")
                    }
                    e.x > width * 2f / 3f -> {
                        seekRelative(10_000)
                        showGestureIndicator(GestureType.SEEK_FORWARD, "+10 ثواني")
                    }
                    else -> togglePlayPause()
                }
                return true
            }
        })
    }

    // ---------- إظهار/إخفاء عناصر التحكم ----------

    private fun toggleControlsVisibility() {
        if (isControlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        isControlsVisible = true
        binding.topBar.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.VISIBLE
        scheduleHideControls()
    }

    private fun hideControls() {
        isControlsVisible = false
        binding.topBar.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
    }

    private fun scheduleHideControls() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, HIDE_DELAY_MS)
    }

    // ---------- مؤشر السحب ----------

    private enum class GestureType { BRIGHTNESS, VOLUME, SEEK_FORWARD, SEEK_BACK, SPEED }

    private fun showGestureIndicator(type: GestureType, text: String) {
        val iconRes = when (type) {
            GestureType.BRIGHTNESS -> R.drawable.ic_brightness
            GestureType.VOLUME -> R.drawable.ic_volume
            GestureType.SEEK_FORWARD -> R.drawable.ic_forward10
            GestureType.SEEK_BACK -> R.drawable.ic_rewind10
            GestureType.SPEED -> R.drawable.ic_speed
        }
        binding.gestureIndicatorIcon.setImageResource(iconRes)
        binding.gestureIndicatorText.text = text
        binding.gestureIndicatorContainer.visibility = View.VISIBLE

        if (type == GestureType.SEEK_FORWARD || type == GestureType.SEEK_BACK || type == GestureType.SPEED) {
            mainHandler.postDelayed({ binding.gestureIndicatorContainer.visibility = View.GONE }, 700)
        }
    }

    // ---------- السطوع ----------

    private fun currentBrightness(): Float {
        val current = window.attributes.screenBrightness
        return if (current in 0f..1f) current else 0.5f
    }

    private fun setBrightness(value: Float) {
        val attrs = window.attributes
        attrs.screenBrightness = value
        window.attributes = attrs
    }

    // ---------- تنسيق الوقت ----------

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // ---------- الوضع الغامر (إخفاء أشرطة النظام) ----------

    private fun enterImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }
}
