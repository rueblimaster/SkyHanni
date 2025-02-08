package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.features.inventory.shoppinglist.ShoppingList.currentlyOpenRecipe
import at.hannibal2.skyhanni.features.inventory.shoppinglist.ShoppingList.resetDisplayItem
import at.hannibal2.skyhanni.utils.HypixelCommands.viewRecipe
import at.hannibal2.skyhanni.utils.InventoryUtils.getAmountInInventoryAndSacks
import at.hannibal2.skyhanni.utils.InventoryUtils.inInventory
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.setLore
import at.hannibal2.skyhanni.utils.KeyboardManager
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

    /*
    what do we want to be able to do from the display widget:
        * left click is for doing stuff with it
        - (left click) break down into its subitems
        - (shift + left click) get from ah/bz  (switch ah/bz and break down as a setting) also only (click) if no recipe
        * right click is for doing stuff with the item itself
        - (right click) change the amount (but if nothing is entered remove if I can discriminate between cancel and remove)
        - remove completely (if it isn't a subitem of another item)
        - (shift + right click) hide/unhide
        - (ctrl + shift + right click) hide/unhide all whole tree
        - (ctrl + right click) pin/unpin

        - (middle click) copy to clipboard

        maybe?
        (probably not as it isn't really necessary and a lot of work)
        - move to another category
        - copy to another category

     what may we want to see of the item:
        - the name with rarity as color
        - the required amount
        - the possesed amount
        - the missing amount
        - the price for 1 (on hover)
        - the price for the required amount

        - icon (if plausible)

     what may we want to see of the subitems additionally:
        - the amount per craft (on hover)
        - the price for the amount per craft (on hover)
     */

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
        return ingredients.any { it.internalName == topLevelItem?.internalName }
    }

    fun PrimitiveRecipe.isRecursingCompacting(): Boolean {
        val firstIngredient = ingredients.firstOrNull() ?: return false
        if (ingredients.any { it.internalName != firstIngredient.internalName }) {
            return false
        }

        val recipes = NeuItems.getRecipes(firstIngredient.internalName).filter { it.isCraftingRecipe() }
        return recipes.any { recipe -> recipe.ingredients.any { it.internalName == internalName } }
    }

    fun getPossibleRecipes() {
        possibleRecipes = NeuItems.getRecipes(internalName).filter { it.isCraftingRecipe() }.filter { recipe ->
            !recipe.isRecursing() && !recipe.isRecursingCompacting()
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
//         println("adding recipe for $internalName: $recipe")
        val usedRecipe: PrimitiveRecipe = recipe?.copy() ?: return

        for (ingredient: PrimitiveIngredient in usedRecipe.ingredients) {
            // TODO: why is .count a double, is there the possibility for half an item or what???
//             println("add item: ${ingredient.internalName} amount: ${ingredient.count.toInt()}")
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
                string += "§7${amount.displayAmount()}x "
            }

            string += "§e${internalName.itemNameWithoutColor} §f${getCurrentAmount()}/${totalAmount.displayAmount()}"

            val downBreakable: Boolean
            if (subItems.isEmpty() && possibleRecipes.isNotEmpty()) {
                downBreakable = true
                // TODO: isn't this really ressource intensive?
                if (inInventory()) {
                    string += " §7Click to break down into recipe"
                }
            } else {
                downBreakable = false
            }

            if (downBreakable) {
                renderables.add(
                    if (inInventory()) Renderable.multiClickAndHover(
                        string, listOf("test1", "test2"),
                        false,
                        mapOf<Int, () -> Unit>(
                            0 to { println("test3") },
                            1 to { println("test4") },
                            2 to { println("test5") },
                            3 to { println("test6") }),
//                         onClick = {
//                             if (KeyboardManager.isModifierKeyDown()) itemRemover.invoke(internalName, cleanName)
//                             else itemHider.invoke(internalName, hidden)
//                             update()
                    ) else Renderable.string(string),
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
