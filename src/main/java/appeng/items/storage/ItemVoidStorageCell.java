package appeng.items.storage;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellUpgrades;
import appeng.me.storage.VoidCellInventory;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemVoidStorageCell extends AEBaseItem implements ICellWorkbenchItem {

    public ItemVoidStorageCell() {
        this.setFeature(EnumSet.of(AEFeature.StorageCells));
        this.setMaxStackSize(1);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addCheckedInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
            final boolean displayMoreInfo) {
        lines.add(GuiText.VoidCellTooltip.getLocal());
        lines.add(0 + " " + GuiText.Of.getLocal() + " \u00A7k9999\u00A77 " + GuiText.BytesUsed.getLocal());
        VoidCellInventory inv = (VoidCellInventory) VoidCellInventory.getCell(stack);
        if (inv != null && stack.getItem() instanceof ItemVoidStorageCell cell) {
            if (inv.isPreformatted()) {
                String filter = cell.getOreFilter(stack);
                if (filter.isEmpty()) {
                    final String list = (inv.getWhitelist() == IncludeExclude.WHITELIST ? GuiText.Included
                            : GuiText.Excluded).getLocal();
                    if (inv.isFuzzy()) {
                        lines.add(GuiText.Partitioned.getLocal() + " - " + list + ' ' + GuiText.Fuzzy.getLocal());
                    } else {
                        lines.add(GuiText.Partitioned.getLocal() + " - " + list + ' ' + GuiText.Precise.getLocal());
                    }
                    if (GuiScreen.isShiftKeyDown()) {
                        lines.add(GuiText.Filter.getLocal() + ": ");
                        for (int i = 0; i < cell.getConfigInventory(stack).getSizeInventory(); ++i) {
                            ItemStack s = cell.getConfigInventory(stack).getStackInSlot(i);
                            if (s != null) lines.add(s.getDisplayName());
                        }
                    }
                } else {
                    lines.add(GuiText.PartitionedOre.getLocal() + " : " + filter);
                }
            }
        }
    }

    @Override
    public boolean isEditable(ItemStack is) {
        return true;
    }

    @Override
    public IInventory getUpgradesInventory(ItemStack is) {
        return new CellUpgrades(is, 2);
    }

    @Override
    public IInventory getConfigInventory(ItemStack is) {
        return new CellConfig(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        final String fz = Platform.openNbtData(is).getString("FuzzyMode");
        try {
            return FuzzyMode.valueOf(fz);
        } catch (final Throwable t) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        Platform.openNbtData(is).setString("FuzzyMode", fzMode.name());
    }

    @Override
    public String getOreFilter(ItemStack is) {
        return Platform.openNbtData(is).getString("OreFilter");
    }

    @Override
    public void setOreFilter(ItemStack is, String filter) {
        Platform.openNbtData(is).setString("OreFilter", filter);
    }
}
