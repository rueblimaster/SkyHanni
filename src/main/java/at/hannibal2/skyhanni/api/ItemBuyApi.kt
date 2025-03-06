package at.hannibal2.skyhanni.api

import at.hannibal2.skyhanni.features.inventory.bazaar.BazaarApi
import at.hannibal2.skyhanni.features.inventory.bazaar.BazaarApi.isBazaarItem
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.ItemPriceUtils.isAuctionHouseItem
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.StringUtils.removeColor

object ItemBuyApi {

    fun NeuInternalName.buy(amount: Int) {
        when {
            isBazaarItem() -> BazaarApi.searchForBazaarItem(itemName, amount)
            isAuctionHouseItem() -> HypixelCommands.auctionSearch(itemName.removeColor())
            else -> ChatUtils.chat("Could not find $itemName§e on AH or BZ!", replaceSameMessage = true)
        }
    }

    fun NeuInternalName.createBuyTip(
        colorActive: LorenzColor = LorenzColor.YELLOW,
        colorInactive: LorenzColor = LorenzColor.RED,
        clickType: String = "Click",
    ): List<String> {
        val firstPart = "§${colorActive.chatColorCode}$clickType to search for $itemName §${colorActive.chatColorCode}in"

        return listOf(
            when {
                isBazaarItem() -> "$firstPart Bazaar!"
                isAuctionHouseItem() -> "$firstPart Auction House!"
                else -> "§${colorInactive.chatColorCode}Could not find $itemName §${colorInactive.chatColorCode}on AH or BZ!"
            },
        )
    }
}
