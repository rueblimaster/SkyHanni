package at.hannibal2.skyhanni.events.render.gui

import at.hannibal2.skyhanni.api.event.RenderingSkyHanniEvent
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen

/**
 * Renders only while inside an inventory, a sign, chat or the game menu.
 * Gets grayed out while in an inventory.
 */
class ScreenDrawnEvent(context: GuiGraphics, val gui: Screen?) : RenderingSkyHanniEvent(context)
