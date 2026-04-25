package appeng.util;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import codechicken.nei.recipe.StackInfo;
import it.unimi.dsi.fastutil.objects.ObjectLongImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectLongPair;

public class FluidUtils {

    public static boolean isFluidContainer(@Nullable ItemStack itemStack) {
        if (itemStack == null) return false;
        Item item = itemStack.getItem();
        if (item instanceof IFluidContainerItem) return true;
        return FluidContainerRegistry.isContainer(itemStack);
    }

    public static boolean isFilledFluidContainer(@Nullable ItemStack itemStack) {
        if (itemStack == null) return false;
        Item item = itemStack.getItem();
        if (item instanceof IFluidContainerItem container) {
            FluidStack content = container.getFluid(itemStack);
            return content != null && content.amount > 0;
        }
        return FluidContainerRegistry.isFilledContainer(itemStack);
    }

    public static int getFluidContainerCapacity(@Nullable ItemStack itemStack, FluidStack fluidStack) {
        if (itemStack == null) return 0;
        if (itemStack.getItem() instanceof IFluidContainerItem container) {
            return container.getCapacity(itemStack);
        } else if (FluidContainerRegistry.isContainer(itemStack)) {
            return FluidContainerRegistry.getContainerCapacity(fluidStack, itemStack);
        }
        return 0;
    }

    public static ItemStack getFilledFluidContainer(@Nullable ItemStack itemStack, FluidStack fluidStack) {
        if (itemStack == null) return null;

        fluidStack = fluidStack.copy();
        itemStack = itemStack.copy();

        fluidStack.amount = Integer.MAX_VALUE;
        itemStack.stackSize = 1;

        if (itemStack.getItem() instanceof IFluidContainerItem container) {
            container.fill(itemStack, fluidStack, true);
            return itemStack;
        } else if (FluidContainerRegistry.isContainer(itemStack)) {
            return FluidContainerRegistry.fillFluidContainer(fluidStack, itemStack);
        }
        return null;
    }

    public static ItemStack clearFluidContainer(ItemStack itemStack) {
        if (itemStack == null) return null;
        if (itemStack.getItem() instanceof IFluidContainerItem container) {
            container.drain(itemStack, container.getFluid(itemStack).amount, true);
            return itemStack;
        } else if (FluidContainerRegistry.isContainer(itemStack)) {
            return FluidContainerRegistry.drainFluidContainer(itemStack);
        }
        return null;
    }

    public static FluidStack getFluidFromContainer(ItemStack itemStack) {
        if (itemStack == null) return null;

        if (itemStack.getItem() instanceof IFluidContainerItem container) {
            return container.getFluid(itemStack);
        } else if (FluidContainerRegistry.isContainer(itemStack)) {
            return FluidContainerRegistry.getFluidForFilledItem(itemStack);
        } else {
            return StackInfo.getFluid(Platform.copyStackWithSize(itemStack, 1));
        }
    }

    public static ObjectLongPair<ItemStack> fillFluidContainer(@Nullable ItemStack itemStack, FluidStack fluidStack) {
        if (itemStack == null) return new ObjectLongImmutablePair<>(null, 0);

        itemStack.stackSize = 1;

        if (itemStack.getItem() instanceof IFluidContainerItem container) {
            return new ObjectLongImmutablePair<>(itemStack, container.fill(itemStack, fluidStack, true));
        } else if (FluidContainerRegistry.isContainer(itemStack)) {
            ItemStack filledContainer = FluidContainerRegistry.fillFluidContainer(fluidStack, itemStack);
            FluidStack filled = FluidContainerRegistry.getFluidForFilledItem(filledContainer);
            return new ObjectLongImmutablePair<>(filledContainer, filled != null ? filled.amount : 0);
        }

        return new ObjectLongImmutablePair<>(null, 0);
    }
}
