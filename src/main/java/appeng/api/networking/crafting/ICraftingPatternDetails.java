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

package appeng.api.networking.crafting;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/**
 * do not implement provided by {@link ICraftingPatternItem}
 * <p>
 * caching this INSTANCE will increase performance of validation and checks.
 */
public interface ICraftingPatternDetails {

    /**
     * @return source item.
     */
    ItemStack getPattern();

    /**
     * @param slotIndex specific slot index
     * @param itemStack item in slot
     * @param world     crafting world
     * @return if an item can be used in the specific slot for this pattern.
     */
    boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world);

    default boolean isValidItemForSlot(int slotIndex, IAEStack<?> aes, World world) {
        if (aes instanceof IAEItemStack ais) {
            return isValidItemForSlot(slotIndex, ais.getItemStack(), world);
        }
        return false;
    }

    /**
     * @return if this pattern is a crafting pattern ( work bench )
     */
    boolean isCraftable();

    /**
     * @return a list of the inputs, will include nulls.
     */
    default IAEStack<?>[] getAEInputs() {
        return getInputs();
    }

    @Deprecated
    IAEItemStack[] getInputs();

    /**
     * @return a list of the inputs, will be clean
     */
    default IAEStack<?>[] getCondensedAEInputs() {
        return getCondensedInputs();
    }

    @Deprecated
    IAEItemStack[] getCondensedInputs();

    /**
     * @return a list of the outputs, will be clean
     */
    default IAEStack<?>[] getCondensedAEOutputs() {
        return getCondensedOutputs();
    }

    @Deprecated
    IAEItemStack[] getCondensedOutputs();

    /**
     * @return a list of the outputs, will include nulls.
     */
    default IAEStack<?>[] getAEOutputs() {
        return getOutputs();
    }

    @Deprecated
    IAEItemStack[] getOutputs();

    /**
     * @return if this pattern is enabled to support substitutions.
     */
    boolean canSubstitute();

    /**
     * @return if this pattern can be used in autocrafting as a substitute for another item.
     */
    default boolean canBeSubstitute() {
        return true;
    }

    /**
     * Allow using this INSTANCE of the pattern details to preform the crafting action with performance enhancements.
     *
     * @param craftingInv inventory
     * @param world       crafting world
     * @return the crafted ( work bench ) item.
     */
    ItemStack getOutput(InventoryCrafting craftingInv, World world);

    /**
     * Get the priority of this pattern
     *
     * @return the priority of this pattern
     */
    int getPriority();

    /**
     * Set the priority the of this pattern.
     *
     * @param priority priority of pattern
     */
    void setPriority(int priority);

    /**
     * @return true if this pattern is input-only and should be inlined during resolution.
     */
    default boolean isInputOnly() {
        return false;
    }

    /**
     * @return unique id for input-only patterns, or null if not applicable.
     */
    default java.util.UUID getInputOnlyUuid() {
        return null;
    }
}
