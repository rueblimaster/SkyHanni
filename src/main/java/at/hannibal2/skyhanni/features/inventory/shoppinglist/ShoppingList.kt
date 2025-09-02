package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierArguments
import at.hannibal2.skyhanni.config.commands.brigadier.arguments.InternalNameArgumentType
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.NeuInternalName

@SkyHanniModule
object ShoppingList {

    fun add(internalName: NeuInternalName, amount: Int) {
        println("adding: $internalName $amount")
    }

    fun remove(internalName: NeuInternalName, amount: Int?) {
        println("removing: $internalName $amount")
    }

    fun clear() {
        println("clearing")
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
