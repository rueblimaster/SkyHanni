package at.hannibal2.skyhanni.features.inventory.shoppinglist

import com.google.gson.annotations.Expose

class CategoryTemplate {
    @Expose
    val name: String
    @Expose
    val color: Char
    @Expose
    val hidden: Boolean
    @Expose
    val pinned: Boolean

    @Expose
    val items: List<ItemTemplate>

    constructor(sourceCategory: ShoppingListCategory) {
        this.name = sourceCategory.name
        this.color = sourceCategory.color.chatColorCode
        this.hidden = sourceCategory.hidden
        this.pinned = sourceCategory.pinned

        this.items = sourceCategory.items.map { ItemTemplate(it) }
    }

    constructor(name: String) {
        this.name = name
        this.color = '6'
        this.hidden = false
        this.pinned = false
        this.items = emptyList()
    }

    override fun toString(): String {
        return "$name (${items.size} items)"
    }
}
