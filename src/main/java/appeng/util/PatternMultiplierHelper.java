package appeng.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;

public class PatternMultiplierHelper {

    public static int getMaxBitMultiplier(ICraftingPatternDetails details) {
        int maxMulti = 62;
        for (IAEStack<?> input : details.getAEInputs()) {
            if (input == null) continue;
            long size = input.getStackSize();
            if (size <= 0) continue;
            int highestBit = 63 - Long.numberOfLeadingZeros(size);
            int max = 62 - highestBit;
            if (max < 0) max = 0;
            if (max < maxMulti) maxMulti = max;
        }
        for (IAEStack<?> out : details.getAEOutputs()) {
            if (out == null) continue;
            long size = out.getStackSize();
            if (size <= 0) continue;
            int highestBit = 63 - Long.numberOfLeadingZeros(size);
            int max = 62 - highestBit;
            if (max < 0) max = 0;
            if (max < maxMulti) maxMulti = max;
        }
        return maxMulti;
    }

    public static int getMaxBitDivider(ICraftingPatternDetails details) {
        int maxDiv = 62;
        for (IAEStack<?> input : details.getAEInputs()) {
            if (input == null) continue;
            long size = input.getStackSize();
            if (size <= 0) continue;
            int tz = Math.min(Long.numberOfTrailingZeros(size), 62);
            if (tz < maxDiv) maxDiv = tz;
        }
        for (IAEStack<?> out : details.getAEOutputs()) {
            if (out == null) continue;
            long size = out.getStackSize();
            if (size <= 0) continue;
            int tz = Math.min(Long.numberOfTrailingZeros(size), 62);
            if (tz < maxDiv) maxDiv = tz;
        }
        return maxDiv;
    }

    public static void applyModification(ItemStack stack, int bitMultiplier) {
        if (bitMultiplier == 0) return;
        boolean isDividing = false;
        if (bitMultiplier < 0) {
            isDividing = true;
            bitMultiplier = -bitMultiplier;
        }
        NBTTagCompound encodedValue = stack.stackTagCompound;
        final NBTTagList inTag = encodedValue.getTagList("in", 10);
        final NBTTagList outTag = encodedValue.getTagList("out", 10);
        for (int x = 0; x < inTag.tagCount(); x++) {
            final NBTTagCompound tag = inTag.getCompoundTagAt(x);
            if (tag.hasNoTags()) continue;
            if (tag.hasKey("Count")) {
                tag.setInteger(
                        "Count",
                        isDividing ? tag.getInteger("Count") >> bitMultiplier
                                : tag.getInteger("Count") << bitMultiplier);
            }
            // Support for IAEItemStack (ae2fc patterns)
            if (tag.hasKey("Cnt", 4)) {
                tag.setLong(
                        "Cnt",
                        isDividing ? tag.getLong("Cnt") >> bitMultiplier : tag.getLong("Cnt") << bitMultiplier);
            }
        }

        for (int x = 0; x < outTag.tagCount(); x++) {
            final NBTTagCompound tag = outTag.getCompoundTagAt(x);
            if (tag.hasNoTags()) continue;
            if (tag.hasKey("Count")) {
                tag.setInteger(
                        "Count",
                        isDividing ? tag.getInteger("Count") >> bitMultiplier
                                : tag.getInteger("Count") << bitMultiplier);
            }
            // Support for IAEItemStack (ae2fc patterns)
            if (tag.hasKey("Cnt", 4)) {
                tag.setLong(
                        "Cnt",
                        isDividing ? tag.getLong("Cnt") >> bitMultiplier : tag.getLong("Cnt") << bitMultiplier);
            }
        }
    }
}
