package at.hannibal2.skyhanni.features.inventory.shoppinglist

import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import com.google.gson.annotations.Expose

class ShoppingListItem(
    @Expose
    val internalName: NeuInternalName,
    @Expose
    var amount: Int,
) {
    override fun toString(): String {
        return "$internalName x$amount"
    }

    fun buildDisplay(): List<Renderable> {
        return listOf(Renderable.text("${internalName.repoItemName}§f x$amount"))
    }
}
