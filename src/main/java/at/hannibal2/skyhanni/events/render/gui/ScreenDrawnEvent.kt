package at.hannibal2.skyhanni.events.render.gui

import at.hannibal2.skyhanni.api.event.RenderingSkyHanniEvent
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen

/**
 * Render event for the foreground of inventories, signs, chat and the game menu.
 *
 * Renders only while inside an inventory, a sign, chat or the game menu.
 * While in an inventory it renders a bit darker, gray
 */
class ScreenDrawnEvent(context: GuiGraphics, val gui: Screen?) : RenderingSkyHanniEvent(context)
