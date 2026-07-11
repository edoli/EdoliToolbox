package kr.co.edoli.edolitool.feature.pets

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
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
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.random.Random

object PetOverlayController {
    private const val preferencesName = "pet_overlay"
    private const val activePetIdsKey = "active_pet_ids"
    private const val petSizeDpKey = "pet_size_dp"
    private const val smoothDragKey = "smooth_drag"
    private const val smoothDragFollowFractionKey = "smooth_drag_follow_fraction"
    private const val actionShow = "kr.co.edoli.edolitool.action.SHOW_PET"
    private const val actionHide = "kr.co.edoli.edolitool.action.HIDE_PET"
    private const val actionUpdateSize = "kr.co.edoli.edolitool.action.UPDATE_PET_SIZE"
    const val extraPetId = "kr.co.edoli.edolitool.extra.PET_ID"

    fun activePetIds(context: Context): Set<String> =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getStringSet(activePetIdsKey, emptySet())
            .orEmpty()
            .toSet()

    fun canDrawOverApps(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun petSizeDp(context: Context): Int =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getInt(petSizeDpKey, defaultPetSizeDp)

    fun updatePetSize(context: Context, sizeDp: Int) {
        val clampedSize = sizeDp.coerceIn(minPetSizeDp, maxPetSizeDp)
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putInt(petSizeDpKey, clampedSize)
            .apply()
        if (activePetIds(context).isNotEmpty()) {
            context.startService(
                Intent(context, PetOverlayService::class.java).setAction(actionUpdateSize),
            )
        }
    }
    fun isSmoothDragEnabled(context: Context): Boolean =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getBoolean(smoothDragKey, true)

    fun setSmoothDragEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(smoothDragKey, enabled)
            .apply()
    }

    fun smoothDragFollowFraction(context: Context): Float =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getFloat(smoothDragFollowFractionKey, defaultSmoothDragFollowFraction)
            .coerceIn(minSmoothDragFollowFraction, maxSmoothDragFollowFraction)

    fun setSmoothDragFollowFraction(context: Context, fraction: Float) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putFloat(
                smoothDragFollowFractionKey,
                fraction.coerceIn(minSmoothDragFollowFraction, maxSmoothDragFollowFraction),
            )
            .apply()
    }

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

    fun hide(context: Context, petId: String? = null) {
        context.startService(
            Intent(context, PetOverlayService::class.java)
                .setAction(actionHide)
                .apply { petId?.let { putExtra(extraPetId, it) } },
        )
    }

    internal fun setActivePetIds(context: Context, petIds: Set<String>) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(activePetIdsKey, petIds)
            .apply()
    }

    const val minPetSizeDp = 32
    const val maxPetSizeDp = 512
    const val defaultPetSizeDp = 112
    const val minSmoothDragFollowFraction = 0.02f
    const val maxSmoothDragFollowFraction = 0.50f
    const val defaultSmoothDragFollowFraction = 0.28f
}

class PetOverlayService : Service() {
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val overlayViews = mutableMapOf<String, PetSpriteView>()
    private val restoreIndicatorViews = mutableMapOf<String, PetRestoreIndicatorView>()
    private val hiddenPetFiles = mutableMapOf<String, CodexPetFiles>()
    private val petTransitionDistancePx: Float
        get() = resources.displayMetrics.density * 32

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            actionHide -> {
                val petId = intent.getStringExtra(PetOverlayController.extraPetId)
                if (petId == null) {
                    stopSelf()
                } else {
                    removePet(petId)
                    if (overlayViews.isEmpty()) stopSelf()
                }
                return START_NOT_STICKY
            }

            actionShow -> {
                val petId = intent.getStringExtra(PetOverlayController.extraPetId) ?: return START_NOT_STICKY
                if (!PetOverlayController.canDrawOverApps(this)) return START_NOT_STICKY
                val petFiles = CodexPetRepository.findPetFiles(this, petId) ?: return START_NOT_STICKY
                if (overlayViews.isEmpty()) startForeground(notificationId, createNotification())
                showPet(petFiles)
                updateActivePets()
                return START_STICKY
            }

