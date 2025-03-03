package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.api.GetFromSackApi
import at.hannibal2.skyhanni.api.ItemBuyApi.buy
import at.hannibal2.skyhanni.features.inventory.shoppinglist.ShoppingList.currentlyOpenRecipe
import at.hannibal2.skyhanni.features.inventory.shoppinglist.ShoppingList.resetDisplayItem
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.HypixelCommands.craft
import at.hannibal2.skyhanni.utils.HypixelCommands.viewRecipe
import at.hannibal2.skyhanni.utils.InventoryUtils.getAmountInInventory
import at.hannibal2.skyhanni.utils.InventoryUtils.getAmountInInventoryAndSacks
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.setLore
import at.hannibal2.skyhanni.utils.KeyboardManager.LEFT_MOUSE
import at.hannibal2.skyhanni.utils.KeyboardManager.RIGHT_MOUSE
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.NeuItems.isVanillaItem
import at.hannibal2.skyhanni.utils.PrimitiveIngredient
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.Renderable.Companion.ClickTypeWithModifiers
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import org.lwjgl.input.Keyboard

@Suppress("TooManyFunctions")
class ShoppingListItem(
    val internalName: NeuInternalName,
    var amount: Double = 1.0,
    val topLevelCategory: ShoppingListCategory,
    val topLevelItem: ShoppingListItem? = null,
    var recipe: PrimitiveRecipe? = null,
) {
    var hidden = false

    constructor(
        template: ItemTemplate,
        topLevelCategory: ShoppingListCategory,
        topLevelItem: ShoppingListItem? = null,
    ) : this(
        NeuInternalName.fromItemNameOrInternalName(template.internalName),
        template.amount,
        topLevelCategory,
        topLevelItem,
        template.recipe?.toPrimitiveRecipe(),
    ) {
        hidden = template.hidden

        template.subItems.forEach {
            subItems.add(ShoppingListItem(it, topLevelCategory, this))
        }

        val ingredients: MutableMap<NeuInternalName, Double> = mutableMapOf()
        recipe?.ingredients?.forEach {
            ingredients[it.internalName] = it.count / (recipe?.output?.count ?: 1.0)
        }

        subItems.forEach {
            if (it.internalName in ingredients && ingredients[it.internalName] != null) {
                it.amount = ingredients[it.internalName] ?: 1.0
            }
        }
    }

    // TODO soon (probably): add a way to offset the amount of an item counted in the inventory etc.

    val totalAmount: Double
        get() = amount * (topLevelItem?.remainingAmount ?: 1.0)

    val remainingAmount: Double
        get() = if (getCurrentAmount() > totalAmount) 0.0 else totalAmount - getCurrentAmount()

    var possibleRecipes: List<PrimitiveRecipe> = emptyList()
    var displayItem: ItemStack? = null
    val downBreakable: Boolean
        get() {
            return subItems.isEmpty() && possibleRecipes.isNotEmpty()
        }

    val subItems = mutableListOf<ShoppingListItem>()

    init {
        loadPossibleRecipes()
    }

    val clickLayout: MutableMap<ClickTypeWithModifiers, () -> Unit> = mutableMapOf()

    /*
    TODO later: make this all configurable
    what do we want to be able to do from the display widget:
        left click is for doing stuff with it
        - (left click) get from ah/bz  (switch ah/bz and break down as a setting) also only (click) if no recipe
        - open recipe to craft it
        - (shift + left click) break down into its subitems
        right click is for doing stuff with the item itself
        TODO later (maybe): implement change amount by right clicking, as can also be done with the command,
          but could maybe also be done by pressing arrow keys
        - (right click) change the amount (but if nothing is entered remove if I can discriminate between cancel and remove)
        - remove completely (if it isn't a subitem of another item)
        - (shift + right click) hide/unhide
        - (ctrl + shift + right click) hide/unhide all whole tree
        - (ctrl + right click) move to top

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

     what may we want to see of subitems additionally:
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

        if (recipe == null) {
            val success = decideRecipe()
            if (success == false) {
                return
            }
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

    fun loadPossibleRecipes() {
        if (recipe != null) return

        possibleRecipes = NeuItems.getRecipes(internalName).filter { it.isCraftingRecipe() }.filter { recipe ->
            !recipe.isRecursing() && !recipe.isRecursingCompacting()
        }
    }

    fun decideRecipe(): Boolean {
        if (possibleRecipes.isEmpty() != false) {
            ChatUtils.chat("No recipes found for ${internalName.itemNameWithoutColor}")
            return false
        }

        if (possibleRecipes.size > 1) {
            ChatUtils.chat("Multiple recipes found for ${internalName.itemNameWithoutColor}\n§7Select one")

            val lore = buildList {
                add("§8(From SkyHanni)")
//                 add("")

                // TODO (maybe): add stuff
            }

            displayItem = ItemStack(Blocks.diamond_block).setLore(lore).setStackDisplayName("§bSelect Recipe")
            ShoppingList.displayItem = displayItem

            viewRecipe(internalName.asString())
        } else {
            recipe = possibleRecipes[0]
        }
        return true
    }

    fun onItemClick(clickedItem: ItemStack): Boolean {
        if (displayItem != null && clickedItem == displayItem) {
//             println("Clicked on display item for $internalName")
            recipe = currentlyOpenRecipe
            displayItem = null
            resetDisplayItem()
            breakDownIntoSubitems()
            return true
        }
        subItems.forEach {
            if (it.onItemClick(clickedItem)) {
                return true
            }
        }
        return false
    }

    fun addRecipe() {
        val usedRecipe: PrimitiveRecipe = recipe?.copy() ?: return

        for (ingredient: PrimitiveIngredient in usedRecipe.ingredients) {
            val item = subItems.firstOrNull { it.internalName == ingredient.internalName } as ShoppingListItem?

            val ingredientAmount = ingredient.count / (usedRecipe.output?.count ?: 1.0)

            if (item == null) {
                subItems.add(ShoppingListItem(ingredient.internalName, ingredientAmount, topLevelCategory, this))
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
        // TODO later: also get the amount in the storage (as an option), as it's relevant for supercraft
        return internalName.getAmountInInventoryAndSacks()
    }

    fun getMissingAmountInInventory(): Double {
        return totalAmount - internalName.getAmountInInventory()
    }

    fun hasItems(): Boolean {
        return totalAmount <= getCurrentAmount()
    }

    fun hasAllSubItems(): Boolean {
        return if (subItems.isEmpty()) {
            hasItems()
        } else {
            subItems.all { it.hasAllSubItems() }
        }
    }

    fun getItemsOverall(): Map<NeuInternalName, Pair<Double, Int>> {
        return buildMap {
            this[internalName] = Pair(totalAmount, 1)

            subItems.forEach {
                it.getItemsOverall().forEach { (name, pair: Pair<Double, Int>) ->
                    if (this.containsKey(name)) {
                        this[name]?.let { it1 -> this[name] = Pair(it1.first + pair.first, it1.second + pair.second) }
                    } else {
                        this[name] = pair
                    }
                }
            }
        }
    }

    fun checkIfInSignAndInsertAmount(): Boolean {
//         println("checking for in sign")
        if (Minecraft.getMinecraft().currentScreen is GuiEditSign) {
            ChatUtils.chat("Detected sign gui, pasting number into sign instead")
            LorenzUtils.setTextIntoSign("${remainingAmount.toInt()}")
            return true
        } else {
            return false
        }
    }

    fun buyItem() {
//         println("Buying item: $internalName ${totalAmount.toInt()}, getting ${remainingAmount.toInt()}")
        if (checkIfInSignAndInsertAmount()) return
        if (remainingAmount <= 0) return

        internalName.buy(remainingAmount.toInt())
    }

    fun openCraftingRecipe() {
//         println("Opening crafting recipe: $internalName")
        if (checkIfInSignAndInsertAmount()) return
        // if (remainingAmount <= 0) return // this shouldn't happen anyways

        if (internalName.isVanillaItem()) {
            ChatUtils.chat("Vanilla item, can't open recipe, opening the crafting table and getting all required items instead")
            subItems.forEach {
                it.fetchItemFromAvailableStorage()
                craft()
            }

        } else {
            viewRecipe(internalName.asString())
            // TODO soon (maybe): hide the display item while doing this
        }
    }

    fun fetchItemFromAvailableStorage() {
//         println(
//             "Fetching item from available storage: $internalName ${totalAmount.toInt()}, " +
//                 "getting ${getMissingAmountInInventory().toInt()}",
//         )
        if (checkIfInSignAndInsertAmount()) return
        if (getMissingAmountInInventory() <= 0) return

        GetFromSackApi.getFromSack(internalName, getMissingAmountInInventory().toInt())
    }

    fun removeItem() {
//         println("Removing item: $internalName")
        if (topLevelItem != null) return
        topLevelCategory.remove(internalName)
    }

    fun moveItemToTop(item: ShoppingListItem) {
        subItems.remove(item)
        subItems.add(0, item)
        ShoppingList.update()
    }

    fun moveThisToTop() {
//         println("moving $internalName to top")
        topLevelItem?.moveItemToTop(this) ?: topLevelCategory.moveItemToTop(this)
    }

    fun unhideCategory() {
        topLevelCategory.hidden = false
    }

    fun toggleHide(hideTree: Boolean = false, forceSetTo: Boolean? = null) {
//         println("toggling hide for $internalName, hideTree: $hideTree")
        hidden = forceSetTo ?: !hidden
        if (hideTree) {
            subItems.forEach {
                it.toggleHide(true, forceSetTo ?: hidden)
            }
        }
        if (!hidden) {
            unhideCategory()
        }
        if (forceSetTo == null) {
            ShoppingList.update()
        }
    }

    // TODO later: implement
    fun copyToClipboard() {
        println("copying $internalName to clipboard")
    }

    fun Double.displayAmount(): String {
        return if (this % 1 == 0.0) {
            this.toInt().toString()
        } else {
            this.toString()
        }
    }

    fun getRenderables(indent: String, continuedIndent: String? = null): List<Renderable> {
        val renderables = mutableListOf<Renderable>()
        if (!hidden || ShoppingList.isInventoryOpen()) {
//             renderables.add(
//                 Renderable.line {

//             println(internalName.itemName)
//             println("Adding §e${internalName.itemNameWithoutColor} x$amount to renderables")

            var string = "§8$indent"
            val tooltip = mutableListOf<String>()

            if (topLevelItem != null) {
                string += "§7${amount.displayAmount()}x "
            }

            string += "${internalName.itemName} §f${getCurrentAmount()}/${totalAmount.displayAmount()}"

            ShoppingList.ItemsOverall.get(internalName)?.let {
                if (it.second > 1) {
                    string += " (${it.first.displayAmount()} total over ${it.second} items)"
                }
            }

            // left click
            if (hasItems()) {
                string += " §a✓"
                clickLayout[ClickTypeWithModifiers(LEFT_MOUSE)] = { fetchItemFromAvailableStorage() }
                tooltip.add("§7left click to fetch from storage")
                clickLayout[ClickTypeWithModifiers(LEFT_MOUSE, setOf(Keyboard.KEY_LSHIFT))] = { breakDownIntoSubitems() }
                tooltip.add("§7shift + left click to break down recipe")
            } else if (hasAllSubItems()) {
                string += " §e✓"
                clickLayout[ClickTypeWithModifiers(LEFT_MOUSE)] = { openCraftingRecipe() }
                tooltip.add("§7left click to open crafting recipe")
                clickLayout[ClickTypeWithModifiers(LEFT_MOUSE, setOf(Keyboard.KEY_LSHIFT))] = { breakDownIntoSubitems() }
                tooltip.add("§7shift + left click to break down recipe")
            } else {
                if (downBreakable) {
                    clickLayout[ClickTypeWithModifiers(LEFT_MOUSE)] = { breakDownIntoSubitems() }
                    tooltip.add("§7left click to break down recipe")
                    clickLayout[ClickTypeWithModifiers(LEFT_MOUSE, setOf(Keyboard.KEY_LSHIFT))] = { buyItem() }
                    tooltip.add("§7shift + left click to buy")
                } else {
                    clickLayout[ClickTypeWithModifiers(LEFT_MOUSE)] = { buyItem() }
                    tooltip.add("§7left click to buy")
                    clickLayout[ClickTypeWithModifiers(LEFT_MOUSE, setOf(Keyboard.KEY_LSHIFT))] = { breakDownIntoSubitems() }
                    tooltip.add("§7shift + left click to break down recipe")
                }
            }

            // right click
            if (topLevelItem == null) {
                clickLayout[ClickTypeWithModifiers(RIGHT_MOUSE)] = { removeItem() }
                tooltip.add("§7right click to remove")
                clickLayout[ClickTypeWithModifiers(RIGHT_MOUSE, setOf(Keyboard.KEY_LSHIFT))] = { toggleHide() }
                tooltip.add("§7shift + right click to ${if (hidden) "un" else ""}hide")
                clickLayout[ClickTypeWithModifiers(RIGHT_MOUSE, setOf(Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT))] = { toggleHide(true) }
                tooltip.add("§7ctrl + shift + right click to ${if (hidden) "un" else ""}hide tree")
            } else {
                clickLayout[ClickTypeWithModifiers(RIGHT_MOUSE)] = { toggleHide() }
                tooltip.add("§7right click to ${if (hidden) "un" else ""}hide")
                clickLayout[ClickTypeWithModifiers(RIGHT_MOUSE, setOf(Keyboard.KEY_LSHIFT))] = { toggleHide(true) }
                tooltip.add("§7shift + right click to ${if (hidden) "un" else ""}hide tree")
                clickLayout[ClickTypeWithModifiers(RIGHT_MOUSE, setOf(Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT))] = { toggleHide(true) }
            }

            if (hidden) {
                string = "§8${string.removeColor()}"
            }

            clickLayout.toMap()

            // TODO (maybe): make the tooltips be generated from the clickLayout
            clickLayout[ClickTypeWithModifiers(RIGHT_MOUSE, setOf(Keyboard.KEY_LCONTROL))] = { moveThisToTop() }
            tooltip.add("§7ctrl + right click to move to top")
//             clickLayout["middle"] = { copyToClipboard() }  // TODO later: implement middle click
//             tooltip.add("§7middle click to copy to clipboard")

            clickLayout.toMap()

            renderables.add(
                Renderable.clickableWithModifiers(
                    text = string,
                    tips = tooltip,
                    onAnyClick = clickLayout.toMap(),
                ),
            )
        }
//             val itemIcon = internalName.getItemStackOrNull()
//             renderables.add(
//                 if (itemIcon != null) {
//                     Renderable.itemStack(itemIcon)
//                 } else {
//                     ItemNameResolver.getInternalNameOrNull(internalName.itemName)?.let { Renderable.itemStack(it.getItemStack()) }
//                         ?: Renderable.string("§c?")
//                 },
//             )
//                 },
//             )

        for (i in 0 until subItems.size) {
            val isLastItem = i == subItems.size - 1
            var newContinuedIndent = continuedIndent ?: indent

            var newIndent = continuedIndent ?: indent
            if (!isLastItem) {
                newIndent += "|·"
                newContinuedIndent += "| "
            } else {
                newIndent += "`·"
                newContinuedIndent += "  "
            }

            subItems[i].getRenderables(newIndent, newContinuedIndent).forEach {
                renderables.add(it)
            }
        }
        return renderables
    }
}
