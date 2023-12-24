package appeng.items.storage;

import java.text.NumberFormat;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.google.common.base.Optional;

import appeng.api.AEApi;
import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.util.item.ItemList;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemExtremeStorageCell extends ItemBasicStorageCell {

    protected final int totalTypes;

    @SuppressWarnings("Guava")
    public ItemExtremeStorageCell(String name, long bytes, int types, int perType, double drain) {
        super(Optional.of(name));
        this.setFeature(EnumSet.of(AEFeature.XtremeStorageCells));
        this.setMaxStackSize(1);
        this.totalBytes = bytes;
        this.perType = perType;
        this.totalTypes = types;
        this.idleDrain = drain;
    }

    @Override
    public int getTotalTypes(final ItemStack cellItem) {
        return totalTypes;
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack stack, final World world, final EntityPlayer player) {
        return stack;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
            float hitX, float hitY, float hitZ) {
        return false;
    }

    @Override
    public ItemStack getContainerItem(final ItemStack itemStack) {
        return null;
    }

    @Override
    public boolean hasContainerItem(final ItemStack stack) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addCheckedInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
            final boolean displayMoreInfo) {
        final IMEInventoryHandler<?> inventory = AEApi.instance().registries().cell()
                .getCellInventory(stack, null, StorageChannel.ITEMS);

        if (inventory instanceof ICellInventoryHandler handler) {
            final ICellInventory cellInventory = handler.getCellInv();

            if (cellInventory != null) {
                lines.add(
                        NumberFormat.getInstance().format(cellInventory.getUsedBytes()) + " "
                                + GuiText.Of.getLocal()
                                + ' '
                                + NumberFormat.getInstance().format(cellInventory.getTotalBytes())
                                + ' '
                                + GuiText.BytesUsed.getLocal());

                lines.add(
                        NumberFormat.getInstance().format(cellInventory.getStoredItemTypes()) + " "
                                + GuiText.Of.getLocal()
                                + ' '
                                + NumberFormat.getInstance().format(cellInventory.getTotalItemTypes())
                                + ' '
                                + GuiText.Types.getLocal());

                if (cellInventory.getStoredItemTypes() != 0) {
                    ItemStack itemStack = handler.getAvailableItems(new ItemList()).getFirstItem().getItemStack();
                    lines.add(GuiText.Contains.getLocal() + ": " + itemStack.getDisplayName());
                }

                if (handler.isPreformatted()) {
                    String filter = cellInventory.getOreFilter();
                    if (filter.isEmpty()) {
                        final String list = (handler.getIncludeExcludeMode() == IncludeExclude.WHITELIST
                                ? GuiText.Included
                                : GuiText.Excluded).getLocal();

                        if (handler.isFuzzy()) {
                            lines.add(GuiText.Partitioned.getLocal() + " - " + list + ' ' + GuiText.Fuzzy.getLocal());
                        } else {
                            lines.add(GuiText.Partitioned.getLocal() + " - " + list + ' ' + GuiText.Precise.getLocal());
                        }
                        if (GuiScreen.isShiftKeyDown()) {
                            lines.add(GuiText.Filter.getLocal() + ": ");
                            for (int i = 0; i < cellInventory.getConfigInventory().getSizeInventory(); ++i) {
                                ItemStack s = cellInventory.getConfigInventory().getStackInSlot(i);
                                if (s != null) lines.add(s.getDisplayName());
                            }
                        }
                    } else {
                        lines.add(GuiText.PartitionedOre.getLocal() + " : " + filter);
                    }

                    if (handler.getSticky()) {
                        lines.add(GuiText.Sticky.getLocal());
                    }
                }
            }
        }
    }

}
