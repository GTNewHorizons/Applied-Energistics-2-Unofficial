package appeng.items;

import java.text.NumberFormat;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.base.Optional;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.implementations.items.IItemGroup;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.StorageChannel;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellConfigLegacy;
import appeng.items.contents.CellUpgrades;
import appeng.items.contents.PortableCellViewer;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import appeng.me.storage.CellInventoryHandler;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class AEBasePortableCell extends AEBasePoweredItem implements IStorageCell, IGuiItem, IItemGroup {

    public AEBasePortableCell() {
        super(AEConfig.instance.portableCellBattery, Optional.absent());
        this.setFeature(EnumSet.of(AEFeature.PortableCell, AEFeature.StorageCells, AEFeature.PoweredTools));
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack item, final World w, final EntityPlayer player) {
        Platform.openGUI(player, null, ForgeDirection.UNKNOWN, GuiBridge.GUI_PORTABLE_CELL);
        return item;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean isFull3D() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addCheckedInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
            final boolean displayMoreInfo) {
        super.addCheckedInformation(stack, player, lines, displayMoreInfo);

        if (AEApi.instance().registries().cell()
                .getCellInventory(stack, null, getStorageChannel()) instanceof CellInventoryHandler<?>cih) {
            final ICellInventory<?> cd = cih.getCellInv();
            if (cd != null) {
                lines.add(
                        EnumChatFormatting.WHITE + NumberFormat.getInstance().format(cd.getUsedBytes())
                                + EnumChatFormatting.GRAY
                                + " "
                                + GuiText.Of.getLocal()
                                + " "
                                + EnumChatFormatting.DARK_GREEN
                                + cd.getTotalBytes()
                                + " "
                                + EnumChatFormatting.GRAY
                                + GuiText.BytesUsed.getLocal());
                lines.add(
                        EnumChatFormatting.WHITE + NumberFormat.getInstance().format(cd.getStoredItemTypes())
                                + EnumChatFormatting.GRAY
                                + " "
                                + GuiText.Of.getLocal()
                                + " "
                                + EnumChatFormatting.DARK_GREEN
                                + cd.getTotalItemTypes()
                                + " "
                                + EnumChatFormatting.GRAY
                                + GuiText.Types.getLocal());
            }
        }
    }

    @Override
    public int getBytes(final ItemStack cellItem) {
        return 512;
    }

    @Override
    public int BytePerType(final ItemStack cell) {
        return 8;
    }

    @Override
    public int getBytesPerType(final ItemStack cellItem) {
        return 8;
    }

    @Override
    public int getTotalTypes(final ItemStack cellItem) {
        return 27;
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    @Override
    public boolean isStorageCell(final ItemStack i) {
        return true;
    }

    @Override
    public double getIdleDrain() {
        return 0.5;
    }

    @Override
    public String getUnlocalizedGroupName(final Set<ItemStack> others, final ItemStack is) {
        return GuiText.StorageCells.getUnlocalized();
    }

    @Override
    public boolean isEditable(final ItemStack is) {
        return true;
    }

    @Override
    public IInventory getUpgradesInventory(final ItemStack is) {
        return new CellUpgrades(is, 2);
    }

    @Override
    public IInventory getConfigInventory(final ItemStack is) {
        return new CellConfigLegacy(new CellConfig(is), StorageChannel.ITEMS);
    }

    @Override
    public IAEStackInventory getConfigAEInventory(ItemStack is) {
        return new CellConfig(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(final ItemStack is) {
        return FuzzyMode.fromItemStack(is);
    }

    @Override
    public void setFuzzyMode(final ItemStack is, final FuzzyMode fzMode) {
        Platform.openNbtData(is).setString("FuzzyMode", fzMode.name());
    }

    @Override
    public IGuiItemObject getGuiObject(final ItemStack is, final World w, final EntityPlayer p, final int x,
            final int y, final int z) {
        return new PortableCellViewer<>(is, x, getStorageChannel());
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
