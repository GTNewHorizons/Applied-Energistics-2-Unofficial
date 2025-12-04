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

import static appeng.api.storage.data.IItemList.LIST_FLUID;
import static appeng.api.storage.data.IItemList.LIST_ITEM;
import static appeng.api.storage.data.IItemList.LIST_MIXED;
import static appeng.util.Platform.convertStack;
import static appeng.util.Platform.isAE2FCLoaded;
import static appeng.util.Platform.writeAEStackListNBT;

import java.text.NumberFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.glodblock.github.common.item.ItemFluidDrop;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;
import appeng.core.localization.PlayerMessages;
import appeng.util.Platform;

public class MECraftingInventory implements IMEInventory<IAEStack> {

    private final MECraftingInventory par;

    private final IStorageMonitorable target;
    private final IItemList<IAEItemStack> localItemCache;
    private final IItemList<IAEFluidStack> localFluidCache;

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
        this.localItemCache = AEApi.instance().storage().createItemList();
        this.localFluidCache = AEApi.instance().storage().createFluidList();
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

        this.localItemCache = parent.getAvailableItems(AEApi.instance().storage().createItemList());
        this.localFluidCache = parent.getAvailableItems(AEApi.instance().storage().createFluidList());

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

        this.localItemCache = AEApi.instance().storage().createItemList();
        this.localFluidCache = AEApi.instance().storage().createFluidList();

        for (final IAEItemStack is : target.getItemInventory().getStorageList()) {
            this.localItemCache.add(is.copy());
        }
        for (final IAEFluidStack is : target.getFluidInventory().getStorageList()) {
            this.localFluidCache.add(is.copy());
        }

