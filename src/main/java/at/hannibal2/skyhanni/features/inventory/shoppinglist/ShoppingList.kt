package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.OwnInventoryItemUpdateEvent
import at.hannibal2.skyhanni.events.SackDataUpdateEvent
import at.hannibal2.skyhanni.events.entity.ItemAddInInventoryEvent
import at.hannibal2.skyhanni.events.minecraft.WorldChangeEvent
import at.hannibal2.skyhanni.events.render.gui.ReplaceItemEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.CollectionUtils.addString
import at.hannibal2.skyhanni.utils.InventoryUtils.closeInventory
import at.hannibal2.skyhanni.utils.InventoryUtils.inAnyInventory
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.PrimitiveIngredient
import at.hannibal2.skyhanni.utils.PrimitiveItemStack.Companion.toPrimitiveStackOrNull
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import at.hannibal2.skyhanni.utils.RecipeType
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.renderables.Renderable
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.item.ItemStack

@SkyHanniModule
object ShoppingList {
    private val config get() = SkyHanniMod.feature.inventory.shoppingList

    private val categories = mutableListOf<ShoppingListCategory>()
    private val items = ShoppingListCategory("Items")
    private val itemsOverall = ShoppingListCategory("Overall") // TODO

    // TODO: add a kind of summary over all items needed in recipes

    // TODO: somehow also make it searchable?
    private var display = listOf<Renderable>()

    private var inventoryOpen = false

    var currentlyOpenRecipe: PrimitiveRecipe? = null
    var displayItem: ItemStack? = null

    // all the functions for interacting with the shopping list come here
    fun add(itemName: NeuInternalName, amount: Double = 1.0, categoryName: String? = null) {
        // TODO: shouldn't happen @Thunderblade73
        if (!isEnabled()) return
        println("Adding ${itemName.itemName} x$amount to $categoryName")

        val category: ShoppingListCategory
        if (categoryName != null) {
            if (!categoryName.isCategory()) {
                category = ShoppingListCategory(categoryName)
                categories.add(category)
            } else {
                category = categories.firstOrNull { it.name == categoryName } ?: return
            }
        } else {
            category = items
        }
        category.add(itemName, amount)

        createDisplay()
    }

    fun removeCategory(categoryName: String) {
        if (!isEnabled()) return

        val category = categories.firstOrNull { it.name == categoryName } ?: return
        categories.remove(category)
    }

    // maybe name it removeCommand ???
    fun remove(name: String, amount: Double? = null, categoryName: String? = null) {
        if (!isEnabled()) return
        println("Removing $name x$amount from $categoryName")

        var itemName: NeuInternalName? = name.toInternalName()
        if (itemName == null || !itemName.isKnownItem()) {
            itemName = null
            if (!name.isCategory()) {
                ChatUtils.userError("Item $name not found")
            } else {
                removeCategory(name)
            }

        } else if (categoryName != null) {
            if (!categoryName.isCategory()) {
                ChatUtils.userError("Category $categoryName not found")
                return
            } else {
                val category = categories.firstOrNull { it.name == categoryName } ?: return

                category.remove(itemName, amount)
            }

        } else if (items.contains(itemName)) {
            items.remove(itemName, amount)

        } else {
            var category: ShoppingListCategory? = null
            for (cat in categories) {
                if (cat.contains(itemName)) {
                    if (category != null) {
                        ChatUtils.userError(
                            "Item ${itemName.itemName} found in multiple categories, " + "please specify the category to remove from",
                        )
                        return
                    }
                    category = cat
                }
            }
            if (category == null) {
                ChatUtils.userError("Item ${itemName.itemName} not found")
            } else {
                category.remove(itemName, amount)
            }
        }

        createDisplay()
    }

    fun clear() {
        categories.clear()
        items.clear()

        createDisplay()
    }

    // logic and related functions
    fun isEnabled() = LorenzUtils.inSkyBlock && config.enabled

    fun String.isCategory() = categories.any { it.name == this }

    fun resetDisplayItem() {
        displayItem = null
    }

    fun isInventoryOpen() = inventoryOpen

    fun recheckInInventory() {
        if (!isEnabled()) return
        var currentlyOpen = inAnyInventory()
        if (inventoryOpen != currentlyOpen) {
            inventoryOpen = currentlyOpen
            update()
//             println("Inventory open: $inventoryOpen")
        }
    }

    // all display related functions
    fun createDisplay() {
//         println("Creating display")
        if (!isEnabled() || (categories.isEmpty() && items.items.isEmpty())) {
            display = emptyList()
            return
        }
        display = buildList {
            addString("§l" + "Shopping List")
            categories.forEach {

                addString("${it.color.getChatColor()}§n" + it.name)

                addAll(it.getRenderables(1))
            }
            addAll(items.getRenderables(0))
        }
    }

    // other functions etc.
    fun update() {
        if (!isEnabled()) return

        createDisplay()
    }

