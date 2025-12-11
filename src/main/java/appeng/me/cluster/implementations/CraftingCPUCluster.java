/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.cluster.implementations;

import static appeng.util.Platform.convertStack;
import static appeng.util.Platform.readAEStackListNBT;
import static appeng.util.Platform.readStackNBT;
import static appeng.util.Platform.stackConvert;
import static appeng.util.Platform.writeAEStackListNBT;
import static appeng.util.Platform.writeStackNBT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.Level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.CraftingAllow;
import appeng.api.config.CraftingMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Upgrades;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingMedium.BlockingMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.CraftCancelListener;
import appeng.api.util.CraftCompleteListener;
import appeng.api.util.CraftUpdateListener;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IInterfaceViewable;
import appeng.api.util.NamedDimensionalCoord;
import appeng.api.util.WorldCoord;
import appeng.container.ContainerNull;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingWatcher;
import appeng.crafting.MECraftingInventory;
import appeng.helpers.DualityInterface;
import appeng.hooks.CraftingNotificationManager;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.IAECluster;
import appeng.tile.AEBaseTile;
import appeng.tile.crafting.TileCraftingMonitorTile;
import appeng.tile.crafting.TileCraftingTile;
import appeng.tile.misc.TileInterface;
import appeng.util.Platform;
import appeng.util.ScheduledReason;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.FMLCommonHandler;

public final class CraftingCPUCluster implements IAECluster, ICraftingCPU {

    private static final String LOG_MARK_AS_COMPLETE = "Completed job for %s.";

    private final WorldCoord min;
    private final WorldCoord max;
    private final int[] usedOps = new int[3];
    private final Comparator<ICraftingPatternDetails> priorityComparator = Comparator
            .comparing(ICraftingPatternDetails::getPriority).thenComparing(ICraftingPatternDetails::hashCode);
    private final Map<ICraftingPatternDetails, TaskProgress> tasks = new TreeMap<>(priorityComparator);
    private Map<ICraftingPatternDetails, TaskProgress> workableTasks = new TreeMap<>(priorityComparator);
    private HashSet<ICraftingMedium> knownBusyMediums = new HashSet<>();
    // INSTANCE sate
    private final LinkedList<TileCraftingTile> tiles = new LinkedList<>();
    private final LinkedList<TileCraftingTile> storage = new LinkedList<>();
    private final LinkedList<TileCraftingMonitorTile> status = new LinkedList<>();
    private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();
    private final HashMap<IAEStack<?>, List<NamedDimensionalCoord>> providers = new HashMap<>();
    private ICraftingLink myLastLink;
    private String myName = "";
    private boolean isDestroyed = false;
    private boolean suspended = false;
    /**
     * crafting job info
     */
    private MECraftingInventory inventory = new MECraftingInventory();

    private IAEStack finalOutput;
    private boolean waiting = false;
    private IItemList<IAEStack<?>> waitingFor = AEApi.instance().storage().createAEStackList();
    private IItemList<IAEStack<?>> waitingForMissing = AEApi.instance().storage().createAEStackList();
    private long availableStorage = 0;
    private long usedStorage = 0;
    private MachineSource machineSrc = null;
    private int accelerator = 0;
    private boolean isComplete = true;
    private int remainingOperations;
    private boolean somethingChanged;
    private boolean isFakeCrafting;

    private long lastTime;
    private long elapsedTime;
    private long startItemCount;
    private long remainingItemCount;
    private long numsOfOutput;
    private int countToTryExtractItems;
    private boolean isMissingMode;
    private CraftingAllow craftingAllowMode = CraftingAllow.ALLOW_ALL;

    private final Map<String, List<CraftNotification>> unreadNotifications = new HashMap<>();

    private final List<CraftCompleteListener> defaultOnComplete = Arrays
            .asList((finalOutput, numsOfOutput, elapsedTime) -> {
                if (!this.playersFollowingCurrentCraft.isEmpty()) {
                    final CraftNotification notification = new CraftNotification(
                            finalOutput,
                            numsOfOutput,
                            elapsedTime);
                    for (String playerName : this.playersFollowingCurrentCraft) {
                        // Get each EntityPlayer
                        EntityPlayer player = getPlayerByName(playerName);
                        if (player != null) {
                            // Send message to player
                            player.addChatMessage(notification.createMessage());
                            player.worldObj.playSoundAtEntity(player, "random.levelup", 1f, 1f);
                        } else {
                            this.unreadNotifications.computeIfAbsent(playerName, name -> new ArrayList<>())
                                    .add(notification);
                        }
                    }
                }
            });

    private List<CraftCompleteListener> craftCompleteListeners = initializeDefaultOnCompleteListener();
    private final List<CraftUpdateListener> craftUpdateListeners = new ArrayList<>();
    private final List<CraftCancelListener> craftCancelListeners = new ArrayList<>();
    private final List<String> playersFollowingCurrentCraft = new ArrayList<>();
    private final HashMap<ICraftingPatternDetails, List<ICraftingMedium>> parallelismProvider = new HashMap<>();
    private final HashMap<ICraftingPatternDetails, ScheduledReason> reasonProvider = new HashMap<>();
    private BaseActionSource currentJobSource = null;

    public CraftingCPUCluster(final WorldCoord min, final WorldCoord max) {
        this.min = min;
        this.max = max;
        CraftingNotificationManager.register(this.unreadNotifications);
    }

    @Override
    public void resetFinalOutput() {
        finalOutput = null;
        currentJobSource = null;
    }

    @Override
    public IAEItemStack getFinalOutput() {
        return stackConvert(finalOutput);
    }

    @Override
    public IAEStack<?> getFinalMultiOutput() {
        return finalOutput;
    }

    public boolean isDestroyed() {
        return this.isDestroyed;
    }

    public ICraftingLink getLastCraftingLink() {
        return this.myLastLink;
    }

    private List<CraftCompleteListener> initializeDefaultOnCompleteListener() {
        return new ArrayList<>(defaultOnComplete);
    }

    @Override
    public void addOnCompleteListener(CraftCompleteListener craftCompleteListener) {
        this.craftCompleteListeners.add(craftCompleteListener);
    }

    @Override
    public void addOnCancelListener(CraftCancelListener onCancelListener) {
        this.craftCancelListeners.add(onCancelListener);
    }

    @Override
    public void addOnCraftingUpdateListener(CraftUpdateListener onCraftingStatusUpdate) {
        this.craftUpdateListeners.add(onCraftingStatusUpdate);
    }

