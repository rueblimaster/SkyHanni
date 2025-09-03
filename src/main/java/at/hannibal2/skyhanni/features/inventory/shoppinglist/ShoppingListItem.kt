package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import at.hannibal2.skyhanni.utils.RecipeResolver
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.primitives.text
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

    private val breakDownPossible get() = recipeResolver.hasValidRecipes && subitems.isEmpty()

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

    private fun getDisplayRepresentation(indent: String): String {
        var displayString = "§8$indent"

        if (parentItem != null) {
            displayString += "§7${amount.clean()}x "
        }

        displayString += "${internalName.repoItemName} "

        displayString += "§fx${totalAmount.clean()}"

        return displayString
    }

    fun buildDisplay(indent: String = "", indentForSubitems: String? = null): List<Renderable> {
        return buildList {
            add(
                if (breakDownPossible) Renderable.clickable(
                    getDisplayRepresentation(indent),
                    onLeftClick = ::triggerBreakDown,
                    tips = listOf("§7left-click to expand recipe"),
                ) else Renderable.text(
                    getDisplayRepresentation(indent),
                ),
            )
            val actualIndentForSubitems: String = indentForSubitems ?: indent

            subitems.forEachIndexed { index, item ->
                val isLastItem = index == subitems.size - 1

                val newIndent = actualIndentForSubitems + if (!isLastItem) "|·" else "`·"

                val newIndentForSubitems = actualIndentForSubitems + if (!isLastItem) "| " else "  "

                addAll(item.buildDisplay(newIndent, newIndentForSubitems))
            }
        }
    }
}
