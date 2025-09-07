package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.model.TabWidget
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.WidgetUpdateEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryDetector
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.groupOrNull
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object BankApi {
    // TODO: make the values change when some money is taken from the system for some other action

    var dirty: Boolean = true

    val totalCoins: Double get() = coopCoins + personalCoins
    var coopCoins: Double = 0.0
        set(value) {
            field = value
            dirty = false
        }
    var personalCoins: Double = 0.0
        set(value) {
            field = value
            dirty = false
        }

    private val patternGroup = RepoPattern.group("inventory.bank")

    @HandleEvent
    fun onWidgetUpdate(event: WidgetUpdateEvent) {
        if (!event.isWidget(TabWidget.BANK)) return

        event.widget.matchMatcherFirstLine {
            println("amount ${group("amount")}")
            println("personal ${groupOrNull("personal")}")
            if (groupOrNull("personal") != null) {
                // in a coop
                if (group("amount") != coopCoins.toInt().shortFormat()) dirty = true
                if (groupOrNull("personal") != personalCoins.toInt().shortFormat()) dirty = true
            } else {
                // not in a coop
                if (group("amount") != personalCoins.toInt().shortFormat()) dirty = true
            }
        }
    }

    private fun onBankOpen(event: InventoryFullyOpenedEvent) {
        when {
            event.inventoryName == "Bank" -> {
                event.inventoryItems.values.forEach {
                    balancePattern.firstMatcher(it.getLore()) {
                        val balance: Double = group("amount").toDouble()
                        when {
                            coopAccountPattern.matches(it.displayName.removeColor()) -> coopCoins = balance
                            personalAccountPattern.matches(it.displayName.removeColor()) -> personalCoins = balance
                        }
                    }
                }
            }

            coopAccountPattern.matches(event.inventoryName) -> {
                loop@ for (it in event.inventoryItems.values) {
                    balancePattern.firstMatcher(it.getLore()) {
                        coopCoins = group("amount").toDouble()
                        break@loop
                    }
                }
            }

            personalAccountPattern.matches(event.inventoryName) -> {
                loop@ for (it in event.inventoryItems.values) {
                    balancePattern.firstMatcher(it.getLore()) {
                        personalCoins = group("amount").toDouble()
                        break@loop
                    }
                }
            }
        }
    }

    init {
        InventoryDetector(::onBankOpen) { it.contains("Bank") }
    }

    /**
     * REGEX-TEST: §7Balance: §642,969,320.5
     * REGEX-TEST: §7Current balance: §642,969,320.5
     * REGEX-TEST: §7Balance: §60
     * REGEX-TEST: §7Current balance: §60
     */
    private val balancePattern by patternGroup.pattern(
        "balance",
        "(Balance|Current balance): (?<amount>[^§]+)",
    )

    /**
     * REGEX-TEST: Co-op Bank Account
     */
    private val coopAccountPattern by patternGroup.pattern(
        "account.coop",
        "(Co-op Bank Account)",
    )

    /**
     * REGEX-TEST: Personal Bank Account
     */
    private val personalAccountPattern by patternGroup.pattern(
        "account.personal",
        "(Personal Bank Account)",
    )
}
