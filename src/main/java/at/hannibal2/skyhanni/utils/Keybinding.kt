package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.events.IslandChangeEvent
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
    val requireSeparateTaps: Boolean = true, // TODO: define
    val name: String? = null, // this is used for debugging and logging
) {
    private var keyCode: Int = keyCodeProvider()
        get() {
            if (keyCodeProvider() != field) {
                field = keyCodeProvider()
                updateActiveState()
            }
            return field
        }

    private val keybindingType: KeybindingType?
        get() = when {
            keyCode < 0 -> KeybindingType.MOUSE
            keyCode in 1 until Keyboard.KEYBOARD_SIZE -> KeybindingType.KEYBOARD
            else -> null
        }

    private var lastTimeActiveChecked: SimpleTimeMark = SimpleTimeMark.farPast()
    var active: Boolean = false
        get() {
            if (lastTimeActiveChecked.passedSince() > 10.seconds) {
                updateActiveState()
            }
            return field
        }
        private set

    private var lastTimeExecuted: SimpleTimeMark = SimpleTimeMark.farPast()
    private var lastTimePressed: SimpleTimeMark = SimpleTimeMark.farPast()
    private var lastTimeUnpressed: SimpleTimeMark = SimpleTimeMark.now()

    init {
        addKeyBinding(this)
    }

    override fun toString(): String = if (name != null) {
        "Keybinding(name='$name', keyCode=$keyCode, keybindingType=$keybindingType, " +
            "active=$active, lastTimeActiveChecked=$lastTimeActiveChecked, lastTimeExecuted=$lastTimeExecuted)"
    } else {
        "Keybinding(keyCode=$keyCode, keybindingType=$keybindingType, active=$active, " +
            "lastTimeActiveChecked=$lastTimeActiveChecked, lastTimeExecuted=$lastTimeExecuted)"
    }

    private fun isKeyDown(): Boolean = when (keybindingType) {
        KeybindingType.MOUSE -> MouseCompat.isButtonDown(keyCode + 100)
        KeybindingType.KEYBOARD -> Keyboard.isKeyDown(keyCode)
        null -> false
    }.also {
        if (it) lastTimePressed = SimpleTimeMark.now() else lastTimeUnpressed = SimpleTimeMark.now()
    }

    fun checkCondition() = condition?.invoke() ?: true
    fun checkInstantCondition() = instantCondition?.invoke() ?: true

    fun isActive() = active

    private fun checkIsActive(): Boolean {
        if (keybindingType == null) return false
        if (onlyOnIsland != IslandType.ANY && !onlyOnIsland.isInIsland()) return false
        if (onlyOnIslands.isNotEmpty() && !onlyOnIslands.any { it.isInIsland() }) return false
        return checkCondition()
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

    private fun isKeyTapped(): Boolean = (!requireSeparateTaps || lastTimeUnpressed > lastTimePressed) && isKeyDown()

    private fun onTick() {
        if (active && checkInstantCondition() && isKeyTapped() && !isOnCooldown()) {
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

        private val keybindings = mutableSetOf<Keybinding>()

        private fun addKeyBinding(keybinding: Keybinding) {
            keybindings.add(keybinding)
            keybinding.updateActiveState()
        }

        @HandleEvent(eventTypes = [ConfigLoadEvent::class, WorldChangeEvent::class, IslandChangeEvent::class, GraphAreaChangeEvent::class])
        fun updateActiveStates() {
            keybindings.forEach { it.updateActiveState() }
        }

        @HandleEvent
        fun onTick(event: SkyHanniTickEvent) {
            keybindings.forEach { it.onTick() }
        }

        @HandleEvent
        fun onDebug(event: DebugDataCollectEvent) {
            val activeKeybindings = keybindings.filter { it.active }
            val nullKeybindings = keybindings.filter { it.keybindingType == null }
            val inactiveKeybindings = keybindings.filter {
                !activeKeybindings.contains(it) && !nullKeybindings.contains(it)
            }

            event.title("Keybindings")
            event.addIrrelevant {
                add("Total of ${keybindings.size} keybindings")
                if (activeKeybindings.isNotEmpty()) {
                    add("${activeKeybindings.size} active keybindings:")
                    activeKeybindings.forEach {
                        add(it.toString())
                    }
                }
                if (nullKeybindings.isNotEmpty()) {
                    add("${nullKeybindings.size} keybindings without a set key:")
                    nullKeybindings.forEach {
                        add(it.toString())
                    }
                }
                if (inactiveKeybindings.isNotEmpty()) {
                    add("${inactiveKeybindings.size} inactive keybindings:")
                    inactiveKeybindings.forEach {
                        add(it.toString())
                    }
                }
            }
        }

        @HandleEvent
        fun onCommandRegistration(event: CommandRegistrationEvent) {
            event.register("shreloadkeybindings") {
                description = "Reloads the active state of all keybindings"
                category = CommandCategory.USERS_BUG_FIX
                callback {
                    val oldActiveKeyBindings = keybindings.filter { keybinding -> keybinding.active }
                    updateActiveStates()
                    val newActiveKeyBindings = keybindings.filter { keybinding -> keybinding.active }
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
    }
}
