package at.hannibal2.skyhanni.events.render.gui

import at.hannibal2.skyhanni.api.event.RenderingSkyHanniEvent
import net.minecraft.client.gui.GuiGraphics

/**
 * Render event for the background of inventories, signs and the game menu.
 *
 * Renders only while inside an inventory, a sign or the game menu (any inventory etc. that grays out the background)
 */
class DrawBackgroundEvent(context: GuiGraphics) : RenderingSkyHanniEvent(context)
