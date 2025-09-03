package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import at.hannibal2.skyhanni.utils.RecipeResolver
import at.hannibal2.skyhanni.utils.renderables.Renderable
import com.google.gson.annotations.Expose

class ShoppingListItem(
    @Expose
    val internalName: NeuInternalName,
    @Expose
    var amount: Double,
    recipe: PrimitiveRecipe? = null,
    @Expose
    val parentItem: ShoppingListItem? = null,
) {
    @Expose
    val recipeResolver: RecipeResolver = RecipeResolver(internalName, recipe)

    @Expose
    var subitems = listOf<ShoppingListItem>()

    val totalAmount: Double
        get() = if (parentItem == null) amount else amount * parentItem.totalAmount

    override fun toString(): String {
        return "${internalName.itemNameWithoutColor} x$amount: $recipeResolver"
    }

    private fun triggerBreakDown() {
        recipeResolver.resolveRecipe { breakDown() }
    }

    private fun breakDown() {
        if (!recipeResolver.resolved) return

        val numOutputPerCraft = recipeResolver.recipe?.output?.count ?: return

        val ingredients: Map<NeuInternalName, Double> =
            recipeResolver.recipe?.ingredients?.groupingBy { it.internalName }?.fold(0.0) { acc, ing -> acc + ing.count } ?: return

        subitems = ingredients.map { (internalName, amount) ->
            ShoppingListItem(internalName, amount / numOutputPerCraft, parentItem = this)
        }

        ShoppingList.update()
    }

    private fun Double.clean(): String =
        if (this % 1.0 == 0.0) this.toInt().toString() else this.roundTo(2).toString()

    private fun getDisplayString(indent: Int): String {
        var displayString = "§8${"-".repeat(indent)}"

        if (parentItem != null) {
            displayString += "§7${amount.clean()}x "
        }

        displayString += "${internalName.repoItemName} "

        displayString += "§fx${totalAmount.clean()}"

        return displayString
    }

    fun buildDisplay(indent: Int = 0): List<Renderable> {
        return buildList {
            add(
                Renderable.clickable(
                    getDisplayString(indent),
                    onLeftClick = ::triggerBreakDown,
                ),
            )
            addAll(subitems.flatMap { it.buildDisplay(indent + 1) })
        }
    }
}
