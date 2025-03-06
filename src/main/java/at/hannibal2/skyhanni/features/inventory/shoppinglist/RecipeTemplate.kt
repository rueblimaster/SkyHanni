package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import com.google.gson.annotations.Expose

class RecipeTemplate {
    @Expose
    val ingredients = mutableListOf<IngredientTemplate>()

    @Expose
    val result: IngredientTemplate?

    constructor(sourceRecipe: PrimitiveRecipe) {
        for (ingredient in sourceRecipe.ingredients) {
            ingredients.add(IngredientTemplate(ingredient))
        }

        this.ingredients
        this.result = sourceRecipe.output?.let { IngredientTemplate(it) }
    }

    fun toPrimitiveRecipe(): PrimitiveRecipe? {
        val ingredients = ingredients.map { it.toPrimitiveIngredient() }
        val result = result?.toPrimitiveIngredient()

        val possibleRecipes = NeuItems.getRecipes(result?.internalName ?: return null)

        for (recipe in possibleRecipes) {
            if (recipe.ingredients.containsAll(ingredients)) {
                return recipe
            }
        }
        return null
    }
}
