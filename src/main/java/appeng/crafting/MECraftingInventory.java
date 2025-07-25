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

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;
import appeng.core.localization.PlayerMessages;
import appeng.util.IterationCounter;

public class MECraftingInventory implements IMEInventory<IAEItemStack> {

    private final MECraftingInventory par;

    private final IMEInventory<IAEItemStack> target;
    private final IItemList<IAEItemStack> localCache;

    private final boolean logExtracted;
    private final IItemList<IAEItemStack> extractedCache;

    private final boolean logInjections;
    private final IItemList<IAEItemStack> injectedCache;

    private final boolean logMissing;
    private final IItemList<IAEItemStack> missingCache;

    private final IItemList<IAEItemStack> failedToExtract = AEApi.instance().storage().createItemList();
    private MECraftingInventory cpuinv;
    private boolean isMissingMode;

    public MECraftingInventory() {
        this.localCache = AEApi.instance().storage().createItemList();
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
        this.target = parent;
        this.logExtracted = parent.logExtracted;
        this.logInjections = parent.logInjections;
        this.logMissing = parent.logMissing;

        if (this.logMissing) {
            this.missingCache = AEApi.instance().storage().createItemList();
        } else {
            this.missingCache = null;
        }

        if (this.logExtracted) {
            this.extractedCache = AEApi.instance().storage().createItemList();
        } else {
            this.extractedCache = null;
        }

        if (this.logInjections) {
            this.injectedCache = AEApi.instance().storage().createItemList();
        } else {
            this.injectedCache = null;
        }

        this.localCache = this.target
                .getAvailableItems(AEApi.instance().storage().createItemList(), IterationCounter.fetchNewId());

        this.par = parent;
    }

    public MECraftingInventory(final IMEMonitor<IAEItemStack> target, final BaseActionSource src,
            final boolean logExtracted, final boolean logInjections, final boolean logMissing) {
        this.target = target;
        this.logExtracted = logExtracted;
        this.logInjections = logInjections;
        this.logMissing = logMissing;

        if (logMissing) {
            this.missingCache = AEApi.instance().storage().createItemList();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = AEApi.instance().storage().createItemList();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = AEApi.instance().storage().createItemList();
        } else {
            this.injectedCache = null;
        }

        this.localCache = AEApi.instance().storage().createItemList();
        for (final IAEItemStack is : target.getStorageList()) {
            this.localCache.add(target.extractItems(is, Actionable.SIMULATE, src));
        }

        this.par = null;
    }

    public MECraftingInventory(final IMEInventory<IAEItemStack> target, final boolean logExtracted,
            final boolean logInjections, final boolean logMissing) {
        this.target = target;
        this.logExtracted = logExtracted;
        this.logInjections = logInjections;
        this.logMissing = logMissing;

        if (logMissing) {
            this.missingCache = AEApi.instance().storage().createItemList();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = AEApi.instance().storage().createItemList();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = AEApi.instance().storage().createItemList();
        } else {
            this.injectedCache = null;
        }

        this.localCache = target
                .getAvailableItems(AEApi.instance().storage().createItemList(), IterationCounter.fetchNewId());
        this.par = null;
    }

    @Override
    public IAEItemStack injectItems(final IAEItemStack input, final Actionable mode, final BaseActionSource src) {
        if (input == null) {
            return null;
        }

        if (mode == Actionable.MODULATE) {
            if (this.logInjections) {
                this.injectedCache.add(input);
            }
            this.localCache.add(input);
        }

        return null;
    }

