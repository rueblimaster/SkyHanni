package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.OtherInventoryData.Inventory
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.render.gui.ReplaceItemEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import com.google.gson.annotations.Expose
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.ItemStack

class RecipeResolver(
    @Expose
    val internalName: NeuInternalName,
    inputRecipe: PrimitiveRecipe? = null,
    @Expose
    val ignoreBlocksOfOres: Boolean = true,
) {
    @Expose
    var recipe = inputRecipe
        private set
    var resolved = recipe != null

    val hasValidRecipes get() = possibleRecipes.isNotEmpty()

    private val possibleRecipes: List<PrimitiveRecipe> = getAllPossibleRecipes()

    private fun getAllPossibleRecipes(): List<PrimitiveRecipe> {
        var recipes = NeuItems.getRecipes(internalName)
            .filter { recipe -> recipe.recipeType == RecipeType.CRAFTING || recipe.recipeType == RecipeType.KAT_UPGRADE }
            .filter { !it.isRecursing() && !it.isRecursingCompacting() }
        if (ignoreBlocksOfOres && recipes.size > 1) {
            val recipesWithoutBlocksOfOres = recipes.filter { !it.comesFromBlockOfOre() }
            if (recipesWithoutBlocksOfOres.isNotEmpty() && recipesWithoutBlocksOfOres.size != recipes.size) {
                recipes = recipesWithoutBlocksOfOres
            }
        }

        if (recipes.size == 1) {
            recipe = recipes[0]
            resolved = true
        }
        return recipes
    }

    private var currentCallback: (() -> Unit)? = null
    private var displayItem: ItemStack? = null

    override fun toString(): String {
        return "RecipeResolver(${internalName.asString()}: recipe $recipe, currently getting decided: ${displayItem != null})"
    }

    /* To be used externaly to set the recipe */
    fun setRecipe(recipe: PrimitiveRecipe) {
        this.recipe = recipe
        resolved = true
        currentCallback?.invoke()
        currentCallback = null
        displayItem = null
    }

    fun resolveRecipe(callback: () -> Unit) {
        if (resolved) {
            callback()
            return
        }

        if (possibleRecipes.isEmpty()) {
            ChatUtils.chat("No recipes found for ${internalName.itemNameWithoutColor}")
            callback()
            return
        }

        if (possibleRecipes.size > 1) {
            ChatUtils.chat("Multiple recipes found for ${internalName.itemNameWithoutColor}\nSelect one")

            launchResolving(callback)
        } else {
            recipe = possibleRecipes[0]
            resolved = true
            callback()
        }
    }

    private fun launchResolving(callback: () -> Unit) {
        if (resolved) {
            callback()
            return
        }

        currentCallback = callback

        val lore = buildList {
            add("§8(From SkyHanni)")
        }

        displayItem = ItemUtils.createItemStack(
            ItemStack(Blocks.DIAMOND_BLOCK).item,
            "§bSelect Recipe",
            lore,
        )

        currentlyDecidingRecipe = this

        HypixelCommands.viewRecipe(internalName)
    }

    private fun resolveToRecipe(recipe: PrimitiveRecipe) {
        this.recipe = recipe
        resolved = true
        currentCallback?.invoke()
        currentCallback = null
        displayItem = null
    }

    private fun cancelResolving() {
        currentCallback?.invoke()
        currentCallback = null
        displayItem = null
    }

    @SkyHanniModule
    companion object {
        private const val SLOT_ID = 43

        private var resetBlock = false

        var currentlyDecidingRecipe: RecipeResolver? = null
            set(value) {
                if (value != null) resetBlock = true
                field?.cancelResolving() // Cancel the previous recipe resolving
                field = value
            }

        @HandleEvent(onlyOnSkyblock = true)
        fun replaceItem(event: ReplaceItemEvent) {
            val displayItem = currentlyDecidingRecipe?.displayItem ?: return
            if (RecipeInventory.currentlyOpenRecipe == null) return
            if (event.slot != SLOT_ID) return

            event.replace(displayItem)
        }

        @HandleEvent(onlyOnSkyblock = true)
        fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
            val currentlyDecidingRecipe = currentlyDecidingRecipe ?: return
            if (event.slotId != SLOT_ID) return
            if (event.item == null) return

            currentlyDecidingRecipe.resolveToRecipe(RecipeInventory.currentlyOpenRecipe ?: return)

            MinecraftCompat.localPlayer.closeContainer()
        }

        @HandleEvent(onlyOnSkyblock = true)
        fun onInventoryClose(event: InventoryCloseEvent) {
            if (resetBlock) return
            if (event.reopenSameName) return
            currentlyDecidingRecipe = null
        }

        @HandleEvent(onlyOnSkyblock = true, eventType = InventoryFullyOpenedEvent::class)
        fun onInventoryOpen() {
            resetBlock = false
        }
    }

    private fun PrimitiveRecipe.isRecursing(): Boolean {
        return ingredients.any { it.internalName == internalName }
    }

    private fun PrimitiveRecipe.isRecursingCompacting(): Boolean {
        val firstIngredient = ingredients.firstOrNull() ?: return false
        if (ingredients.any { it.internalName != firstIngredient.internalName }) {
            return false
        }

        val recipes = NeuItems.getRecipes(firstIngredient.internalName).filter { it.isCraftingRecipe() }
        return recipes.any { it.isRecursing() }
    }

    private fun PrimitiveRecipe.comesFromBlockOfOre(): Boolean {
        val firstIngredient = ingredients.firstOrNull() ?: return false
        if (!ingredients.all { it.isSameAs(firstIngredient) }) {
            return false
        }
        return firstIngredient.internalName.isBlockOfOre()
    }

    private fun PrimitiveIngredient.isSameAs(other: PrimitiveIngredient) =
        this.internalName == other.internalName && this.count == other.count

    private fun NeuInternalName.isBlockOfOre(): Boolean {
        val recipes = NeuItems.getRecipes(this).filter { it.isCraftingRecipe() }
        if (recipes.size != 1) {
            return false
        }

        val firstIngredient = recipes.first().ingredients.firstOrNull() ?: return false
        if (recipes.first().ingredients.size != 9) {
            return false
        }
        if (!recipes.first().ingredients.all { it.isSameAs(firstIngredient) }) {
            return false
        }

        val recipesOfIngredient = NeuItems.getRecipes(firstIngredient.internalName).filter { it.isCraftingRecipe() }
        return recipesOfIngredient.any { recipe ->
            recipe.ingredients.any { ingredient ->
                ingredient.internalName == this
            }
        }
    }
}
