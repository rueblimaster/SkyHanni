package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.features.inventory.shoppinglist.RecipeTemplate.Companion.toRecipeTemplate
import com.google.gson.annotations.Expose

class ItemTemplate(
    @Expose val internalName: String,
    @Expose val amount: Double,
    @Expose val hidden: Boolean,
    @Expose val recipe: RecipeTemplate?,
    @Expose val subItems: List<ItemTemplate>,
) {

    companion object {
        fun ShoppingListItem.toItemTemplate(): ItemTemplate {
            return ItemTemplate(internalName.asString(), amount, hidden, recipe?.toRecipeTemplate(), subItems.map { it.toItemTemplate() })
        }
    }
}
