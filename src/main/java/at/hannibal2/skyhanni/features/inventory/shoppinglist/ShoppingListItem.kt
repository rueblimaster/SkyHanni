package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.api.StorageApi.getTotalAmount
import at.hannibal2.skyhanni.config.features.inventory.ShoppingListConfig
import at.hannibal2.skyhanni.features.inventory.shoppinglist.ShoppingListItem.Companion.ItemDisplayRepresentationManager.getDisplayRepresentation
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import at.hannibal2.skyhanni.utils.RecipeResolver
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import com.google.gson.annotations.Expose

class ShoppingListItem(
    @Expose
    val internalName: NeuInternalName,
    @Expose
    var amount: Double,
    recipe: PrimitiveRecipe? = null,
    @Expose
    val parentItem: ShoppingListItem? = null,
) {
    @Expose
    val recipeResolver: RecipeResolver = RecipeResolver(internalName, recipe, ignoreBlocksOfOres = ShoppingList.config.ignoreBlocksOfOres)

    @Expose
    var subitems = listOf<ShoppingListItem>()

    val totalAmount: Double
        get() = if (parentItem == null) amount else amount * parentItem.totalAmount

    fun getPossessedAmount(): Double {
        return internalName.getTotalAmount()
    }

    override fun toString(): String {
        return "${internalName.itemNameWithoutColor} x$amount: $recipeResolver"
    }

    private val breakDownPossible get() = recipeResolver.hasValidRecipes && subitems.isEmpty()

    private fun triggerBreakDown() {
        recipeResolver.resolveRecipe { breakDown() }
    }

    private fun breakDown() {
        if (!recipeResolver.resolved) return

        val numOutputPerCraft = recipeResolver.recipe?.output?.count ?: return

        val ingredients: Map<NeuInternalName, Double> =
            recipeResolver.recipe?.ingredients
                ?.groupBy { it.internalName }
                ?.mapValues { (_, list) -> list.sumOf { it.count } }
                ?: return

        subitems = ingredients.map { (internalName, amount) ->
            ShoppingListItem(internalName, amount / numOutputPerCraft, parentItem = this)
        }

        ShoppingList.update()
    }

    fun buildDisplay(indent: String = "", indentForSubitems: String? = null): List<Renderable> {
        return buildList {
            add(
                if (breakDownPossible) Renderable.clickable(
                    getDisplayRepresentation(this@ShoppingListItem, indent),
                    onLeftClick = ::triggerBreakDown,
                    tips = listOf("§7left-click to expand recipe"),
                ) else Renderable.text(
                    getDisplayRepresentation(this@ShoppingListItem, indent),
                ),
            )
            val actualIndentForSubitems: String = indentForSubitems ?: indent

            subitems.forEachIndexed { index, item ->
                val isLastItem = index == subitems.size - 1

                val newIndent = actualIndentForSubitems + if (!isLastItem) "|·" else "`·"

                val newIndentForSubitems = actualIndentForSubitems + if (!isLastItem) "| " else "  "

                addAll(item.buildDisplay(newIndent, newIndentForSubitems))
            }
        }
    }

    companion object {
        object ItemDisplayRepresentationManager {
            private sealed class DisplayEntryResolver {
                abstract val condition: (ShoppingListItem) -> Boolean
                abstract val replacesSpace: Boolean
                abstract fun getRepresentation(item: ShoppingListItem): String

                class Adaptive(
                    override val condition: (ShoppingListItem) -> Boolean = { true },
                    override val replacesSpace: Boolean = false,
                    private val representation: (ShoppingListItem) -> String,
                ) : DisplayEntryResolver() {
                    override fun getRepresentation(item: ShoppingListItem): String = representation(item)
                }

                class NonAdaptive(
                    private val value: String,
                    override val replacesSpace: Boolean = false,
                    override val condition: (ShoppingListItem) -> Boolean = { true },
                ) : DisplayEntryResolver() {
                    override fun getRepresentation(item: ShoppingListItem): String = value
                }
            }

            private fun Double.clean(): String =
                if (this % 1.0 == 0.0) this.toInt().toString() else this.roundTo(2).toString()

            private fun isInRecipe(item: ShoppingListItem) = item.parentItem != null

            private fun ShoppingListConfig.ItemDisplayEntry.toNonAdaptive(condition: (ShoppingListItem) -> Boolean = { true }) =
                DisplayEntryResolver.NonAdaptive(this.asString(), replacesSpace = true, condition = condition)

            private val displayEntries: Map<ShoppingListConfig.ItemDisplayEntry, DisplayEntryResolver> = mapOf(
                // special characters
                ShoppingListConfig.ItemDisplayEntry.SPACE_1 to ShoppingListConfig.ItemDisplayEntry.SPACE_1.toNonAdaptive(),
                ShoppingListConfig.ItemDisplayEntry.SPACE_RECIPE_1
                    to ShoppingListConfig.ItemDisplayEntry.SPACE_RECIPE_1.toNonAdaptive(condition = ::isInRecipe),
                ShoppingListConfig.ItemDisplayEntry.SLASH_YELLOW_1 to ShoppingListConfig.ItemDisplayEntry.SLASH_YELLOW_1.toNonAdaptive(),
                ShoppingListConfig.ItemDisplayEntry.X_GRAY_RECIPE_1
                    to ShoppingListConfig.ItemDisplayEntry.X_GRAY_RECIPE_1.toNonAdaptive(condition = ::isInRecipe),
                ShoppingListConfig.ItemDisplayEntry.X_YELLOW_1 to ShoppingListConfig.ItemDisplayEntry.X_YELLOW_1.toNonAdaptive(),
                // stuff with values
                ShoppingListConfig.ItemDisplayEntry.ITEM_NAME
                    to DisplayEntryResolver.Adaptive { it.internalName.repoItemName },
                ShoppingListConfig.ItemDisplayEntry.ITEM_NAME_WITHOUT_RARITY_COLOR
                    to DisplayEntryResolver.Adaptive { "§f${it.internalName.itemNameWithoutColor}" },
                ShoppingListConfig.ItemDisplayEntry.AMOUNT_IN_RECIPE
                    to DisplayEntryResolver.Adaptive(condition = ::isInRecipe) { "§7${it.amount.clean()}" },
                ShoppingListConfig.ItemDisplayEntry.AMOUNT_TOTAL
                    to DisplayEntryResolver.Adaptive { "§e${it.totalAmount.clean()}" },
                ShoppingListConfig.ItemDisplayEntry.AMOUNT_POSSESSED
                    to DisplayEntryResolver.Adaptive { "§e${it.getPossessedAmount().clean()}" },
            )

            init {
                val missingEntries = ShoppingListConfig.ItemDisplayEntry.entries.filter { !displayEntries.containsKey(it) }
                if (missingEntries.isNotEmpty()) {
                    error(
                        "not all ShoppingListConfig.ItemDisplayEntry entries are present in displayEntries. " +
                            "Missing entries: $missingEntries",
                    )
                }
            }

            private fun getResolvers(item: ShoppingListItem): List<DisplayEntryResolver> {
                val itemFormat = ShoppingList.config.itemFormat.get()

                return itemFormat.mapNotNull { entry ->
                    val resolver = displayEntries[entry]
                        ?: error("Missing resolver for ShoppingListConfig.ItemDisplayEntry: $entry")
                    if (resolver.condition(item)) resolver else null
                }

            }

            fun getDisplayRepresentation(item: ShoppingListItem, indent: String): String {
                val resolvers = getResolvers(item)

                return buildString {
                    append("§8$indent")

                    resolvers.zipWithNext { current, next ->
                        append(current.getRepresentation(item))
                        if (!(current.replacesSpace || next.replacesSpace)) {
                            append(" ")
                        }
                    }
                    // add last element explicitly
                    append(resolvers.last().getRepresentation(item))
                }
            }
        }
    }
}
