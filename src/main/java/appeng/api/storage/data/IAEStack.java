/*
 * The MIT License (MIT) Copyright (c) 2013 AlgorithmX2 Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions: The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.storage.data;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.StorageChannel;
import appeng.core.AELog;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public interface IAEStack<StackType extends IAEStack> {

    String ST_NULL = "";
    String ST_ITEM = "item";
    String ST_FLUID = "fluid";

    /**
     * add two stacks together
     *
     * @param is added item
     */
    void add(StackType is);

    /**
     * number of items in the stack.
     *
     * @return basically ItemStack.stackSize
     */
    long getStackSize();

    /**
     * changes the number of items in the stack.
     *
     * @param stackSize , ItemStack.stackSize = N
     */
    StackType setStackSize(long stackSize);

    /**
     * Percentage of items used during crafting
     *
     * @return Percentage of items used
     */
    default float getUsedPercent() {
        return 0;
    }

    /**
     * Change percentage of items used during crafting
     *
     * @param percent Percentage of items used
     */
    default StackType setUsedPercent(float percent) {
        return (StackType) this;
    }

    /**
     * Same as getStackSize, but for requestable items. ( LP )
     *
     * @return basically itemStack.stackSize but for requestable items.
     */
    long getCountRequestable();

    /**
     * Same as setStackSize, but for requestable items. ( LP )
     *
     * @return basically itemStack.stackSize = N but for setStackSize items.
     */
    StackType setCountRequestable(long countRequestable);

    /**
     * Number of requests made to achieve requested item count
     *
     * @return requests made during crafting simulation
     */
    default long getCountRequestableCrafts() {
        return 0;
    }

    /**
     * Change the number of requests made to achieve requested item count
     *
     * @param countRequestableCrafts requests made during crafting simulation
     */
    default StackType setCountRequestableCrafts(long countRequestableCrafts) {
        return (StackType) this;
    }

    /**
     * true, if the item can be crafted.
     *
     * @return true, if it can be crafted.
     */
    boolean isCraftable();

    /**
     * change weather the item can be crafted.
     *
     * @param isCraftable can item be crafted
     */
    StackType setCraftable(boolean isCraftable);

    /**
     * clears, requestable, craftable, and stack sizes.
     */
    StackType reset();

    /**
     * returns true, if the item can be crafted, requested, or extracted.
     *
     * @return isThisRecordMeaningful
     */
    boolean isMeaningful();

    /**
     * Adds more to the stack size...
     *
     * @param i additional stack size
     */
    void incStackSize(long i);

    /**
     * removes some from the stack size.
     */
    void decStackSize(long i);

    /**
     * adds items to the requestable
     *
     * @param i increased amount of requested items
     */
    void incCountRequestable(long i);

    /**
     * removes items from the requestable
     *
     * @param i decreased amount of requested items
     */
    void decCountRequestable(long i);

    /**
     * write to a NBTTagCompound.
     *
     * @param i to be written data
     */
    void writeToNBT(NBTTagCompound i);

    /**
     * write to a NBTTagCompound with StackType.
     * 
     * @param tag to be written data
     */
    default void writeToNBTGeneric(@Nonnull NBTTagCompound tag) {
        tag.setString("StackType", this.getStackType().getId());
        this.writeToNBT(tag);
    }

    /**
     * @return NBTTagCompound with StackType
     */
    @Nonnull
    default NBTTagCompound toNBTGeneric() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("StackType", this.getStackType().getId());
        this.writeToNBT(tag);
        return tag;
    }

    /**
     *
     * @param tag empty or include StackType
     * @return stack for StackType. null if {@code tag} has no tags.
     */
    @Nullable
    static IAEStack<?> fromNBTGeneric(@Nonnull NBTTagCompound tag) {
        if (tag.hasNoTags()) return null;

        String id = tag.getString("StackType");
        if (id.isEmpty()) {
            AELog.warn("Cannot deserialize generic stack from nbt %s because key 'StackType' is missing.", tag);
            return null;
        }

        IAEStackType<?> type = AEStackTypeRegistry.getType(id);
        if (type == null) {
            AELog.warn("Cannot deserialize generic stack from nbt %s because stack type is missing.", tag);
            return null;
        }
        return type.loadStackFromNBT(tag);
    }

    /**
     * Slower for disk saving, but smaller/more efficient for packets.
     *
     * @param data to be written data
     * @throws IOException
     */
    void writeToPacket(ByteBuf data) throws IOException;

    static IAEStack<?> fromPacketGeneric(ByteBuf buffer) throws IOException {
        final String id = ByteBufUtils.readUTF8String(buffer);
        if (id.isEmpty()) return null;

        IAEStackType<?> type = AEStackTypeRegistry.getType(id);
        if (type == null) {
            AELog.warn("Cannot deserialize generic stack from ByteBuf because stack type %s is missing.", id);
            return null;
        }
        return type.loadStackFromByte(buffer);
    }

    /**
     * Compare stacks using precise logic.
     * <p>
     * a IAEItemStack to another AEItemStack or a ItemStack.
     * <p>
     * or
     * <p>
     * IAEFluidStack, FluidStack
     *
     * @param obj compared object
     * @return true if they are the same.
     */
    @Override
    boolean equals(Object obj);

    /**
     * compare stacks using fuzzy logic
     * <p>
     * a IAEItemStack to another AEItemStack or a ItemStack.
     *
     * @param st   stacks
     * @param mode used fuzzy mode
     * @return true if two stacks are equal based on AE Fuzzy Comparison.
     */
    boolean fuzzyComparison(Object st, FuzzyMode mode);

    /**
     * Clone the Item / Fluid Stack
     *
     * @return a new Stack, which is copied from the original.
     */
    StackType copy();

    /**
     * create an empty stack.
     *
     * @return a new stack, which represents an empty copy of the original.
     */
    StackType empty();

    /**
     * obtain the NBT Data for the item.
     *
     * @return nbt data
     */
    IAETagCompound getTagCompound();

    /**
     * @return true if the stack is a {@link IAEItemStack}
     */
    boolean isItem();

    /**
     * @return true if the stack is a {@link IAEFluidStack}
     */
    boolean isFluid();

    /**
     * @return ITEM or FLUID
     */
    StorageChannel getChannel();

    /**
     * @return Display name of item
     */
    String getLocalizedName();

    boolean isSameType(StackType stack);

    boolean isSameType(Object stack);

    String getUnlocalizedName();

    String getDisplayName();

    String getModId();

    void setTagCompound(NBTTagCompound tag);

    boolean hasTagCompound();

    @Nullable
    ItemStack getItemStackForNEI();

    @Nullable
    default ItemStack getItemStackForNEI(int amount) {
        long currentAmount = this.getStackSize();
        this.setStackSize(amount);
        ItemStack result = this.getItemStackForNEI();
        this.setStackSize(currentAmount);
        return result;
    }

    @SideOnly(Side.CLIENT)
    void drawInGui(Minecraft mc, int x, int y);

    @SideOnly(Side.CLIENT)
    void drawOverlayInGui(Minecraft mc, int x, int y, boolean showAmount, boolean showAmountAlways,
            boolean showCraftableText, boolean showCraftableIcon);

    @SideOnly(Side.CLIENT)
    void drawOnBlockFace(World world);

    int getAmountPerUnit();

    IAEStackType<StackType> getStackType();
}
