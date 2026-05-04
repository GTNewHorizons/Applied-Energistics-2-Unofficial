package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IProgressProvider;
import appeng.container.slot.SlotOutput;
import appeng.container.slot.SlotRestrictedInput;
import appeng.tile.misc.TileAdvancedInscriber;
import appeng.util.Platform;

public class ContainerAdvancedInscriber extends ContainerUpgradeable implements IProgressProvider {

    private final TileAdvancedInscriber tile;

    @GuiSync(2)
    public int maxProcessingTime = -1;

    @GuiSync(3)
    public int processingTime = -1;

    @GuiSync(7)
    public boolean topLocked = true;

    @GuiSync(8)
    public boolean bottomLocked = true;

    public ContainerAdvancedInscriber(final InventoryPlayer ip, final TileAdvancedInscriber te) {
        super(ip, te);
        this.tile = te;

        this.addSlotToContainer(
                new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.INSCRIBER_PLATE,
                        this.tile,
                        TileAdvancedInscriber.SLOT_TOP,
                        45,
                        16,
                        this.getInventoryPlayer()));
        this.addSlotToContainer(
                new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.INSCRIBER_INPUT,
                        this.tile,
                        TileAdvancedInscriber.SLOT_MIDDLE,
                        63,
                        39,
                        this.getInventoryPlayer()));
        this.addSlotToContainer(
                new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.INSCRIBER_PLATE,
                        this.tile,
                        TileAdvancedInscriber.SLOT_BOTTOM,
                        45,
                        62,
                        this.getInventoryPlayer()));

        this.addSlotToContainer(new SlotOutput(this.tile, TileAdvancedInscriber.SLOT_OUTPUT, 113, 40, -1));
    }

    @Override
    protected int getHeight() {
        return 176;
    }

    @Override
    protected int getToolboxY() {
        return 113;
    }

    @Override
    protected boolean supportCapacity() {
        return false;
    }

    @Override
    public int availableUpgrades() {
        return 5;
    }

    @Override
    public void detectAndSendChanges() {
        this.standardDetectAndSendChanges();

        if (Platform.isServer()) {
            this.maxProcessingTime = this.tile.getMaxProcessingTime();
            this.processingTime = this.tile.getProcessingTime();
            this.topLocked = this.tile.isTopLocked();
            this.bottomLocked = this.tile.isBottomLocked();
        }
    }

    @Override
    public int getCurrentProgress() {
        return this.processingTime;
    }

    @Override
    public int getMaxProgress() {
        return this.maxProcessingTime;
    }

    public void setLock(final String slot, final boolean locked) {
        switch (slot) {
            case "top" -> this.tile.setTopLocked(locked);
            case "bottom" -> this.tile.setBottomLocked(locked);
            default -> {}
        }
    }
}