    fun test() {
        ChatUtils.chat("test triggered")

        add("enchanted carrot".toInternalName(), 49.0)
        add("aspect of the end".toInternalName(), 1.0, "Weapons")
        add("diamond".toInternalName(), 1.0)

        createDisplay()

        ChatUtils.chat("test done")
    }

    fun InventoryFullyOpenedEvent.isRecipe() = inventoryName.contains("Recipe") && inventorySize == 54

    // all events come here
    @HandleEvent(onlyOnSkyblock = true)
    fun onOwnInventoryItemUpdate(event: OwnInventoryItemUpdateEvent) {
        update()
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onItemAddInInventoryEvent(event: ItemAddInInventoryEvent) {
        update()
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onSackUpdate(event: SackDataUpdateEvent) {
        update()
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onInventoryClose(event: InventoryCloseEvent) {
        recheckInInventory()
        update()
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onWorldChange(event: WorldChangeEvent) {
        recheckInInventory()
        update()
    }

    // this triggers only when opening another inventory, not the own inventory
    @HandleEvent(onlyOnSkyblock = true)
    fun onInventorOpen(event: InventoryFullyOpenedEvent) {
        if (!isEnabled()) return
        if (!event.isRecipe()) {
            currentlyOpenRecipe = null
            return
        }

        val ingredients = listOf(10, 11, 12, 13, 19, 20, 21, 28, 29, 30).mapNotNull {
            event.inventoryItems[it]?.toPrimitiveStackOrNull()?.toPrimitiveIngredient()
        }.toSet<PrimitiveIngredient>()

        val result = event.inventoryItems[25]?.toPrimitiveStackOrNull()?.toPrimitiveIngredient()

        println("Relevant items: $ingredients")
        currentlyOpenRecipe = PrimitiveRecipe(ingredients, setOf(result ?: return), RecipeType.CRAFTING)

        recheckInInventory()
        update()
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun replaceItem(event: ReplaceItemEvent) {
        if (!isEnabled()) return
        if (event.inventory !is InventoryPlayer && event.slot == 51) {
            displayItem?.let { event.replace(it) }
        }
    }

    @HandleEvent(onlyOnSkyblock = true, priority = HandleEvent.HIGH)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (!isEnabled()) return
        if (event.slotId != 51) return
        if (event.item == null) return

        println("Slot click event: ${event.item.displayName}")
        if (event.item.displayName == "§bSelect Recipe") {
            event.cancel()
            for (category in categories + items) {
                if (category.onItemClicked(event.item)) {
                    closeInventory()
                    return
                }
            }
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onRender(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!isEnabled()) return
//         println("Rendering shopping list, in GuiOverlayRenderEvent")
        config.position.renderRenderables(display, posLabel = "Shopping List")
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onRender(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!isEnabled()) return
//         println("Rendering shopping list, in ChestGuiOverlayRenderEvent")
        if (!inventoryOpen) {
            inventoryOpen = true
            update()
        }
//         recheckInInventory()
        config.position.renderRenderables(display, posLabel = "Shopping List")
    }

    // this event should be last
    // TODO: better argument handling
    @HandleEvent()
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shshoppinglistclear") {
            description = "Clear the shopping list"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("shslclear")
            callback { clear() }
        }
        event.register("shshoppinglistadd") {
            description = "Add an item to the shopping list"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("shsladd")
            autoComplete { listOf("Carrot", "Potato", "Wheat") }
            callback { add(it[0].toInternalName(), it.getOrNull(1)?.toDoubleOrNull() ?: 1.0, it.getOrNull(2)) }
        }
        event.register("shshoppinglistremove") {
            description = "Remove an item from the shopping list"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("shslremove")
            autoComplete { listOf("Carrot", "Potato", "Wheat") }
            callback { remove(it[0], it.getOrNull(1)?.toDoubleOrNull(), it.getOrNull(2)) }
        }
        event.register("shshoppinglistremovecategory") {
            description = "Remove a category from the shopping list"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("shslremovecategory")
            autoComplete { categories.map { category -> category.name } }
            callback { removeCategory(it[0]) }
        }
//         TODO: add a hide command
//         TODO: implement set
//         event.register("shshoppinglistset") {
//             description = "Set the amount of an item in the shopping list"
//             category = CommandCategory.USERS_ACTIVE
//             aliases = listOf("shslset")
//             callback { set(it[0].toInternalName(), it.getOrNull(1)?.toIntOrNull() ?: 1, it.getOrNull(2)) }
//         }
        event.register("shshoppinglistupdate") {
            description = "Update the shopping list"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("shslupdate")
            callback { update() }
        }
        event.register("shshoppinglisttest") {
            description = "Test the shopping list feature"
            category = CommandCategory.DEVELOPER_TEST
            aliases = listOf("shsltest")
            callback { test() }
        }

    }

}