    /**
     * add a new Listener to the monitor, be sure to properly remove yourself when your done.
     */
    @Override
    public void addListener(final IMEMonitorHandlerReceiver l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    /**
     * remove a Listener to the monitor.
     */
    @Override
    public void removeListener(final IMEMonitorHandlerReceiver l) {
        this.listeners.remove(l);
    }

    public MECraftingInventory getInventory() {
        return this.inventory;
    }

    @Override
    public void updateStatus(final boolean updateGrid) {
        for (final TileCraftingTile r : this.tiles) {
            r.updateMeta(true);
        }
    }

    @Override
    public void destroy() {
        if (this.isDestroyed) {
            return;
        }
        this.isDestroyed = true;

        CraftingNotificationManager.unregister(this.unreadNotifications);

        boolean posted = false;

        for (final TileCraftingTile r : this.tiles) {
            final IGridNode n = r.getActionableNode();
            if (n != null && !posted) {
                final IGrid g = n.getGrid();
                if (g != null) {
                    g.postEvent(new MENetworkCraftingCpuChange(n));
                    posted = true;
                }
            }

            r.updateStatus(null);
        }
    }

    @Override
    public Iterator<IGridHost> getTiles() {
        return (Iterator) this.tiles.iterator();
    }

    void addTile(final TileCraftingTile te) {
        if (this.machineSrc == null || te.isCoreBlock()) {
            this.machineSrc = new MachineSource(te);
        }

        te.setCoreBlock(false);
        te.markDirty();
        this.tiles.push(te);

        if (te.isStorage()) {
            long additionalStorage = te.getStorageBytes();
            if (Long.MAX_VALUE - additionalStorage >= this.availableStorage) {
                // Safe to add as it does not cause overflow
                this.availableStorage += additionalStorage;
                this.storage.add(te);
            } else {
                // Prevent form CPU if storage overflowed
                this.tiles.remove(te);
            }
        } else if (te.isStatus()) {
            this.status.add((TileCraftingMonitorTile) te);
        } else if (te.isAccelerator()) {
            this.accelerator += te.acceleratorValue();
        }
    }

    public boolean canAccept(final IAEStack<?> input) {
        final IAEStack<?> is = this.waitingFor.findPrecise(input);
        return is != null && is.getStackSize() > 0;
    }

    public IAEStack<?> injectItems(final IAEStack<?> input, final Actionable type, final BaseActionSource src) {
        final IAEStack what = input.copy();
        final IAEStack<?> is = this.waitingFor.findPrecise(what);
        final IAEStack<?> ism = this.waitingForMissing.findPrecise(what);

        if (type == Actionable.SIMULATE) // causes crafting to lock up?
        {
            if (is != null && is.getStackSize() > 0) {
                if (is.getStackSize() >= what.getStackSize()) {
                    if (Objects.equals(this.finalOutput, what)) {
                        if (this.myLastLink != null) {
                            return ((CraftingLink) this.myLastLink).injectItems(what.copy(), type);
                        }

                        return what; // ignore it.
                    }

                    return null;
                }

                final IAEStack leftOver = what.copy();
                leftOver.decStackSize(is.getStackSize());

                final IAEStack<?> used = what.copy();
                used.setStackSize(is.getStackSize());

                if (Objects.equals(finalOutput, what)) {
                    if (this.myLastLink != null) {
                        leftOver.add(((CraftingLink) this.myLastLink).injectItems(used.copy(), type));
                        return leftOver;
                    }

                    return what; // ignore it.
                }

                return leftOver;
            }
        } else if (type == Actionable.MODULATE) {
            if (is != null && is.getStackSize() > 0) {
                this.waiting = false;
                this.postChange(is, src);

                if (is.getStackSize() >= what.getStackSize()) {
                    is.decStackSize(what.getStackSize());
                    if (ism != null) ism.decStackSize(what.getStackSize());

                    this.updateElapsedTime(what);
                    this.markDirty();
                    this.postCraftingStatusChange(is);
                    for (CraftUpdateListener craftUpdateListener : craftUpdateListeners) {
                        // whatever it passes is not important, if it's not 0, it indicates the craft is active rather
                        // than stuck.
                        craftUpdateListener.accept(1);
                    }

                    if (Objects.equals(finalOutput, what)) {
                        IAEStack<?> leftover = what;

                        this.finalOutput.decStackSize(what.getStackSize());

                        if (this.myLastLink != null) {
                            leftover = ((CraftingLink) this.myLastLink).injectItems(what, type);
                        }

                        if (this.finalOutput.getStackSize() <= 0) {
                            this.completeJob();
                        }

                        this.updateCPU();

                        return leftover; // ignore it.
                    }

                    // 2000
                    this.inventory.injectItems(what, type);
                    return null;
                }

                final IAEStack insert = what.copy();
                insert.setStackSize(is.getStackSize());
                what.decStackSize(is.getStackSize());

                is.setStackSize(0);
                if (ism != null) ism.setStackSize(0);

                if (Objects.equals(finalOutput, insert)) {
                    IAEStack<?> leftover = input;

                    this.finalOutput.decStackSize(insert.getStackSize());

                    if (this.myLastLink != null) {
                        what.add(((CraftingLink) this.myLastLink).injectItems(insert.copy(), type));
                        leftover = what;
                    }

                    if (this.finalOutput.getStackSize() <= 0) {
                        this.completeJob();
                    }

                    this.updateCPU();
                    this.markDirty();

                    return leftover; // ignore it.
                }

                this.inventory.injectItems(insert, type);
                this.markDirty();

                return what;
            }
        }

        return input;
    }

    private void postChange(final IAEStack<?> diff, final BaseActionSource src) {
        final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();

        // protect integrity
        if (i.hasNext()) {
            final ImmutableList<IAEStack<?>> single = ImmutableList.of(diff.copy());

            while (i.hasNext()) {
                final Entry<IMEMonitorHandlerReceiver, Object> o = i.next();
                final IMEMonitorHandlerReceiver receiver = o.getKey();

                if (receiver.isValid(o.getValue())) {
                    receiver.postChange(null, single, src);
                } else {
                    i.remove();
                }
            }
        }
    }

    public void markDirty() {
        this.getCore().markDirty();
    }

    private void postCraftingStatusChange(final IAEStack<?> aeDiff) {
        IAEItemStack diff = stackConvert(aeDiff); // emitters
        if (this.getGrid() == null) {
            return;
        }

        final CraftingGridCache sg = this.getGrid().getCache(ICraftingGrid.class);

        if (sg.getInterestManager().containsKey(diff)) {
            final Collection<CraftingWatcher> list = sg.getInterestManager().get(diff);

            if (!list.isEmpty()) {
                for (final CraftingWatcher iw : list) {

                    iw.getHost().onRequestChange(sg, diff);
                }
            }
        }
    }

    private void completeJob() {
        if (this.hasRemainingTasks()) return; // dont complete if still working
        if (this.myLastLink != null) {
            ((CraftingLink) this.myLastLink).markDone();
            this.myLastLink = null;
        }

        if (AELog.isCraftingLogEnabled()) {
            final IAEStack<?> logStack = this.finalOutput.copy();
            logStack.setStackSize(this.startItemCount);
            AELog.crafting(LOG_MARK_AS_COMPLETE, logStack);
        }

        craftCompleteListeners.forEach(f -> f.apply(this.finalOutput, this.numsOfOutput, elapsedTime));
        this.isFakeCrafting = false;
        this.usedStorage = 0;
        this.remainingItemCount = 0;
        this.startItemCount = 0;
        this.lastTime = 0;
        this.elapsedTime = 0;
        this.numsOfOutput = 0;
        this.isComplete = true;
        this.playersFollowingCurrentCraft.clear();
        this.craftCompleteListeners = initializeDefaultOnCompleteListener();
        this.craftCancelListeners.clear(); // complete listener will clean external state
                                           // so cancel listener is not called here.
        this.craftUpdateListeners.clear();
    }

    private EntityPlayerMP getPlayerByName(String playerName) {
        return MinecraftServer.getServer().getConfigurationManager().func_152612_a(playerName);
    }

    private void updateCPU() {
        IAEStack<?> send = this.finalOutput;

        if (this.finalOutput != null && this.finalOutput.getStackSize() <= 0) {
            send = null;
        }

        for (final TileCraftingMonitorTile t : this.status) {
            t.setJob(send);
        }
    }

    private Iterator<Entry<IMEMonitorHandlerReceiver, Object>> getListeners() {
        return this.listeners.entrySet().iterator();
    }

    private TileCraftingTile getCore() {
        return (TileCraftingTile) this.machineSrc.via;
    }

    public IGrid getGrid() {
        IGrid node;
        for (final TileCraftingTile r : this.tiles) {
            final IGridNode gn = r.getActionableNode();
            if (gn == null || (node = gn.getGrid()) == null) continue;

            return node;
        }

        return null;
    }

    private ArrayList<IAEStack<?>> getExtractItems(IAEStack ingredient, ICraftingPatternDetails patternDetails) {
        ArrayList<IAEStack<?>> list = new ArrayList<>();
        if (patternDetails.canSubstitute() && ingredient instanceof IAEItemStack aiss) {
            for (IAEItemStack fuzz : this.inventory.findFuzzy(aiss, FuzzyMode.IGNORE_ALL)) {
                if (!patternDetails.isCraftable() && fuzz.getStackSize() <= 0) continue;
                if (patternDetails.isCraftable()) {
                    final IAEStack<?>[] inputSlots = patternDetails.getAEInputs();
                    final IAEStack<?> finalIngredient = ingredient; // have to copy because of Java lambda capture
                                                                    // rules here
                    final int matchingSlot = IntStream.range(0, inputSlots.length)
                            .filter(idx -> inputSlots[idx] != null && Objects.equals(inputSlots[idx], finalIngredient))
                            .findFirst().orElse(-1);
                    if (matchingSlot < 0) {
                        continue;
                    }
                    if (!patternDetails.isValidItemForSlot(matchingSlot, fuzz, getWorld())) {
                        // Skip invalid fuzzy matches
                        continue;
                    }
                }
                fuzz = fuzz.copy();
                fuzz.setStackSize(ingredient.getStackSize());
                final IAEItemStack ais = this.inventory.extractItems(fuzz, Actionable.SIMULATE);

                if (ais != null && ais.getStackSize() == ingredient.getStackSize()) {
                    list.add(ais);
                    return list;
                } else if (ais != null && patternDetails.isCraftable()) {
                    ingredient = ingredient.copy();
                    ingredient.decStackSize(ais.getStackSize());
                    list.add(ais);
                }
            }
        } else {
            final IAEStack<?> extractItems = this.inventory.extractItems(ingredient, Actionable.SIMULATE);
            if (extractItems != null && extractItems.getStackSize() == ingredient.getStackSize()) {
                list.add(extractItems);
                return list;
            }
        }
        return list;
    }

    private boolean canCraft(final ICraftingPatternDetails details, final IAEStack<?>[] condensedInputs) {
        for (IAEStack<?> g : condensedInputs) {
            if (getExtractItems(g, details).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void cancel() {
        if (this.myLastLink != null) {
            this.myLastLink.cancel();
        }

        final IItemList<IAEStack<?>> list;
        this.getModernListOfItem(list = AEApi.instance().storage().createAEStackList(), CraftingItemList.ALL);
        for (final IAEStack<?> is : list) {
            this.postChange(is, this.machineSrc);
        }

        this.usedStorage = 0;
        this.isComplete = true;
        this.myLastLink = null;
        this.tasks.clear();
        this.providers.clear();
        final ImmutableSet<IAEStack<?>> items = ImmutableSet.copyOf(this.waitingFor);

        this.waitingFor.resetStatus();
        this.waitingForMissing.resetStatus();
        parallelismProvider.clear();
        reasonProvider.clear();

        for (final IAEStack<?> is : items) {
            this.postCraftingStatusChange(is);
        }

        this.finalOutput = null;
        this.updateCPU();
        this.craftCompleteListeners = initializeDefaultOnCompleteListener();
        for (Runnable onCancelListener : this.craftCancelListeners) {
            onCancelListener.run();
        }
        this.craftCancelListeners.clear();
        this.craftUpdateListeners.clear();
        this.storeItems(); // marks dirty
    }

    public void updateCraftingLogic(final IGrid grid, final IEnergyGrid eg, final CraftingGridCache cc) {
        if (!this.getCore().isActive()) {
            return;
        }

        if (this.myLastLink != null) {
            if (this.myLastLink.isCanceled()) {
                this.myLastLink = null;
                this.cancel();
            }
        }

        if (this.isComplete) {
            if (this.inventory.isEmpty()) {
                return;
            }

            this.storeItems();
            return;
        }

        this.waiting = false;
        if (this.waiting || this.tasks.isEmpty()) // nothing to do here...
        {
            return;
        }

        this.remainingOperations = this.accelerator + 1 - (this.usedOps[0] + this.usedOps[1] + this.usedOps[2]);
        final int started = this.remainingOperations;

        // Shallow copy tasks so we may remove them after visiting
        this.workableTasks.clear();
        this.workableTasks.putAll(this.tasks);
        this.knownBusyMediums.clear();
        if (this.remainingOperations > 0) {
            do {
                this.somethingChanged = false;
                this.executeCrafting(eg, cc);
            } while (this.somethingChanged && this.remainingOperations > 0);
        }
        this.usedOps[2] = this.usedOps[1];
        this.usedOps[1] = this.usedOps[0];
        this.usedOps[0] = started - this.remainingOperations;

        this.knownBusyMediums.clear();

        if (this.remainingOperations > 0 && !this.somethingChanged) {
            this.waiting = true;
        }

        if (this.isFakeCrafting) {
            final IAEStack<?> is = this.waitingFor.findPrecise(finalOutput);
            if (is != null) {
                long stackSize = is.getStackSize();
                is.decStackSize(stackSize);
                this.markDirty();
                this.postCraftingStatusChange(is);
                this.finalOutput.decStackSize(stackSize);
                if (this.finalOutput.getStackSize() <= 0) {
                    this.completeJob();
                }
                this.updateCPU();
            }
        }
    }

    private void executeCrafting(final IEnergyGrid eg, final CraftingGridCache cc) {
        if (this.suspended) return;

        final Iterator<Entry<ICraftingPatternDetails, TaskProgress>> craftingTaskIterator = this.workableTasks
                .entrySet().iterator();

        int executedTasks = 0;
        while (craftingTaskIterator.hasNext()) {
            final Entry<ICraftingPatternDetails, TaskProgress> craftingEntry = craftingTaskIterator.next();

            if (craftingEntry.getValue().value <= 0) {
                final ICraftingPatternDetails ceKey = craftingEntry.getKey();
                this.tasks.remove(ceKey);
                parallelismProvider.remove(ceKey);
                reasonProvider.remove(ceKey);
                craftingTaskIterator.remove();
                continue;
            }

            final ICraftingPatternDetails details = craftingEntry.getKey();
            ScheduledReason sr = null;
            if (!this.canCraft(details, details.getCondensedAEInputs())) {
                craftingTaskIterator.remove(); // No need to revisit this task on next executeCrafting this tick
                reasonProvider.put(details, ScheduledReason.NOT_ENOUGH_INGREDIENTS);
                continue;
            }

            boolean pushedPattern = false;
            boolean didPatternCraft;

            List<ICraftingMedium> mediumsList = cc.getMediums(details);
            List<ICraftingMedium> mediumListCheck = null;

            if (mediumsList.size() > 1) {
                mediumListCheck = parallelismProvider.getOrDefault(details, new ArrayList<>(mediumsList));
            }

            doWhileCraftingLoop: do {
                MEInventoryCrafting craftingInventory = null;
                didPatternCraft = false;

                if (mediumListCheck != null) {
                    if (mediumListCheck.isEmpty()) {
                        mediumListCheck = new ArrayList<>(mediumsList);
                    } else {
                        mediumsList = new ArrayList<>(mediumListCheck);
                    }
                }

                for (final ICraftingMedium medium : mediumsList) {
                    if (mediumListCheck != null) mediumListCheck.remove(medium);

                    if (craftingEntry.getValue().value <= 0 || knownBusyMediums.contains(medium)) {
                        continue;
                    }

                    if (medium.isBusy()) {
                        knownBusyMediums.add(medium);
                        sr = medium.getScheduledReason();
                        continue;
                    }

                    // Find a valid craftingInventory for this craft.
                    double sum = 0;
                    if (craftingInventory == null) {
                        final IAEStack<?>[] input = details.getAEInputs();

                        for (final IAEStack<?> anInput : input) {
                            if (anInput != null) {
                                sum += (double) anInput.getStackSize() / anInput.getPowerMultiplier();
                            }
                        }
                        // upgraded interface uses more power
                        if (medium instanceof DualityInterface) sum *= Math
                                .pow(4.0, ((DualityInterface) medium).getInstalledUpgrades(Upgrades.PATTERN_CAPACITY));

                        // check if there is enough power
                        if (eg.extractAEPower(sum, Actionable.SIMULATE, PowerMultiplier.CONFIG) < sum - 0.01) continue;

                        craftingInventory = details.isCraftable() ? new MEInventoryCrafting(new ContainerNull(), 3, 3)
                                : new MEInventoryCrafting(new ContainerNull(), details.getAEInputs().length, 1);

                        // Check if all items can be used for crafting.
                        boolean found = false;
                        for (int x = 0; x < input.length; x++) {
                            if (input[x] != null) {
                                found = false;
                                for (IAEStack ias : getExtractItems(input[x], details)) {
                                    IAEStack tempStack = ias.copy();
                                    if (details.isCraftable()
                                            && !details.isValidItemForSlot(x, tempStack, this.getWorld()))
                                        continue;

                                    final IAEStack<?> aes = this.inventory.extractItems(tempStack, Actionable.MODULATE);
                                    if (aes != null) {
                                        found = true;
                                        craftingInventory.setInventorySlotContents(x, aes);
                                        if (!details.canBeSubstitute()
                                                && aes.getStackSize() == input[x].getStackSize()) {
                                            this.postChange(input[x], this.machineSrc);
                                            break;
                                        } else {
                                            this.postChange(aes, this.machineSrc);
                                        }
                                    }
                                }
                                if (!found) {
                                    break;
                                }
                            }
                        }

                        if (!found) {
                            // put stuff back.
                            returnItems(craftingInventory);
                            craftingInventory = null;
                            break;
                        }
                    }

                    if (medium.pushPattern(details, craftingInventory)) {
                        eg.extractAEPower(sum, Actionable.MODULATE, PowerMultiplier.CONFIG);
                        this.somethingChanged = true;
                        this.remainingOperations--;
                        pushedPattern = true;
                        this.isFakeCrafting = (medium instanceof DualityInterface di && di.isFakeCraftingMode());

                        // Process output items.
                        for (final IAEStack<?> outputItemStack : details.getCondensedAEOutputs()) {
                            this.postChange(outputItemStack, this.machineSrc);
                            this.waitingFor.add(outputItemStack.copy());
                            this.postCraftingStatusChange(outputItemStack.copy());

                            // Add this medium to the list of providers for the outputItemStack if not yet in there.
                            providers.computeIfAbsent(outputItemStack, k -> new ArrayList<>());
                            List<NamedDimensionalCoord> list = providers.get(outputItemStack);
                            if (medium instanceof ICraftingProvider) {
                                TileEntity tile = this.getTile(medium);
                                if (tile == null) continue;
                                NamedDimensionalCoord tileDimensionalCoord;
                                if (tile instanceof TileInterface tileInterface) {
                                    tileDimensionalCoord = new NamedDimensionalCoord(
                                            tile,
                                            tileInterface.getCustomName());
                                } else {
                                    tileDimensionalCoord = new NamedDimensionalCoord(tile, "");
                                }

                                boolean isAdded = false;
                                for (DimensionalCoord dimensionalCoord : list) {
                                    if (dimensionalCoord.isEqual(tileDimensionalCoord)) {
                                        isAdded = true;
                                        break;
                                    }
                                }
                                if (!isAdded) {
                                    list.add(tileDimensionalCoord);
                                }
                            }
                        }

                        if (details.isCraftable()) {
                            FMLCommonHandler.instance().firePlayerCraftingEvent(
                                    Platform.getPlayer((WorldServer) this.getWorld()),
                                    details.getOutput(craftingInventory, this.getWorld()),
                                    craftingInventory);
                            for (int x = 0; x < craftingInventory.getSizeInventory(); x++) {
                                final ItemStack output = Platform.getContainerItem(craftingInventory.getStackInSlot(x));
                                if (output != null) {
                                    final IAEItemStack cItem = AEItemStack.create(output);
                                    this.postChange(cItem, this.machineSrc);
                                    this.waitingFor.add(cItem);
                                    this.postCraftingStatusChange(cItem);
                                }
                            }
                        }

                        craftingInventory = null; // hand off complete!
                        didPatternCraft = true;
                        this.markDirty();

                        executedTasks += 1;
                        craftingEntry.getValue().value--;
                        if (craftingEntry.getValue().value <= 0) {
                            // This craftingEntry is done.
                            break doWhileCraftingLoop;
                        }

                        if (this.remainingOperations == 0) {
                            if (mediumListCheck != null) parallelismProvider.put(details, mediumListCheck);
                            return;
                        }
                        // Smart blocking is fine sending the same recipe again.
                        if (medium.getBlockingMode() == BlockingMode.BLOCKING) break;

                        if (!this.canCraft(details, details.getCondensedAEInputs())) {
                            sr = ScheduledReason.NOT_ENOUGH_INGREDIENTS;
                            break;
                        }
                    }

                    sr = medium.getScheduledReason();
                }
                if (craftingInventory != null) {
                    // No suitable craftingInventory was found,
                    // put stuff back that was injected during the search.
                    returnItems(craftingInventory);
                }
            } while (didPatternCraft);

            if (mediumListCheck != null) parallelismProvider.put(details, mediumListCheck);

            if (sr != null) reasonProvider.put(details, sr);

            if (!pushedPattern) {
                // If in all mediums no pattern was pushed,
                // no need to revisit this task on next executeCrafting this tick
                craftingTaskIterator.remove();
            }

        }
        for (IntConsumer craftingStatusListener : craftUpdateListeners) {
            // if executed tasks is 0 for too much long time, we may need to send an alert in callback registered by
            // addon mods, like an email.
            craftingStatusListener.accept(executedTasks);
        }
    }

    private void returnItems(MEInventoryCrafting ic) {
        for (int x = 0; x < ic.getSizeInventory(); x++) {
            final IAEStack<?> aes = ic.getAEStackInSlot(x);
            if (aes != null) {
                this.inventory.injectItems(aes, Actionable.MODULATE);
            }
        }
    }

    private void storeItems() {
        final IGrid g = this.getGrid();

        if (g == null) {
            return;
        }

        final IStorageGrid sg = g.getCache(IStorageGrid.class);
        final IMEInventory<IAEItemStack> ii = sg.getItemInventory();
        final IMEInventory<IAEFluidStack> fi = sg.getFluidInventory();

        for (IAEItemStack is : this.inventory.getItemList()) {
            is = this.inventory.extractItems(is.copy(), Actionable.MODULATE);

            if (is != null) {
                this.postChange(is, this.machineSrc);
                is = ii.injectItems(is, Actionable.MODULATE, this.machineSrc);

            }

            if (is != null) {
                this.inventory.injectItems(is, Actionable.MODULATE);
            }
        }

        for (IAEFluidStack is : this.inventory.getFluidList()) {
            is = this.inventory.extractItems(is.copy(), Actionable.MODULATE);

            if (is != null) {
                this.postChange(is, this.machineSrc);
                is = fi.injectItems(is, Actionable.MODULATE, this.machineSrc);
            }

            if (is != null) {
                this.inventory.injectItems(is, Actionable.MODULATE);
            }
        }

        if (this.inventory.isEmpty()) {
            this.inventory = new MECraftingInventory();
        }

        this.markDirty();
    }

    public boolean isMissingMode() {
        return this.isMissingMode;
    }

    public ICraftingLink submitJob(final IGrid g, final ICraftingJob job, final BaseActionSource src,
            final ICraftingRequester requestingMachine) {
        if (this.myLastLink != null && this.isBusy()
                && this.finalOutput.isSameType((Object) job.getOutput())
                && this.availableStorage >= this.usedStorage + job.getByteTotal()) {
            return mergeJob(g, job, src, requestingMachine);
        }

        if (!this.tasks.isEmpty() || !this.waitingFor.isEmpty()) {
            return null;
        }

        if (this.isBusy() || !this.isActive() || this.availableStorage < job.getByteTotal()) {
            return null;
        }

        if (!job.supportsCPUCluster(this)) {
            return null;
        }
        this.providers.clear();
        final IStorageGrid sg = g.getCache(IStorageGrid.class);
        final MECraftingInventory ci = new MECraftingInventory(sg, true, false, false);
        this.isMissingMode = job.getCraftingMode() == CraftingMode.IGNORE_MISSING;
        ci.setMissingMode(this.isMissingMode);
        ci.setCpuInventory(this.inventory);

        try {
            this.waitingFor.resetStatus();
            this.waitingForMissing.resetStatus();
            job.startCrafting(ci, this, src);

            // Clear the follow list by default
            this.playersFollowingCurrentCraft.clear();

            if (ci.commit(src)) {
                craftCancelListeners.clear();
                craftUpdateListeners.clear();
                craftCompleteListeners = initializeDefaultOnCompleteListener(); // clear all possible listeners
                // when it comes to a new craft,
                if (job.getOutput() != null) {
                    this.finalOutput = job.getOutput();
                    this.isFakeCrafting = false;
                    this.waiting = false;
                    this.isComplete = false;
                    this.suspended = false;
                    this.usedStorage = job.getByteTotal();
                    this.numsOfOutput = job.getOutput().getStackSize();
                    this.currentJobSource = src;
                    for (IAEStack<?> fte : ci.getExtractFailedList()) {
                        this.waitingForMissing.add(fte);
                    }
                    for (IAEStack<?> wfm : this.waitingForMissing) {
                        this.waitingFor.add(wfm);
                    }
                    this.markDirty();

                    this.updateCPU();
                    final String craftID = this.generateCraftingID();

                    this.myLastLink = new CraftingLink(
                            this.generateLinkData(craftID, requestingMachine == null, false),
                            this);

                    this.prepareElapsedTime();
                    this.prepareStepCount();

                    if (requestingMachine == null) {
                        return this.myLastLink;
                    }

                    final ICraftingLink whatLink = new CraftingLink(
                            this.generateLinkData(craftID, false, true),
                            requestingMachine);

                    this.submitLink(this.myLastLink);
                    this.submitLink(whatLink);

                    final IItemList<IAEStack<?>> list = AEApi.instance().storage().createAEStackList();
                    this.getModernListOfItem(list, CraftingItemList.ALL);
                    for (final IAEStack<?> ge : list) {
                        this.postChange(ge, this.machineSrc);
                    }

                    return whatLink;
                }
            } else {
                this.waitingForMissing.resetStatus();
                this.tasks.clear();
                this.providers.clear();
                this.inventory.resetStatus();
            }
        } catch (final CraftBranchFailure e) {
            handleCraftBranchFailure(e, src);

            this.waitingForMissing.resetStatus();
            this.tasks.clear();
            this.providers.clear();
            this.inventory.resetStatus();
        }

        return null;
    }

    private void handleCraftBranchFailure(final CraftBranchFailure e, final BaseActionSource src) {
        final IAEStack<?> missingStack = e.getMissing();

        if (!(src instanceof PlayerSource playerSource) || playerSource.player == null || missingStack == null) {
            return;
        }

        try {
            long missingCount = missingStack.getStackSize();
            IChatComponent missingItem;
            if (missingStack instanceof IAEItemStack ais) {
                missingItem = ais.getItemStack().func_151000_E();
                missingItem.getChatStyle().setColor(EnumChatFormatting.GOLD);
            } else {
                String missingName = missingStack.getUnlocalizedName();
                if (StatCollector.canTranslate(missingName + ".name") && StatCollector
                        .translateToLocal(missingName + ".name").equals(missingStack.getDisplayName())) {
                    missingItem = new ChatComponentTranslation(missingName + ".name");
                } else {
                    missingItem = new ChatComponentText(missingStack.getDisplayName());
                }
            }

            String missingCountText = EnumChatFormatting.RED
                    + NumberFormat.getNumberInstance(Locale.getDefault()).format(missingCount)
                    + EnumChatFormatting.RESET;
            playerSource.player.addChatMessage(
                    new ChatComponentTranslation(
                            PlayerMessages.CraftingItemsWentMissing.getUnlocalized(),
                            missingCountText,
                            missingItem));
        } catch (Exception ex) {
            AELog.error(ex, "Could not notify player of crafting failure");
        }
    }

    public ICraftingLink mergeJob(final IGrid g, final ICraftingJob job, final BaseActionSource src,
            final ICraftingRequester requestingMachine) {
        final IStorageGrid sg = g.getCache(IStorageGrid.class);
        final MECraftingInventory ci = new MECraftingInventory(sg, true, false, false);

        final MECraftingInventory backupInventory = new MECraftingInventory(inventory);
        final IItemList<IAEStack<?>> backupWaitingForMissing = AEApi.instance().storage().createAEStackList();
        for (IAEStack<?> ais : waitingForMissing) {
            backupWaitingForMissing.add(ais);
        }
        final Map<ICraftingPatternDetails, TaskProgress> tasksBackup = new TreeMap<>(priorityComparator);
        for (Entry<ICraftingPatternDetails, TaskProgress> entry : tasks.entrySet()) {
            TaskProgress newTaskProgress = new TaskProgress();
            newTaskProgress.value = entry.getValue().value;
            tasksBackup.put(entry.getKey(), newTaskProgress);
        }

        try {
            job.startCrafting(ci, this, src);
            if (ci.commit(src)) {
                this.finalOutput.add(job.getOutput());
                this.usedStorage += job.getByteTotal();
                this.numsOfOutput += job.getOutput().getStackSize();
                this.isMissingMode = job.getCraftingMode() == CraftingMode.IGNORE_MISSING;
                this.currentJobSource = src;

                this.prepareStepCount();
                this.markDirty();
                this.updateCPU();

                final ICraftingLink whatLink = new CraftingLink(
                        this.generateLinkData(this.myLastLink.getCraftingID(), false, true),
                        requestingMachine);

                this.submitLink(whatLink);
                return whatLink;
            } else {
                inventory = backupInventory;
                waitingForMissing = backupWaitingForMissing;
                tasks.clear();
                tasks.putAll(tasksBackup);
            }
        } catch (final CraftBranchFailure e) {
            inventory = backupInventory;
            waitingForMissing = backupWaitingForMissing;
            tasks.clear();
            tasks.putAll(tasksBackup);
            handleCraftBranchFailure(e, src);
        }

        return null;
    }

    private boolean hasRemainingTasks() {
        this.tasks.entrySet().removeIf(
                iCraftingPatternDetailsTaskProgressEntry -> iCraftingPatternDetailsTaskProgressEntry.getValue().value
                        <= 0);
        return !this.tasks.isEmpty();
    }

    @Override
    public boolean isBusy() {
        return this.hasRemainingTasks() || !this.waitingFor.isEmpty();
    }

    @Override
    public BaseActionSource getActionSource() {
        return this.machineSrc;
    }

    @Override
    public long getAvailableStorage() {
        return this.availableStorage;
    }

    @Override
    public long getUsedStorage() {
        return this.usedStorage;
    }

    @Override
    public int getCoProcessors() {
        return this.accelerator;
    }

    @Override
    public String getName() {
        return this.myName;
    }

    public boolean isActive() {
        final TileCraftingTile core = this.getCore();

        if (core == null) {
            return false;
        }

        final IGridNode node = core.getActionableNode();
        if (node == null) {
            return false;
        }

        return node.isActive();
    }

    private String generateCraftingID() {
        final long now = System.currentTimeMillis();
        final int hash = System.identityHashCode(this);
        final int hmm = this.finalOutput == null ? 0 : this.finalOutput.hashCode();

        return Long.toString(now, Character.MAX_RADIX) + '-'
                + Integer.toString(hash, Character.MAX_RADIX)
                + '-'
                + Integer.toString(hmm, Character.MAX_RADIX);
    }

    private NBTTagCompound generateLinkData(final String craftingID, final boolean standalone, final boolean req) {
        final NBTTagCompound tag = new NBTTagCompound();

        tag.setString("CraftID", craftingID);
        tag.setBoolean("canceled", false);
        tag.setBoolean("done", false);
        tag.setBoolean("standalone", standalone);
        tag.setBoolean("req", req);

        return tag;
    }

    private void submitLink(final ICraftingLink myLastLink2) {
        if (this.getGrid() != null) {
            final CraftingGridCache cc = this.getGrid().getCache(ICraftingGrid.class);
            cc.addLink((CraftingLink) myLastLink2);
        }
    }

    @Deprecated
    public void getListOfItem(final IItemList<IAEItemStack> list, final CraftingItemList whichList) {
        switch (whichList) {
            case ACTIVE -> {
                for (final IAEStack<?> ais : this.waitingFor) {
                    list.add(stackConvert(ais));
                }
            }
            case PENDING -> {
                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (IAEItemStack ais : t.getKey().getCondensedOutputs()) {
                        ais = ais.copy();
                        ais.setStackSize(ais.getStackSize() * t.getValue().value);
                        list.add(ais);
                    }
                }
            }
            case STORAGE -> {
                inventory.getAvailableItems(list);
            }

            default -> {
                inventory.getAvailableItems(list);

                for (final IAEStack<?> ais : this.waitingFor) {
                    list.add(stackConvert(ais));
                }

                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (IAEItemStack ais : t.getKey().getCondensedOutputs()) {
                        ais = ais.copy();
                        ais.setStackSize(ais.getStackSize() * t.getValue().value);
                        list.add(ais);
                    }
                }
            }
        }
    }

    public void getModernListOfItem(final IItemList<IAEStack<?>> list, final CraftingItemList whichList) {
        switch (whichList) {
            case ACTIVE -> {
                for (final IAEStack<?> ais : this.waitingFor) {
                    list.add(ais);
                }
            }
            case PENDING -> {
                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (IAEStack<?> ais : t.getKey().getCondensedAEOutputs()) {
                        ais = ais.copy();
                        ais.setStackSize(ais.getStackSize() * t.getValue().value);
                        list.add(ais);
                    }
                }
            }
            case STORAGE -> {
                inventory.getAvailableItems(list);
            }

            default -> {
                inventory.getAvailableItems(list);

                for (final IAEStack<?> ais : this.waitingFor) {
                    list.add(ais);
                }

                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (IAEStack<?> ais : t.getKey().getCondensedAEOutputs()) {
                        ais = ais.copy();
                        ais.setStackSize(ais.getStackSize() * t.getValue().value);
                        list.add(ais);
                    }
                }
            }
        }
    }

