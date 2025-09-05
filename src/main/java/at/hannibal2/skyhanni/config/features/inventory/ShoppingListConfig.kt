package at.hannibal2.skyhanni.config.features.inventory

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.core.config.Position
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

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
    @ConfigLink(owner = ShoppingListConfig::class, field = "enabled")
    val position: Position = Position(144, 139)
}
