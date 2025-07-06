package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.RecipeResolver
import com.google.gson.annotations.Expose

class ItemTemplate(
    @Expose val internalName: String,
    @Expose val amount: Double,
    @Expose val hidden: Boolean,
    @Expose val recipeResolver: RecipeResolver?,
    @Expose val subItems: List<ItemTemplate>,
) {

    fun toShoppingListItem(topLevelCategory: ShoppingListCategory, topLevelItem: ShoppingListItem? = null): ShoppingListItem {
        val result = ShoppingListItem(
            NeuInternalName.fromItemNameOrInternalName(this.internalName),
            this.amount,
            topLevelCategory,
            topLevelItem,
            hidden = this.hidden,
            recipeResolver = this.recipeResolver,
        )
        this.subItems.forEach {
            result.displayedSubItems.add(it.toShoppingListItem(topLevelCategory, result))
        }

        val recipe = result.recipe.recipe
        val ingredients: MutableMap<NeuInternalName, Double> = mutableMapOf()
        recipe?.ingredients?.forEach {
            ingredients[it.internalName] = it.count / (recipe.output?.count ?: 1.0)
        }

        result.displayedSubItems.forEach {
            if (it.internalName in ingredients && ingredients[it.internalName] != null) {
                it.amount = ingredients[it.internalName] ?: 1.0
            }
        }
        return result
    }

    companion object {
        fun ShoppingListItem.toItemTemplate(): ItemTemplate {
            return ItemTemplate(internalName.asString(), amount, hidden, recipe, displayedSubItems.map { it.toItemTemplate() })
        }
    }
}
