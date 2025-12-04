package appeng.items;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.core.localization.GuiText;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellUpgrades;
import appeng.me.storage.CellInventoryHandler;
import appeng.tile.inventory.IAEStackInventory;

public abstract class AEBaseInfiniteCell extends AEBaseItem implements IStorageCell {

    @Override
    protected void addCheckedInformation(ItemStack stack, EntityPlayer player, List<String> lines,
            boolean displayMoreInfo) {
        final IMEInventoryHandler<?> inventory = AEApi.instance().registries().cell()
                .getCellInventory(stack, null, getStorageChannel());
        if (!(inventory instanceof CellInventoryHandler<?>handler)) {
            return;
        }
        ICellInventory<?> cellInventory = handler.getCellInv();
        if (cellInventory == null) return;

        if (handler.isPreformatted()) {
            lines.add(GuiText.Partitioned.getLocal());
            if (GuiScreen.isShiftKeyDown()) {
                int usedFilters = 0;
                ArrayList<String> filtersTexts = new ArrayList<>();
                for (int i = 0; i < cellInventory.getConfigAEInventory().getSizeInventory(); ++i) {
                    final IAEStack<?> s = cellInventory.getConfigAEInventory().getAEStackInSlot(i);
                    if (s != null) {
                        usedFilters++;
                        filtersTexts.add(s.getDisplayName());
                    }
                }
                lines.add(
                        GuiText.Filter.getLocal() + " ("
                                + usedFilters
                                + "/"
                                + cellInventory.getConfigAEInventory().getSizeInventory()
                                + ")"
                                + ": ");

                if (!filtersTexts.isEmpty()) {
                    lines.addAll(filtersTexts);
                }

            }
        }
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return 0;
    }

    @Override
    public long getBytesLong(ItemStack cellItem) {
        return 0;
    }

    @Override
    public int BytePerType(ItemStack cellItem) {
        return 0;
    }

    @Override
    public int getBytesPerType(ItemStack cellItem) {
        return 0;
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    @Override
    public boolean isStorageCell(ItemStack i) {
        return true;
    }

    @Override
    public double getIdleDrain() {
        return 0;
    }

    @Override
    public IAEStackInventory getConfigAEInventory(ItemStack is) {
        return new CellConfig(is);
    }

    @Override
    public IInventory getUpgradesInventory(ItemStack is) {
        return new CellUpgrades(is, 0);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {

    }
}
