package at.hannibal2.skyhanni.features.inventory.shoppinglist

import com.google.gson.annotations.Expose

class CategoryTemplate {
    @Expose
    val name: String
    @Expose
    val color: String
    @Expose
    val hidden: Boolean
    @Expose
    val pinned: Boolean

    @Expose
    val items: List<ItemTemplate>

    constructor(sourceCategory: ShoppingListCategory) {
        this.name = sourceCategory.name
        this.color = sourceCategory.color.toString()
        this.hidden = sourceCategory.hidden
        this.pinned = sourceCategory.pinned

        this.items = sourceCategory.items.map { ItemTemplate(it) }
    }

    constructor(name: String) {
        this.name = name
        this.color = "GOLD"
        this.hidden = false
        this.pinned = false
        this.items = emptyList()
    }
}
