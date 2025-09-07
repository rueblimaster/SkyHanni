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

    enum class Account {
        Coop,
        Personal,
    }

    private var lastVisitedAccount: Account? = null

    init {
        InventoryDetector(::onBankOpen) { it.contains("Bank") }
    }

    private fun onBankOpen(event: InventoryFullyOpenedEvent) {
        when {
            event.inventoryName == "Bank" -> {
                event.inventoryItems.values.forEach {
                    if (it.displayName == " ") return@forEach
                    balancePattern.firstMatcher(it.getLore()) {
                        val balance: Double = group("amount").formatDouble()
                        when {
                            coopAccountPattern.matches(it.displayName.removeColor()) -> coopCoins = balance
                            personalAccountPattern.matches(it.displayName.removeColor()) -> personalCoins = balance
                        }
                    }
                }
            }

            coopAccountPattern.matches(event.inventoryName) -> {
                lastVisitedAccount = Account.Coop
                loop@ for (it in event.inventoryItems.values) {
                    balancePattern.firstMatcher(it.getLore()) {
                        coopCoins = group("amount").formatDouble()
                        break@loop
                    }
                }
            }

            personalAccountPattern.matches(event.inventoryName) -> {
                lastVisitedAccount = Account.Personal
                loop@ for (it in event.inventoryItems.values) {
                    balancePattern.firstMatcher(it.getLore()) {
                        personalCoins = group("amount").formatDouble()
                        break@loop
                    }
                }
            }
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (event.item == null) return
        val lore = event.item.getLore()
        var change = 0.0
        depositPattern.firstMatcher(lore) {
            change += group("amount").formatDouble()
        }
        withdrawPattern.firstMatcher(lore) {
            if (change != 0.0) error("deposit pattern already matched but withdraw pattern did also match.")
            change -= group("amount").formatDouble()
        }
        if (change == 0.0) return
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
}
