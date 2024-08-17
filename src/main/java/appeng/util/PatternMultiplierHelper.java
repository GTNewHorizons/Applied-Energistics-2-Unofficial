package appeng.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

public class PatternMultiplierHelper {

    private static final int FINAL_BIT = 1 << 30; // 1 bit before integer overflow

    public static int getMaxBitMultiplier(ICraftingPatternDetails details) {
        // limit to 2B per item in pattern
        int maxMulti = 30;
        for (IAEItemStack input : details.getInputs()) {
            if (input == null) continue;
            long size = input.getStackSize();
            int max = 0;
            while ((size & FINAL_BIT) == 0) {
                size <<= 1;
                max++;
            }
            if (max < maxMulti) maxMulti = max;
        }
        for (IAEItemStack out : details.getOutputs()) {
            if (out == null) continue;
            long size = out.getStackSize();
            int max = 0;
            while ((size & FINAL_BIT) == 0) {
                size <<= 1;
                max++;
            }
            if (max < maxMulti) maxMulti = max;
        }

        return maxMulti;
    }

    public static int getMaxBitDivider(ICraftingPatternDetails details) {
        // limit to 2B per item in pattern
        int maxDiv = 30;
        for (IAEItemStack input : details.getInputs()) {
            if (input == null) continue;
            long size = input.getStackSize();
            int max = 0;
            while ((size & 1) == 0) {
                size >>= 1;
                max++;
            }
            if (max < maxDiv) maxDiv = max;
        }
        for (IAEItemStack out : details.getOutputs()) {
            if (out == null) continue;
            long size = out.getStackSize();
            int max = 0;
            while ((size & 1) == 0) {
                size >>= 1;
                max++;
            }
            if (max < maxDiv) maxDiv = max;
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