    public void addStorage(final IAEStack<?> extractItems) {
        extractItems.setCraftable(false);
        this.inventory.injectItems(extractItems, Actionable.MODULATE);
    }

    public void addEmitable(final IAEStack<?> i) {
        this.waitingForMissing.add(i);
    }

    public void addCrafting(final ICraftingPatternDetails details, final long crafts) {
        TaskProgress i = this.tasks.get(details);

        if (i == null) {
            this.tasks.put(details, i = new TaskProgress());
        }

        i.value += crafts;
    }

    public IAEStack<?> getItemStack(final IAEStack what, final CraftingItemList storage2) {
        IAEStack<?> aes;
        switch (storage2) {
            case STORAGE -> aes = this.inventory.findPrecise(what);
            case ACTIVE -> aes = this.waitingFor.findPrecise(what);
            case PENDING -> {
                CraftingGridCache cache = null;
                if (this.getGrid() != null) {
                    cache = this.getGrid().getCache(ICraftingGrid.class);
                }
                aes = what.copy();
                aes.setStackSize(0);
                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (final IAEStack<?> ais : t.getKey().getCondensedAEOutputs()) {
                        if (Objects.equals(ais, aes)) {
                            aes.setStackSize(aes.getStackSize() + ais.getStackSize() * t.getValue().value);
                            if (cache != null) {
                                List<ICraftingMedium> craftingProviders = cache.getMediums(t.getKey());
                                List<NamedDimensionalCoord> dimensionalCoords = new ArrayList<>();
                                for (ICraftingMedium craftingProvider : craftingProviders) {
                                    final TileEntity tile = this.getTile(craftingProvider);
                                    if (tile instanceof TileInterface tileInterface) {
                                        final String dispName = translateFromNetwork(
                                                tileInterface.getInterfaceDuality().getTermName());
                                        dimensionalCoords.add(new NamedDimensionalCoord(tile, dispName));
                                    }
                                }
                                this.providers.put(aes, dimensionalCoords);
                            }
                        }
                    }
                }
            }
            default -> throw new IllegalStateException("Invalid Operation");
        }

