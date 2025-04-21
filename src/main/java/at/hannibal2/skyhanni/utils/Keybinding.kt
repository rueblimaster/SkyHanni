package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
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
    val keyCodeProvider: () -> Int, // this may range from -100 to keyboard.KEYBOARD_SIZE
    val functionToExecute: () -> Unit,
    val cooldown: Duration = 2.seconds,
    val condition: (() -> Boolean)? = null,
    val instantCondition: (() -> Boolean)? = { Minecraft.getMinecraft().currentScreen == null && !NeuItems.neuHasFocus() },
    val onlyOnIsland: IslandType = IslandType.ANY,
    vararg val onlyOnIslands: IslandType = arrayOf(),
) {
    private var keyCode: Int = keyCodeProvider()
        get() {
            if (keyCodeProvider() != field) {
                field = keyCodeProvider()
                reloadKeybindingType()
            }
            return field
        }

    private var keybindingType: KeybindingType? = null

    private var lastTimeActiveChecked: SimpleTimeMark = SimpleTimeMark.farPast()
    var active: Boolean = false
        get() {
            if (lastTimeActiveChecked.passedSince() > 10.seconds) {
                updateActiveState()
            }
            return field
        }
        private set(value) {
            field = value
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

    override fun toString(): String {
        return "Keybinding(keyCode=$keyCode, keybindingType=$keybindingType, active=$active, " +
            "lastTimeActiveChecked=$lastTimeActiveChecked, lastTimeExecuted=$lastTimeExecuted)"
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

    fun checkCondition() = condition?.invoke() ?: true
    fun checkInstantCondition() = instantCondition?.invoke() ?: true

    fun reloadKeybindingType() {
        keybindingType = when {
            keyCode < 0 -> KeybindingType.MOUSE
            keyCode in 1 until Keyboard.KEYBOARD_SIZE -> KeybindingType.KEYBOARD
            else -> null
        }
    }

    fun isActive() = active

    private fun checkIsActive(): Boolean {
        if (keybindingType == null) return false
        if (onlyOnIsland != IslandType.ANY && !onlyOnIsland.isInIsland()) return false
        if (onlyOnIslands.isNotEmpty() && !onlyOnIslands.any { it.isInIsland() }) return false
        return checkCondition()
    }

    fun updateActiveState() {
        reloadKeybindingType()

        active = checkIsActive()
        lastTimeActiveChecked = SimpleTimeMark.now()
    }

    fun execute() {
        lastTimeExecuted = SimpleTimeMark.now()
        functionToExecute()
    }

    private fun isOnCooldown(): Boolean = lastTimeExecuted.passedSince() < cooldown

    private fun onTick() {
        if (checkInstantCondition() && isKeyDown() && !isOnCooldown()) {
            execute()
        }
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
            updateActiveKeybindings() // this is technically not needed, but it makes sure that activeKeybindings is up to date
        }

        private fun updateActiveKeybindings() {
            activeKeybindings.clear()
            keybindings.forEach { if (it.active) activeKeybindings.add(it) }
        }

        @HandleEvent
        fun onTick(event: SkyHanniTickEvent) {
            activeKeybindings.forEach { it.onTick() }
        }

        @HandleEvent
        fun onDebug(event: DebugDataCollectEvent) {
            event.title("Keybindings")
            event.addData {
                add("${activeKeybindings.size} active keybindings out of ${keybindings.size} keybindings")
                add("Active keybindings:")
                activeKeybindings.forEach {
                    add(it.toString())
                }
                add("Inactive Keybindings:")
                keybindings.forEach {
                    if (activeKeybindings.contains(it)) return@forEach
                    add(it.toString())
                }
            }
        }

        @HandleEvent
        fun onCommandRegistration(event: CommandRegistrationEvent) {
            event.register("shreloadkeybindings") {
                description = "Reloads the active state of all keybindings"
                category = CommandCategory.USERS_BUG_FIX
                callback {
                    val oldActiveKeyBindings = activeKeybindings.toList()
                    updateActiveStates()
                    val newActiveKeyBindings = activeKeybindings.toList()
                    val removedKeyBindings = oldActiveKeyBindings.filter { keybinding ->
                        keybinding !in newActiveKeyBindings
                    }
                    val addedKeyBindings = newActiveKeyBindings.filter { keybinding ->
                        keybinding !in oldActiveKeyBindings
                    }
                    ChatUtils.debug("Removed $removedKeyBindings")
                    ChatUtils.debug("Added $addedKeyBindings")
                    ChatUtils.chat("Reloaded keybindings")
                }
            }
        }

        // from here on downwards all the events on which the active state of the keybindings are updated
        @HandleEvent
        fun onConfigLoad(event: ConfigLoadEvent) {
            updateActiveStates()
        }

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
