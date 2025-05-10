package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.container.slot.AppEngSlot;
import appeng.tile.misc.TileGrower;

public class ContainerGrower extends ContainerUpgradeable {

    private final TileGrower ti;

    public ContainerGrower(final InventoryPlayer ip, final TileGrower te) {
        super(ip, te);
        ti = te;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlotToContainer(new AppEngSlot(ti, x + y * 9, 8 + x * 18, 17 + y * 18) {

                    @Override
                    public boolean isItemValid(ItemStack is) {
                        return ti.isItemValidForSlot(0, is);
                    }

                });
            }
        }
    }

    @Override
    public void detectAndSendChanges() {
        standardDetectAndSendChanges();
    }

    @Override
    protected int getHeight() {
        return 166;
    }

    @Override
    /**
     * Overridden super.setupConfig to prevent setting up the fake slots
     */
    protected void setupConfig() {
        setupUpgrades();
    }

    @Override
    protected boolean supportCapacity() {
        return false;
    }

    @Override
    public int availableUpgrades() {
        return 3;
    }
}
