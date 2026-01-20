package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.model.TabWidget
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.WidgetUpdateEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryDetector
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.NumberUtil.formatDouble
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.groupOrNull
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.compat.formattedTextCompatLeadingWhiteLessResets
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object BankApi {
    // TODO: make the values change when some money is taken from the system for some other action

    var dirty: Boolean = true

    val totalCoins: Double get() = coopCoins + personalCoins
    var coopCoins: Double = 0.0
        private set(value) {
            field = value
            dirty = false
        }
    var personalCoins: Double = 0.0
        private set(value) {
            field = value
            dirty = false
        }

    enum class Account {
        Coop,
        Personal,
    }

    private var lastVisitedAccount: Account? = null

    // <editor-fold desc="Patterns">

    private val patternGroup = RepoPattern.group("inventory.bank")

    /**
     * REGEX-TEST: §7Balance: §642,969,320.5
     * REGEX-TEST: §7Balance: §60
     * REGEX-TEST: §7Current balance: §642,969,320.5
     * REGEX-TEST: §7Current balance: §60
     */
    private val balancePattern by patternGroup.pattern(
        "balance",
        "§7(?:Balance|Current balance): §6(?<amount>[^§]+)",
    )

    /**
     * REGEX-TEST: §7Amount to deposit: §642,972,046
     * REGEX-TEST: §7Amount to deposit: §60
     */
    private val depositPattern by patternGroup.pattern(
        "deposit",
        "§7Amount to deposit: §6(?<amount>[^§]+)",
    )

    /**
     * REGEX-TEST: §7Amount to withdraw: §642,969,320.5
     * REGEX-TEST: §7Amount to withdraw: §60
     */
    private val withdrawPattern by patternGroup.pattern(
        "withdraw",
        "§7Amount to withdraw: §6(?<amount>[^§]+)",
    )

    /**
     * REGEX-TEST: Co-op Bank Account
     */
    private val coopAccountPattern by patternGroup.pattern(
        "account.coop",
        "Co-op Bank Account",
    )

    /**
     * REGEX-TEST: Personal Bank Account
     */
    private val personalAccountPattern by patternGroup.pattern(
        "account.personal",
        "Personal Bank Account",
    )
    // </editor-fold>

    init {
        InventoryDetector(::onBankOpen) { it.contains("Bank") }
    }

    private fun onBankOpen(event: InventoryFullyOpenedEvent) {
        when {
            event.inventoryName == "Bank" -> parseInventoryBank(event)
            coopAccountPattern.matches(event.inventoryName) -> parseInventoryCoopAccount(event)
            personalAccountPattern.matches(event.inventoryName) -> parseInventoryPersonalAccount(event)
        }
    }

    private fun parseInventoryBank(event: InventoryFullyOpenedEvent) {
        event.inventoryItems.values.forEach {
            if (it.displayName.formattedTextCompatLeadingWhiteLessResets() == " ") return@forEach

            balancePattern.firstMatcher(it.getLore()) {
                val balance: Double = group("amount").formatDouble()
                when {
                    coopAccountPattern.matches(
                        it.displayName.formattedTextCompatLeadingWhiteLessResets().removeColor(),
                    ) -> coopCoins = balance

                    personalAccountPattern.matches(
                        it.displayName.formattedTextCompatLeadingWhiteLessResets().removeColor(),
                    ) -> personalCoins = balance
                }
            }
        }
    }

    private fun parseInventoryCoopAccount(event: InventoryFullyOpenedEvent) {
        lastVisitedAccount = Account.Coop
        for (it in event.inventoryItems.values) {
            balancePattern.firstMatcher(it.getLore()) {
                coopCoins = group("amount").formatDouble()
                return
            }
        }
    }

    private fun parseInventoryPersonalAccount(event: InventoryFullyOpenedEvent) {
        lastVisitedAccount = Account.Personal
        for (it in event.inventoryItems.values) {
            balancePattern.firstMatcher(it.getLore()) {
                personalCoins = group("amount").formatDouble()
                return
            }
        }
    }

    private fun parseChangeUponClick(lore: List<String>): Double? {
        var change: Double? = null
        depositPattern.firstMatcher(lore) {
            change = group("amount").formatDouble()
        }
        withdrawPattern.firstMatcher(lore) {
            if (change != null) error("deposit pattern already matched but withdraw pattern did also match.")
            change = -group("amount").formatDouble()
        }
        return change
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (event.item == null) return
        val change = parseChangeUponClick(event.item.getLore()) ?: return

        when (lastVisitedAccount) {
            Account.Coop -> coopCoins += change
            Account.Personal -> personalCoins += change
            null -> {}
        }
    }

    private fun Number.shortFormatLikeSkyblock() = toInt().shortFormat(round = true)

    @HandleEvent
    fun onWidgetUpdate(event: WidgetUpdateEvent) {
        if (!event.isWidget(TabWidget.BANK)) return

        event.widget.matchMatcherFirstLine {
            if (group("amount") == "...") return
            if (groupOrNull("personal") != null) {
                // in a coop
                if (group("amount") != coopCoins.shortFormatLikeSkyblock()) dirty = true
                if (groupOrNull("personal") != personalCoins.shortFormatLikeSkyblock()) dirty = true
            } else {
                // not in a coop
                if (group("amount") != personalCoins.toInt().shortFormat(round = true)) dirty = true
            }
        }
    }
}
