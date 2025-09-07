package at.hannibal2.skyhanni.api

import at.hannibal2.skyhanni.data.BankApi
import at.hannibal2.skyhanni.data.BitsApi
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.PurseApi
import at.hannibal2.skyhanni.data.StorageData
import at.hannibal2.skyhanni.data.model.SkyHanniInventoryContainer
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.PrimitiveItemStack.Companion.toPrimitiveStackOrNull
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.subMapOfStringsStartingWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object StorageApi {
    @Suppress("unused")
    val currentStorage: SkyHanniInventoryContainer? get() = StorageData.currentStorage

    enum class StorageType(private val cache: StorageDataProvider) {
        Enderchest(InventoryTotalsCache { subMapOfStringsStartingWith("Ender Chest", StorageData.storage) }),
        Backpack(InventoryTotalsCache { subMapOfStringsStartingWith("Backpack", StorageData.storage) }),
        RiftStorage(InventoryTotalsCache { subMapOfStringsStartingWith("Rift Storage", StorageData.storage) }),
        IslandChest(
            InventoryTotalsCache(
                { if (IslandType.PRIVATE_ISLAND.isCurrent()) 0.2.seconds else 5.seconds },
            ) { subMapOfStringsStartingWith("Private Island Chest", StorageData.storage) },
        ),
        Purse(
            object : SimpleProvider {
                override fun getAllTotals(): Map<NeuInternalName, Double> = mapOf(NeuInternalName.SKYBLOCK_COIN to PurseApi.currentPurse)
            },
        ),
        Bank(
            object : SimpleProvider {
                override fun getAllTotals(): Map<NeuInternalName, Double> = mapOf(NeuInternalName.SKYBLOCK_COIN to BankApi.totalCoins)
            },
        ),
        Bits(
            object : SimpleProvider {
                override fun getAllTotals(): Map<NeuInternalName, Double> =
                    mapOf(NeuInternalName.SKYBLOCK_BIT to BitsApi.bitsAvailable.toDouble())
            },
        ),
        // TODO: add sacks and other kind of bags
        // TODO: add waredrobe
        ;

        fun getTotal(name: NeuInternalName): Double = cache.getTotal(name)
        fun getAllTotals(): Map<NeuInternalName, Double> = cache.getAllTotals()
    }

    fun NeuInternalName.getAmountIn(storageType: StorageType): Double = storageType.getTotal(this)
    fun NeuInternalName.getAmountIn(storageTypes: Collection<StorageType>): Double = storageTypes.sumOf { it.getTotal(this) }
    fun NeuInternalName.getAmountNotIn(storageType: StorageType): Double = getAmountIn(StorageType.entries.minus(storageType))
    fun NeuInternalName.getAmountNotIn(storageTypes: Collection<StorageType>): Double =
        getAmountIn(StorageType.entries.minus(storageTypes.toSet()))

    fun NeuInternalName.getTotalAmount(): Double = getAmountIn(StorageType.entries)

    fun NeuInternalName.getAllAmounts(): Map<StorageType, Double> =
        StorageType.entries.associateWith { it.getTotal(this) }

    private sealed interface StorageDataProvider {
        fun getTotal(name: NeuInternalName): Double
        fun getAllTotals(): Map<NeuInternalName, Double>
    }

    private interface SimpleProvider : StorageDataProvider {
        override fun getTotal(name: NeuInternalName): Double = getAllTotals().getOrDefault(name, 0.0)
    }

    private class InventoryTotalsCache(
        private val cacheDurationProvider: () -> Duration = { 0.2.seconds },
        private val storageProvider: () -> Map<String, SkyHanniInventoryContainer>,
    ) : StorageDataProvider {
        private var totalsCache: Map<NeuInternalName, Double> = emptyMap()
        private var lastCacheTime: SimpleTimeMark = SimpleTimeMark.farPast()

        private fun refreshIfNeeded() {
            if (lastCacheTime.passedSince() > cacheDurationProvider()) {
                totalsCache = storageProvider().values
                    .flatMap { it.items }
                    .mapNotNull { it?.toPrimitiveStackOrNull() }
                    .groupingBy { it.internalName }
                    .fold(0.0) { acc, stack -> acc + stack.amount }

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
}
