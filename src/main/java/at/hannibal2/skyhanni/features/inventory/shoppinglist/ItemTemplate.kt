package at.hannibal2.skyhanni.features.inventory.shoppinglist

import com.google.gson.annotations.Expose

class ItemTemplate {
    @Expose
    val internalName: String
    @Expose
    val amount: Double
    @Expose
    val hidden: Boolean
    @Expose
    val pinned: Boolean

    @Expose
    val recipe: RecipeTemplate?

    constructor(sourceItem: ShoppingListItem) {
        this.internalName = sourceItem.internalName.toString()
        this.amount = sourceItem.amount
        this.hidden = sourceItem.hidden
        this.pinned = sourceItem.pinned

        this.recipe = sourceItem.recipe?.let { RecipeTemplate(it) }
    }
}
