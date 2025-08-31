package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.render.gui.ReplaceItemEvent
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.setLore
import com.google.gson.annotations.Expose
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack

class RecipeResolver(
    @Expose
    val internalName: NeuInternalName,
    inputRecipe: PrimitiveRecipe? = null,
) {
    @Expose
    var recipe = inputRecipe
        private set
    var resolved = recipe != null

    val possibleRecipes: List<PrimitiveRecipe> =
        NeuItems.getRecipes(internalName).filter { it.isCraftingRecipe() }.filter { recipe ->
            !recipe.isRecursing() && !recipe.isRecursingCompacting()
        }.also {
            if (it.size == 1) {
                recipe = it[0]
                resolved = true
            }
        }

    private var currentCallback: ((Boolean) -> Unit)? = null
    private var displayItem: ItemStack? = null

    override fun toString(): String {
        return "RecipeResolver(${internalName.asString()}: recipe $recipe, currently getting decided: ${displayItem != null})"
    }

    /* To be used externaly to set the recipe */
    fun setRecipe(recipe: PrimitiveRecipe) {
        this.recipe = recipe
        resolved = true
        currentCallback?.invoke(true)
        currentCallback = null
        displayItem = null
    }

    fun decideRecipe(callback: (Boolean) -> Unit) {
        if (possibleRecipes.isEmpty()) {
            ChatUtils.chat("No recipes found for ${internalName.itemNameWithoutColor}")
            callback(false)
            return
        }

        if (possibleRecipes.size > 1) {
            ChatUtils.chat("Multiple recipes found for ${internalName.itemNameWithoutColor}\n§7Select one")

            launchResolving(callback)
        } else {
            recipe = possibleRecipes[0]
            resolved = true
            callback(true)
        }
    }

    private fun launchResolving(callback: (Boolean) -> Unit) {
        if (resolved) {
            callback(true)
            return
        }

        currentCallback = callback

        val lore = buildList {
            add("§8(From SkyHanni)")
        }

        displayItem = ItemStack(Blocks.diamond_block).setLore(lore).setStackDisplayName("§bSelect Recipe")

        currentlyDecidingRecipe = this

        HypixelCommands.viewRecipe(internalName)
    }

    private fun resolveToRecipe(recipe: PrimitiveRecipe) {
        this.recipe = recipe
        resolved = true
        currentCallback?.invoke(true)
        currentCallback = null
        displayItem = null
    }

    private fun cancelResolving() {
        currentCallback?.invoke(false)
        currentCallback = null
        displayItem = null
    }

    companion object {
        const val SLOT_ID = 43

        var currentlyDecidingRecipe: RecipeResolver? = null
            set(value) {
                field?.cancelResolving() // Cancel the previous recipe resolving
                field = value
            }

        @HandleEvent(onlyOnSkyblock = true)
        fun replaceItem(event: ReplaceItemEvent) {
            val displayItem = currentlyDecidingRecipe?.displayItem ?: return
            if (RecipeInventory.currentlyOpenRecipe == null) return
            if (event.inventory is InventoryPlayer) return
            if (event.slot != SLOT_ID) return

            event.replace(displayItem)
        }

        @HandleEvent(onlyOnSkyblock = true)
        fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
            val currentlyDecidingRecipe = currentlyDecidingRecipe ?: return
            if (event.slotId != SLOT_ID) return
            if (event.item == null) return

            currentlyDecidingRecipe.resolveToRecipe(RecipeInventory.currentlyOpenRecipe ?: return)
        }

        @HandleEvent(onlyOnSkyblock = true, eventType = InventoryCloseEvent::class)
        fun onInventoryClose() {
            currentlyDecidingRecipe = null
        }
    }

    // TODO: not make these extensions
    // TODO: make this work
    private fun PrimitiveRecipe.isRecursing(): Boolean {
        return ingredients.any { it.internalName == internalName }
    }

    private fun PrimitiveRecipe.isRecursingCompacting(): Boolean {
        val firstIngredient = ingredients.firstOrNull() ?: return false
        if (ingredients.any { it.internalName != firstIngredient.internalName }) {
            return false
        }

        val recipes = NeuItems.getRecipes(firstIngredient.internalName).filter { it.isCraftingRecipe() }
        return recipes.any { recipe -> recipe.ingredients.any { it.internalName == internalName } }
    }
}
