package appeng.items.storage;

import java.util.EnumSet;

import net.minecraft.item.ItemStack;

import com.google.common.base.Optional;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.api.exceptions.MissingDefinition;
import appeng.core.features.AEFeature;
import appeng.items.materials.MaterialType;

public class ItemAdvancedStorageCell extends ItemBasicStorageCell {

    @SuppressWarnings("Guava")
    public ItemAdvancedStorageCell(final MaterialType whichCell, final long kilobytes) {
        super(Optional.of(kilobytes + "k"));

        this.setFeature(EnumSet.of(AEFeature.XtremeStorageCells));
        this.setMaxStackSize(1);
        this.totalBytes = kilobytes * 1024;
        this.component = whichCell;

        switch (this.component) {
            case Cell256kPart -> {
                this.idleDrain = 2.5;
                this.perType = 2048;
            }
            case Cell1024kPart -> {
                this.idleDrain = 3.0;
                this.perType = 8192;
            }
            case Cell4096kPart -> {
                this.idleDrain = 3.5;
                this.perType = 32768;
            }
            case Cell16384kPart -> {
                this.idleDrain = 4.0;
                this.perType = 131072;
            }
            default -> {
                this.idleDrain = 0.0;
                this.perType = 8;
            }
        }
    }

    @Override
    protected IItemDefinition getStorageCellCase() {
        return AEApi.instance().definitions().materials().emptyAdvancedStorageCell();
    }

    @Override
    public ItemStack getContainerItem(final ItemStack itemStack) {
        for (final ItemStack stack : AEApi.instance().definitions().materials().emptyAdvancedStorageCell().maybeStack(1)
                .asSet()) {
            return stack;
        }
        throw new MissingDefinition("Tried to use empty storage cells while basic storage cells are defined.");
    }
}
