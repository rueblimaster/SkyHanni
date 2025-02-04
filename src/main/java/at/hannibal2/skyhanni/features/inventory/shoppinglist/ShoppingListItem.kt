package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.features.inventory.shoppinglist.ShoppingList.currentlyOpenRecipe
import at.hannibal2.skyhanni.features.inventory.shoppinglist.ShoppingList.resetDisplayItem
import at.hannibal2.skyhanni.utils.HypixelCommands.viewRecipe
import at.hannibal2.skyhanni.utils.InventoryUtils.getAmountInInventoryAndSacks
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.setLore
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.PrimitiveIngredient
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import at.hannibal2.skyhanni.utils.renderables.Renderable
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack

class ShoppingListItem(
    val internalName: NeuInternalName,
    var amount: Double = 1.0,
    val topLevelItem: ShoppingListItem? = null,
    var recipe: PrimitiveRecipe? = null,
) {
    var hidden = false

    val totalAmount: Double
        get() = amount * (topLevelItem?.totalAmount ?: 1.0)

    val remainingAmount: Double
        get() = totalAmount - getCurrentAmount()

    var possibleRecipes: List<PrimitiveRecipe> = emptyList()
    var currentlyDecidingRecipe = false // TODO: remove this as displayItem already tells if we are currently deciding a recipe
    var displayItem: ItemStack? = null

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

    fun PrimitiveRecipe.isRecursing(): Boolean {
        return ingredients.any { it.internalName == internalName }
    }

    fun getPossibleRecipes() {
        println("getting the all possible recipes")

        possibleRecipes = NeuItems.getRecipes(internalName).filter { it.isCraftingRecipe() }.filter { recipe ->
            println(recipe.toString() + " " + (!recipe.ingredients.any { it.internalName.toString() == internalName.toString() }).toString())
            !recipe.ingredients.any { it.internalName.toString() == internalName.toString() }
        }
        possibleRecipes.forEach { recipe ->
            println("Recipe: $recipe")
            recipe.ingredients.forEach { println("Checking ${it.internalName} vs $internalName") }
        }
    }

    fun decideRecipe() {
        if (possibleRecipes.isEmpty() != false) {
            println("No recipes found for ${internalName.itemName}")
            return
        }

        if (possibleRecipes.size > 1) {
            println("Multiple recipes found for ${internalName.itemName}")

//             println(possibleRecipes[0].ingredients)

            currentlyDecidingRecipe = true

            val lore = buildList {
                add("§8(From SkyHanni)")
                add("")

                // TODO: add stuff
            }

            displayItem = ItemStack(Blocks.diamond_block).setLore(lore).setStackDisplayName("§bSelect Recipe")
            ShoppingList.displayItem = displayItem

            viewRecipe(internalName.asString())
        } else {
            recipe = possibleRecipes[0]
        }
    }

    fun onItemClicked(clickedItem: ItemStack): Boolean {
        if (currentlyDecidingRecipe && clickedItem == displayItem) {
            println("Clicked on display item for $internalName")
            recipe = currentlyOpenRecipe
            currentlyDecidingRecipe = false
            displayItem = null
            resetDisplayItem()
            // TODO: close the inventory
            breakDownIntoSubitems()
            return true
        }
        subItems.forEach {
            if (it.onItemClicked(clickedItem)) {
                return true
            }
        }
        return false
    }

    fun addRecipe() {
        println("adding recipe for $internalName: $recipe")
        val usedRecipe: PrimitiveRecipe = recipe?.copy() ?: return

        for (ingredient: PrimitiveIngredient in usedRecipe.ingredients) {
            // TODO: why is .count a double, is there the possibility for half an item or what???
            println("add item: ${ingredient.internalName} amount: ${ingredient.count.toInt()}")
            val item = subItems.firstOrNull { it.internalName == ingredient.internalName } as ShoppingListItem?

            val ingredientAmount = ingredient.count / (usedRecipe.output?.count ?: 1.0)

            if (item == null) {
                subItems.add(ShoppingListItem(ingredient.internalName, ingredientAmount, this))
            } else {
                item.changeAmountBy(ingredientAmount)
            }
        }
    }

    fun changeAmountBy(amount: Double) {
        this.amount += amount
    }

    fun changeAmountTo(amount: Double) {
        this.amount = amount
    }

    fun getCurrentAmount(): Int {
//         println(
//             "Getting current amount for $internalName, " +
//                 "amount: ${internalName.getAmountInInventory()} + ${internalName.getAmountInSacks()} = " +
//                 "${internalName.getAmountInInventory() + internalName.getAmountInSacks()}: ${internalName.getAmountInInventoryAndSacks()}",
//         )
        return internalName.getAmountInInventoryAndSacks()
    }

    fun hasItems(): Boolean {
        return totalAmount <= getCurrentAmount()
    }

    // TODO: do some nice indent
    fun getIndent(amount: Int): String {
        return if (topLevelItem == null || amount == 0) {
            "  ".repeat(amount)
        } else {
            "§7  " + "| ".repeat(amount - 1)
        }
    }

    fun Double.displayAmount(): String {
        return if (this % 1 == 0.0) {
            this.toInt().toString()
        } else {
            this.toString()
        }
    }

    fun getRenderables(indent: Int): List<Renderable> {
        val renderables = mutableListOf<Renderable>()
        if (!hidden) {
//             println(internalName.itemName)
//             println("Adding §e${internalName.itemNameWithoutColor} x$amount to renderables")

            var string = getIndent(indent)
            if (topLevelItem != null) {
                string += "§f${amount.displayAmount()}x "
            }

            string += "§e${internalName.itemNameWithoutColor} ${getCurrentAmount()}/${totalAmount.displayAmount()}"

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
                    Renderable.string(string),
                )
            }
            subItems.forEach {
                renderables.addAll(it.getRenderables(indent + 1))
            }
        }
        return renderables
    }
}
