package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import at.hannibal2.skyhanni.utils.RecipeResolver
import com.google.gson.annotations.Expose

class ShoppingListItem(
    @Expose
    val internalName: NeuInternalName,
    @Expose
    var amount: Int,
    recipe: PrimitiveRecipe? = null,
) {
    @Expose
    val recipeResolver: RecipeResolver = RecipeResolver(internalName, recipe)

    override fun toString(): String {
        return "$internalName: $recipeResolver"
    }
}
