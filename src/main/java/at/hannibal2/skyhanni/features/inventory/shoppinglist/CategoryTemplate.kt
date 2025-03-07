package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.features.inventory.shoppinglist.ItemTemplate.Companion.toItemTemplate
import com.google.gson.annotations.Expose

class CategoryTemplate(
    @Expose val name: String,
    @Expose val color: Char = '6',
    @Expose val hidden: Boolean = false,
    @Expose val items: List<ItemTemplate> = emptyList(),
) {

    companion object {
        fun ShoppingListCategory.toCategoryTemplate(): CategoryTemplate {
            return CategoryTemplate(name, color.chatColorCode, hidden, items.map { it.toItemTemplate() })
        }
    }

    override fun toString(): String {
        return "CategoryTemplate $name (${items.size} items)"
    }
}
