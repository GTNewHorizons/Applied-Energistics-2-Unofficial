package appeng.util.item;

import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.client.texture.ExtraBlockTextures;
import appeng.core.localization.GuiText;
import appeng.util.FluidUtils;
import codechicken.nei.recipe.StackInfo;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectLongImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectLongPair;

public class AEFluidStackType implements IAEStackType<IAEFluidStack> {

    public static final AEFluidStackType FLUID_STACK_TYPE = new AEFluidStackType();
    public static final String FLUID_STACK_ID = "fluid";

    @Override
    public String getId() {
        return FLUID_STACK_ID;
    }

    @Override
    public String getDisplayName() {
        return GuiText.Fluids.getLocal();
    }

    @Override
    public String getDisplayUnit() {
        return "mB";
    }

    @Override
    public IAEFluidStack loadStackFromNBT(NBTTagCompound tag) {
        return AEFluidStack.loadFluidStackFromNBT(tag);
    }

    @Override
    public IAEFluidStack loadStackFromByte(ByteBuf buffer) throws IOException {
        return AEFluidStack.loadFluidStackFromPacket(buffer);
    }

    @Override
    public IItemList<IAEFluidStack> createList() {
        return AEApi.instance().storage().createFluidList();
    }

    @Override
    @Range(from = 1, to = Integer.MAX_VALUE)
    public int getAmountPerUnit() {
        return 1;
    }

    @Override
    public boolean isContainerItemForType(@Nullable ItemStack container) {
        return FluidUtils.isFluidContainer(container);
    }

    @Override
    public @Nullable IAEFluidStack getStackFromContainerItem(@NotNull ItemStack container) {
        FluidStack fluidStack = FluidUtils.getFluidFromContainer(container);
        if (fluidStack == null) return null;
        return AEFluidStack.create(fluidStack);
    }

    @Override
    public @Nullable IAEFluidStack convertStackFromItem(@NotNull ItemStack itemStack) {
        if (FluidUtils.isFluidContainer(itemStack)) return null;

        itemStack = itemStack.copy();
        itemStack.stackSize = 1;

        FluidStack fluidStack = StackInfo.getFluid(itemStack);
        if (fluidStack == null) return null;
        return AEFluidStack.create(fluidStack);
    }

    @Override
    public long getContainerItemCapacity(@NotNull ItemStack container, @NotNull IAEFluidStack stack) {
        return FluidUtils.getFluidContainerCapacity(container, stack.getFluidStack());
    }

    @Override
    public @NotNull ObjectLongPair<ItemStack> drainStackFromContainer(@NotNull ItemStack itemStack,
            @NotNull IAEFluidStack stack) {
        if (itemStack.getItem() instanceof IFluidContainerItem container) {
            FluidStack fluid = container.getFluid(itemStack);
            if (fluid == null || !stack.getFluidStack().isFluidEqual(fluid))
                return new ObjectLongImmutablePair<>(itemStack, 0);
            FluidStack drained = container
                    .drain(itemStack, (int) Math.min(stack.getStackSize(), Integer.MAX_VALUE), true);
            return new ObjectLongImmutablePair<>(itemStack, drained.amount);
        } else if (FluidContainerRegistry.isContainer(itemStack)) {
            FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(itemStack);
            if (fluid == null || !stack.getFluidStack().isFluidEqual(fluid) || fluid.amount > stack.getStackSize())
                return new ObjectLongImmutablePair<>(itemStack, 0);

            ItemStack empty = FluidContainerRegistry.drainFluidContainer(itemStack);
            if (empty == null) return new ObjectLongImmutablePair<>(itemStack, 0);

            return new ObjectLongImmutablePair<>(empty, fluid.amount);
        }

        return new ObjectLongImmutablePair<>(itemStack, 0);
    }

    @Override
    public @Nullable ItemStack clearFilledContainer(@NotNull ItemStack container) {
        return FluidUtils.clearFluidContainer(container);
    }

    @Override
    public @NotNull ObjectLongPair<ItemStack> fillContainer(@NotNull ItemStack container,
            @NotNull IAEFluidStack stack) {
        return FluidUtils.fillFluidContainer(container, stack.getFluidStack());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ResourceLocation getButtonTexture() {
        return ExtraBlockTextures.GuiTexture("guis/states.png");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getButtonIcon() {
        return new IIcon() {

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }

            @Override
            public float getMinU() {
                return (float) 128 / 256;
            }

            @Override
            public float getMaxU() {
                return (float) 144 / 256;
            }

            @Override
            public float getInterpolatedU(double p_94214_1_) {
                return 0;
            }

            @Override
            public float getMinV() {
                return (float) 48 / 256;
            }

            @Override
            public float getMaxV() {
                return (float) 64 / 256;
            }

            @Override
            public float getInterpolatedV(double p_94207_1_) {
                return 0;
            }

            @Override
            public String getIconName() {
                return "FluidIcon";
            }
        };
    }
}
