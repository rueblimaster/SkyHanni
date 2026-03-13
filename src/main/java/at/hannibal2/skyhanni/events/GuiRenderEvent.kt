package at.hannibal2.skyhanni.events

import at.hannibal2.skyhanni.api.event.RenderingSkyHanniEvent
import net.minecraft.client.gui.GuiGraphics

sealed class GuiRenderEvent(context: GuiGraphics) : RenderingSkyHanniEvent(context) {

    /**
     * Renders only while inside an inventory.
     */
    class ChestGuiOverlayRenderEvent(context: GuiGraphics) : GuiRenderEvent(context)

    /**
     * Renders always, and while in any inventory etc. that grays out the background also gets grayed out.
     */
    class GuiOverlayRenderEvent(context: GuiGraphics) : GuiRenderEvent(context)

    /**
     * Renders as [GuiOverlayRenderEvent] if not inside an inventory and runs as [ChestGuiOverlayRenderEvent] when inside an inventory.
     */
    class GuiOnTopRenderEvent(context: GuiGraphics) : RenderingSkyHanniEvent(context)
    // This is intentional not an [GuiRenderEvent] since it will cause double renders
}
