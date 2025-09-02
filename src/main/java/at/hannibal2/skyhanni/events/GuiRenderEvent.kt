package at.hannibal2.skyhanni.events

import at.hannibal2.skyhanni.api.event.RenderingSkyHanniEvent
import at.hannibal2.skyhanni.utils.compat.DrawContext

open class GuiRenderEvent(context: DrawContext) : RenderingSkyHanniEvent(context) {

    /**
     * Renders only while inside an inventory
     */
    class ChestGuiOverlayRenderEvent(context: DrawContext) : GuiRenderEvent(context)

    /**
     * Renders always, and while in any inventoy etc. that grays out the background also gets grayed out
     */
    class GuiOverlayRenderEvent(context: DrawContext) : GuiRenderEvent(context)

    /**
     * Renders as [GuiOverlayRenderEvent] if not inside an inventory and runs as [ChestGuiOverlayRenderEvent] when inside an inventory
     */
    class GuiOnTopRenderEvent(context: DrawContext) : RenderingSkyHanniEvent(context)
    // This is intentional not an [GuiRenderEvent] since it will cause double renders
}
