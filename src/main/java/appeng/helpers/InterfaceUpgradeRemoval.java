package appeng.helpers;

import net.minecraft.inventory.IInventory;

/** Non-mutating capacity check for remote upgrade removal. Local interface GUIs retain their normal drop behavior. */
public final class InterfaceUpgradeRemoval {

    private InterfaceUpgradeRemoval() {}

    public static boolean canRemoveCapacityCard(IInventory patterns, int remainingCards, int rowSize) {
        if (remainingCards < 0 || rowSize <= 0) return false;
        final long enabledSlots = ((long) remainingCards + 1) * rowSize;
        // Check exact disabled slots: a single pattern in the last row must block removal too.
        for (long slot = enabledSlots; slot < patterns.getSizeInventory(); slot++) {
            if (patterns.getStackInSlot((int) slot) != null) return false;
        }
        return true;
    }
}
