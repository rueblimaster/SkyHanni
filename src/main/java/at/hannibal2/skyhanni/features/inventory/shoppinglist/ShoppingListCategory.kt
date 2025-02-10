package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.renderables.Renderable
import net.minecraft.item.ItemStack

class ShoppingListCategory(
    val name: String,
    val color: LorenzColor = LorenzColor.GOLD,
    val icon: ItemStack? = null,
) {
    val items = mutableListOf<ShoppingListItem>()
    var hidden = false
    var pinned = false

    /*
    what do we want to be able to do from the display widget:
        - (right click) remove it
        - (shift + right click) hide/unhide it along with tree
        - (ctrl + right click) pin/unpin it

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
            items.add(ShoppingListItem(itemName, amount, this))
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
            if (it.onItemClick(clickedItem)) {
                return true
            }
        }
        return false
    }

    fun togglePin() {
        pinned = !pinned
        ShoppingList.update()
    }

    fun toggleHide() {
        hidden = !hidden
        items.forEach {
            it.toggleHide(true, hidden)
        }
        ShoppingList.update()
    }

    fun onNormalRightClick() {
        println("category: right click")
        ShoppingList.removeCategory(this)
    }

    fun onShiftRightClick() {
        println("category: shift right click")
        toggleHide()
    }

    fun onCtrlRightClick() {
        println("category: ctrl right click")
        togglePin()
    }

    fun getRenderables(indent: Int, showThis: Boolean = true): List<Renderable> {
        val renderables = mutableListOf<Renderable>()

        if (!hidden || ShoppingList.isInventoryOpen()) {
            if (showThis) {
                var string = ""
                val tooltip = mutableListOf<String>()

                if (pinned) {
                    string += "§e*"
                    tooltip.add("§ePinned")
                }

                string += if (!hidden) color.getChatColor() else "§8"
                string += "§n$name"

                tooltip.add("§7Right click to remove")
                tooltip.add("§7Shift + right click to ${if (hidden) "un" else ""}hide")
                tooltip.add("§7Ctrl + right click to ${if (pinned) "un" else ""}pin")

                renderables.add(
                    Renderable.multiClickAndHover(
                        string,
                        tooltip,
                        false,
                        mapOf<Int, () -> Unit>(
                            0 to { },
                            1 to {
                                if (KeyboardManager.isModifierKeyDown()) {
                                    onCtrlRightClick()
                                } else if (KeyboardManager.isShiftKeyDown()) {
                                    onShiftRightClick()
                                } else {
                                    onNormalRightClick()
                                }
                            },
                        ),
                    ),
                )
            }

            items.forEach { item ->
                if (item.pinned) {
                    renderables.addAll(item.getRenderables("  ".repeat(indent)))
                }
            }
            items.forEach { item ->
                if (!item.pinned) {
                    renderables.addAll(item.getRenderables("  ".repeat(indent)))
                }
            }
        }
        return renderables
    }
}
