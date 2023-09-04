package appeng.util.inv;

import appeng.api.config.Actionable;
import appeng.api.config.InsertionMode;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.tile.storage.TileChest;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;

public class AdaptorMEChest extends AdaptorIInventory {

    private final TileChest meChest;
    public AdaptorMEChest(TileChest meChest) {
        super(meChest.getInternalInventory());
        this.meChest = meChest;
    }

    @Override
    public ItemStack addItems(ItemStack toBeAdded, InsertionMode insertionMode) {
        if (meChest.getItemInventory() == null) {
            return toBeAdded;
        }
        // Ignore insertion mode since injecting into a ME system doesn't have this concept
        IAEItemStack result = (IAEItemStack) meChest.getItemInventory().injectItems(
                AEItemStack.create(toBeAdded),
                Actionable.MODULATE,
                meChest.getActionSource()
        );
        return result == null ? null : result.getItemStack();
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated, InsertionMode insertionMode) {
        if (meChest.getItemInventory() == null) {
            return toBeSimulated;
        }
        // Ignore insertion mode since injecting into a ME system doesn't have this concept
        IAEItemStack result = (IAEItemStack) meChest.getItemInventory().injectItems(
                AEItemStack.create(toBeSimulated),
                Actionable.SIMULATE,
                meChest.getActionSource()
        );
        return result == null ? null : result.getItemStack();
    }
}
