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
import at.hannibal2.skyhanni.utils.InventoryUtils.getAmountInInventory
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.PrimitiveItemStack.Companion.toPrimitiveStackOrNull
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.subMapOfStringsStartingWith
import net.minecraft.item.ItemStack
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object StorageApi {
    @Suppress("unused")
    val currentStorage: SkyHanniInventoryContainer? get() = StorageData.currentStorage

    enum class StorageType(private val cache: StorageDataProvider) {
        Inventory(
            object : CachedProvider(itemProvider = { InventoryUtils.getItemsInOwnInventory() }) {
                override fun getTotal(name: NeuInternalName): Double = name.getAmountInInventory().toDouble()
            },
        ),
        Enderchest(InventoryTotalsCache { subMapOfStringsStartingWith("Ender Chest", StorageData.storage) }),
        Backpack(InventoryTotalsCache { subMapOfStringsStartingWith("Backpack", StorageData.storage) }),
        RiftStorage(InventoryTotalsCache { subMapOfStringsStartingWith("Rift Storage", StorageData.storage) }),
        IslandChest(
            InventoryTotalsCache(
                { if (IslandType.PRIVATE_ISLAND.isCurrent()) 0.2.seconds else 5.seconds },
            ) { subMapOfStringsStartingWith("Private Island Chest", StorageData.storage) },
        ),
        Sack(
            object : StorageDataProvider {
                override fun getTotal(name: NeuInternalName): Double {
                    val sackItem: SackItem = SackApi.sackData[name] ?: return 0.0
                    if (!sackItem.statusIsCorrectOrAlright()) return 0.0
                    return sackItem.amount.toDouble()
                }

                override fun getAllTotals(): Map<NeuInternalName, Double> =
                    SackApi.sackData.filterValues { it.statusIsCorrectOrAlright() }.mapValues { it.value.amount.toDouble() }
            },
        ),
        Quiver(
            object : StorageDataProvider {
                override fun getTotal(name: NeuInternalName): Double = QuiverApi.getArrowByNameOrNull(name)?.amount?.toDouble() ?: 0.0

                override fun getAllTotals(): Map<NeuInternalName, Double> =
                    QuiverApi.arrowTypes.associate { it.internalName to it.amount.toDouble() }
            },
        ),
        Pets(
            object : SimpleProvider {
                override fun getAllTotals(): Map<NeuInternalName, Double> {
                    val pets = PetStorageApi.petStorage?.pets ?: return mapOf()
                    return pets.groupBy { it.petInternalName }.mapValues { it.value.size.toDouble() }
                }
            },
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

    private interface StorageDataProvider {
        fun getTotal(name: NeuInternalName): Double
        fun getAllTotals(): Map<NeuInternalName, Double>
    }

    private interface SimpleProvider : StorageDataProvider {
        override fun getTotal(name: NeuInternalName): Double = getAllTotals().getOrDefault(name, 0.0)
    }

    private abstract class CachedProvider(
        private val cacheDurationProvider: () -> Duration = { 0.2.seconds },
        private val itemProvider: () -> Collection<ItemStack>,
    ) : StorageDataProvider {

        private var totalsCache: Map<NeuInternalName, Double> = emptyMap()
        private var lastCacheTime: SimpleTimeMark = SimpleTimeMark.farPast()

        private fun refreshIfNeeded() {
            if (lastCacheTime.passedSince() > cacheDurationProvider()) {
                totalsCache =
                    itemProvider().mapNotNull { it.toPrimitiveStackOrNull() }.groupingBy { it.internalName }
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

    private class InventoryTotalsCache(
        cacheDurationProvider: () -> Duration = { 0.2.seconds },
        private val storageProvider: () -> Map<String, SkyHanniInventoryContainer>,
    ) : CachedProvider(cacheDurationProvider, { storageProvider().values.flatMap { it.items }.filterNotNull() })
}
