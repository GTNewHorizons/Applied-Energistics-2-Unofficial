/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.misc;

import static appeng.util.Platform.readAEStackListNBT;
import static appeng.util.Platform.writeAEStackListNBT;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingPostPatternChangeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageInterceptor;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.DimensionalCoord;
import appeng.client.texture.CableBusTextures;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.Reflected;
import appeng.me.GridAccessException;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cache.NetworkMonitor;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.storage.MEMonitorPassThrough;
import appeng.me.storage.NullInventory;
import appeng.parts.PartBasicState;
import appeng.tile.networking.TileCableBus;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PartPatternRepeater extends PartBasicState
        implements ICraftingProvider, IStorageInterceptor, ICraftingPostPatternChangeListener {

    private final List<ICraftingPatternDetails> craftingList = new ArrayList<>();
    private IItemList<IAEStack<?>> waitingStacks = AEApi.instance().storage().createAEStackList();
    private CraftingGridCache targetCraftingGrid = null;
    private CraftingGridCache currentCraftingGrid = null;
    private AENetworkProxy targetNetworkProxy = null;
    private boolean provider = false;
    private PartPatternRepeater pairPatternRepeater = null;

    @SuppressWarnings({ "rawtypes" })
    private final Map<IAEStackType<?>, MEMonitorPassThrough> monitors = new IdentityHashMap<>();
    private final MachineSource actionSource;

    @Reflected
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public PartPatternRepeater(final ItemStack is) {
        super(is);

        this.actionSource = new MachineSource(this);

        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            MEMonitorPassThrough monitor = new MEMonitorPassThrough(new NullInventory<>(), type);
            monitor.setChangeSource(actionSource);
            this.monitors.put(type, monitor);
        }
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkChannelsChanged c) {
        gridChanged();
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange c) {
        gridChanged();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        rh.setTexture(
                CableBusTextures.PartMonitorSides.getIcon(),
                CableBusTextures.PartMonitorSides.getIcon(),
                CableBusTextures.PartMonitorBack.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartMonitorSides.getIcon(),
                CableBusTextures.PartMonitorSides.getIcon());

        rh.setBounds(2, 2, 14, 14, 14, 16);
        rh.renderInventoryBox(renderer);

        rh.setBounds(5, 5, 12, 11, 11, 13);
        rh.renderInventoryBox(renderer);

        rh.setBounds(5, 5, 13, 11, 11, 14);
        rh.renderInventoryBox(renderer);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void gridChanged() {
        this.init();

        if (this.provider) {
            try {
                for (Map.Entry<IAEStackType<?>, MEMonitorPassThrough> entry : monitors.entrySet()) {
                    entry.getValue().setInternal(this.getProxy().getStorage().getMEMonitor(entry.getKey()));
                }
            } catch (final GridAccessException gae) {
                for (MEMonitorPassThrough monitor : monitors.values()) {
                    monitor.setInternal(new NullInventory<>());
                }
            }

        } else if (this.pairPatternRepeater != null) {
            this.pairPatternRepeater.addInterception();
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));
        rh.setTexture(
                CableBusTextures.PartMonitorSides.getIcon(),
                CableBusTextures.PartMonitorSides.getIcon(),
                CableBusTextures.PartMonitorBack.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartMonitorSides.getIcon(),
                CableBusTextures.PartMonitorSides.getIcon());

        rh.setBounds(2, 2, 14, 14, 14, 16);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(
                CableBusTextures.PartMonitorSides.getIcon(),
                CableBusTextures.PartMonitorSides.getIcon(),
                CableBusTextures.PartMonitorBack.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartMonitorSides.getIcon(),
                CableBusTextures.PartMonitorSides.getIcon());

        rh.setBounds(5, 5, 12, 11, 11, 13);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorBack.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon());

        rh.setBounds(5, 5, 13, 11, 11, 14);
        rh.renderBlock(x, y, z, renderer);

        this.renderLights(x, y, z, rh, renderer);
    }

    @Override
    public int cableConnectionRenderTo() {
        return 4;
    }

    @Override
    public void onNeighborChanged() {
        this.init();
    }

    @Override
    public IIcon getBreakingTexture() {
        return this.getItemStack().getIconIndex();
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        data.setTag("waitingStacks", writeAEStackListNBT(this.waitingStacks));
        data.setBoolean("provider", this.provider);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        this.waitingStacks = readAEStackListNBT((NBTTagList) data.getTag("waitingStacks"));
        this.provider = data.getBoolean("provider");
    }

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        if (this.targetCraftingGrid != null) {
            for (ICraftingMedium medium : this.targetCraftingGrid.getMediums(patternDetails)) {
                if (medium.pushPattern(patternDetails, table)) {
                    for (IAEStack<?> outputStack : patternDetails.getCondensedAEOutputs()) {
                        this.waitingStacks.add(outputStack.copy());
                    }

                    this.addInterception();

                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public void provideCrafting(final ICraftingProviderHelper craftingTracker) {
        if (this.provider && this.getProxy().isActive() && this.craftingList != null) {
            for (final ICraftingPatternDetails details : this.craftingList) {
                craftingTracker.addCraftingOption(this, details);
            }
        }
    }

    public void init() {
        this.craftingList.clear();
        this.targetCraftingGrid = null;
        this.targetNetworkProxy = null;
        this.pairPatternRepeater = null;
        this.currentCraftingGrid = null;

        final TileEntity self = this.getHost().getTile();
        final TileEntity target = self.getWorldObj().getTileEntity(
                self.xCoord + this.getSide().offsetX,
                self.yCoord + this.getSide().offsetY,
                self.zCoord + this.getSide().offsetZ);

        if (target instanceof TileCableBus tcb
                && tcb.getPart(this.getSide().getOpposite()) instanceof PartPatternRepeater ppr) {
            this.pairPatternRepeater = ppr;
            this.targetNetworkProxy = ppr.getProxy();

            if (this.provider && !ppr.provider) {
                final IGridNode gn = tcb.getGridNode(ForgeDirection.UNKNOWN);
                if (gn == null) return;

                this.targetCraftingGrid = gn.getGrid().getCache(ICraftingGrid.class);

                final ImmutableSet<Entry<IAEItemStack, ImmutableList<ICraftingPatternDetails>>> tempPatterns = this.targetCraftingGrid
                        .getCraftingPatterns().entrySet();

                for (Entry<IAEItemStack, ImmutableList<ICraftingPatternDetails>> entry : tempPatterns) {
                    this.craftingList.addAll(entry.getValue());
                }

                if (!this.craftingList.isEmpty()) {
                    try {
                        this.getProxy().getGrid()
                                .postEvent(new MENetworkCraftingPatternChange(this, this.getProxy().getNode()));
                    } catch (final GridAccessException e) {
                        // :P
                    }
                }
            } else {
                final IGridNode gn = this.getGridNode(ForgeDirection.UNKNOWN);
                if (gn == null) return;

                this.currentCraftingGrid = gn.getGrid().getCache(ICraftingGrid.class);
                this.currentCraftingGrid.addPostPatternChangeListeners(this);
            }
        }
    }

    @Override
    public boolean onPartActivate(EntityPlayer player, Vec3 pos) {
        if (Platform.isClient()) return true;

        if (player.isSneaking()) {
            this.waitingStacks.resetStatus();
            return true;
        }

        final DimensionalCoord dc = this.getLocation();
        if (Platform.isWrench(player, player.getHeldItem(), dc.x, dc.y, dc.z)) {
            this.provider = !this.provider;

            if (this.provider) player.addChatMessage(PlayerMessages.PatternRepeaterProvider.toChat());
            else {
                try {
                    this.getProxy().getGrid()
                            .postEvent(new MENetworkCraftingPatternChange(this, this.getProxy().getNode()));
                } catch (final GridAccessException ignored) {}

                player.addChatMessage(PlayerMessages.PatternRepeaterAccessor.toChat());
            }

            this.gridChanged();

            return true;
        }

        return false;
    }

    @Override
    public boolean canAccept(IAEStack<?> stack) {
        return this.waitingStacks.findPrecise(stack) != null;
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public IAEStack<?> injectItems(final IAEStack<?> input, final Actionable type, final BaseActionSource src) {
        if (input == null) return null;

        final IAEStack<?> waitingStack = this.waitingStacks.findPrecise(input);

        final long inputSize = input.getStackSize();
        final long waitingSize = waitingStack.getStackSize();

        final IAEStack<?> tempStack = input.copy();
        long leftOver = 0;

        if (inputSize > waitingSize) {
            leftOver = inputSize - waitingSize;
            tempStack.setStackSize(waitingSize);
        }

        final long tempStackSize = tempStack.getStackSize();

        final IAEStack<?> result = this.monitors.get(tempStack.getStackType())
                .injectItems(tempStack, type, this.actionSource);

        final long reducedSize;
        if (result == null) reducedSize = 0;
        else reducedSize = result.getStackSize();

        final long returnSize = reducedSize + leftOver;

        if (type == Actionable.MODULATE) waitingStack.setStackSize(waitingSize - tempStackSize + reducedSize);

        return returnSize > 0 ? input.copy().setStackSize(returnSize) : null;
    }

    @Override
    public boolean shouldRemoveInterceptor(IAEStack<?> stack) {
        return this.waitingStacks.isEmpty();
    }

    private void addInterception() {
        if (waitingStacks.isEmpty() || this.targetNetworkProxy == null) return;

        List<IAEStackType<?>> types = new ArrayList<>(AEStackTypeRegistry.getAllTypes());

        for (IAEStack<?> aes : this.waitingStacks) {
            IAEStackType<?> type = aes.getStackType();
            if (types.contains(type)) {
                types.remove(type);
                try {
                    if (this.targetNetworkProxy.getStorage().getMEMonitor(type) instanceof NetworkMonitor<?>nm) {
                        nm.addStorageInterceptor(this);
                    }
                } catch (GridAccessException ignored) {}
            }

            if (types.isEmpty()) break;
        }
    }

    @Override
    public void onPostPatternChange() {
        if (!this.provider && pairPatternRepeater != null) pairPatternRepeater.init();
    }

    @Override
    public void getDrops(List<ItemStack> drops, boolean wrenched) {
        if (this.currentCraftingGrid != null) this.currentCraftingGrid.removePostPatternChangeListeners(this);
        if (this.targetNetworkProxy != null) try {
            if (this.targetNetworkProxy.getStorage().getItemInventory() instanceof NetworkMonitor<?>nm) {
                nm.removeStorageInterceptor(this);
            }
            if (this.targetNetworkProxy.getStorage().getFluidInventory() instanceof NetworkMonitor<?>nm) {
                nm.removeStorageInterceptor(this);
            }
        } catch (GridAccessException ignored) {}

        super.getDrops(drops, wrenched);
    }

    public boolean isProvider() {
        return this.provider;
    }

    public IItemList<IAEStack<?>> getWaitingStacks() {
        return this.waitingStacks;
    }
}
