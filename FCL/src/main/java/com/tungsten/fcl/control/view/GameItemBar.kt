package com.tungsten.fcl.control.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.mio.datastore.GameItemBarSetting
import com.mio.datastore.gameItemBarDataStore
import com.tungsten.fcl.control.GameItemBarSettingDialog
import com.tungsten.fcl.control.GameMenu
import com.tungsten.fcl.setting.GameOption
import com.tungsten.fcl.setting.GameOption.GameOptionListener
import com.tungsten.fclauncher.keycodes.FCLKeycodes
import com.tungsten.fclauncher.keycodes.MinecraftKeyBindingMapper
import kotlinx.coroutines.launch

class GameItemBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // =========================================================
    // Fields
    // =========================================================
    private lateinit var gameMenu:   GameMenu
    private lateinit var gameOption: GameOption

    // ✅ FIX 1: @Volatile for thread visibility
    @Volatile
    var optionListener: GameOptionListener? = null
        private set

    private var restore:                  Runnable? = null
    private var lastMovePosition:         Int       = 0
    private var lastClickTime:            Long      = 0L
    private var lastClickPosition:        Int       = 0
    private var isTwoFingerGestureActive: Boolean   = false
    private var twoFingerStartY:          Float     = 0f

    // =========================================================
    // Constants
    // =========================================================
    private val swipeDistanceThreshold = 200f
    private val doubleClickThresholdMs = 200L
    private val colorHighlight         = -0x7f010000
    private val colorTransparent       = 0x00000000
    private val restoreDelayMs         = 1500L

    // =========================================================
    // Settings
    // =========================================================
    private var setting: GameItemBarSetting? = null

    // =========================================================
    // Init
    // =========================================================
    init {
        isClickable = true
        isFocusable = true
    }

    // =========================================================
    // Setup
    // =========================================================

    fun setup(gameMenu: GameMenu, gameOption: GameOption) {
        this.gameMenu   = gameMenu
        this.gameOption = gameOption

        val scaleFactor = gameMenu.bridge?.scaleFactor ?: 1.0
        val width  = (gameMenu.touchPad.width  * scaleFactor).toInt()
        val height = (gameMenu.touchPad.height * scaleFactor).toInt()

        // ✅ FIX 2: Local val 'l' - Kotlin smart cast fix
        // var field directly use karne se smart cast fail hota hai
        // Local val se compiler guarantee le sakta hai
        val l = GameOptionListener { manually: Boolean ->
            onOptionChanged(manually, width, height)
        }

        // Field mein store karo
        optionListener = l

        // ✅ FIX 3: Local val use karo - field nahi
        // Isse "could be mutated concurrently" error nahi aayega
        l.onOptionChanged(false)
        gameOption.addGameOptionListener(l)

        // DataStore collect karo
        gameMenu.activity.lifecycleScope.launch {
            context.gameItemBarDataStore.data.collect { data ->
                setting = data
            }
        }
    }

    // =========================================================
    // Option Changed
    // =========================================================

    private fun onOptionChanged(manually: Boolean, width: Int, height: Int) {
        val itemBarScale = gameMenu.menuSetting.itemBarScale
        val size = if (itemBarScale == 0) {
            gameOption.getGuiScale(width, height, 0) * 20
        } else {
            itemBarScale
        }

        notifySize(size)

        if (manually) {
            showHighlightThenRestore()
        }
    }

    private fun showHighlightThenRestore() {
        restore?.let { removeCallbacks(it) }

        setBackgroundColor(colorHighlight)

        val restoreRunnable = Runnable {
            setBackgroundColor(colorTransparent)
            restore = null
        }
        restore = restoreRunnable
        postDelayed(restoreRunnable, restoreDelayMs)
    }

    // =========================================================
    // Size
    // =========================================================

    fun notifySize(size: Int) {
        post {
            val params = layoutParams
            params.width  = size * 9
            params.height = size
            setLayoutParams(params)
        }
    }

    // =========================================================
    // Touch Events
    // =========================================================

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                handleActionDown(event)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                handlePointerDown(event)
            }

            MotionEvent.ACTION_MOVE -> {
                handleActionMove(event)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (event.pointerCount <= 1) {
                    isTwoFingerGestureActive = false
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount < 2) {
                    isTwoFingerGestureActive = false
                }
            }
        }
        return true
    }

    // =========================================================
    // Touch Handlers
    // =========================================================

    private fun handleActionDown(event: MotionEvent) {
        if (event.pointerCount != 1) return
        runIfInPosition(event) { position ->
            sendHotbarKey(position)
            handleDoubleClick(position)
        }
    }

    private fun handlePointerDown(event: MotionEvent) {
        if (event.pointerCount == 2) {
            isTwoFingerGestureActive = true
            twoFingerStartY = event.getY(0)
        }
    }

    private fun handleActionMove(event: MotionEvent) {
        when {
            event.pointerCount == 1 -> {
                if (setting?.slideSelection == true) {
                    runIfInPosition(event) { position ->
                        if (lastMovePosition != position) {
                            sendHotbarKey(position)
                        }
                        lastMovePosition = position
                    }
                }
            }
            event.pointerCount >= 2 && isTwoFingerGestureActive -> {
                val currentY = event.getY(0)
                val deltaY   = twoFingerStartY - currentY
                if (deltaY > swipeDistanceThreshold) {
                    showSettingDialog()
                    isTwoFingerGestureActive = false
                }
            }
        }
    }

    // =========================================================
    // Double Click
    // =========================================================

    private fun handleDoubleClick(position: Int) {
        val currentTime  = System.currentTimeMillis()
        val isDoubleClick = (currentTime - lastClickTime < doubleClickThresholdMs)
                         && (position == lastClickPosition)

        if (isDoubleClick) {
            if (setting?.doubleTapSwapHands == true) {
                swapHands()
            }
            lastClickTime     = 0L
            lastClickPosition = 0
        } else {
            lastClickTime     = currentTime
            lastClickPosition = position
        }
    }

    // =========================================================
    // Setting Dialog
    // =========================================================

    private fun showSettingDialog() {
        if (!::gameMenu.isInitialized) return

        // ✅ FIX 4: Local val - setting smart cast fix
        val currentSetting = setting ?: return

        GameItemBarSettingDialog(context, currentSetting) { result ->
            gameMenu.activity.lifecycleScope.launch {
                context.gameItemBarDataStore.updateData { existing ->
                    existing.copy(
                        slideSelection     = result.slideSelection,
                        doubleTapSwapHands = result.doubleTapSwapHands
                    )
                }
            }
        }.show()
    }

    // =========================================================
    // Position Helper
    // =========================================================

    fun runIfInPosition(e: MotionEvent, func: (position: Int) -> Unit) {
        val viewHeight = height
        if (viewHeight <= 0) return

        if (e.x >= 0f && e.x <= 9f * viewHeight) {
            val position = ((e.x / viewHeight).toInt() + 1).coerceIn(1, 9)
            func(position)
        }
    }

    // =========================================================
    // Key Events
    // =========================================================

    private fun swapHands() {
        sendBoundKey(
            MinecraftKeyBindingMapper.BINDING_SWAP_HANDS,
            FCLKeycodes.KEY_F
        )
    }

    fun dropItem() {
        sendBoundKey(
            MinecraftKeyBindingMapper.BINDING_DROP,
            FCLKeycodes.KEY_Q
        )
    }

    private fun sendHotbarKey(position: Int) {
        val (binding, keycode) = getHotbarBinding(position)
        sendBoundKey(binding, keycode)
    }

    private fun getHotbarBinding(position: Int): Pair<String, Int> {
        return when (position) {
            1 -> MinecraftKeyBindingMapper.BINDING_HOTBAR_1 to FCLKeycodes.KEY_1
            2 -> MinecraftKeyBindingMapper.BINDING_HOTBAR_2 to FCLKeycodes.KEY_2
            3 -> MinecraftKeyBindingMapper.BINDING_HOTBAR_3 to FCLKeycodes.KEY_3
            4 -> MinecraftKeyBindingMapper.BINDING_HOTBAR_4 to FCLKeycodes.KEY_4
            5 -> MinecraftKeyBindingMapper.BINDING_HOTBAR_5 to FCLKeycodes.KEY_5
            6 -> MinecraftKeyBindingMapper.BINDING_HOTBAR_6 to FCLKeycodes.KEY_6
            7 -> MinecraftKeyBindingMapper.BINDING_HOTBAR_7 to FCLKeycodes.KEY_7
            8 -> MinecraftKeyBindingMapper.BINDING_HOTBAR_8 to FCLKeycodes.KEY_8
            9 -> MinecraftKeyBindingMapper.BINDING_HOTBAR_9 to FCLKeycodes.KEY_9
            else -> MinecraftKeyBindingMapper.BINDING_HOTBAR_1 to FCLKeycodes.KEY_1
        }
    }

    private fun sendBoundKey(binding: String, keycode: Int) {
        gameMenu.input.sendBoundKeyEvent(gameOption, binding, keycode, true)
        gameMenu.input.sendBoundKeyEvent(gameOption, binding, keycode, false)
    }

    // =========================================================
    // Cleanup
    // =========================================================

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        // ✅ FIX 5: Local val 'l' - Smart cast fix for cleanup
        // optionListener directly use karne se error aata hai
        // Local val se freeze hoti hai value
        val l = optionListener
        if (l != null) {
            if (::gameOption.isInitialized) {
                gameOption.removeGameOptionListener(l)
            }
            optionListener = null
        }

        // Pending restore cancel karo
        restore?.let {
            removeCallbacks(it)
            restore = null
        }
    }
}
