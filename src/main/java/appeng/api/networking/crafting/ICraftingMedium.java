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

import appeng.util.ScheduledReason;
import appeng.util.inv.MEInventoryCrafting;

/**
 * A place to send Items for crafting purposes, this is considered part of AE's External crafting system.
 */
public interface ICraftingMedium {

    /**
     * Instruct a medium to create one instance of the item represented by the pattern and table.
     *
     * @deprecated Implement {@link #pushPattern(ICraftingPatternDetails, MEInventoryCrafting, int)} instead. This
     *             method is kept as a compatibility bridge for existing crafting integrations.
     */
    @Deprecated
    default boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        throw new IllegalStateException(
                "Crafting medium must override the legacy pushPattern method or the multiplier pushPattern method");
    }

    /**
     * Instruct a medium to handle a pattern push. The multiplier describes how many executions are being submitted
     * together. The table contains the complete inputs for the merged push, with stack sizes already multiplied by this
     * value. Implementations that support merged pushes may use the multiplier to reserve or queue additional
     * executions.
     *
     * @param patternDetails pattern details
     * @param table          complete input items for the merged push; stack sizes are already multiplied
     * @param multiplier     number of executions represented by this push
     * @return true if the complete push was accepted
     */
    default boolean pushPattern(final ICraftingPatternDetails patternDetails, final MEInventoryCrafting table,
            final int multiplier) {
        return this.pushPattern(patternDetails, table);
    }

    /**
     * Returns whether this medium supports submitting multiple executions of the same pattern in one push.
     *
     * @param patternDetails pattern to be submitted
     * @return true if the merged push path is supported
     */
    default boolean canMergePatternPush(final ICraftingPatternDetails patternDetails) {
        return false;
    }

    /**
     * Returns the maximum number of executions this medium can accept in one merged push at this moment.
     *
     * This method is only queried after {@link #canMergePatternPush(ICraftingPatternDetails)} returns true. A return
     * value of zero means that the medium is temporarily unavailable for a merged push. The default value of one keeps
     * the original single-execution behavior for existing integrations.
     *
     * @param patternDetails pattern to be submitted
     * @param maxMultiplier  upper bound imposed by the crafting CPU
     * @return a value between zero and maxMultiplier
     */
    default int getMaxPatternPushMultiplier(final ICraftingPatternDetails patternDetails, final int maxMultiplier) {
        return maxMultiplier <= 0 ? 0 : 1;
    }

    /**
     * @return if this is false, the crafting engine will refuse to send new jobs to this medium.
     */
    boolean isBusy();

    /**
     * @return An itemstack representing the machine that will craft the patterns pushed into this medium. Shown in the
     *         crafting simulation tree view.
     */
    default ItemStack getCrafterIcon() {
        return null;
    }

    /**
     * @return The blocking mode of the crafting medium.
     */
    default BlockingMode getBlockingMode() {
        return BlockingMode.NONE;
    }

    default ScheduledReason getScheduledReason() {
        return ScheduledReason.UNDEFINED;
    }

    enum BlockingMode {
        NONE,
        BLOCKING,
        SMART_BLOCKING
    }
}
