package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import at.hannibal2.skyhanni.utils.RecipeResolver
import at.hannibal2.skyhanni.utils.renderables.Renderable
import com.google.gson.annotations.Expose

class ShoppingListItem(
    @Expose
    val internalName: NeuInternalName,
    @Expose
    var amount: Int,
    recipe: PrimitiveRecipe? = null,
    @Expose
    val parentItem: ShoppingListItem? = null,
) {
    @Expose
    val recipeResolver: RecipeResolver = RecipeResolver(internalName, recipe)

    @Expose
    var subitems = listOf<ShoppingListItem>()

    override fun toString(): String {
        return "${internalName.itemNameWithoutColor} x$amount: $recipeResolver"
    }

    private fun triggerBreakDown() {
        recipeResolver.resolveRecipe { breakDown() }
    }

    private fun breakDown() {
        if (!recipeResolver.resolved) return

        val ingredients: Map<NeuInternalName, Int> =
            recipeResolver.recipe?.ingredients?.groupingBy { it.internalName }?.fold(0) { acc, ing -> acc + ing.count.toInt() } ?: return

        subitems = ingredients.map { (internalName, amount) -> ShoppingListItem(internalName, amount, parentItem = this) }

        ShoppingList.update()
    }

    fun buildDisplay(indent: Int = 0): List<Renderable> {
        return buildList {
            add(
                Renderable.clickable(
                    "§8${"-".repeat(indent)}${internalName.repoItemName}§f x$amount",
                    onLeftClick = ::triggerBreakDown,
                ),
            )
            addAll(subitems.flatMap { it.buildDisplay(indent + 1) })
        }
    }
}
