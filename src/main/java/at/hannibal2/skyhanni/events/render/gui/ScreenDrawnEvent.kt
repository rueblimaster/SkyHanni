package at.hannibal2.skyhanni.events.render.gui

import at.hannibal2.skyhanni.api.event.RenderingSkyHanniEvent
import at.hannibal2.skyhanni.utils.compat.DrawContext
import net.minecraft.client.gui.GuiScreen

/**
 * Renders only while inside an inventory, a sign, chat or the game menu
 * While in an inventory it renders a bit darker, gray
 * Use this for signs and chat
 */
class ScreenDrawnEvent(context: DrawContext, val gui: GuiScreen?) : RenderingSkyHanniEvent(context)
