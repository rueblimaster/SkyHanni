package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.renderables.Renderable
import net.minecraft.item.ItemStack

class ShoppingListCategory(val name: String) {
    val items = mutableListOf<ShoppingListItem>()
    var hidden = false
    var pinned = false // TODO: implement this

    /*
    what do we want to be able to do from the display widget:
        - remove it
        - hide/unhide it
        - pin/unpin it

    what may we want to see:
        - name
        - optional icon?
        - total cost
    */

    override fun toString(): String {
        return name
    }

    fun add(itemName: NeuInternalName, amount: Double = 1.0) {
        if (!itemName.isKnownItem()) {
            ChatUtils.userError("Item ${itemName.itemName} not found")
            return
        }

        val item = items.firstOrNull { it.internalName == itemName } as ShoppingListItem?

        if (item == null) {
            items.add(ShoppingListItem(itemName, amount))
        } else {
            item.changeAmountBy(amount)
        }
    }

    fun remove(itemName: NeuInternalName, amount: Double? = null) {
        if (!itemName.isKnownItem()) {
            ChatUtils.userError("Item ${itemName.itemName} not found")
            return
        }

        val item = items.firstOrNull { it.internalName == itemName } as ShoppingListItem?

        if (item == null) {
            ChatUtils.userError("Item ${itemName.itemName} not found in category $name")
        } else {
            if (amount == null) {
                items.remove(item)
            } else {
                item.changeAmountBy(-amount)
                if (item.amount <= 0.0) {
                    items.remove(item)
                }
            }
        }
    }

    fun clear() {
        items.clear()
    }

    fun contains(itemName: NeuInternalName): Boolean {
        return items.any { it.internalName == itemName }
    }

    fun onItemClicked(clickedItem: ItemStack): Boolean {
        items.forEach {
            if (it.onItemClicked(clickedItem)) {
                return true
            }
        }
        return false
    }

    fun getRenderables(indent: Int): List<Renderable> {
        val renderables = mutableListOf<Renderable>()
        items.forEach { item ->
            renderables.addAll(item.getRenderables(indent))
        }
        return renderables
    }
}
