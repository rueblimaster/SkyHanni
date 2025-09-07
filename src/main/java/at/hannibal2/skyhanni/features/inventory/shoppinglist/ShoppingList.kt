package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierArguments
import at.hannibal2.skyhanni.config.commands.brigadier.arguments.InternalNameArgumentType
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConditionalUtils.afterChange
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.RenderDisplayHelper
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.add
import at.hannibal2.skyhanni.utils.renderables.Renderable

@SkyHanniModule
object ShoppingList {
    val config get() = SkyHanniMod.feature.inventory.shoppingList

    val items = mutableMapOf<NeuInternalName, ShoppingListItem>()

    private var display = listOf<Renderable>()

    fun update() {
        buildDisplay()
    }

    private fun Double.clean(): String =
        if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()

    fun add(internalName: NeuInternalName, amount: Double) {
        val item = items[internalName]
        if (item == null) {
            items.add(internalName to ShoppingListItem(internalName, amount))
            ChatUtils.chat("Added item '${internalName.itemNameWithoutColor}' with amount ${amount.clean()}.")
        } else {
            item.amount += amount
            ChatUtils.chat("Increased amount of item '${internalName.itemNameWithoutColor}' by ${amount.clean()}.")
        }
        update()
    }

    fun remove(internalName: NeuInternalName, amount: Double?) {
        val item = items[internalName]
        if (item == null) {
            ChatUtils.chat("Item '${internalName.itemNameWithoutColor}' not found.")
            return
        }
        if (amount != null) {
            item.amount -= amount
            if (item.amount <= 0) {
                items.remove(internalName)
                ChatUtils.chat("Removed item '${internalName.itemNameWithoutColor}' from Shopping List.")
            } else {
                ChatUtils.chat("Reduced amount of item '${internalName.itemNameWithoutColor}' by ${amount.clean()}.")
            }
        } else {
            items.remove(internalName)
            ChatUtils.chat("Removed item '${internalName.itemNameWithoutColor}' from Shopping List.")
        }
        update()
    }

    fun clear() {
        items.clear()
        ChatUtils.chat("Cleared Shopping List.")
        update()
    }

    private fun buildDisplay() {
        display = items.values.flatMap(ShoppingListItem::buildDisplay)
    }

    init {
        RenderDisplayHelper(
            outsideInventory = true,
            inOwnInventory = true,
            condition = ::isEnabled,
        ) {
            config.position.renderRenderables(display, posLabel = "Shopping List")
        }
    }

    private fun isEnabled(): Boolean = SkyBlockUtils.inSkyBlock && config.enabled

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.registerBrigadier("shshoppinglist") {
            description = "Shopping List commands."
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("shsl")
            literal("add") {
                description = "Add items to the Shopping List."
                arg("item", InternalNameArgumentType.itemName(isGreedy = false)) {
                    arg("amount", BrigadierArguments.double()) {
                        callback { add(getArgByName("item"), getArgByName<Double>("amount")) }
                    }
                    callback { add(getArgByName("item"), 1.0) }
                }
            }
            literal("remove") {
                description = "Remove items from the Shopping List."
                arg("item", InternalNameArgumentType.itemName(isGreedy = false)) {
                    arg("amount", BrigadierArguments.double()) {
                        callback { remove(getArgByName("item"), getArgByName<Double>("amount")) }
                    }
                    callback { remove(getArgByName("item"), null) }
                }
            }
            literalCallback("clear") { clear() }
            literalCallback("update") { update() }
        }
    }

    @HandleEvent
    fun onSecondPassed() {
        update()
    }

    @HandleEvent
    fun onConfigLoad() {
        config.itemFormat.afterChange {
            update()
        }
        update()
    }
}
