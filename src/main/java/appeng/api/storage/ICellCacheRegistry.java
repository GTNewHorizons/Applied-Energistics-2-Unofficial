package appeng.api.storage;

/**
 * For {@link appeng.me.storage.CellInventoryHandler}(or FluidCellInventoryHandler in AE2FC) to easily get bytes
 * informations
 */
public interface ICellCacheRegistry {

    boolean canGetInv();

    long getTotalBytes();

    long getFreeBytes();

    long getUsedBytes();

    long getTotalTypes();

    long getFreeTypes();

    long getUsedTypes();

    int getCellStatus();

    default StorageChannel getStorageChannel() {
        return StorageChannel.ITEMS;
    }

    TYPE getCellType();

    enum TYPE {
        ITEM,
        FLUID,
        ESSENTIA
    }

}
