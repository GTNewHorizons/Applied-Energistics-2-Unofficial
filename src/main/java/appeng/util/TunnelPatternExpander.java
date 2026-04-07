package appeng.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.items.misc.ItemTunnelPattern;
import appeng.me.cache.CraftingGridCache;

public final class TunnelPatternExpander {

    private TunnelPatternExpander() {}

    public static List<IAEStack<?>> expandInputs(final IAEStack<?>[] inputs, final CraftingGridCache cache,
            final Set<ICraftingPatternDetails> parentPatterns) {
        if (inputs == null || inputs.length == 0) {
            return new ArrayList<>(0);
        }
        final List<IAEStack<?>> expandedInputs = new ArrayList<>(inputs.length);
        final Set<UUID> expansionStack = new HashSet<>();
        for (IAEStack<?> input : inputs) {
            if (!expandInputOnlyPattern(input, cache, parentPatterns, expansionStack, expandedInputs)) {
                return null;
            }
        }
        return expandedInputs;
    }

    private static boolean expandInputOnlyPattern(final IAEStack<?> input, final CraftingGridCache cache,
            final Set<ICraftingPatternDetails> parentPatterns, final Set<UUID> expansionStack,
            final List<IAEStack<?>> expandedInputs) {
        if (input == null) {
            return true;
        }
        if (!(input instanceof IAEItemStack ais)) {
            expandedInputs.add(input);
            return true;
        }
        final ItemStack itemStack = ais.getItemStack();
        if (!ItemTunnelPattern.isTunnelPattern(itemStack)) {
            expandedInputs.add(input);
            return true;
        }
        final UUID uuid = ItemTunnelPattern.getTunnelUuid(itemStack);
        if (uuid == null) {
            return false;
        }
        final ICraftingPatternDetails tunnelPattern = cache != null ? cache.getInputOnlyPattern(uuid) : null;
        if (tunnelPattern == null) {
            expandedInputs.add(input);
            return true;
        }
        if (!tunnelPattern.isInputOnly()) {
            expandedInputs.add(input);
            return true;
        }
        if (parentPatterns != null && parentPatterns.contains(tunnelPattern)) {
            return false;
        }
        if (!expansionStack.add(uuid)) {
            return false;
        }
        final IAEStack<?>[] tunnelInputs = tunnelPattern.getCondensedAEInputs();
        if (tunnelInputs.length == 0) {
            expansionStack.remove(uuid);
            return false;
        }
        for (IAEStack<?> tunnelInput : tunnelInputs) {
            if (tunnelInput == null) {
                continue;
            }
            final IAEStack<?> expanded = tunnelInput.copy();
            final long multiplier = input.getStackSize();
            final long expandedAmount;
            try {
                expandedAmount = Math.multiplyExact(expanded.getStackSize(), multiplier);
            } catch (ArithmeticException ex) {
                expansionStack.remove(uuid);
                return false;
            }
            expanded.setStackSize(expandedAmount);
            if (!expandInputOnlyPattern(expanded, cache, parentPatterns, expansionStack, expandedInputs)) {
                expansionStack.remove(uuid);
                return false;
            }
        }
        expansionStack.remove(uuid);
        return true;
    }
}
