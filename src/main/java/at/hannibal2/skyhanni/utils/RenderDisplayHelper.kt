package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.render.gui.ScreenDrawnEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen

/**
 * RenderDisplayHelper determines when to render displays based on
 * conditions and context, such as whether the player is in their inventory or
 * outside of an inventory GUI, or in an inventory defined by InventoryDetector.
 *
 * @property inventory set a InventoryDetector the display should be rendered in.
 * @property outsideInventory Specifies if the display should render when not inside any inventory.
 * @property inOwnInventory Specifies if the display should render when the player is in their own inventory.
 * @property inSign Specifies if the display should render when in a sign.
 * @property inChat Specifies if the display should render when in chat.
 * @property inIngameMenu Specifies if the display should render when in the Ingame Menu aka "Escape Menu".
 * @property condition Should the display be rendered at all? Insert the isEnabled() function here.
 * @property onRender This is getting called when the render should happen.
 */
class RenderDisplayHelper(
    private val inventory: InventoryDetector = NO_INVENTORY,
    private val outsideInventory: Boolean = false,
    private val inOwnInventory: Boolean = false,
    private val inSign: Boolean = false,
    private val inChat: Boolean = false,
    private val inIngameMenu: Boolean = false,
    private val condition: () -> Boolean,
    private val onlyOnIsland: IslandType? = null,
    private val onRender: () -> Unit,
) {

    init {
        // Registers the instance to the list of all display helpers.
        allDisplays.add(this)
    }

    @SkyHanniModule
    companion object {
        val NO_INVENTORY = InventoryDetector { false }
        val ANY_INVENTORY = InventoryDetector { true }
        private val allDisplays = mutableListOf<RenderDisplayHelper>()
        private var currentlyVisibleDisplays = emptyList<RenderDisplayHelper>()

        @HandleEvent
        fun onTick() {
            currentlyVisibleDisplays = allDisplays.filter { it.checkCondition() }
        }

        @HandleEvent(eventType = GuiRenderEvent.GuiOverlayRenderEvent::class)
        fun onOutsideRender() {
            if (InventoryUtils.inAnyScreen()) return // if we're inside an inventory etc. don't render anything
            currentlyVisibleDisplays.filter { it.outsideInventory }.map { it.render() }
        }

        @HandleEvent(eventType = GuiRenderEvent.ChestGuiOverlayRenderEvent::class)
        fun onInventoryRender() {
            if (InventoryUtils.inOwnInventory()) {
                currentlyVisibleDisplays.filter { it.inOwnInventory }.map { it.render() }
            } else if (InventoryUtils.inInventory()) {
                currentlyVisibleDisplays.filter { it.inventory.isInside() }.map { it.render() }
            } else return // not in an inventory
        }

        // TODO: fix
//         @HandleEvent(eventType = ScreenDrawnEvent::class)
//         fun onOtherRender() {
//             if (InventoryUtils.inSign()) {
//                 currentlyVisibleDisplays.filter { it.inSign }.map { it.render() }
//             } else if (InventoryUtils.inChat()) {
//                 currentlyVisibleDisplays.filter { it.inChat }.map { it.render() }
//             } else if (InventoryUtils.inIngameMenu()) {
//                 currentlyVisibleDisplays.filter { it.inIngameMenu }.map { it.render() }
//             } else return // unknown screen type
//         }
    }

    private fun checkCondition(): Boolean = try {
        condition() && checkIslandCondition()
    } catch (e: Exception) {
        ErrorManager.logErrorWithData(e, "Failed to check render display condition")
        false
    }

    private fun checkIslandCondition(): Boolean = onlyOnIsland == null || onlyOnIsland.isCurrent()

    private fun render() {
        try {
            onRender()
        } catch (e: Exception) {
            ErrorManager.logErrorWithData(e, "Failed to render a display")
        }
    }
}
