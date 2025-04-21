package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.events.minecraft.WorldChangeEvent
import at.hannibal2.skyhanni.events.skyblock.GraphAreaChangeEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.compat.MouseCompat
import net.minecraft.client.Minecraft
import org.lwjgl.input.Keyboard
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Keybinding(
    // it would be an easy change to support modifieres etc. with this,
    // but it's not added as it's not really possible to set in config
    val keyCodeGet: () -> Int, // this may range from -100 to keyboard.KEYBOARD_SIZE
    val functionToExecute: () -> Unit,
    val cooldown: Duration = 2.seconds,
    val condition: () -> Boolean = { true },
    val guiCondition: () -> Boolean = { Minecraft.getMinecraft().currentScreen == null && !NeuItems.neuHasFocus() },
    val onlyOnIsland: IslandType = IslandType.ANY,
    vararg val onlyOnIslands: IslandType = arrayOf(),
) {
    private var keyCode: Int = keyCodeGet()
        get() = keyCodeGet()

    private val keybindingType: KeybindingType? = if (keyCode < 0) {
        KeybindingType.MOUSE
    } else if (keyCode < Keyboard.KEYBOARD_SIZE) {
        KeybindingType.KEYBOARD
    } else {
        ChatUtils.userError("Invalid keybind with keycode: $keyCode, key: ${keyCode.toChar()}")
        null
    }

    private var lastTimeActiveChecked: SimpleTimeMark = SimpleTimeMark.farPast()
    private var activeCache: Boolean = false
    var active: Boolean
        get() {
            if (lastTimeActiveChecked.passedSince() > 10.seconds) {
                updateActiveState()
            }
            return activeCache
        }
        private set(value) {
            activeCache = value
            if (value) {
                activeKeybindings.add(this)
            } else {
                activeKeybindings.remove(this)
            }
        }

    private var lastTimeExecuted: SimpleTimeMark = SimpleTimeMark.farPast()
    private var lastTimePressed: SimpleTimeMark = SimpleTimeMark.farPast()

    init {
        addKeyBinding(this)
    }

    private fun isKeyDown(): Boolean = when (keybindingType) {
        KeybindingType.MOUSE -> MouseCompat.isButtonDown(keyCode + 100)
        KeybindingType.KEYBOARD -> Keyboard.isKeyDown(keyCode)
        null -> false
    }.also {
        if (it) {
            lastTimePressed = SimpleTimeMark.now()
        }
    }

    fun isActive() = active

    private fun checkIsActive(): Boolean {
        if (keybindingType == null) return false
        if (onlyOnIsland != IslandType.ANY && !onlyOnIsland.isInIsland()) return false
        if (onlyOnIslands.isNotEmpty() && !onlyOnIslands.any { it.isInIsland() }) return false
        return condition()
    }

    fun updateActiveState() {
        active = checkIsActive()
        lastTimeActiveChecked = SimpleTimeMark.now()
    }

    fun execute() {
        lastTimeExecuted = SimpleTimeMark.now()
        functionToExecute()
    }

    private fun isOnCooldown(): Boolean = lastTimeExecuted.passedSince() < cooldown

    private fun onTick() {
        println("Keybinding $keyCode tick")
        if (guiCondition() && isKeyDown() && !isOnCooldown()) {
            println("Keybinding $keyCode pressed")
            execute()
        }
    }

    fun updateAllActiveStates() {
        updateActiveStates()
    }

    @SkyHanniModule
    companion object {
        fun List<Keybinding>.updateActiveStates() {
            forEach { it.updateActiveState() }
        }

        private enum class KeybindingType {
            MOUSE,
            KEYBOARD,
        }

        private val keybindings = mutableListOf<Keybinding>()
        private val activeKeybindings = mutableListOf<Keybinding>()

        private fun addKeyBinding(keybinding: Keybinding) {
            keybindings.add(keybinding)
            keybinding.updateActiveState()
            if (keybinding.active) {
                activeKeybindings.add(keybinding)
            }
        }

        fun updateActiveStates() {
            keybindings.forEach { it.updateActiveState() }
            updateActiveKeybindings() // this is technically not needed, but it makes sure the activeKeybindings list is up to date
        }

        private fun updateActiveKeybindings() {
            activeKeybindings.clear()
            keybindings.forEach { if (it.active) activeKeybindings.add(it) }
        }

        @HandleEvent
        fun onTick(event: SkyHanniTickEvent) {
            activeKeybindings.forEach { it.onTick() }
        }

        // from here on downwards all the events on which the active state of the keybindings are updated
        @HandleEvent
        fun onWorldChange(event: WorldChangeEvent) {
            updateActiveStates()
        }

        @HandleEvent
        fun onAreaChange(event: GraphAreaChangeEvent) {
            updateActiveStates()
        }
    }
}
