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
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingPostPatternChangeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.crafting.ICraftingWatcher;
import appeng.api.networking.crafting.ICraftingWatcherHost;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageInterceptor;
import appeng.api.parts.ILevelEmitter;
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
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PartPatternRepeater extends PartBasicState
        implements ICraftingWatcherHost, ICraftingProvider, IStorageInterceptor, ICraftingPostPatternChangeListener {

    private final Set<ICraftingPatternDetails> craftingList = new HashSet<>();
    private final Map<IAEStack<?>, Boolean> emitableCrafting = new HashMap<>();
    private IItemList<IAEStack<?>> waitingStacks = AEApi.instance().storage().createAEStackList();
    private CraftingGridCache targetCraftingGrid = null;
    private CraftingGridCache currentCraftingGrid = null;
    private AENetworkProxy targetNetworkProxy = null;
    private boolean provider = false;
    private PartPatternRepeater pairPatternRepeater = null;

    private ICraftingWatcher myCraftingWatcher = null;

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

    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        return pushPatternToRepeater(patternDetails, table, new HashSet<>());
    }

    public boolean pushPatternToRepeater(final ICraftingPatternDetails patternDetails, final InventoryCrafting table,
            Set<CraftingGridCache> visitedRepeaters) {
        if (this.targetCraftingGrid == null) return false;

        // Keeps track of the nets of pattern repeaters that are called recursively to ensure no loops occur
        visitedRepeaters.add(this.targetCraftingGrid);
        List<ICraftingMedium> craftingMediumList = this.targetCraftingGrid.getMediums(patternDetails);
        for (ICraftingMedium medium : craftingMediumList) {
            // recursively call this method on the repeater if it is one,
            // if not, add an interception to the original caller's monitors for whatever is expected,
            // so items are passed all the way up
            if (medium instanceof PartPatternRepeater pushRepeater) {
                if (pushRepeater.targetCraftingGrid != null
                        && !visitedRepeaters.contains(pushRepeater.targetCraftingGrid)
                        && pushRepeater.pushPatternToRepeater(patternDetails, table, visitedRepeaters)) {
                    for (IAEStack<?> outputStack : patternDetails.getCondensedAEOutputs()) {
                        waitingStacks.add(outputStack.copy());
                    }
                    this.addInterception();

                    return true;
                }
            } else {
                if (medium.pushPattern(patternDetails, table)) {
                    for (IAEStack<?> outputStack : patternDetails.getCondensedAEOutputs()) {
                        waitingStacks.add(outputStack.copy());
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
        if (this.provider && this.getProxy().isActive()) {
            for (final ICraftingPatternDetails details : this.craftingList) {
                craftingTracker.addCraftingOption(this, details);
            }
            for (final IAEStack<?> item : this.emitableCrafting.keySet()) {
                craftingTracker.setEmitable(this, item);
            }
        }
    }

    private boolean duringFletchPatterns = false;

    public void init() {
        if (this.duringFletchPatterns) return;
        this.unregisterPostPatternChangeListener();
        this.craftingList.clear();
        this.emitableCrafting.clear();
        this.targetCraftingGrid = null;
        this.targetNetworkProxy = null;
        this.pairPatternRepeater = null;

        final TileEntity self = this.getHost().getTile();
        final TileEntity target = self.getWorldObj().getTileEntity(
                self.xCoord + this.getSide().offsetX,
                self.yCoord + this.getSide().offsetY,
                self.zCoord + this.getSide().offsetZ);

        if (Platform.getPartFromTE(target, this.getSide().getOpposite()) instanceof PartPatternRepeater ppr) {
            this.pairPatternRepeater = ppr;
            this.targetNetworkProxy = ppr.getProxy();

            if (this.provider) {
                if (ppr.provider) return;
                final IGridNode gn = ppr.getGridNode(ForgeDirection.UNKNOWN);
                if (gn == null) return;

                this.duringFletchPatterns = true;

                // drop patterns from this
                this.triggerPatternUpdate();

                this.targetCraftingGrid = gn.getGrid().getCache(ICraftingGrid.class);

                final ImmutableSet<Entry<IAEStack<?>, ImmutableList<ICraftingPatternDetails>>> tempPatterns = this.targetCraftingGrid
                        .getCraftingMultiPatterns().entrySet();

                tempPatterns.forEach((entry) -> this.craftingList.addAll(entry.getValue()));

                this.targetCraftingGrid.getEmitableItems().forEach((stack) -> this.emitableCrafting.put(stack, false));

                this.triggerPatternUpdate();
                this.configureWatchers();
                this.updateEmitableStatus();

                this.duringFletchPatterns = false;
            } else {
                final IGridNode gn = this.getGridNode(ForgeDirection.UNKNOWN);
                if (gn == null) return;

                this.currentCraftingGrid = gn.getGrid().getCache(ICraftingGrid.class);
                this.currentCraftingGrid.addPostPatternChangeListeners(this);
            }
        }
    }

    private void triggerPatternUpdate() {
        try {
            this.getProxy().getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.getProxy().getNode()));
        } catch (final GridAccessException e) {
            // :P
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

    private boolean injecting = false;

    public boolean isRequestingEmitable(IAEStack<?> stack) {
        return this.emitableCrafting.getOrDefault(stack, false);
    }

    public boolean isRequesting(IAEStack<?> stack) {
        return this.waitingStacks.findPrecise(stack) != null || this.isRequestingEmitable(stack);
    }

    @Override
    public boolean canAccept(IAEStack<?> stack) {
        return !injecting && this.isRequesting(stack);
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public IAEStack<?> injectItems(final IAEStack<?> input, final Actionable type, final BaseActionSource src) {
        if (input == null) return null;
        if (injecting) return input;
        injecting = true;

        final IAEStack<?> waitingStack = this.waitingStacks.findPrecise(input);

        final IAEStack<?> tempStack = input.copy();
        long leftOver = 0;

        if (waitingStack != null) {
            final long inputSize = input.getStackSize();
            final long waitingSize = waitingStack.getStackSize();

            if (inputSize > waitingSize) {
                leftOver = inputSize - waitingSize;
                tempStack.setStackSize(waitingSize);
            }
        }

        final long tempStackSize = tempStack.getStackSize();

        final IAEStack<?> result = this.monitors.get(tempStack.getStackType())
                .injectItems(tempStack, type, this.actionSource);

        final long reducedSize;
        if (result == null) reducedSize = 0;
        else reducedSize = result.getStackSize();

        final long returnSize = reducedSize + leftOver;

        if (waitingStack != null && type == Actionable.MODULATE) {
            waitingStack.setStackSize(waitingStack.getStackSize() - tempStackSize + reducedSize);
        }

        injecting = false;
        return returnSize > 0 ? input.copy().setStackSize(returnSize) : null;
    }

    @Override
    public boolean shouldRemoveInterceptor(IAEStack<?> stack) {
        return this.waitingStacks.isEmpty() && !this.emitableCrafting.containsValue(true);
    }

    private void addInterception() {
        if (this.targetNetworkProxy == null) return;

        List<IAEStackType<?>> types = new ArrayList<>(AEStackTypeRegistry.getAllTypes());
        List<IAEStackType<?>> addedTypes = new ArrayList<>();

        for (IAEStack<?> aes : this.waitingStacks) {
            addedTypes.add(aes.getStackType());
        }

        for (Entry<IAEStack<?>, Boolean> entry : this.emitableCrafting.entrySet()) {
            if (entry.getValue()) {
                addedTypes.add(entry.getKey().getStackType());
            }
        }

        for (IAEStackType<?> type : addedTypes) {
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
        if (!this.provider && this.pairPatternRepeater != null) this.pairPatternRepeater.init();
    }

    @Override
    public void getDrops(List<ItemStack> drops, boolean wrenched) {
        this.unregisterPostPatternChangeListener();
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

    private void unregisterPostPatternChangeListener() {
        if (this.currentCraftingGrid != null) {
            this.currentCraftingGrid.removePostPatternChangeListeners(this);
            this.currentCraftingGrid = null;
        }
    }

    public boolean isProvider() {
        return this.provider;
    }

    public IItemList<IAEStack<?>> getWaitingStacks() {
        return this.waitingStacks;
    }

    public PartPatternRepeater getPair() {
        return this.pairPatternRepeater;
    }

    private static void fletchRepeaters(final IGrid grid, final Set<IGrid> gridSet) {
        for (IGridNode node : grid.getMachines(PartPatternRepeater.class)) {
            final PartPatternRepeater rep = (PartPatternRepeater) node.getMachine();
            if (!rep.isProvider() || rep.getPair() == null || !node.isActive() || rep.getPair().isProvider()) continue;
            final IGridNode n = rep.getPair().getGridNode();
            if (n == null || !n.isActive()) continue;
            final IGrid currentGrid = n.getGrid();
            if (!gridSet.contains(currentGrid)) {
                gridSet.add(currentGrid);
                fletchRepeaters(currentGrid, gridSet);
            }
        }
    }

    public static Set<IGrid> collectReachableGrids(IGrid grid) {
        final Set<IGrid> gridSet = new HashSet<>();
        gridSet.add(grid);
        fletchRepeaters(grid, gridSet);
        return gridSet;
    }

    private void configureWatchers() {
        if (this.myCraftingWatcher != null && this.provider) {
            this.myCraftingWatcher.clear();
            this.myCraftingWatcher.addAll(this.emitableCrafting.keySet());
        }
    }

    private void propagateEmitableStatus(IAEStack<?> what, Set<CraftingGridCache> visitedRepeaters) {
        if (this.targetCraftingGrid == null || !this.emitableCrafting.containsKey(what)) {
            return;
        }

        visitedRepeaters.add(this.targetCraftingGrid);

        final IGrid grid = this.getGridNode().getGrid();
        final ICraftingGrid cg = grid.getCache(ICraftingGrid.class);
        boolean isCrafting = cg.isRequesting(what);

        for (IGridNode node : grid.getMachines(PartPatternRepeater.class)) {
            final PartPatternRepeater rep = (PartPatternRepeater) node.getMachine();
            if (!rep.isProvider() && rep.getPair() != null
                    && rep.getPair().isProvider()
                    && rep.getPair().isRequestingEmitable(what)) {
                isCrafting = true;
            }
        }

        this.emitableCrafting.put(what, isCrafting);
        for (final ICraftingMedium medium : this.targetCraftingGrid.getEmitableMediums(what)) {
            if (medium instanceof ILevelEmitter emitter) {
                emitter.updateEmitableStatus(what);
            } else
                if (medium instanceof PartPatternRepeater rep && !visitedRepeaters.contains(rep.targetCraftingGrid)) {
                    rep.propagateEmitableStatus(what, visitedRepeaters);
                }
        }
        this.addInterception();
    }

    public void updateEmitableStatus(IAEStack<?> what) {
        propagateEmitableStatus(what, new HashSet<>());
    }

    public void updateEmitableStatus() {
        this.emitableCrafting.keySet().forEach(this::updateEmitableStatus);
    }

    @Override
    public void updateWatcher(final ICraftingWatcher newWatcher) {
        this.myCraftingWatcher = newWatcher;
        this.configureWatchers();
    }

    @Override
    public void onRequestChange(final ICraftingGrid craftingGrid, IAEItemStack what) {
        this.updateEmitableStatus(what);
    }
}
