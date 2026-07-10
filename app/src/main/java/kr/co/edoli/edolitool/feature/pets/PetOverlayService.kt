package kr.co.edoli.edolitool.feature.pets

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kr.co.edoli.edolitool.MainActivity
import kr.co.edoli.edolitool.R
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

object PetOverlayController {
    private const val preferencesName = "pet_overlay"
    private const val activePetIdKey = "active_pet_id"
    private const val actionShow = "kr.co.edoli.edolitool.action.SHOW_PET"
    private const val actionHide = "kr.co.edoli.edolitool.action.HIDE_PET"
    const val extraPetId = "kr.co.edoli.edolitool.extra.PET_ID"

    fun activePetId(context: Context): String? =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getString(activePetIdKey, null)

    fun canDrawOverApps(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openOverlayPermission(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun show(context: Context, petId: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, PetOverlayService::class.java)
                .setAction(actionShow)
                .putExtra(extraPetId, petId),
        )
    }

    fun hide(context: Context) {
        context.startService(
            Intent(context, PetOverlayService::class.java).setAction(actionHide),
        )
    }

    internal fun setActivePetId(context: Context, petId: String?) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(activePetIdKey, petId)
            .apply()
    }
}

class PetOverlayService : Service() {
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var overlayView: PetSpriteView? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            actionHide -> {
                stopSelf()
                return START_NOT_STICKY
            }

            actionShow -> {
                val petId = intent.getStringExtra(PetOverlayController.extraPetId) ?: return START_NOT_STICKY
                if (!PetOverlayController.canDrawOverApps(this)) return START_NOT_STICKY
                val petFiles = CodexPetRepository.findPetFiles(this, petId) ?: return START_NOT_STICKY
                startForeground(notificationId, createNotification(petFiles.pet.displayName))
                showPet(petFiles)
                PetOverlayController.setActivePetId(this, petId)
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        PetOverlayController.setActivePetId(this, null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showPet(petFiles: CodexPetFiles) {
        overlayView?.let { windowManager.removeView(it) }
        val size = resources.displayMetrics.density.times(112).roundToInt()
        overlayView = PetSpriteView(this, BitmapFactory.decodeFile(petFiles.spritesheet.path)).also { view ->
            windowManager.addView(
                view,
                WindowManager.LayoutParams(
                    size,
                    size,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 24
                    y = 160
                },
            )
        }
    }

    private fun createNotification(petName: String): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "화면 펫", NotificationManager.IMPORTANCE_LOW),
        )
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_START_TOOL_ID, "codex_pets"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$petName 펫 표시 중")
            .setContentText("Codex 펫 화면 오버레이가 실행 중입니다.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val actionShow = "kr.co.edoli.edolitool.action.SHOW_PET"
        const val actionHide = "kr.co.edoli.edolitool.action.HIDE_PET"
        const val channelId = "pet_overlay"
        const val notificationId = 1101
    }
}

