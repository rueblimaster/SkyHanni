package at.hannibal2.skyhanni.events

import at.hannibal2.skyhanni.api.event.RenderingSkyHanniEvent
import net.minecraft.client.gui.GuiGraphics

sealed class GuiRenderEvent(context: GuiGraphics) : RenderingSkyHanniEvent(context) {

    /**
     * Render event for inside inventories.
     *
     * Renders only while inside an inventory.
     * Will override ScreenDrawnEvent but not render anything inside of a sign!
     */
    class ChestGuiOverlayRenderEvent(context: GuiGraphics) : GuiRenderEvent(context)

    /**
     * Render event for everywhere else.
     *
     * Renders always, and while in an inventory it renders a bit darker, gray.
     */
    class GuiOverlayRenderEvent(context: GuiGraphics) : GuiRenderEvent(context)

    /**
     * Renders as [GuiOverlayRenderEvent] if not inside an inventory and runs as [ChestGuiOverlayRenderEvent] when inside an inventory.
     */
    class GuiOnTopRenderEvent(context: GuiGraphics) : RenderingSkyHanniEvent(context)
    // This is intentional not an [GuiRenderEvent] since it will cause double renders
}
