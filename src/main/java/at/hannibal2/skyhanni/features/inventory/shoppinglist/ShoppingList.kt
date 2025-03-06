package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
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
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.setLore
import at.hannibal2.skyhanni.utils.LorenzColor
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
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack

@SkyHanniModule
object ShoppingList {
    private val config get() = SkyHanniMod.feature.inventory.shoppingList

    private val storage: ProfileSpecificStorage.ShoppingListStorage? get() = ProfileStorageData.profileSpecific?.shoppingList

    private var isConfigLoaded = false
    private val categories: MutableList<ShoppingListCategory> = mutableListOf()
    private var items: ShoppingListCategory = ShoppingListCategory("Items")

    object ItemsOverall {
        private val allItems: MutableMap<NeuInternalName, Pair<Double, Int>> = mutableMapOf()

        fun update() {
            if (!isConfigLoaded) return
            val items = items

            allItems.clear()
            for (category in categories + items) {

                category.getItemsOverall().forEach { (name, pair: Pair<Double, Int>) ->
                    if (allItems.containsKey(name)) {
                        allItems[name]?.let { entry ->
                            allItems[name] = Pair(entry.first + pair.first, entry.second + pair.second)
                        }
                    } else {
                        allItems[name] = pair
                    }
                }
            }
        }

        fun getItems(): MutableMap<NeuInternalName, Pair<Double, Int>> {
            return allItems
        }

        override fun toString(): String {
            var result = "ItemsOverall("

            for ((item, pair) in allItems) {
                result += ("\nItem: $item, Amount: ${pair.first}, in items: ${pair.second}")
            }

            result += "\n)"
            return result
        }

        fun get(item: NeuInternalName) = allItems[item]
    }

    // TODO soon: somehow also make it searchable?
    private var display = listOf<Renderable>()

    private var inventoryOpen = false

    var currentlyOpenRecipe: PrimitiveRecipe? = null
    var displayItem: ItemStack? = null