        if (aes != null) {
            return aes.copy();
        }

        return what.copy().setStackSize(0);
    }

    private NBTTagCompound persistListeners(int from, List<?> listeners) throws IOException {
        NBTTagCompound tagListeners = new NBTTagCompound();
        for (int i = from; i < listeners.size(); i++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream saveListener = new ObjectOutputStream(out);
            saveListener.writeObject(listeners.get(i));
            tagListeners.setByteArray(String.valueOf(i), out.toByteArray());
        }
        return tagListeners;
    }

    public void writeToNBT(final NBTTagCompound data) {
        data.setTag("finalOutput", writeStackNBT(this.finalOutput, new NBTTagCompound(), true));
        data.setTag("inventory", inventory.writeInventory());
        data.setBoolean("waiting", this.waiting);
        data.setBoolean("isComplete", this.isComplete);
        data.setBoolean("suspended", this.suspended);
        data.setLong("usedStorage", this.usedStorage);
        data.setLong("numsOfOutput", this.numsOfOutput);
        data.setBoolean("isMissingMode", this.isMissingMode);
        data.setInteger("craftingAllowMode", this.craftingAllowMode.ordinal());
        try {
            data.setTag("craftCompleteListeners", persistListeners(1, craftCompleteListeners));
            data.setTag("onCancelListeners", persistListeners(0, craftCancelListeners));
            data.setTag("craftStatusListeners", persistListeners(0, craftUpdateListeners));
        } catch (IOException e) {
            // should not affect normal persistence even if there's mistake here.
            AELog.error(e, "Could not save notification listeners to NBT");
        }

        if (!this.playersFollowingCurrentCraft.isEmpty()) {
            NBTTagList nbtTagList = new NBTTagList();
            for (String name : this.playersFollowingCurrentCraft) {
                nbtTagList.appendTag(new NBTTagString(name));
            }
            data.setTag("playerNameList", nbtTagList);
        }

        if (!this.unreadNotifications.isEmpty()) {
            NBTTagList unreadNotificationsTag = new NBTTagList();
            for (Entry<String, List<CraftNotification>> entry : this.unreadNotifications.entrySet()) {
                NBTTagList notificationsTag = new NBTTagList();
                for (CraftNotification notification : entry.getValue()) {
                    NBTTagCompound tag = new NBTTagCompound();
                    notification.writeToNBT(tag);
                    notificationsTag.appendTag(tag);
                }
                NBTTagCompound playerTag = new NBTTagCompound();
                playerTag.setString("playerName", entry.getKey());
                playerTag.setTag("notifications", notificationsTag);
                unreadNotificationsTag.appendTag(playerTag);
            }
            data.setTag("unreadNotifications", unreadNotificationsTag);
        }

        if (this.myLastLink != null) {
            final NBTTagCompound link = new NBTTagCompound();
            this.myLastLink.writeToNBT(link);
            data.setTag("link", link);
        }

        NBTTagList list = new NBTTagList();
        for (final Entry<ICraftingPatternDetails, TaskProgress> e : this.tasks.entrySet()) {
            final NBTTagCompound item = new NBTTagCompound();
            AEItemStack.create(e.getKey().getPattern()).writeToNBT(item);
            item.setLong("craftingProgress", e.getValue().value);
            list.appendTag(item);
        }
        data.setTag("tasks", list);

        data.setTag("waitingFor", writeAEStackListNBT(this.waitingFor));
        data.setTag("waitingForMissing", writeAEStackListNBT(this.waitingForMissing));

        data.setLong("elapsedTime", this.getElapsedTime());
        data.setLong("startItemCount", this.getStartItemCount());
        data.setLong("remainingItemCount", this.getRemainingItemCount());

        list = new NBTTagList();
        for (final Entry<IAEStack<?>, List<NamedDimensionalCoord>> e : this.providers.entrySet()) {
            NBTTagCompound tmp = new NBTTagCompound();
            tmp.setTag("item", writeStackNBT(e.getKey(), new NBTTagCompound(), true));
            NamedDimensionalCoord.writeListToNBTNamed(tmp, e.getValue());
            list.appendTag(tmp);
        }
        data.setTag("providers", list);
    }

    void done() {
        final TileCraftingTile core = this.getCore();

        core.setCoreBlock(true);

        if (core.getPreviousState() != null) {
            this.readFromNBT(core.getPreviousState());
            core.setPreviousState(null);
        }

        this.updateCPU();
        this.updateName();

    }

    private <T> void unpersistListeners(int from, List<T> toAdd, NBTTagCompound tagCompound)
            throws IOException, ClassNotFoundException {
        if (tagCompound != null) {
            int i = from;
            byte[] r;
            while ((r = tagCompound.getByteArray(String.valueOf(i))).length != 0) {
                toAdd.add((T) new ObjectInputStream(new ByteArrayInputStream(r)).readObject());
                i++;
            }
        }
    }

    public void readFromNBT(final NBTTagCompound data) {
        this.finalOutput = readStackNBT((NBTTagCompound) data.getTag("finalOutput"), true);
        this.inventory.readInventory((NBTTagList) data.getTag("inventory"));
        this.waiting = data.getBoolean("waiting");
        this.isComplete = data.getBoolean("isComplete");
        this.suspended = data.getBoolean("suspended");
        this.usedStorage = data.getLong("usedStorage");
        this.craftingAllowMode = CraftingAllow.values()[(data.getInteger("craftingAllowMode"))];

        if (data.hasKey("link")) {
            final NBTTagCompound link = data.getCompoundTag("link");
            this.myLastLink = new CraftingLink(link, this);
            this.submitLink(this.myLastLink);
        }

        NBTTagList list = data.getTagList("tasks", 10);
        for (int x = 0; x < list.tagCount(); x++) {
            final NBTTagCompound item = list.getCompoundTagAt(x);
            final IAEItemStack pattern = AEItemStack.loadItemStackFromNBT(item);
            if (pattern != null && pattern.getItem() instanceof ICraftingPatternItem cpi) {
                final ICraftingPatternDetails details = cpi.getPatternForItem(pattern.getItemStack(), this.getWorld());
                if (details != null) {
                    final TaskProgress tp = new TaskProgress();
                    tp.value = item.getLong("craftingProgress");
                    this.tasks.put(details, tp);
                }
            }
        }

        this.waitingFor = readAEStackListNBT((NBTTagList) data.getTag("waitingFor"), true);
        for (final IAEStack<?> is : this.waitingFor) {
            this.postCraftingStatusChange(is.copy());
        }
        this.waitingForMissing = readAEStackListNBT((NBTTagList) data.getTag("waitingForMissing"), true);

        this.lastTime = System.nanoTime();
        this.elapsedTime = data.getLong("elapsedTime");
        this.startItemCount = data.getLong("startItemCount");
        this.remainingItemCount = data.getLong("remainingItemCount");
        this.numsOfOutput = data.getLong("numsOfOutput");
        this.isMissingMode = data.getBoolean("isMissingMode");

        NBTBase tag = data.getTag("playerNameList");
        if (tag instanceof NBTTagList ntl) {
            this.playersFollowingCurrentCraft.clear();
            for (int index = 0; index < ntl.tagCount(); index++) {
                this.playersFollowingCurrentCraft.add(ntl.getStringTagAt(index));
            }
        }

        list = data.getTagList("providers", 10);
        for (int x = 0; x < list.tagCount(); x++) {
            final NBTTagCompound pro = list.getCompoundTagAt(x);
            this.providers.put(
                    readStackNBT(pro.getCompoundTag("item"), true),
                    NamedDimensionalCoord.readAsListFromNBTNamed(pro));
        }
        try {
            unpersistListeners(1, craftCompleteListeners, data.getCompoundTag("craftCompleteListeners"));
            unpersistListeners(0, craftCancelListeners, data.getCompoundTag("onCancelListeners"));
            unpersistListeners(0, craftUpdateListeners, data.getCompoundTag("craftStatusListeners"));
        } catch (IOException | ClassNotFoundException e) {
            // should not affect normal persistence even if there's mistake here.
            AELog.error(e, "Could not load notification listeners from NBT");
        }

        if (data.getTag("unreadNotifications") instanceof NBTTagList unreadNotificationsTag) {
            for (int i = 0; i < unreadNotificationsTag.tagCount(); i++) {
                NBTTagCompound playerTag = unreadNotificationsTag.getCompoundTagAt(i);
                String playerName = playerTag.getString("playerName");
                List<CraftNotification> notifications = new ArrayList<>();
                if (playerTag.getTag("notifications") instanceof NBTTagList notificationsTag) {
                    for (int j = 0; j < notificationsTag.tagCount(); j++) {
                        final CraftNotification notification = new CraftNotification();
                        notification.readFromNBT(notificationsTag.getCompoundTagAt(j));
                        notifications.add(notification);
                    }
                }
                if (!notifications.isEmpty()) {
                    this.unreadNotifications.put(playerName, notifications);
                }
            }
        }
    }

    public void updateName() {
        this.myName = "";
        for (final TileCraftingTile te : this.tiles) {

            if (te.hasCustomName()) {
                if (!this.myName.isEmpty()) {
                    this.myName += ' ' + te.getCustomName();
                } else {
                    this.myName = te.getCustomName();
                }
            }
        }
    }

    private World getWorld() {
        return this.getCore().getWorldObj();
    }

    public boolean isMaking(final IAEItemStack what) {
        return isMaking(convertStack(what));
    }

    public boolean isMaking(final IAEStack<?> what) {
        final IAEStack<?> wat = this.waitingFor.findPrecise(what);
        return wat != null && wat.getStackSize() > 0;
    }

    public void breakCluster() {
        final TileCraftingTile t = this.getCore();

        if (t != null) {
            t.breakCluster();
        }
    }

    private void prepareElapsedTime() {
        this.lastTime = System.nanoTime();
        this.elapsedTime = 0;
    }

    private void prepareStepCount() {
        final IItemList<IAEStack<?>> list = AEApi.instance().storage().createAEStackList();

        this.getModernListOfItem(list, CraftingItemList.ACTIVE);
        this.getModernListOfItem(list, CraftingItemList.PENDING);

        long itemCount = 0;
        for (final IAEStack<?> ge : list) {
            itemCount += ge.getStackSize();
        }

        if (this.startItemCount > 0) {
            // If a job was merged, update total steps to be inclusive of completed steps
            long completedSteps = this.startItemCount - this.remainingItemCount;
            this.startItemCount = itemCount + completedSteps;
        } else {
            this.startItemCount = itemCount;
        }
        this.remainingItemCount = itemCount;
    }

    private void updateElapsedTime(final IAEStack<?> is) {
        final long nextStartTime = System.nanoTime();
        this.elapsedTime = this.getElapsedTime() + nextStartTime - this.lastTime;
        this.lastTime = nextStartTime;
        this.remainingItemCount = this.getRemainingItemCount() - is.getStackSize();
    }

    @Override
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    @Override
    public long getRemainingItemCount() {
        return this.remainingItemCount;
    }

    @Override
    public long getStartItemCount() {
        return this.startItemCount;
    }

    public List<String> getPlayersFollowingCurrentCraft() {
        return playersFollowingCurrentCraft;
    }

    public void togglePlayerFollowStatus(final String name) {
        if (this.playersFollowingCurrentCraft.contains(name)) {
            this.playersFollowingCurrentCraft.remove(name);
        } else {
            this.playersFollowingCurrentCraft.add(name);
        }

        final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();
        while (i.hasNext()) {
            if (i.next().getKey() instanceof ContainerCraftingCPU cccpu) {
                cccpu.sendUpdateFollowPacket(playersFollowingCurrentCraft);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public List<NamedDimensionalCoord> getProviders(IAEStack<?> is) {
        return this.providers.getOrDefault(is, Collections.EMPTY_LIST);
    }

    public ScheduledReason getScheduledReason(IAEStack<?> is) {
        for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
            for (final IAEStack<?> ais : t.getKey().getCondensedAEOutputs()) {
                if (Objects.equals(ais, is)) {
                    return reasonProvider.getOrDefault(t.getKey(), ScheduledReason.UNDEFINED);
                }
            }
        }
        return ScheduledReason.UNDEFINED;
    }

    private TileEntity getTile(ICraftingMedium craftingProvider) {
        if (craftingProvider instanceof DualityInterface) {
            return ((DualityInterface) craftingProvider).getHost().getTile();
        } else if (craftingProvider instanceof AEBaseTile) {
            return ((AEBaseTile) craftingProvider).getTile();
        } else if (craftingProvider instanceof IInterfaceViewable interfaceViewable) {
            return interfaceViewable.getTileEntity();
        }
        try {
            Method method = craftingProvider.getClass().getMethod("getTile");
            return (TileEntity) method.invoke(craftingProvider);
        } catch (Exception ignored) {
            return null;
        }

    }

    public int getRemainingOperations() {
        if (this.isComplete) {
            return 0;
        } else {
            return this.remainingOperations;
        }
    }

    public void tryExtractItems() {
        if (this.waitingForMissing.isEmpty()) return;
        if (countToTryExtractItems > 1200) {
            countToTryExtractItems = 0;
            for (IAEStack<?> waitingForItem : this.waitingForMissing) {
                final IGrid grid = this.getGrid();
                if (grid != null) {
                    final IStorageGrid pg = grid.getCache(IStorageGrid.class);
                    if (pg != null) {
                        IAEStack<?> extractedItems = null;
                        if (waitingForItem instanceof IAEItemStack ais) {
                            extractedItems = pg.getItemInventory()
                                    .extractItems(ais, Actionable.MODULATE, this.machineSrc);
                        } else if (waitingForItem instanceof IAEFluidStack ifs) {
                            extractedItems = pg.getFluidInventory()
                                    .extractItems(ifs, Actionable.MODULATE, this.machineSrc);
                        }

                        if (extractedItems != null) {
                            IAEStack<?> notInjected = injectItems(extractedItems, Actionable.MODULATE, this.machineSrc);
                            if (notInjected != null) { // not sure if this even need, but still
                                AELog.logSimple(Level.INFO, "MISSING MODE OVERFLOW! TELL DEVS ASAP!");
                                if (notInjected instanceof IAEItemStack ais) {
                                    pg.getItemInventory().injectItems(ais, Actionable.MODULATE, this.machineSrc);
                                } else if (notInjected instanceof IAEFluidStack ifs) {
                                    pg.getFluidInventory().injectItems(ifs, Actionable.MODULATE, this.machineSrc);
                                }
                                waitingForItem.setStackSize(0);
                            }
                        }
                    }
                }
            }
        } else {
            countToTryExtractItems++;
        }
    }

    public static String translateFromNetwork(String name) {
        final String dispName;
        if (StatCollector.canTranslate(name)) {
            dispName = StatCollector.translateToLocal(name);
        } else {
            String fallback = name + ".name"; // its whatever. save some bytes on network but looks ugly
            if (StatCollector.canTranslate(fallback)) {
                dispName = StatCollector.translateToLocal(fallback);
            } else {
                dispName = StatCollector.translateToFallback(name);
            }
        }
        return dispName;
    }

    public BaseActionSource getCurrentJobSource() {
        return currentJobSource;
    }

    private static class TaskProgress {

        private long value;
    }

    public static class CraftNotification {

        private IAEStack<?> finalOutput;
        private long outputsCount;
        private long elapsedTime;

        public CraftNotification() {
            this.finalOutput = null;
            this.outputsCount = 0L;
            this.elapsedTime = 0L;
        }

        public CraftNotification(IAEStack<?> finalOutput, long outputsCount, long elapsedTime) {
            this.finalOutput = finalOutput;
            this.outputsCount = outputsCount;
            this.elapsedTime = elapsedTime;
        }

        public IChatComponent createMessage() {
            final String elapsedTimeText = DurationFormatUtils.formatDuration(
                    TimeUnit.MILLISECONDS.convert(this.elapsedTime, TimeUnit.NANOSECONDS),
                    GuiText.ETAFormat.getLocal());
            return PlayerMessages.FinishCraftingRemind.toChat(
                    new ChatComponentText(EnumChatFormatting.GREEN + String.valueOf(this.outputsCount)),
                    this.finalOutput.getDisplayName(),
                    new ChatComponentText(EnumChatFormatting.GREEN + elapsedTimeText));
        }

        public void readFromNBT(NBTTagCompound tag) {
            if (tag.hasKey("finalOutput")) {
                this.finalOutput = readStackNBT(tag.getCompoundTag("finalOutput"), true);
            }
            this.outputsCount = tag.getLong("outputsCount");
            this.elapsedTime = tag.getLong("elapsedTime");
        }

        public void writeToNBT(NBTTagCompound tag) {
            if (this.finalOutput != null) {
                NBTTagCompound finalOutputTag = new NBTTagCompound();
                this.finalOutput.writeToNBT(finalOutputTag);
                tag.setTag("finalOutput", finalOutputTag);
            }
            tag.setLong("outputsCount", this.outputsCount);
            tag.setLong("elapsedTime", this.elapsedTime);
        }
    }

    public CraftingAllow getCraftingAllowMode() {
        return this.craftingAllowMode;
    }

    public void changeCraftingAllowMode(CraftingAllow mode) {
        this.craftingAllowMode = mode;
        this.markDirty();
    }

    @Override
    public boolean isSuspended() {
        return this.suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }
}
