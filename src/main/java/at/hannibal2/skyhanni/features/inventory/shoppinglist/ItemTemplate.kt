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
    val recipe: RecipeTemplate?

    @Expose
    val subItems: List<ItemTemplate>

    constructor(sourceItem: ShoppingListItem) {
        this.internalName = sourceItem.internalName.asString()
        this.amount = sourceItem.amount
        this.hidden = sourceItem.hidden

        this.recipe = sourceItem.recipe?.let { RecipeTemplate(it) }

        this.subItems = sourceItem.subItems.map { ItemTemplate(it) }
    }
}