            actionUpdateSize -> {
                resizePet()
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlayViews.values.forEach { windowManager.removeView(it) }
        overlayViews.clear()
        restoreIndicatorViews.values.forEach { windowManager.removeView(it) }
        restoreIndicatorViews.clear()
        hiddenPetFiles.clear()
        PetOverlayController.setActivePetIds(this, emptySet())
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayViews.toList().forEach { (petId, view) ->
            val params = view.layoutParams as WindowManager.LayoutParams
            if (hiddenSide(params) != null) {
                CodexPetRepository.findPetFiles(this, petId)?.let { petFiles ->
                    hidePetIfDraggedOffscreen(petFiles, params)
                }
            } else {
                params.x = params.x.coerceIn(0, (resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0))
                params.y = params.y.coerceIn(
                    indicatorMarginPx,
                    (resources.displayMetrics.heightPixels - params.height - indicatorMarginPx).coerceAtLeast(indicatorMarginPx),
                )
                windowManager.updateViewLayout(view, params)
            }
        }
        restoreIndicatorViews.values.forEach { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.x = if (view.side == HiddenSide.Left) 0 else resources.displayMetrics.widthPixels - params.width
            params.y = params.y.coerceIn(
                indicatorMarginPx,
                (resources.displayMetrics.heightPixels - params.height - indicatorMarginPx).coerceAtLeast(indicatorMarginPx),
            )
            windowManager.updateViewLayout(view, params)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showPet(
        petFiles: CodexPetFiles,
        appearingFrom: HiddenSide? = null,
        removeRestoreIndicator: Boolean = true,
    ) {
        removePet(
            petId = petFiles.pet.id,
            updateState = false,
            removeIndicator = removeRestoreIndicator,
        )
        if (removeRestoreIndicator) {
            removeRestoreIndicator(petFiles.pet.id)
        }
        val size = resources.displayMetrics.density
            .times(PetOverlayController.petSizeDp(this))
            .roundToInt()
        val view = PetSpriteView(
            context = this,
            spritesheet = BitmapFactory.decodeFile(petFiles.spritesheet.path),
            isSmoothDragEnabled = { PetOverlayController.isSmoothDragEnabled(this) },
            smoothDragFollowFraction = { PetOverlayController.smoothDragFollowFraction(this) },
            onDragFinished = { params -> hidePetIfDraggedOffscreen(petFiles, params) },
        )
        overlayViews[petFiles.pet.id] = view
        val offset = resources.displayMetrics.density.times(36 * (overlayViews.size - 1)).roundToInt()
        windowManager.addView(
            view,
            WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 24 + offset
                y = 160 + offset
            },
        )
        if (appearingFrom != null) {
            view.alpha = 0f
            view.translationX = if (appearingFrom == HiddenSide.Left) -petTransitionDistancePx else petTransitionDistancePx
            view.animate().alpha(1f).translationX(0f).setDuration(petTransitionDurationMillis).start()
        }
    }

    private fun resizePet() {
        val size = resources.displayMetrics.density
            .times(PetOverlayController.petSizeDp(this))
            .roundToInt()
        overlayViews.values.forEach { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.width = size
            params.height = size
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun hidePetIfDraggedOffscreen(petFiles: CodexPetFiles, params: WindowManager.LayoutParams) {
        val side = hiddenSide(params) ?: return
        val view = overlayViews.remove(petFiles.pet.id) ?: return
        hiddenPetFiles[petFiles.pet.id] = petFiles
        updateActivePets()
        showRestoreIndicator(petFiles, side, params.y)
        view.animate()
            .alpha(0f)
            .translationXBy(if (side == HiddenSide.Left) -petTransitionDistancePx else petTransitionDistancePx)
            .setDuration(petTransitionDurationMillis)
            .withEndAction {
                windowManager.removeView(view)
            }
            .start()
    }

    private fun hiddenSide(params: WindowManager.LayoutParams): HiddenSide? {
        return when {
            params.x <= -(params.width * hiddenFraction) -> HiddenSide.Left
            params.x >= resources.displayMetrics.widthPixels - (params.width * (1 - hiddenFraction)) -> HiddenSide.Right
            else -> null
        }
    }

    private fun showRestoreIndicator(petFiles: CodexPetFiles, side: HiddenSide, petY: Int) {
        removeRestoreIndicator(petFiles.pet.id)
        val indicatorWidth = resources.displayMetrics.density.times(indicatorWidthDp).roundToInt()
        val indicatorHeight = resources.displayMetrics.density.times(indicatorHeightDp).roundToInt()
        val screenHeight = resources.displayMetrics.heightPixels
        PetRestoreIndicatorView(this, side) { restorePet(petFiles.pet.id) }.also { view ->
            restoreIndicatorViews[petFiles.pet.id] = view
            windowManager.addView(
                view,
                WindowManager.LayoutParams(
                    indicatorWidth,
                    indicatorHeight,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = if (side == HiddenSide.Left) 0 else resources.displayMetrics.widthPixels - indicatorWidth
                    y = petY.coerceIn(indicatorMarginPx, screenHeight - indicatorHeight - indicatorMarginPx)
                },
            )
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(indicatorTransitionDurationMillis).start()
        }
    }

    private fun restorePet(petId: String) {
        val petFiles = hiddenPetFiles[petId] ?: return
        val indicator = restoreIndicatorViews[petId] ?: return
        val side = indicator.side
        val indicatorParams = indicator.layoutParams as WindowManager.LayoutParams
        showPet(
            petFiles = petFiles,
            appearingFrom = side,
            removeRestoreIndicator = false,
        )
        overlayViews[petFiles.pet.id]?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.x = if (side == HiddenSide.Left) 0 else resources.displayMetrics.widthPixels - params.width
            params.y = indicatorParams.y.coerceIn(
                indicatorMarginPx,
                resources.displayMetrics.heightPixels - params.height - indicatorMarginPx,
            )
            windowManager.updateViewLayout(view, params)
        }
        indicator.animate()
            .alpha(0f)
            .setDuration(indicatorTransitionDurationMillis)
            .withEndAction {
                if (restoreIndicatorViews[petId] === indicator) {
                    restoreIndicatorViews.remove(petId)
                    hiddenPetFiles.remove(petId)
                    windowManager.removeView(indicator)
                }
            }
            .start()
        updateActivePets()
    }

    private fun removePet(
        petId: String,
        updateState: Boolean = true,
        removeIndicator: Boolean = true,
    ) {
        overlayViews.remove(petId)?.let { windowManager.removeView(it) }
        if (removeIndicator) {
            removeRestoreIndicator(petId)
            hiddenPetFiles.remove(petId)
        }
        if (updateState) updateActivePets()
    }

    private fun removeRestoreIndicator(petId: String) {
        restoreIndicatorViews.remove(petId)?.let { windowManager.removeView(it) }
    }

    private fun updateActivePets() {
        PetOverlayController.setActivePetIds(this, overlayViews.keys.toSet())
        getSystemService(NotificationManager::class.java).notify(notificationId, createNotification())
    }

    private fun createNotification(): Notification {
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
            .setContentTitle("${overlayViews.size}마리 펫 표시 중")
            .setContentText("Codex 펫 화면 오버레이가 실행 중입니다.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val actionShow = "kr.co.edoli.edolitool.action.SHOW_PET"
        const val actionHide = "kr.co.edoli.edolitool.action.HIDE_PET"
        const val actionUpdateSize = "kr.co.edoli.edolitool.action.UPDATE_PET_SIZE"
        const val channelId = "pet_overlay"
        const val notificationId = 1101
        const val hiddenFraction = 2f / 3f
        const val indicatorWidthDp = 24
        const val indicatorHeightDp = 72
        const val indicatorMarginPx = 16
        const val petTransitionDurationMillis = 160L
        const val indicatorTransitionDurationMillis = 160L
    }
}

private enum class HiddenSide {
    Left,
    Right,
}

private class PetSpriteView(
    context: Context,
    private val spritesheet: Bitmap,
    private val isSmoothDragEnabled: () -> Boolean,
    private val smoothDragFollowFraction: () -> Float,
    private val onDragFinished: (WindowManager.LayoutParams) -> Unit,
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
    private var targetWindowX = 0
    private var targetWindowY = 0
    private var isSmoothFollowerRunning = false
    private var isSlidingLook = false
    private var slidingLookFrames = intArrayOf(4, 5, 4, 3)
    private var slidingLookIndex = 0
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
    private val smoothFollower = object : Runnable {
        override fun run() {
            val windowManager = context.getSystemService(WindowManager::class.java)
            val params = layoutParams as? WindowManager.LayoutParams ?: run {
                isSmoothFollowerRunning = false
                return
            }
            val horizontalDistance = targetWindowX - params.x
            val verticalDistance = targetWindowY - params.y
            params.x = moveTowards(params.x, targetWindowX, horizontalDistance)
            params.y = moveTowards(params.y, targetWindowY, verticalDistance)
            windowManager.updateViewLayout(this@PetSpriteView, params)
            if (params.x == targetWindowX && params.y == targetWindowY) {
                isSmoothFollowerRunning = false
                stopSlidingLook()
            } else {
                handler.postDelayed(this, smoothDragFrameDurationMillis)
            }
        }
    }
    private val slidingLook = object : Runnable {
        override fun run() {
            slidingLookIndex = (slidingLookIndex + 1) % slidingLookFrames.size
            invalidate()
            handler.postDelayed(this, 120L)
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
        val lookFrame = if (isSlidingLook) slidingLookFrames[slidingLookIndex] else null
        val spriteColumn = lookFrame?.rem(8) ?: frameIndex
        val source = Rect(
            spriteColumn * cellWidth,
            (if (lookFrame == null) animation.row else if (lookFrame < 8) 9 else 10) * cellHeight,
            (spriteColumn + 1) * cellWidth,
            (if (lookFrame == null) animation.row + 1 else if (lookFrame < 8) 10 else 11) * cellHeight,
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
                targetWindowX = params.x
                targetWindowY = params.y
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
                updateWindowPosition(
                    windowManager = windowManager,
                    params = params,
                    targetX = downWindowX + horizontalDistance.roundToInt(),
                    targetY = downWindowY + verticalDistance.roundToInt(),
                )
                lastRawX = event.rawX
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                when {
                    isDragging -> {
                        isDragging = false
                        val endParams = if (isSmoothDragEnabled()) {
                            WindowManager.LayoutParams().apply {
                                width = params.width
                                x = targetWindowX
                                y = targetWindowY
                            }
                        } else {
                            params
                        }
                        onDragFinished(endParams)
                        if (isSmoothDragEnabled() && isSmoothFollowerRunning) {
                            startSlidingLook(targetWindowX - params.x, targetWindowY - params.y)
                        } else {
                            play(PetAnimation.Idle)
                        }
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

    private fun updateWindowPosition(
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
        targetX: Int,
        targetY: Int,
    ) {
        if (isSmoothDragEnabled()) {
            targetWindowX = targetX
            targetWindowY = targetY
            if (!isSmoothFollowerRunning) {
                isSmoothFollowerRunning = true
                handler.post(smoothFollower)
            }
        } else {
            handler.removeCallbacks(smoothFollower)
            isSmoothFollowerRunning = false
            params.x = targetX
            params.y = targetY
            windowManager.updateViewLayout(this, params)
        }
    }

    private fun moveTowards(current: Int, target: Int, distance: Int): Int {
        if (abs(distance) <= smoothDragArrivalDistancePx / smoothDragFollowFraction()) return target
        return current + (distance * smoothDragFollowFraction()).roundToInt()
    }

    private fun startSlidingLook(horizontalDistance: Int, verticalDistance: Int) {
        isSlidingLook = true
        val degrees = Math.toDegrees(atan2(horizontalDistance.toDouble(), -verticalDistance.toDouble()))
        val baseDirection = ((degrees / 22.5).roundToInt() + 16) % 16
        slidingLookFrames = intArrayOf(
            baseDirection,
            (baseDirection + 1) % 16,
            baseDirection,
            (baseDirection + 15) % 16,
        )
        slidingLookIndex = 0
        handler.removeCallbacks(advanceAnimation)
        handler.removeCallbacks(slidingLook)
        handler.post(slidingLook)
        invalidate()
    }

    private fun stopSlidingLook() {
        if (!isSlidingLook) return
        isSlidingLook = false
        handler.removeCallbacks(slidingLook)
        play(PetAnimation.Idle)
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
        const val smoothDragFrameDurationMillis = 16L
        const val smoothDragArrivalDistancePx = 1
    }
}

private class PetRestoreIndicatorView(
    context: Context,
    val side: HiddenSide,
    private val onRestore: () -> Unit,
) : View(context) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 30, 30, 30)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), width / 2f, width / 2f, backgroundPaint)
        val centerX = width / 2f
        val centerY = height / 2f
        val chevronFullSize = width * chevronSizeFraction
        val chevronHalfSize = chevronFullSize / 2f
        arrowPaint.strokeWidth = width * chevronStrokeFraction
        val chevron = Path().apply {
            if (side == HiddenSide.Left) {
                moveTo(centerX - chevronHalfSize, centerY - chevronFullSize)
                lineTo(centerX + chevronHalfSize, centerY)
                lineTo(centerX - chevronHalfSize, centerY + chevronFullSize)
            } else {
                moveTo(centerX + chevronHalfSize, centerY - chevronFullSize)
                lineTo(centerX - chevronHalfSize, centerY)
                lineTo(centerX + chevronHalfSize, centerY + chevronFullSize)
            }
        }
        canvas.drawPath(chevron, arrowPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) onRestore()
        return true
    }

    private companion object {
        const val chevronSizeFraction = 0.25f
        const val chevronStrokeFraction = 0.08f
    }
}

internal enum class PetAnimation(
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
