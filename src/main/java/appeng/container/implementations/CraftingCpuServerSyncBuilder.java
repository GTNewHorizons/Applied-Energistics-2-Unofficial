package appeng.container.implementations;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.jetbrains.annotations.NotNull;

import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;

final class CraftingCpuServerSyncBuilder {

    private CraftingCpuServerSyncBuilder() {}

    static NBTTagCompound buildFollowingPlayersNbt(final List<String> playersFollowingCurrentCraft) {
        final NBTTagCompound result = new NBTTagCompound();
        final NBTTagList tagList = new NBTTagList();

        if (playersFollowingCurrentCraft != null) {
            for (final String name : playersFollowingCurrentCraft) {
                tagList.appendTag(new NBTTagString(name));
            }
        }

        result.setTag("playNameList", tagList);
        return result;
    }

    static List<CraftingCpuEntry> buildVisualEntryUpdates(@NotNull final CraftingCPUCluster monitor,
            final Iterable<IAEStack<?>> changedStacks) {
        final List<CraftingCpuEntry> updates = new ArrayList<>();
        for (final IAEStack<?> stack : changedStacks) {
            final IAEStack<?> normalizedStack = normalizeStack(stack);
            final long storedAmount = monitor.getStackAmount(normalizedStack, CraftingItemList.STORAGE);
            final long activeAmount = monitor.getStackAmount(normalizedStack, CraftingItemList.ACTIVE);
            final long pendingAmount = monitor.getStackAmount(normalizedStack, CraftingItemList.PENDING);
            updates.add(
                    new CraftingCpuEntry(
                            normalizedStack,
                            storedAmount,
                            activeAmount,
                            pendingAmount,
                            monitor.getScheduledReason(normalizedStack)));
        }
        return updates;
    }

    private static IAEStack<?> normalizeStack(final IAEStack<?> stack) {
        final IAEStack<?> normalizedStack = stack.copy();
        normalizedStack.setStackSize(1);
        return normalizedStack;
    }
}
