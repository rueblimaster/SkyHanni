package at.hannibal2.skyhanni.config.features.inventory

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.core.config.Position
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class ShoppingListConfig {
    @Expose
    @ConfigOption(name = "Enabled", desc = "Enables the Shopping List.")
    @ConfigEditorBoolean
    @FeatureToggle
    var enabled: Boolean = true

    @Expose
    @ConfigOption(name = "Ignore Blocks of Ores", desc = "Ignores Blocks of Ores like a Block of Diamond when resolving recipes.")
    @ConfigEditorBoolean
    var ignoreBlocksOfOres: Boolean = true

    @Expose
    @ConfigOption(name = "Item Format", desc = "Drag text to change the appearance of the items.")
    @ConfigEditorDraggableList
    val itemFormat: Property<MutableList<ItemDisplayEntry>> = Property.of(
        mutableListOf(
            ItemDisplayEntry.AMOUNT_IN_RECIPE,
            ItemDisplayEntry.X_GRAY_RECIPE_1,
            ItemDisplayEntry.SPACE_RECIPE_1,
            ItemDisplayEntry.ITEM_NAME,
            ItemDisplayEntry.AMOUNT_POSSESSED,
            ItemDisplayEntry.SLASH_YELLOW_1,
            ItemDisplayEntry.AMOUNT_TOTAL,
        ),
    )

    @Suppress("unused")
    enum class ItemDisplayEntry(private val displayName: String, private val overrideString: String? = null) {
        // special characters
        SPACE_1("§7(space)", " "),
        SPACE_RECIPE_1("§7(space (only if in recipe))", " "),
        SLASH_YELLOW_1("§e/"),
        X_GRAY_RECIPE_1("§7x §7(only if in recipe)", "§7x"),
        X_YELLOW_1("§ex"),

        // stuff with values
        ITEM_NAME("§aEnchanted Carrot §7(color corresponding to rarity)"),
        ITEM_NAME_WITHOUT_RARITY_COLOR("§fEnchanted Carrot §7(always white)"),
        AMOUNT_IN_RECIPE("§716 §7(amount in recipe (only if in recipe))"),
        AMOUNT_TOTAL("§e3 §7(total amount)"),
        AMOUNT_POSSESSED("§e2 §7(amount possessed)"),
        ;

        override fun toString() = displayName
        fun asString() = overrideString ?: displayName
    }

    @Expose
    @ConfigLink(owner = ShoppingListConfig::class, field = "enabled")
    val position: Position = Position(144, 139)
}