    @Override
    public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final BaseActionSource src) {
        if (request == null) {
            return null;
        }

        final IAEItemStack list = this.localCache.findPrecise(request);
        if (list == null || list.getStackSize() == 0) {
            return null;
        }

        if (list.getStackSize() >= request.getStackSize()) {
            if (mode == Actionable.MODULATE) {
                list.decStackSize(request.getStackSize());
                if (this.logExtracted) {
                    this.extractedCache.add(request);
                }
            }

            return request;
        }

        final IAEItemStack ret = request.copy();
        ret.setStackSize(list.getStackSize());

        if (mode == Actionable.MODULATE) {
            list.reset();
            if (this.logExtracted) {
                this.extractedCache.add(ret);
            }
        }

        return ret;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out, int iteration) {
        for (final IAEItemStack is : this.localCache) {
            out.add(is);
        }

        return out;
    }

    @Override
    public IAEItemStack getAvailableItem(@Nonnull IAEItemStack request, int iteration) {
        long count = 0;
        for (final IAEItemStack is : this.localCache) {
            if (is != null && is.getStackSize() > 0 && is.isSameType(request)) {
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

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    public IItemList<IAEItemStack> getExtractFailedList() {
        return failedToExtract;
    }

    public void setMissingMode(boolean b) {
        this.isMissingMode = b;
    }

    public void setCpuInventory(MECraftingInventory cp) {
        this.cpuinv = cp;
    }

    public IItemList<IAEItemStack> getItemList() {
        return this.localCache;
    }

    public boolean commit(final BaseActionSource src) {
        final IItemList<IAEItemStack> added = AEApi.instance().storage().createItemList();
        final IItemList<IAEItemStack> pulled = AEApi.instance().storage().createItemList();
        failedToExtract.resetStatus();
        boolean failed = false;

        if (this.logInjections) {
            for (final IAEItemStack inject : this.injectedCache) {
                IAEItemStack result = null;
                added.add(result = this.target.injectItems(inject, Actionable.MODULATE, src));

                if (result != null) {
                    failed = true;
                    break;
                }
            }
        }

        if (failed) {
            for (final IAEItemStack is : added) {
                this.target.extractItems(is, Actionable.MODULATE, src);
            }

            return false;
        }

        if (this.logExtracted) {
            for (final IAEItemStack extra : this.extractedCache) {
                IAEItemStack result = null;
                pulled.add(result = this.target.extractItems(extra, Actionable.MODULATE, src));

                if (result == null || result.getStackSize() != extra.getStackSize()) {
                    if (isMissingMode) {
                        if (result == null) {
                            failedToExtract.add(extra.copy());
                            cpuinv.localCache.findPrecise(extra).setStackSize(0);
                            extra.setStackSize(0);
                        } else if (result.getStackSize() != extra.getStackSize()) {
                            failedToExtract
                                    .add(extra.copy().setStackSize(extra.getStackSize() - result.getStackSize()));
                            cpuinv.localCache.findPrecise(extra).setStackSize(result.getStackSize());
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
            for (final IAEItemStack is : added) {
                this.target.extractItems(is, Actionable.MODULATE, src);
            }

            for (final IAEItemStack is : pulled) {
                this.target.injectItems(is, Actionable.MODULATE, src);
            }

            return false;
        }

        if (this.logMissing && this.par != null) {
            for (final IAEItemStack extra : this.missingCache) {
                this.par.addMissing(extra);
            }
        }

        return true;
    }

    private void addMissing(final IAEItemStack extra) {
        this.missingCache.add(extra);
    }

    public void ignore(final IAEItemStack what) {
        final IAEItemStack list = this.localCache.findPrecise(what);
        if (list != null) {
            list.setStackSize(0);
        }
    }

    private void handleCraftExtractFailure(final IAEItemStack expected, final IAEItemStack extracted,
            final BaseActionSource src) {
        if (!(src instanceof PlayerSource)) {
            return;
        }

        try {
            EntityPlayer player = ((PlayerSource) src).player;
            if (player == null || expected == null || expected.getItem() == null) return;

            IChatComponent missingDisplayName;
            String missingName = expected.getItemStack().getUnlocalizedName();
            if (StatCollector.canTranslate(missingName + ".name") && StatCollector
                    .translateToLocal(missingName + ".name").equals(expected.getItemStack().getDisplayName()))
                missingDisplayName = new ChatComponentTranslation(missingName + ".name");
            else missingDisplayName = new ChatComponentText(expected.getItemStack().getDisplayName());

            player.addChatMessage(
                    new ChatComponentTranslation(
                            PlayerMessages.CraftingCantExtract.getUnlocalized(),
                            extracted.getStackSize(),
                            expected.getStackSize(),
                            missingName).appendText(" (").appendSibling(missingDisplayName).appendText(")"));

        } catch (Exception ex) {
            AELog.error(ex, "Could not notify player of crafting failure");
        }
    }
}
