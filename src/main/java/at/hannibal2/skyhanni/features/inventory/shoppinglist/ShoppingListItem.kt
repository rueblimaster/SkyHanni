package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.data.SackApi.getAmountInSacks
import at.hannibal2.skyhanni.utils.HypixelCommands.viewRecipe
import at.hannibal2.skyhanni.utils.InventoryUtils.getAmountInInventory
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.PrimitiveIngredient
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import at.hannibal2.skyhanni.utils.renderables.Renderable

class ShoppingListItem(
    val internalName: NeuInternalName,
    var amount: Int = 1,
    val topLevelItem: ShoppingListItem? = null,
    var recipe: PrimitiveRecipe? = null,
) {
    var hidden = false

    val totalAmount: Int
        get() = amount * (topLevelItem?.amount ?: 1)

    val remainingAmount: Int
        get() = totalAmount - getCurrentAmount()

    var possibleRecipes: List<PrimitiveRecipe> = emptyList()

    private val subItems = mutableListOf<ShoppingListItem>()

    init {
        getPossibleRecipes()
    }

    override fun toString(): String {
        return "${internalName.itemName} x$amount" + if (subItems.isNotEmpty()) {
            " (${subItems.joinToString(", ")})"
        } else {
            ""
        }
    }

    fun breakDownIntoSubitems() {
        println("Breaking down $internalName into subitems")

        if (recipe != null) {
            println("Recipe already found")
        } else {
            decideRecipe()
        }

        if (recipe == null) {
            println("No recipe found for $internalName")
            return
        }
        subItems.clear()

        addRecipe()

        ShoppingList.update()
    }

    fun getPossibleRecipes() {
        println("getting the all possible recipes")

        possibleRecipes = NeuItems.getRecipes(internalName).filter { it.isCraftingRecipe() }
        possibleRecipes.forEach { recipe ->
            println("Recipe: $recipe")
        }
    }

    fun decideRecipe() {
        if (possibleRecipes.isEmpty() != false) {
            println("No recipes found for ${internalName.itemName}")
            return
        }

        if (possibleRecipes.size > 1) {
            println("Multiple recipes found for ${internalName.itemName}")
            viewRecipe(internalName.itemName)
        } else {
            recipe = possibleRecipes[0]
        }
    }

    fun addRecipe() {
        println("adding recipe for $internalName: $recipe")
        val usedRecipe: PrimitiveRecipe = recipe?.copy() ?: return

        for (ingredient: PrimitiveIngredient in usedRecipe.ingredients) {
            // TODO: why is .count a double, is there the possibility for half an item or what???
            println("add item: ${ingredient.internalName} amount: ${ingredient.count.toInt()}")
            val item = subItems.firstOrNull { it.internalName == ingredient.internalName } as ShoppingListItem?

            if (item == null) {
                subItems.add(ShoppingListItem(ingredient.internalName, ingredient.count.toInt(), this))
            } else {
                item.changeAmountBy(ingredient.count.toInt())
            }
        }
    }

    fun changeAmountBy(amount: Int) {
        this.amount += amount
    }

    fun changeAmountTo(amount: Int) {
        this.amount = amount
    }

    fun getCurrentAmount(): Int {
        println("Getting current amount for $internalName, amount: ${internalName.getAmountInInventory()} + ${internalName.getAmountInSacks()} = ${internalName.getAmountInInventory() + internalName.getAmountInSacks()}")
        return internalName.getAmountInInventory() + internalName.getAmountInSacks()
    }

    fun hasItems(): Boolean {
        return totalAmount <= getCurrentAmount()
    }

    fun getIndent(amount: Int): String {
        return "- ".repeat(amount)
    }

    fun getRenderables(indent: Int): List<Renderable> {
        val renderables = mutableListOf<Renderable>()
        if (!hidden) {
            println(internalName.itemName)
            println("Adding §e${internalName.itemNameWithoutColor} x$amount to renderables")

            var string = getIndent(indent) + "§e${internalName.itemNameWithoutColor} ${getCurrentAmount()}/$totalAmount"
            if (topLevelItem != null) {
                string += " ($amount each)"
            }

            val downBreakable: Boolean
            if (subItems.isEmpty() && possibleRecipes.isNotEmpty()) {
                downBreakable = true
                string += " §7Click to break down into recipe"
            } else {
                downBreakable = false
            }

            if (downBreakable) {
                renderables.add(
                    Renderable.link(
                        string, true,
                    ) {
                        breakDownIntoSubitems()
                    },
                )
            } else {
                renderables.add(
                    Renderable.string(string))
            }
            subItems.forEach {
                renderables.addAll(it.getRenderables(indent + 1))
            }
        }
        return renderables
    }
}
