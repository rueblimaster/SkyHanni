package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.PrimitiveItemStack.Companion.toPrimitiveStackOrNull

@SkyHanniModule
object RecipeInventory {
    var currentlyOpenRecipe: PrimitiveRecipe? = null

    private fun InventoryFullyOpenedEvent.isRecipe() = inventoryName.contains("Recipe") && inventorySize == 54

    @HandleEvent(onlyOnSkyblock = true)
    fun onInventorOpen(event: InventoryFullyOpenedEvent) {
        if (!event.isRecipe()) {
            currentlyOpenRecipe = null
            return
        }

        val ingredients: Set<PrimitiveIngredient> = listOf(10, 11, 12, 19, 20, 21, 28, 29, 30).mapNotNull {
            event.inventoryItemsWithNull[it]?.toPrimitiveStackOrNull()?.toPrimitiveIngredient()
        }.toSet()

        val output: PrimitiveIngredient = event.inventoryItems[25]?.toPrimitiveStackOrNull()?.toPrimitiveIngredient() ?: return

        currentlyOpenRecipe = PrimitiveRecipe(ingredients, setOf(output), RecipeType.CRAFTING)
    }

    @HandleEvent(onlyOnSkyblock = true, eventType = InventoryCloseEvent::class)
    fun onInventoryClose() {
        currentlyOpenRecipe = null
    }
}
