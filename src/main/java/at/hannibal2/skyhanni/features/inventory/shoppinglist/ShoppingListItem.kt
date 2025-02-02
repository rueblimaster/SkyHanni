package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.utils.HypixelCommands.viewRecipe
import at.hannibal2.skyhanni.utils.ItemUtils.getItemRarityOrCommon
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.LorenzRarity
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.NeuItems.getItemStackOrNull
import at.hannibal2.skyhanni.utils.PrimitiveIngredient
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import at.hannibal2.skyhanni.utils.renderables.Renderable

class ShoppingListItem(
    val name: NeuInternalName,
    var amount: Int = 1,
    val isToplevelItem: Boolean = true,
) {

    var hidden = false

    var recipe: PrimitiveRecipe? = null

    private val subItems = mutableListOf<ShoppingListItem>()

    override fun toString(): String {
        return "${name.itemName} x$amount" + if (subItems.isNotEmpty()) {
            " (${subItems.joinToString(", ")})"
        } else {
            ""
        }
    }

    fun getRecipe() {
        println("getting the Recipe")

        if (recipe != null) {
            println("Recipe already found")
        } else {
            val allRecipes: List<PrimitiveRecipe> = NeuItems.getRecipes(name).filter { it.isCraftingRecipe() }

            if (allRecipes.isEmpty()) {
                println("No recipes found for ${name.itemName}")
                return
            }

            allRecipes.forEach { recipe ->
                println("Recipe: $recipe")
            }
            if (allRecipes.size > 1) {
                println("Multiple recipes found for ${name.itemName}")
                viewRecipe(name.itemName)
            } else {
                recipe = allRecipes[0]
            }
        }

        addRecipe()
    }

    fun addRecipe() {
        println("adding recipe for $name: $recipe")
        if (recipe == null) {
            return
        }

        for (ingredient: PrimitiveIngredient in recipe!!.ingredients) {
            // TODO: why is .count a double, is there the possibility for half an item or what???
            println("add item: ${ingredient.internalName} amount: ${ingredient.count}")
            subItems.add(ShoppingListItem(ingredient.internalName, ingredient.count.toInt(), false))
        }

        ShoppingList.update()
    }

    fun changeAmountBy(amount: Int) {
        this.amount += amount
    }

    fun changeAmountTo(amount: Int) {
        this.amount = amount
    }

    fun getIndent(amount: Int): String {
        return "- ".repeat(amount)
    }

    fun getRenderables(indent: Int): List<Renderable> {
        val renderables = mutableListOf<Renderable>()
        if (!hidden) {
            println(name.itemName)
            val rarity: LorenzRarity? = name.getItemStackOrNull()?.getItemRarityOrCommon()
            val displayName: String = if (rarity == null || rarity == LorenzRarity.COMMON || rarity == LorenzRarity.UNCOMMON) {
                "§e" + name.itemNameWithoutColor
            } else {
                name.itemName
            }
            println("Adding $displayName x$amount to renderables, rarity: $rarity")
            renderables.add(
                Renderable.link(
                    getIndent(indent) + "$displayName§e x$amount" + " §7Click to view recipe", true,
                ) {
                    getRecipe()
                },
            )
            subItems.forEach {
                renderables.addAll(it.getRenderables(indent + 1))
            }
        }
        return renderables
    }
}
