package appeng.integration.modules.NEIHelpers;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.items.misc.ItemEncodedPattern;
import codechicken.nei.api.IStackStringifyHandler;
import codechicken.nei.recipe.stackinfo.DefaultStackStringifyHandler;

public class NEIEncodedPatternStringifyHandler implements IStackStringifyHandler {

    private static final DefaultStackStringifyHandler defaultStackStringifyHandler = new DefaultStackStringifyHandler();

    public NBTTagCompound convertItemStackToNBT(ItemStack stack, boolean saveStackSize) {
        if (!(stack.getItem() instanceof ItemEncodedPattern pattern)) {
            return null;
        }

        stack = pattern.getOutput(stack);

        if (stack == null) {
            return null;
        }

        return defaultStackStringifyHandler.convertItemStackToNBT(stack, saveStackSize);
    }
}
