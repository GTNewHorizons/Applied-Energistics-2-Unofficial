/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.items.tools.powered;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

import com.google.common.base.Optional;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IItemGroup;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.implementations.tiles.IColorableTile;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.block.misc.BlockPaint;
import appeng.block.networking.BlockCableBus;
import appeng.client.gui.implementations.GuiColorSelect;
import appeng.client.render.items.ToolColorApplicatorRender;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketColorSelect;
import appeng.helpers.IMouseWheelItem;
import appeng.hooks.DispenserBlockTool;
import appeng.hooks.IBlockTool;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellConfigLegacy;
import appeng.items.contents.CellUpgrades;
import appeng.items.misc.ItemPaintBall;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import appeng.me.storage.CellInventoryHandler;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.misc.TilePaint;
import appeng.util.ItemSorters;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ToolColorApplicator extends AEBasePoweredItem
        implements IStorageCell, IItemGroup, IBlockTool, IMouseWheelItem {

    private static final Map<Integer, AEColor> ORE_TO_COLOR = new HashMap<>();
    private static final double POWER_PER_USE = 100;
    private static final String NBT_COLOR = "color";

    static {
        for (final AEColor col : AEColor.values()) {
            if (col == AEColor.Transparent) {
                continue;
            }

            ORE_TO_COLOR.put(OreDictionary.getOreID("dye" + col.name()), col);
        }
    }

    public ToolColorApplicator() {
        super(AEConfig.instance.colorApplicatorBattery, Optional.absent());
        this.setFeature(EnumSet.of(AEFeature.ColorApplicator, AEFeature.PoweredTools));
        if (Platform.isClient()) {
            MinecraftForgeClient.registerItemRenderer(this, new ToolColorApplicatorRender());
        }
    }

    @Override
    public void postInit() {
        super.postInit();
        BlockDispenser.dispenseBehaviorRegistry.putObject(this, new DispenserBlockTool());
    }

    @Override
    public boolean onItemUse(final ItemStack stack, final EntityPlayer player, final World world, final int x,
            final int y, final int z, final int side, final float hitX, final float hitY, final float hitZ) {

        final DimensionalCoord coord = new DimensionalCoord(world, x, y, z);
        final ForgeDirection orientation = ForgeDirection.getOrientation(side);

        if (!Platform.hasPermissions(coord, player)) {
            return false;
        }

        if (this.getAECurrentPower(stack) < POWER_PER_USE) {
            return false;
        }

        final IMEInventory<IAEItemStack> inv = AEApi.instance().registries().cell()
                .getCellInventory(stack, null, StorageChannel.ITEMS);
        if (inv == null) {
            return false;
        }

        ItemStack activeConfig = this.getColor(stack);
        activeConfig.stackSize = 1;

        final IAEItemStack extractedSim = inv
                .extractItems(AEItemStack.create(activeConfig), Actionable.SIMULATE, new BaseActionSource());

        if (extractedSim == null) {
            return false;
        }

        ItemStack paintSource = extractedSim.getItemStack();
        AEColor paintColor = this.getColorFromItem(paintSource);

        TileEntity targetTe = world.getTileEntity(x, y, z);
        Block targetBlock = world.getBlock(x, y, z);

        boolean success = false;

        if (paintColor == AEColor.Transparent) {
            success = performClean(world, x, y, z, orientation, player, targetTe);
        } else if (paintColor != null) {
            success = performColor(world, x, y, z, orientation, paintColor, player, targetTe, targetBlock);
        }

        if (success) {
            if (this.consumePowerAndItemsForTe(targetTe)) {
                inv.extractItems(AEItemStack.create(paintSource), Actionable.MODULATE, new BaseActionSource());
                this.extractAEPower(stack, POWER_PER_USE);
            }

            return true;
        }

        return false;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (world.isRemote) {
                openGui(player);
            }
            return stack;
        }

        return super.onItemRightClick(stack, world, player);
    }

    private boolean performClean(World world, int x, int y, int z, ForgeDirection side, EntityPlayer player,
            TileEntity tileEntity) {
        if (tileEntity instanceof IColorableTile colorable) {
            if (colorable.getColor() != AEColor.Transparent) {
                return colorable.recolourBlock(side, AEColor.Transparent, player);
            }
        }

        int tx = x + side.offsetX;
        int ty = y + side.offsetY;
        int tz = z + side.offsetZ;
        Block targetBlock = world.getBlock(tx, ty, tz);
        TileEntity targetTe = world.getTileEntity(tx, ty, tz);

        if (targetBlock instanceof BlockPaint && targetTe instanceof TilePaint) {
            ((TilePaint) targetTe).cleanSide(side.getOpposite());
            return true;
        }

        return false;
    }

    private boolean performColor(World world, int x, int y, int z, ForgeDirection side, AEColor color,
            EntityPlayer player, TileEntity tileEntity, Block block) {

        if (tileEntity instanceof IColorableTile) {
            return ((IColorableTile) tileEntity).recolourBlock(side, color, player);
        }

        return this.recolourBlock(block, side, world, x, y, z, side, color, player);
    }

    private boolean consumePowerAndItemsForTe(TileEntity tileEntity) {
        return !(tileEntity instanceof IColorableTile);
    }

    @Override
    public String getItemStackDisplayName(final ItemStack par1ItemStack) {
        String extra = GuiText.Empty.getLocal();

        final AEColor selected = this.getActiveColor(par1ItemStack);

        if (selected != null && Platform.isClient()) {
            extra = Platform.gui_localize(selected.unlocalizedName);
        }

        return super.getItemStackDisplayName(par1ItemStack) + " - " + extra;
    }

    public AEColor getActiveColor(final ItemStack tol) {
        return this.getColorFromItem(this.getColor(tol));
    }

    private AEColor getColorFromItem(final ItemStack paintBall) {
        if (paintBall == null) {
            return null;
        }

        if (paintBall.getItem() instanceof ItemSnowball) {
            return AEColor.Transparent;
        }

        if (paintBall.getItem() instanceof ItemPaintBall ipb) {
            return ipb.getColor(paintBall);
        } else {
            final int[] id = OreDictionary.getOreIDs(paintBall);

            for (final int oreID : id) {
                if (ORE_TO_COLOR.containsKey(oreID)) {
                    return ORE_TO_COLOR.get(oreID);
                }
            }
        }

        return null;
    }

    public ItemStack getColor(final ItemStack is) {
        final NBTTagCompound c = is.getTagCompound();
        if (c != null && c.hasKey(NBT_COLOR)) {
            final NBTTagCompound color = c.getCompoundTag(NBT_COLOR);
            final ItemStack oldColor = ItemStack.loadItemStackFromNBT(color);
            if (oldColor != null) {
                return oldColor;
            }
        }

        return this.findNextColor(is, null, 0);
    }

    private ItemStack findNextColor(final ItemStack is, final ItemStack anchor, final int scrollOffset) {
        ItemStack newColor = null;

        final IMEInventory<IAEItemStack> inv = AEApi.instance().registries().cell()
                .getCellInventory(is, null, StorageChannel.ITEMS);
        if (inv != null) {
            final IItemList<IAEItemStack> itemList = inv.getAvailableItems(
                    AEApi.instance().storage().createSortedItemList(),
                    IterationCounter.fetchNewId());
            if (anchor == null) {
                final IAEItemStack firstItem = itemList.getFirstItem();
                if (firstItem != null) {
                    newColor = firstItem.getItemStack();
                }
            } else {
                final LinkedList<IAEItemStack> list = new LinkedList<>();

                for (final IAEItemStack i : itemList) {
                    list.add(i);
                }

                list.sort((a, b) -> ItemSorters.compareInt(a.getItemDamage(), b.getItemDamage()));

                if (list.size() <= 0) {
                    return null;
                }

                IAEItemStack where = list.getFirst();
                int cycles = 1 + list.size();

                while (cycles > 0 && !where.equals(anchor)) {
                    list.addLast(list.removeFirst());
                    cycles--;
                    where = list.getFirst();
                }

                if (scrollOffset > 0) {
                    list.addLast(list.removeFirst());
                }

                if (scrollOffset < 0) {
                    list.addFirst(list.removeLast());
                }

                return list.get(0).getItemStack();
            }
        }

        if (newColor != null) {
            this.setColor(is, newColor);
        }

        return newColor;
    }

    public void setColor(final ItemStack is, final AEColor newColor) {
        if (newColor == AEColor.Transparent) {
            setColor(is, (ItemStack) null);
        } else {
            final ItemStack paintBall = AEApi.instance().definitions().items().coloredPaintBall().stack(newColor, 1);
            setColor(is, paintBall);
        }
    }

    private void setColor(final ItemStack is, final ItemStack newColor) {
        final NBTTagCompound data = Platform.openNbtData(is);
        if (newColor == null) {
            data.removeTag(NBT_COLOR);
        } else {
            final NBTTagCompound color = new NBTTagCompound();
            newColor.writeToNBT(color);
            data.setTag(NBT_COLOR, color);
        }
    }

    private boolean recolourBlock(final Block blk, final ForgeDirection side, final World w, final int x, final int y,
            final int z, final ForgeDirection orientation, final AEColor newColor, final EntityPlayer p) {
        if (blk == Blocks.carpet) {
            return recolourBlock(w, x, y, z, newColor, Blocks.carpet);
        }

        if (blk == Blocks.glass) {
            return w.setBlock(x, y, z, Blocks.stained_glass, newColor.ordinal(), 3);
        }

        if (blk == Blocks.stained_glass) {
            return recolourBlock(w, x, y, z, newColor, Blocks.stained_glass);
        }

        if (blk == Blocks.glass_pane) {
            return w.setBlock(x, y, z, Blocks.stained_glass_pane, newColor.ordinal(), 3);
        }

        if (blk == Blocks.stained_glass_pane) {
            return recolourBlock(w, x, y, z, newColor, Blocks.stained_glass_pane);
        }

        if (blk == Blocks.hardened_clay) {
            return w.setBlock(x, y, z, Blocks.stained_hardened_clay, newColor.ordinal(), 3);
        }

        if (blk == Blocks.stained_hardened_clay) {
            return recolourBlock(w, x, y, z, newColor, Blocks.stained_hardened_clay);
        }

        if (blk instanceof BlockCableBus cableBus) {
            return cableBus.recolourBlock(w, x, y, z, side, newColor.ordinal(), p);
        }

        return blk.recolourBlock(w, x, y, z, side, newColor.ordinal());
    }

    private boolean recolourBlock(World world, int x, int y, int z, AEColor newColor, Block block) {
        final int meta = world.getBlockMetadata(x, y, z);
        if (newColor.ordinal() == meta) {
            return false;
        }
        return world.setBlock(x, y, z, block, newColor.ordinal(), 3);
    }

    public void cycleColors(final ItemStack is, final ItemStack paintBall, final int i) {
        if (paintBall == null) {
            this.setColor(is, this.getColor(is));
        } else {
            this.setColor(is, this.findNextColor(is, paintBall, i));
        }
    }

    public Map<AEColor, Long> getAvailableColorsCount(ItemStack stack) {
        if (this.getAECurrentPower(stack) <= 0) {
            return Collections.emptyMap();
        }

        Map<AEColor, Long> availableColors = new HashMap<>();

        final IMEInventory<IAEItemStack> inv = AEApi.instance().registries().cell()
                .getCellInventory(stack, null, StorageChannel.ITEMS);

        if (inv != null) {
            final IItemList<IAEItemStack> itemList = inv.getAvailableItems(
                    AEApi.instance().storage().createSortedItemList(),
                    IterationCounter.fetchNewId());

            for (final IAEItemStack aeItem : itemList) {
                if (aeItem != null) {
                    AEColor color = this.getColorFromItem(aeItem.getItemStack());

                    if (color != null) {
                        availableColors.merge(color, aeItem.getStackSize(), Long::sum);
                    }
                }
            }
        }

        return availableColors;
    }

    @SideOnly(Side.CLIENT)
    private static void openGui(EntityPlayer player) {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new GuiColorSelect(player));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addCheckedInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
            final boolean displayMoreInfo) {
        super.addCheckedInformation(stack, player, lines, displayMoreInfo);

        final IMEInventory<IAEItemStack> cdi = AEApi.instance().registries().cell()
                .getCellInventory(stack, null, StorageChannel.ITEMS);

        if (cdi instanceof CellInventoryHandler) {
            final ICellInventory cd = ((ICellInventoryHandler) cdi).getCellInv();
            if (cd != null) {
                lines.add(
                        cd.getUsedBytes() + " "
                                + GuiText.Of.getLocal()
                                + ' '
                                + cd.getTotalBytes()
                                + ' '
                                + GuiText.BytesUsed.getLocal());
                lines.add(
                        cd.getStoredItemTypes() + " "
                                + GuiText.Of.getLocal()
                                + ' '
                                + cd.getTotalItemTypes()
                                + ' '
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
    public boolean isBlackListed(final IAEStack<?> requestedAddition) {
        if (requestedAddition instanceof IAEItemStack ais) {
            final int[] id = OreDictionary.getOreIDs(ais.getItemStack());

            for (final int x : id) {
                if (ORE_TO_COLOR.containsKey(x)) {
                    return false;
                }
            }

            if (ais.getItem() instanceof ItemSnowball) {
                return false;
            }

            return !(ais.getItem() instanceof ItemPaintBall && ais.getItemDamage() < 20);
        }
        return true;
    }

    @Override
    public boolean storableInStorageCell() {
        return true;
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
    @Deprecated
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
    public void onWheel(final ItemStack is, final boolean up) {
        this.cycleColors(is, this.getColor(is), up ? 1 : -1);
    }

    @Override
    public void onWheelClick(EntityPlayer player, ItemStack is) {
        if (!player.worldObj.isRemote) {
            return;
        }

        final MovingObjectPosition mop = getPlayerLookingTarget(player);

        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }

        TileEntity te = player.worldObj.getTileEntity(mop.blockX, mop.blockY, mop.blockZ);

        if (te instanceof IColorableTile colorableTile) {
            NetworkHandler.instance.sendToServer(new PacketColorSelect(colorableTile.getColor()));
        }
    }

    @SideOnly(Side.CLIENT)
    private static MovingObjectPosition getPlayerLookingTarget(EntityPlayer viewpoint) {
        double reachDistance = Minecraft.getMinecraft().playerController.getBlockReachDistance();

        Vec3 posVec = Vec3.createVectorHelper(
                viewpoint.posX,
                viewpoint.posY + (viewpoint.getEyeHeight() - viewpoint.getDefaultEyeHeight()),
                viewpoint.posZ);
        Vec3 lookVec = viewpoint.getLook(0);
        Vec3 modifiedPosVec = posVec.addVector(
                lookVec.xCoord * reachDistance,
                lookVec.yCoord * reachDistance,
                lookVec.zCoord * reachDistance);

        return viewpoint.worldObj.rayTraceBlocks(posVec, modifiedPosVec);
    }

    @Override
    public String getOreFilter(ItemStack is) {
        return Platform.openNbtData(is).getString("OreFilter");
    }

    @Override
    public void setOreFilter(ItemStack is, String filter) {
        Platform.openNbtData(is).setString("OreFilter", filter);
    }

    @Override
    public StorageChannel getStorageChannel() {
        return StorageChannel.ITEMS;
    }
}
