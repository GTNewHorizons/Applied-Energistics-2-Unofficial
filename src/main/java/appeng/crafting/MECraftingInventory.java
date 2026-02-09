/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.crafting;

import static appeng.util.Platform.writeAEStackListNBT;

import java.text.NumberFormat;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;
import appeng.core.localization.PlayerMessages;
import appeng.util.Platform;

public class MECraftingInventory implements IMEInventory<IAEStack> {

    private final MECraftingInventory par;

    private final IStorageMonitorable target;
    private final Map<IAEStackType<?>, IItemList<IAEStack>> inventoryMap = new IdentityHashMap<>();

    private final boolean logExtracted;
    private final IItemList<IAEStack<?>> extractedCache;

    private final boolean logInjections;
    private final IItemList<IAEStack<?>> injectedCache;

    private final boolean logMissing;
    private final IItemList<IAEStack<?>> missingCache;

    private final IItemList<IAEStack<?>> failedToExtract = AEApi.instance().storage().createAEStackList();
    private MECraftingInventory cpuinv;
    private boolean isMissingMode;

    public MECraftingInventory() {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.inventoryMap.put(type, (IItemList) type.createList());
        }
        this.extractedCache = null;
        this.injectedCache = null;
        this.missingCache = null;
        this.logExtracted = false;
        this.logInjections = false;
        this.logMissing = false;
        this.target = null;
        this.par = null;
    }

    public MECraftingInventory(final MECraftingInventory parent) {
        this.target = parent.target;
        this.logExtracted = parent.logExtracted;
        this.logInjections = parent.logInjections;
        this.logMissing = parent.logMissing;

        if (this.logMissing) {
            this.missingCache = AEApi.instance().storage().createAEStackList();
        } else {
            this.missingCache = null;
        }

        if (this.logExtracted) {
            this.extractedCache = AEApi.instance().storage().createAEStackList();
        } else {
            this.extractedCache = null;
        }

        if (this.logInjections) {
            this.injectedCache = AEApi.instance().storage().createAEStackList();
        } else {
            this.injectedCache = null;
        }

        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.inventoryMap.put(type, parent.getAvailableItems(type.createList()));
        }

        this.par = parent;
    }

    public MECraftingInventory(final MECraftingInventory target, final boolean logExtracted,
            final boolean logInjections, final boolean logMissing) {
        this(target.target, logExtracted, logInjections, logMissing);
    }

    public MECraftingInventory(final IStorageMonitorable target, final boolean logExtracted,
            final boolean logInjections, final boolean logMissing) {
        this.target = target;
        this.logExtracted = logExtracted;
        this.logInjections = logInjections;
        this.logMissing = logMissing;

        if (logMissing) {
            this.missingCache = AEApi.instance().storage().createAEStackList();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = AEApi.instance().storage().createAEStackList();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = AEApi.instance().storage().createAEStackList();
        } else {
            this.injectedCache = null;
        }

        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            IItemList list = type.createList();
            this.inventoryMap.put(type, list);
            for (final IAEStack<?> is : target.getMEMonitor(type).getStorageList()) {
                list.add(is.copy());
            }
        }

        this.par = null;
    }

    public void injectItems(final IAEStack<?> input, final Actionable mode) {
        if (input != null) {
            if (mode == Actionable.MODULATE) {
                this.inventoryMap.get(input.getStackType()).add(input);
                if (this.logInjections) {
                    this.injectedCache.add(input);
                }
            }
        }
    }

    public <StackType extends IAEStack<StackType>> StackType extractItems(final StackType request,
            final Actionable mode) {
        if (request == null) return null;

        IAEStack<?> stack = this.inventoryMap.get(request.getStackType()).findPrecise(request);
        if (stack == null || stack.getStackSize() <= 0) return null;

        if (stack.getStackSize() >= request.getStackSize()) {
            if (mode == Actionable.MODULATE) {
                stack.decStackSize(request.getStackSize());
                if (this.logExtracted) {
                    this.extractedCache.add(request);
                }
            }

            return request;
        }

        final StackType ret = request.copy();
        ret.setStackSize(stack.getStackSize());

        if (mode == Actionable.MODULATE) {
            stack.reset();
            if (this.logExtracted) {
                this.extractedCache.add(ret);
            }
        }

        return ret;
    }

    public IItemList getAvailableItems(final IItemList out) {
        IAEStackType<?> listType = out.getStackType();

        if (listType != null) {
            for (IAEStack<?> stack : this.inventoryMap.get(listType)) {
                out.add(stack);
            }
        } else {
            for (IItemList<IAEStack> list : this.inventoryMap.values()) {
                for (IAEStack<?> stack : list) {
                    out.add(stack);
                }
            }
        }

        return out;
    }

    @SuppressWarnings({ "rawtypes" })
    public IAEStack getAvailableItem(@Nonnull IAEStack request) {
        IAEStack<?> stack = this.inventoryMap.get(request.getStackType()).findPrecise(request);
        return stack != null ? stack.copy() : null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <StackType extends IAEStack<StackType>> Collection<StackType> findFuzzy(final StackType filter,
            final FuzzyMode fuzzy) {
        if (filter == null) return null;

        return (Collection) this.inventoryMap.get(filter.getStackType()).findFuzzy(filter, fuzzy);
    }

    @SuppressWarnings({ "unchecked" })
    public <StackType extends IAEStack<StackType>> StackType findPrecise(final StackType is) {
        if (is == null) return null;

        return (StackType) this.inventoryMap.get(is.getStackType()).findPrecise(is);
    }

    public IItemList<IAEStack<?>> getExtractFailedList() {
        return failedToExtract;
    }

    public void setMissingMode(boolean b) {
        this.isMissingMode = b;
    }

    public void setCpuInventory(MECraftingInventory cp) {
        this.cpuinv = cp;
    }

    public Map<IAEStackType<?>, IItemList<IAEStack>> getInventoryMap() {
        return this.inventoryMap;
    }

    @SuppressWarnings({ "rawtypes" })
    public boolean isEmpty() {
        for (IItemList list : this.inventoryMap.values()) {
            if (!list.isEmpty()) return false;
        }
        return true;
    }

    @SuppressWarnings({ "rawtypes" })
    public void resetStatus() {
        for (IItemList list : this.inventoryMap.values()) {
            list.resetStatus();
        }
    }

    @SuppressWarnings({ "rawtypes" })
    public NBTTagList writeInventory() {
        NBTTagList tag = new NBTTagList();
        for (IItemList list : this.inventoryMap.values()) {
            writeAEStackListNBT(list, tag);
        }
        return tag;
    }

    public void readInventory(NBTTagList tag) {
        IItemList<IAEStack<?>> list = Platform.readAEStackListNBT(tag, true);
        for (IAEStack<?> i : list) {
            injectItems(i, Actionable.MODULATE);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public boolean commit(final BaseActionSource src) {
        final IItemList<IAEStack<?>> added = AEApi.instance().storage().createAEStackList();
        final IItemList<IAEStack<?>> pulled = AEApi.instance().storage().createAEStackList();
        failedToExtract.resetStatus();
        boolean failed = false;

        if (this.logInjections) {
            for (final IAEStack<?> inject : this.injectedCache) {

                IMEMonitor monitor = this.target.getMEMonitor(inject.getStackType());
                IAEStack<?> result = monitor.injectItems(inject, Actionable.MODULATE, src);

                added.add(result);

                if (result != null) {
                    failed = true;
                    break;
                }
            }
        }

        if (failed) {
            for (final IAEStack<?> is : added) {
                IMEMonitor monitor = this.target.getMEMonitor(is.getStackType());
                monitor.extractItems(is, Actionable.MODULATE, src);
            }

            return false;
        }

        if (this.logExtracted) {
            for (final IAEStack<?> extra : this.extractedCache) {
                IMEMonitor monitor = this.target.getMEMonitor(extra.getStackType());
                IAEStack<?> result = monitor.extractItems(extra, Actionable.MODULATE, src);

                pulled.add(result);

                if (result == null || result.getStackSize() != extra.getStackSize()) {
                    if (isMissingMode) {
                        if (result == null) {
                            failedToExtract.add(extra.copy());

                            this.cpuinv.inventoryMap.get(extra.getStackType()).findPrecise(extra).setStackSize(0);

                            extra.setStackSize(0);
                        } else if (result.getStackSize() != extra.getStackSize()) {
                            failedToExtract
                                    .add(extra.copy().setStackSize(extra.getStackSize() - result.getStackSize()));

                            this.cpuinv.inventoryMap.get(extra.getStackType()).findPrecise(extra);

                            extra.setStackSize(result.getStackSize());
                        }
                    } else {
                        failed = true;
                        handleCraftExtractFailure(extra, result, src);
                        break;
                    }
                }
            }
        }

        if (failed) {
            for (final IAEStack<?> is : added) {
                IMEMonitor monitor = this.target.getMEMonitor(is.getStackType());
                monitor.extractItems(is, Actionable.MODULATE, src);
            }

            for (final IAEStack<?> is : pulled) {
                IMEMonitor monitor = this.target.getMEMonitor(is.getStackType());
                monitor.injectItems(is, Actionable.MODULATE, src);
            }

            return false;
        }

        if (this.logMissing && this.par != null) {
            for (final IAEStack<?> extra : this.missingCache) {
                this.par.addMissing(extra);
            }
        }

        return true;
    }

    private void addMissing(final IAEStack<?> extra) {
        this.missingCache.add(extra);
    }

    public void ignore(final IAEStack<?> what) {
        IAEStack<?> stack = this.inventoryMap.get(what.getStackType()).findPrecise(what);
        if (stack != null) {
            stack.setStackSize(0);
        }
    }

    private void handleCraftExtractFailure(final IAEStack<?> expected, final IAEStack<?> extracted,
            final BaseActionSource src) {
        if (!(src instanceof PlayerSource)) {
            return;
        }

        try {
            EntityPlayer player = ((PlayerSource) src).player;
            if (player == null || expected == null) return;

            IChatComponent missingItem;
            if (expected instanceof IAEItemStack ais) {
                missingItem = ais.getItemStack().func_151000_E();
            } else {
                String missingName = expected.getUnlocalizedName();
                if (StatCollector.canTranslate(missingName + ".name")
                        && StatCollector.translateToLocal(missingName + ".name").equals(expected.getDisplayName())) {
                    missingItem = new ChatComponentTranslation(missingName + ".name");
                } else {
                    missingItem = new ChatComponentText(expected.getDisplayName());
                }
            }

            missingItem.getChatStyle().setColor(EnumChatFormatting.GOLD);
            String expectedCount = EnumChatFormatting.RED
                    + NumberFormat.getNumberInstance(Locale.getDefault()).format(expected.getStackSize())
                    + EnumChatFormatting.RESET;
            String extractedCount = EnumChatFormatting.RED
                    + NumberFormat.getNumberInstance(Locale.getDefault()).format(extracted.getStackSize())
                    + EnumChatFormatting.RESET;

            player.addChatMessage(
                    new ChatComponentTranslation(
                            PlayerMessages.CraftingCantExtract.getUnlocalized(),
                            extractedCount,
                            expectedCount,
                            missingItem));

        } catch (Exception ex) {
            AELog.error(ex, "Could not notify player of crafting failure");
        }
    }

    @Override
    @SuppressWarnings({ "rawtypes" })
    public IAEStack injectItems(IAEStack input, Actionable type, BaseActionSource src) {
        this.injectItems(input, type);
        return null;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public IAEStack extractItems(IAEStack request, Actionable mode, BaseActionSource src) {
        return this.extractItems(request, mode);
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }
}
