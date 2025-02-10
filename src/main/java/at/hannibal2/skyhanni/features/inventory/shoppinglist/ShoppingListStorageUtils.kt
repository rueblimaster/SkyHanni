package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.PrimitiveIngredient
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import net.minecraft.item.ItemStack

fun ShoppingListCategory.toEasyStorable(): Map<String, Any?> {
    return buildMap {
        put("name", name)
        put("color", color)
        put("icon", icon?.toEasyStorable())
        put("hidden", hidden)
        put("pinned", pinned)
        put("items", items.map { it.toEasyStorable() })
    }
}

fun ShoppingListItem.toEasyStorable(): Map<String, Any?> {
    return buildMap {
        put("internalName", internalName)
        put("amount", amount)
        put("hidden", hidden)
        put("pinned", pinned)
        put("recipe", recipe?.toEasyStorable())
    }
}

fun PrimitiveRecipe.toEasyStorable(): Map<String, Any> {
    return buildMap {
        val summarizedIngredients = ingredients.groupBy { it.internalName }
            .mapValues { it.value.sumOf { it.count } }
            .map { PrimitiveIngredient(it.key, it.value) }

        put("ingredients", summarizedIngredients.map { it.toEasyStorable() })
    }
}

fun PrimitiveIngredient.toEasyStorable(): Map<String, Any> {
    return buildMap {
        put("internalName", internalName)
        put("amount", count)
    }
}

fun ItemStack.toEasyStorable(): Map<String, Any> {
    return buildMap {
        put("item", item)
    }
}


fun ShoppingListCategory.fromEasyStorable(data: Map<String, Any?>): ShoppingListCategory {
    val name = data["name"] as String
    val color = data["color"] as LorenzColor
    val icon = (data["icon"] as Map<String, Any?>?)?.let { itemStackFromEasyStorable(it) }
    val hidden = data["hidden"] as Boolean
    val pinned = data["pinned"] as Boolean

    val category = ShoppingListCategory(name, color, icon)

    val items = (data["items"] as List<*>).map { shoppingListItemFromEasyStorable(it, category) }

    category.items += items
    category.hidden = hidden
    category.pinned = pinned

    return category
}

fun shoppingListItemFromEasyStorable(
    data: Map<String, Any?>,
    topLevelCategory: ShoppingListCategory,
    topLevelItem: ShoppingListItem? = null,
): ShoppingListItem {
    val internalName = data["internalName"] as String
    val amount = data["amount"] as Double
    val hidden = data["hidden"] as Boolean
    val pinned = data["pinned"] as Boolean
    val recipe = primitiveRecipeFromEasyStorable((data["recipe"] as Map<*, *>))

    return ShoppingListItem(
        internalName.toInternalName(), amount,
        topLevelCategory = topLevelCategory,
        topLevelItem = topLevelItem,
        recipe = recipe,
    ).apply {
        this.hidden = hidden
        this.pinned = pinned
    }
}

fun primitiveRecipeFromEasyStorable(data: Map<String, Any>): PrimitiveRecipe {
    val ingredients = (data["ingredients"] as List<Map<String, Any>>).map { primitiveIngredientFromEasyStorable(it) }

    return PrimitiveRecipe(ingredients)
}

fun primitiveIngredientFromEasyStorable(data: Map<String, Any>): PrimitiveIngredient {
    val internalName = data["internalName"] as String
    val count = data["amount"] as Int

    return PrimitiveIngredient(internalName, count)
}

fun itemStackFromEasyStorable(data: Map<String, Any>): ItemStack {
    val item = data["item"] as ItemStack

    return item
}