private class PetSpriteView(
    context: Context,
    private val spritesheet: Bitmap,
) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val directionSwitchDistance = context.resources.displayMetrics.density * 5
    private var frameIndex = 0
    private var animation = PetAnimation.Idle
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var downWindowX = 0
    private var downWindowY = 0
    private var runningDirection = 0
    private var oppositeDirectionDistance = 0f
    private var isDragging = false
    private var longPressTriggered = false
    private var suppressTapReaction = false
    private var failedInputLockUntil = 0L
    private val recentTapTimes = ArrayDeque<Long>()
    private val advanceAnimation = object : Runnable {
        override fun run() {
            if (frameIndex == animation.frameDurations.lastIndex) {
                if (animation.loops) {
                    frameIndex = 0
                } else {
                    play(PetAnimation.Idle)
                    return
                }
            } else {
                frameIndex += 1
            }
            invalidate()
            handler.postDelayed(this, animation.frameDurations[frameIndex].toLong())
        }
    }
    private val longPress = Runnable {
        if (!isDragging) {
            longPressTriggered = true
            play(PetAnimation.Jumping)
        }
    }
    private val inactivity = Runnable {
        if (!isDragging) {
            play(if (Random.nextBoolean()) PetAnimation.Waiting else PetAnimation.Review)
        }
    }

    init {
        restart(PetAnimation.Idle)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        spritesheet.recycle()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val source = Rect(
            frameIndex * cellWidth,
            animation.row * cellHeight,
            (frameIndex + 1) * cellWidth,
            (animation.row + 1) * cellHeight,
        )
        canvas.drawBitmap(spritesheet, source, Rect(0, 0, width, height), null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val windowManager = context.getSystemService(WindowManager::class.java)
        val params = layoutParams as WindowManager.LayoutParams
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                downWindowX = params.x
                downWindowY = params.y
                isDragging = false
                longPressTriggered = false
                suppressTapReaction = animation == PetAnimation.Failed ||
                    SystemClock.uptimeMillis() < failedInputLockUntil
                runningDirection = 0
                oppositeDirectionDistance = 0f
                handler.removeCallbacks(inactivity)
                if (!suppressTapReaction) {
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val horizontalDistance = event.rawX - downRawX
                val verticalDistance = event.rawY - downRawY
                if (!isDragging && (abs(horizontalDistance) > touchSlop || abs(verticalDistance) > touchSlop)) {
                    isDragging = true
                    handler.removeCallbacks(longPress)
                    suppressTapReaction = false
                    failedInputLockUntil = 0L
                }
                if (!isDragging) return true
                val horizontalStep = event.rawX - lastRawX
                if (abs(horizontalStep) > 0.5f) {
                    updateRunningDirection(if (horizontalStep < 0) -1 else 1, abs(horizontalStep))
                }
                params.x = downWindowX + horizontalDistance.roundToInt()
                params.y = downWindowY + verticalDistance.roundToInt()
                windowManager.updateViewLayout(this, params)
                lastRawX = event.rawX
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                when {
                    isDragging -> {
                        isDragging = false
                        play(PetAnimation.Idle)
                    }

                    !suppressTapReaction && !longPressTriggered -> {
                        play(if (isOverstimulated()) PetAnimation.Failed else PetAnimation.Waving)
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                if (isDragging) {
                    isDragging = false
                    play(PetAnimation.Idle)
                }
                return true
            }
        }
        return true
    }

    private fun updateRunningDirection(nextDirection: Int, distance: Float) {
        when {
            runningDirection == 0 || runningDirection == nextDirection -> {
                runningDirection = nextDirection
                oppositeDirectionDistance = 0f
                playRunningDirection()
            }

            else -> {
                oppositeDirectionDistance += distance
                if (oppositeDirectionDistance >= directionSwitchDistance) {
                    runningDirection = nextDirection
                    oppositeDirectionDistance = 0f
                    playRunningDirection()
                }
            }
        }
    }

    private fun playRunningDirection() {
        play(if (runningDirection < 0) PetAnimation.RunningLeft else PetAnimation.RunningRight)
    }

    private fun play(nextAnimation: PetAnimation) {
        if (animation == nextAnimation) return
        restart(nextAnimation)
    }

    private fun restart(nextAnimation: PetAnimation) {
        animation = nextAnimation
        frameIndex = 0
        if (animation == PetAnimation.Failed) {
            failedInputLockUntil = SystemClock.uptimeMillis() +
                animation.frameDurations.sum() + failedInputGraceMillis
        }
        handler.removeCallbacks(advanceAnimation)
        handler.removeCallbacks(inactivity)
        invalidate()
        handler.postDelayed(advanceAnimation, animation.frameDurations.first().toLong())
        if (animation == PetAnimation.Idle) {
            scheduleInactivity()
        }
    }

    private fun scheduleInactivity() {
        handler.removeCallbacks(inactivity)
        handler.postDelayed(inactivity, Random.nextLong(inactivityMinMillis, inactivityMaxMillis + 1))
    }

    private fun isOverstimulated(): Boolean {
        val now = System.currentTimeMillis()
        while (recentTapTimes.isNotEmpty() && now - recentTapTimes.first > rapidTapWindowMillis) {
            recentTapTimes.removeFirst()
        }
        recentTapTimes.addLast(now)
        return (recentTapTimes.size >= rapidTapCount).also { overstimulated ->
            if (overstimulated) recentTapTimes.clear()
        }
    }

    private companion object {
        const val cellWidth = 192
        const val cellHeight = 208
        const val rapidTapCount = 6
        const val rapidTapWindowMillis = 3_000L
        const val failedInputGraceMillis = 500L
        const val inactivityMinMillis = 4_000L
        const val inactivityMaxMillis = 6_000L
    }
}

private enum class PetAnimation(
    val row: Int,
    val frameDurations: IntArray,
    val loops: Boolean,
) {
    Idle(row = 0, frameDurations = intArrayOf(280, 110, 110, 140, 140, 320), loops = true),
    RunningRight(row = 1, frameDurations = intArrayOf(120, 120, 120, 120, 120, 120, 120, 220), loops = true),
    RunningLeft(row = 2, frameDurations = intArrayOf(120, 120, 120, 120, 120, 120, 120, 220), loops = true),
    Waving(row = 3, frameDurations = intArrayOf(140, 140, 140, 280), loops = false),
    Jumping(row = 4, frameDurations = intArrayOf(140, 140, 140, 140, 280), loops = false),
    Failed(row = 5, frameDurations = intArrayOf(140, 140, 140, 140, 140, 140, 140, 240), loops = false),
    Waiting(row = 6, frameDurations = intArrayOf(150, 150, 150, 150, 150, 260), loops = false),
    Running(row = 7, frameDurations = intArrayOf(120, 120, 120, 120, 120, 220), loops = true),
    Review(row = 8, frameDurations = intArrayOf(150, 150, 150, 150, 150, 280), loops = false),
}
