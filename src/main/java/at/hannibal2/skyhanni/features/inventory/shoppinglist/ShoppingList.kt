package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierArguments
import at.hannibal2.skyhanni.config.commands.brigadier.arguments.InternalNameArgumentType
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.NeuInternalName

@SkyHanniModule
object ShoppingList {

    val items = mutableListOf<ShoppingListItem>()

    private fun getItemOrNull(internalName: NeuInternalName) = items.firstOrNull { it.internalName == internalName }

    fun add(internalName: NeuInternalName, amount: Int) {
        val item = getItemOrNull(internalName)
        if (item == null) {
            items.add(ShoppingListItem(internalName, amount))
            ChatUtils.chat("Added item ${internalName.itemNameWithoutColor} with amount $amount.")
        } else {
            item.amount += amount
            ChatUtils.chat("Increased amount of item ${internalName.itemNameWithoutColor} by $amount.")
        }
    }

    fun remove(internalName: NeuInternalName, amount: Int?) {
        val item = getItemOrNull(internalName)
        if (item == null) {
            ChatUtils.chat("Item ${internalName.itemNameWithoutColor} not found.")
            return
        }
        if (amount != null) {
            item.amount -= amount
            if (item.amount <= 0) {
                items.remove(item)
                ChatUtils.chat("Removed item ${internalName.itemNameWithoutColor} from Shopping List.")
            } else {
                ChatUtils.chat("Reduced amount of item ${internalName.itemNameWithoutColor} by $amount.")
            }
        } else {
            items.remove(item)
            ChatUtils.chat("Removed item ${internalName.itemNameWithoutColor} from Shopping List.")
        }
    }

    fun clear() {
        items.clear()
        ChatUtils.chat("Cleared Shopping List.")
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.registerBrigadier("shshoppinglist") {
            description = "Shopping List commands."
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("shsl")
            literal("add") {
                description = "Add items to the Shopping List."
                arg("item", InternalNameArgumentType.internalName(false)) {
                    arg("amount", BrigadierArguments.integer()) {
                        callback { add(getArgByName("item"), getArgByName("amount")) }
                    }
                    callback { add(getArgByName("item"), 1) }
                }
            }
            literal("remove") {
                description = "Remove items from the Shopping List."
                arg("item", InternalNameArgumentType.internalName(false)) {
                    arg("amount", BrigadierArguments.integer()) {
                        callback { remove(getArgByName("item"), getArgByName("amount")) }
                    }
                    callback { remove(getArgByName("item"), null) }
                }
            }
            literalCallback("clear") { clear() }
        }
    }
}
