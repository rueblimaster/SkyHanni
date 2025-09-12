package at.hannibal2.skyhanni.api

import at.hannibal2.skyhanni.api.pet.PetStorageApi
import at.hannibal2.skyhanni.data.BankApi
import at.hannibal2.skyhanni.data.BitsApi
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.PurseApi
import at.hannibal2.skyhanni.data.QuiverApi
import at.hannibal2.skyhanni.data.QuiverApi.amount
import at.hannibal2.skyhanni.data.SackApi
import at.hannibal2.skyhanni.data.SackItem
import at.hannibal2.skyhanni.data.StorageData
import at.hannibal2.skyhanni.data.model.SkyHanniInventoryContainer
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.PrimitiveItemStack
import at.hannibal2.skyhanni.utils.PrimitiveItemStack.Companion.toPrimitiveStackOrNull
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.subMapOfStringsStartingWith
import net.minecraft.item.ItemStack
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object StorageApi {
    @Suppress("unused")
    val currentStorage: SkyHanniInventoryContainer? get() = StorageData.currentStorage

    enum class StorageType(private val cache: StorageDataHolder) {
        Inventory(ItemStackHolder { InventoryUtils.getItemsInOwnInventory() }),
        Enderchest(InventoryTotalsCache { subMapOfStringsStartingWith("Ender Chest", StorageData.storage) }),
        Backpack(InventoryTotalsCache { subMapOfStringsStartingWith("Backpack", StorageData.storage) }),
        RiftStorage(InventoryTotalsCache { subMapOfStringsStartingWith("Rift Storage", StorageData.storage) }),
        IslandChest(IslandChestHolder),
        Sack(SackHolder),
        Quiver(QuiverHolder),
        Pets(ItemStackHolder { PetStorageApi.petStorage?.pets?.map { it.getItemStackOrNull() } ?: listOf() }),
        Purse(SimpleHolder { mapOf(NeuInternalName.SKYBLOCK_COIN to PurseApi.currentPurse) }),
        Bank(SimpleHolder { mapOf(NeuInternalName.SKYBLOCK_COIN to BankApi.totalCoins) }),
        Bits(SimpleHolder { mapOf(NeuInternalName.SKYBLOCK_BIT to BitsApi.bitsAvailable.toDouble()) }),
        // TODO: add wardrobe
        // TODO: add equipment
        // TODO: add fishing bag
        // TODO: add potion bag
        // TODO: add accessory bag
        // TODO: add time pocket
        // TODO: add event rewards?
        ;

        fun getTotal(name: NeuInternalName): Double = cache.getTotal(name)
        fun getAllTotals(): Map<NeuInternalName, Double> = cache.getAllTotals()
    }

    // <editor-fold desc="All individual holders">
    private object IslandChestHolder :
        InventoryTotalsCache(storageProvider = { subMapOfStringsStartingWith("Private Island Chest", StorageData.storage) }) {
        override fun getCacheDuration(): Duration =
            if (IslandType.PRIVATE_ISLAND.isCurrent()) super.getCacheDuration() else 5.seconds
    }

    private object SackHolder : StorageDataHolder {
        override fun getTotal(name: NeuInternalName): Double {
            val sackItem: SackItem = SackApi.sackData[name] ?: return 0.0
            if (!sackItem.statusIsCorrectOrAlright()) return 0.0
            return sackItem.amount.toDouble()
        }

        override fun getAllTotals(): Map<NeuInternalName, Double> =
            SackApi.sackData.filterValues { it.statusIsCorrectOrAlright() }.mapValues { it.value.amount.toDouble() }
    }

    private object QuiverHolder : StorageDataHolder {
        override fun getTotal(name: NeuInternalName): Double = QuiverApi.getArrowByNameOrNull(name)?.amount?.toDouble() ?: 0.0

        override fun getAllTotals(): Map<NeuInternalName, Double> =
            QuiverApi.arrowTypes.associate { it.internalName to it.amount.toDouble() }
    }
    // </editor-fold>

    fun NeuInternalName.getAmountIn(storageType: StorageType): Double = storageType.getTotal(this)
    fun NeuInternalName.getAmountIn(storageTypes: Collection<StorageType>): Double = storageTypes.sumOf { it.getTotal(this) }
    fun NeuInternalName.getAmountNotIn(storageType: StorageType): Double = getAmountIn(StorageType.entries.minus(storageType))
    fun NeuInternalName.getAmountNotIn(storageTypes: Collection<StorageType>): Double =
        getAmountIn(StorageType.entries.minus(storageTypes.toSet()))

    fun NeuInternalName.getTotalAmount(): Double = getAmountIn(StorageType.entries)

    fun NeuInternalName.getAllAmounts(): Map<StorageType, Double> = StorageType.entries.associateWith { it.getTotal(this) }

    private interface StorageDataHolder {
        fun getTotal(name: NeuInternalName): Double
        fun getAllTotals(): Map<NeuInternalName, Double>
    }

    private class SimpleHolder(
        val getAllAmounts: () -> Map<NeuInternalName, Double>,
    ) : StorageDataHolder {
        override fun getTotal(name: NeuInternalName): Double = getAllTotals().getOrDefault(name, 0.0)
        override fun getAllTotals(): Map<NeuInternalName, Double> = getAllAmounts()
    }

    private abstract class SimpleCachedHolder(
        private val fixedCacheDuration: Duration = 0.2.seconds,
    ) : StorageDataHolder {
        // override this function for adaptive cache duration
        open fun getCacheDuration(): Duration {
            return fixedCacheDuration
        }

        abstract fun loadAllTotals(): Map<NeuInternalName, Double>

        private var totalsCache: Map<NeuInternalName, Double> = emptyMap()
        private var lastCacheTime: SimpleTimeMark = SimpleTimeMark.farPast()

        private fun refreshIfNeeded() {
            if (lastCacheTime.passedSince() > getCacheDuration()) {
                totalsCache = loadAllTotals()
                lastCacheTime = SimpleTimeMark.now()
            }
        }

        override fun getTotal(name: NeuInternalName): Double {
            refreshIfNeeded()
            return totalsCache[name] ?: 0.0
        }

        override fun getAllTotals(): Map<NeuInternalName, Double> {
            refreshIfNeeded()
            return totalsCache
        }
    }

    private open class ItemStackHolder(
        fixedCacheDuration: Duration = 0.2.seconds,
        private val itemStacksProvider: () -> Collection<ItemStack?>,
    ) : SimpleCachedHolder(fixedCacheDuration) {
        override fun loadAllTotals(): Map<NeuInternalName, Double> {
            return itemStacksProvider().mapNotNull { it?.toPrimitiveStackOrNull() }.groupingBy { it.internalName }
                .fold(0.0) { accumulator: Double, element: PrimitiveItemStack -> accumulator + element.amount }
        }
    }

    private open class InventoryTotalsCache(
        fixedCacheDuration: Duration = 0.2.seconds,
        private val storageProvider: () -> Map<String, SkyHanniInventoryContainer>,
    ) : ItemStackHolder(fixedCacheDuration, { storageProvider().values.flatMap { it.items } })
}