    // all the functions for interacting with the shopping list come here
    fun add(itemName: NeuInternalName, amount: Double = 1.0, categoryName: String? = null) {
        if (!isConfigLoaded) return
        val items = items

        // TODO: shouldn't happen @Thunderblade73
        if (!isEnabled()) return

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

    fun addCategory(categoryName: String, color: LorenzColor? = null, saveInStorage: Boolean = true) {
        if (!isEnabled()) return
        if (!isConfigLoaded) return

        if (categories.any { it.name == categoryName }) return

        if (color == null) {
            categories.add(ShoppingListCategory(categoryName, saveInStorage = saveInStorage))
        } else {
            categories.add(ShoppingListCategory(categoryName, color = color, saveInStorage = saveInStorage))
        }

        update()
    }

    fun removeCategory(categoryName: String) {
        if (!isEnabled()) return
        if (!isConfigLoaded) return

        val category = categories.firstOrNull { it.name == categoryName } ?: return
        categories.remove(category)
        update()
    }

    fun removeCategory(category: ShoppingListCategory) {
        if (!isEnabled()) return
        if (!isConfigLoaded) return

        categories.remove(category)
        update()
    }

    fun remove(name: String, amount: Double? = null, categoryName: String? = null) {
        if (!isEnabled()) return
        if (!isConfigLoaded) return

        val itemName: NeuInternalName? = name.toInternalName()

        if (itemName == null || !itemName.isKnownItem()) {
            handleItemNotFound(name)
        } else if (categoryName != null) {
            removeFromCategory(itemName, categoryName, amount)
        } else {
            removeItemFromItemsOrCategories(itemName, amount)
        }

        update()
    }

    private fun handleItemNotFound(name: String) {
        if (name.isCategory()) {
            removeCategory(name)
        } else {
            ChatUtils.userError("Item $name not found")
        }
    }

    private fun removeFromCategory(itemName: NeuInternalName, categoryName: String, amount: Double?) {
        if (!categoryName.isCategory()) {
            ChatUtils.userError("Category $categoryName not found")
            return
        }

        val category = categories.firstOrNull { it.name == categoryName }
        category?.remove(itemName, amount) ?: ChatUtils.userError("Category $categoryName not found")
    }

    private fun removeItemFromItemsOrCategories(itemName: NeuInternalName, amount: Double?) {
        if (items.contains(itemName)) {
            items.remove(itemName, amount)
        } else {
            removeFromMultipleCategories(itemName, amount)
        }
    }

    private fun removeFromMultipleCategories(itemName: NeuInternalName, amount: Double?) {
        var category: ShoppingListCategory? = null
        for (cat in categories) {
            if (cat.contains(itemName)) {
                if (category != null) {
                    ChatUtils.userError(
                        "Item ${itemName.itemName} found in multiple categories, please specify the category to remove from",
                    )
                    return
                }
                category = cat
            }
        }

        category?.remove(itemName, amount) ?: ChatUtils.userError("Item ${itemName.itemName} not found")
    }

    fun clear() {
        if (!isConfigLoaded) return

        categories.clear()
        items.clear()

        update()
    }

    // logic and related functions
    fun isEnabled() = LorenzUtils.inSkyBlock && config.enabled

    fun String.isCategory(): Boolean = categories.any { it.name == this }

    fun resetDisplayItem() {
        displayItem = null
    }

    fun createDisplayItem() {
        if (currentlyOpenRecipe == null) return
        if (displayItem != null && displayItem?.displayName == "§bSelect Recipe") return

        val lore = mutableListOf<String>()
        lore.add("§7Left click to add the recipe to the shopping list")
        lore.add("§7Right click to only add the result to the shopping list")

        displayItem = ItemStack(Blocks.diamond_block).setLore(lore).setStackDisplayName("§bAdd Recipe to shopping list")
    }

    fun isInventoryOpen() = inventoryOpen

    fun recheckInInventory() {
        if (!isEnabled()) return
        val currentlyOpen = inAnyInventory()
        if (inventoryOpen != currentlyOpen) {
            inventoryOpen = currentlyOpen
            update()
        }
    }

    fun loadShoppingList(forceOverwriteCurrent: Boolean = false) {
        if (isConfigLoaded && !forceOverwriteCurrent) return
        if (storage == null) return // technically not needed I guess

        val storedCategories = storage?.categories ?: return
        val storedItems = storage?.items ?: return

        categories.clear()
        for (category in storedCategories) {
            categories.add(ShoppingListCategory(category))
        }

        items = ShoppingListCategory(storedItems)

        isConfigLoaded = true
    }

    fun saveShoppingList() {
        if (!isConfigLoaded) return
        val items = items

        val tempCategories = mutableListOf<CategoryTemplate>()
        for (category in categories) {
            if (category.saveInStorage) {
                tempCategories.add(CategoryTemplate(category))
            }
        }

        ProfileStorageData.profileSpecific?.shoppingList?.categories = tempCategories
        ProfileStorageData.profileSpecific?.shoppingList?.items = CategoryTemplate(items)
    }

    fun moveCategoryToTop(category: ShoppingListCategory) {
        if (!isEnabled()) return
        if (!isConfigLoaded) return

        categories.remove(category)
        categories.add(0, category)

        update()
    }

    // all display related functions
    fun createDisplay() {
        if (!isConfigLoaded) return
        val items = items

        if (!isEnabled() || (categories.isEmpty() && items.isEmpty())) {
            display = emptyList()
            return
        }
        display = buildList {
            addString("§l" + "Shopping List")
            categories.forEach {
                addAll(it.getRenderables(1))
            }
            addAll(items.getRenderables(0, showThis = false))
        }
    }

    // other functions etc.
    fun update() {
        if (!isEnabled()) return
        if (!isConfigLoaded) return

        ItemsOverall.update()

        createDisplay()

        saveShoppingList()
    }

    fun test() {
        println("test triggered")

        println("is config loaded: $isConfigLoaded")
        println("categories: $categories")
        println("items: $items")

        clear()

        add("aspect of the end".toInternalName(), 2.0, categoryName = "Weapons")
        addCategory("Visitors", saveInStorage = false)
        add("enchanted carrot".toInternalName(), 49.0, categoryName = "Visitors")
        add("diamond".toInternalName(), 136.0)

        update()

        println("test done")
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

    @HandleEvent
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

        currentlyOpenRecipe = PrimitiveRecipe(ingredients, setOf(result ?: return), RecipeType.CRAFTING)

        createDisplayItem()

        recheckInInventory()
        update()
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun replaceItem(event: ReplaceItemEvent) {
        if (!isEnabled() || currentlyOpenRecipe == null) return
        if (event.inventory !is InventoryPlayer && event.slot == 51) {
            displayItem?.let { event.replace(it) }
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (!isEnabled()) return
        if (event.slotId != 51) return
        if (event.item == null) return

        if (!isConfigLoaded) return

        val currentlyOpenRecipe = currentlyOpenRecipe

        if (currentlyOpenRecipe == null) {
            return
        }

        if (event.item.displayName == "§bSelect Recipe") {
            event.cancel()
            for (category in categories + items) {
                if (category.onItemClicked(event.item)) {
                    closeInventory()
                    return
                }
            }
        } else if (event.item.displayName == "§bAdd Recipe to shopping list") {
            event.cancel()
            val output = currentlyOpenRecipe.output

            if (output == null) {
                ChatUtils.chat("Invalid Recipe with no output")
                return
            }

            if (event.clickedButton == 2) {
                items.add(output.internalName, 1.0)
            } else {
                items.add(output.internalName, 1.0, recipe = currentlyOpenRecipe)
            }

        }
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        loadShoppingList()
        update()
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onRender(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!isEnabled()) return
        config.position.renderRenderables(display, posLabel = "Shopping List")
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onRender(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!isEnabled()) return
        if (!inventoryOpen) {
            inventoryOpen = true
            update()
        }
        config.position.renderRenderables(display, posLabel = "Shopping List")
    }

    @HandleEvent
    fun onDebugDataCollect(event: DebugDataCollectEvent) {
        event.title("Shopping List")
        if (!isEnabled()) {
            event.addIrrelevant("Shopping List is disabled")
            return
        }

        if (!isConfigLoaded) {
            event.addIrrelevant("Shopping List is not loaded")
            return
        }

        if (categories.isEmpty() && items.isEmpty()) {
            event.addIrrelevant("Shopping List is empty")
            return
        }

        event.addData {
            categories.forEach {
                add("§${it.color.chatColorCode}${it.name}")
                it.items.forEach { item ->
                    add("  $item")
                }
            }

            add("")

            items.items.forEach { item ->
                add(item.toString())
            }

            add("")

            add("ItemsOverall:")
            for ((item, pair) in ItemsOverall.getItems()) {
                add("  Item: ${item.itemNameWithoutColor}, Amount: ${pair.first}, in items: ${pair.second}")
            }
        }
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
            aliases = listOf("shsladd", "shsla")
            autoComplete { listOf("Carrot", "Potato", "Wheat") }
            callback { add(it[0].toInternalName(), it.getOrNull(1)?.toDoubleOrNull() ?: 1.0, it.getOrNull(2)) }
        }
        event.register("shshoppinglistremove") {
            description = "Remove an item from the shopping list"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("shslremove")
            callback { remove(it[0], it.getOrNull(1)?.toDoubleOrNull(), it.getOrNull(2)) }
        }
        event.register("shshoppinglistremovecategory") {
            description = "Remove a category from the shopping list"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("shslremovecategory")
            callback { removeCategory(it[0]) }
        }
//         TODO (maybe): add a hide command

//         TODO : implement set
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