        this.par = null;
    }

    public void injectItems(final IAEStack<?> input, final Actionable mode) {
        if (input != null) {
            if (mode == Actionable.MODULATE) {
                boolean convert = false;
                if (input instanceof IAEItemStack ais) {
                    if (isAE2FCLoaded && ais.getItem() instanceof ItemFluidDrop) {
                        this.localFluidCache.add((IAEFluidStack) convertStack(ais));
                        convert = true;
                    } else {
                        this.localItemCache.add(ais);
                    }
                } else {
                    this.localFluidCache.add((IAEFluidStack) input);
                }
                if (this.logInjections) this.injectedCache.add(convert ? convertStack((IAEItemStack) input) : input);
            }
        }
    }

    public <StackType extends IAEStack<StackType>> StackType extractItems(final StackType request,
            final Actionable mode) {
        if (request == null) return null;

        IAEStack<?> list;
        boolean convert = false;
        if (request instanceof IAEItemStack ais) {
            if (isAE2FCLoaded && ais.getItem() instanceof ItemFluidDrop) {
                list = this.localFluidCache.findPrecise((IAEFluidStack) convertStack(ais));
                convert = true;
            } else list = this.localItemCache.findPrecise(ais);
        } else {
            list = this.localFluidCache.findPrecise((IAEFluidStack) request);
        }
        if (list == null || list.getStackSize() == 0) return null;

        if (list.getStackSize() >= request.getStackSize()) {
            if (mode == Actionable.MODULATE) {
                list.decStackSize(request.getStackSize());
                if (this.logExtracted) {
                    this.extractedCache.add(convert ? convertStack((IAEItemStack) request) : request);
                }
            }

            return request;
        }

        final StackType ret = request.copy();
        ret.setStackSize(list.getStackSize());

        if (mode == Actionable.MODULATE) {
            list.reset();
            if (this.logExtracted) {
                this.extractedCache.add(convert ? convertStack((IAEItemStack) ret) : ret);
            }
        }

        return ret;
    }

    public IItemList getAvailableItems(final IItemList out) {
        byte listType = out.getStackType();
        if (listType == LIST_ITEM || listType == LIST_MIXED) {
            for (final IAEItemStack is : this.localItemCache) {
                out.add(is);
            }
        }

        if (listType == LIST_FLUID || listType == LIST_MIXED) {
            for (final IAEFluidStack is : this.localFluidCache) {
                out.add(is);
            }
        }

        return out;
    }

    public IAEStack getAvailableItem(@Nonnull IAEStack request) {
        long count = 0;

        IItemList<?> list;
        boolean convert = false;
        if (request instanceof IAEItemStack ais) {
            if (isAE2FCLoaded && ais.getItem() instanceof ItemFluidDrop) {
                list = localFluidCache;
                convert = true;
            } else {
                list = localItemCache;
            }
        } else {
            list = localFluidCache;
        }

        for (final IAEStack is : list) {
            if (is != null && is.getStackSize() > 0
                    && (convert ? is.isSameType((Object) convertStack((IAEItemStack) request))
                            : is.isSameType((Object) request))) {
                count += is.getStackSize();
                if (count < 0) {
                    // overflow
                    count = Long.MAX_VALUE;
                    break;
                }
            }
        }

        return count == 0 ? null : request.copy().setStackSize(count);
    }

    public <StackType extends IAEStack<StackType>> Collection<StackType> findFuzzy(final StackType filter,
            final FuzzyMode fuzzy) {
        if (filter == null) return null;
        if (filter instanceof IAEItemStack ais) {
            if (isAE2FCLoaded && ais.getItem() instanceof ItemFluidDrop) {
                return Collections.singletonList((StackType) findPrecise(ais));
            } else {
                return (Collection<StackType>) localItemCache.findFuzzy(ais, fuzzy);
            }
        } else {
            return (Collection<StackType>) localFluidCache.findFuzzy((IAEFluidStack) filter, fuzzy);
        }
    }

    public <StackType extends IAEStack<StackType>> StackType findPrecise(final StackType is) {
        if (is == null) return null;

        if (is instanceof IAEItemStack ais) {
            return (StackType) localItemCache.findPrecise((IAEItemStack) is);
        } else {
            return (StackType) localFluidCache.findPrecise((IAEFluidStack) is);
        }
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

    public IItemList<IAEItemStack> getItemList() {
        return this.localItemCache;
    }

    public IItemList<IAEFluidStack> getFluidList() {
        return this.localFluidCache;
    }

    public boolean isEmpty() {
        return localItemCache.isEmpty() && localFluidCache.isEmpty();
    }

    public void resetStatus() {
        localItemCache.resetStatus();
        localFluidCache.resetStatus();
    }

    public NBTTagList writeInventory() {
        return writeAEStackListNBT(localFluidCache, writeAEStackListNBT(localItemCache));
    }

    public void readInventory(NBTTagList tag) {
        IItemList<IAEStack<?>> list = Platform.readAEStackListNBT(tag, true);
        for (IAEStack<?> i : list) {
            injectItems(i, Actionable.MODULATE);
        }
    }

    public boolean commit(final BaseActionSource src) {
        final IItemList<IAEStack<?>> added = AEApi.instance().storage().createAEStackList();
        final IItemList<IAEStack<?>> pulled = AEApi.instance().storage().createAEStackList();
        failedToExtract.resetStatus();
        boolean failed = false;

        if (this.logInjections) {
            for (final IAEStack<?> inject : this.injectedCache) {
                IAEStack<?> result;
                if (inject.isItem()) {
                    result = this.target.getItemInventory()
                            .injectItems((IAEItemStack) inject, Actionable.MODULATE, src);
                } else {
                    result = this.target.getFluidInventory()
                            .injectItems((IAEFluidStack) inject, Actionable.MODULATE, src);
                }
                added.add(result);

                if (result != null) {
                    failed = true;
                    break;
                }
            }
        }

        if (failed) {
            for (final IAEStack<?> is : added) {
                if (is.isItem()) {
                    this.target.getItemInventory().extractItems((IAEItemStack) is, Actionable.MODULATE, src);
                } else {
                    this.target.getFluidInventory().extractItems((IAEFluidStack) is, Actionable.MODULATE, src);
                }
            }

            return false;
        }

        if (this.logExtracted) {
            for (final IAEStack<?> extra : this.extractedCache) {
                IAEStack<?> result;
                if (extra.isItem()) {
                    result = this.target.getItemInventory()
                            .extractItems((IAEItemStack) extra, Actionable.MODULATE, src);
                } else {
                    result = this.target.getFluidInventory()
                            .extractItems((IAEFluidStack) extra, Actionable.MODULATE, src);
                }
                pulled.add(result);

                if (result == null || result.getStackSize() != extra.getStackSize()) {
                    if (isMissingMode) {
                        if (result == null) {
                            failedToExtract.add(extra.copy());

                            if (extra.isItem()) {
                                cpuinv.localItemCache.findPrecise((IAEItemStack) extra).setStackSize(0);
                            } else {
                                cpuinv.localFluidCache.findPrecise((IAEFluidStack) extra).setStackSize(0);
                            }

                            extra.setStackSize(0);
                        } else if (result.getStackSize() != extra.getStackSize()) {
                            failedToExtract
                                    .add(extra.copy().setStackSize(extra.getStackSize() - result.getStackSize()));

                            if (extra.isItem()) {
                                cpuinv.localItemCache.findPrecise((IAEItemStack) extra)
                                        .setStackSize(result.getStackSize());
                            } else {
                                cpuinv.localFluidCache.findPrecise((IAEFluidStack) extra)
                                        .setStackSize(result.getStackSize());
                            }

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
                if (is.isItem()) {
                    this.target.getItemInventory().extractItems((IAEItemStack) is, Actionable.MODULATE, src);
                } else {
                    this.target.getFluidInventory().extractItems((IAEFluidStack) is, Actionable.MODULATE, src);
                }
            }

            for (final IAEStack<?> is : pulled) {
                if (is.isItem()) {
                    this.target.getItemInventory().injectItems((IAEItemStack) is, Actionable.MODULATE, src);
                } else {
                    this.target.getFluidInventory().injectItems((IAEFluidStack) is, Actionable.MODULATE, src);
                }
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
        if (what.isItem()) {
            final IAEItemStack list = this.localItemCache.findPrecise((IAEItemStack) what);
            if (list != null) list.setStackSize(0);
        } else {
            final IAEFluidStack list = this.localFluidCache.findPrecise((IAEFluidStack) what);
            if (list != null) list.setStackSize(0);
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
    public IAEStack injectItems(IAEStack input, Actionable type, BaseActionSource src) {
        injectItems(input, type);
        return null;
    }

    @Override
    public IAEStack extractItems(IAEStack request, Actionable mode, BaseActionSource src) {
        return extractItems(request, mode);
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }
}
