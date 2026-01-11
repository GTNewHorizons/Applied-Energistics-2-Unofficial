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

package appeng.api.storage;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStackType;

/**
 * A Registration Record for {@link IExternalStorageRegistry}
 */
public interface IExternalStorageHandler {

    @Deprecated
    default boolean canHandle(TileEntity te, ForgeDirection d, StorageChannel channel, BaseActionSource mySrc) {
        if (channel == StorageChannel.ITEMS) {
            return this.canHandle(te, d, ITEM_STACK_TYPE, mySrc);
        }
        if (channel == StorageChannel.FLUIDS) {
            return this.canHandle(te, d, FLUID_STACK_TYPE, mySrc);
        }
        return false;
    }

    /**
     * if this can handle the provided inventory, return true. ( Generally skipped by AE, and it just calls getInventory
     * )
     *
     * @param te    to be handled tile entity
     * @param mySrc source
     * @return true, if it can get a handler via getInventory
     */
    default boolean canHandle(TileEntity te, ForgeDirection d, IAEStackType<?> type, BaseActionSource mySrc) {
        if (type == ITEM_STACK_TYPE) {
            return this.canHandle(te, d, StorageChannel.ITEMS, mySrc);
        }
        if (type == FLUID_STACK_TYPE) {
            return this.canHandle(te, d, StorageChannel.FLUIDS, mySrc);
        }
        return false;
    }

    @Deprecated
    default IMEInventory getInventory(TileEntity te, ForgeDirection d, StorageChannel channel, BaseActionSource src) {
        if (channel == StorageChannel.ITEMS) {
            return this.getInventory(te, d, ITEM_STACK_TYPE, src);
        }
        if (channel == StorageChannel.FLUIDS) {
            return this.getInventory(te, d, FLUID_STACK_TYPE, src);
        }
        return null;
    }

    /**
     * if this can handle the given inventory, return the a IMEInventory implementing class for it, if not return null
     * <p>
     * please note that if your inventory changes and requires polling, you must use an {@link IMEMonitor} instead of an
     * {@link IMEInventory} failure to do so will result in invalid item counts and reporting of the inventory.
     *
     * @param te   to be handled tile entity
     * @param d    direction
     * @param type stack type
     * @param src  source
     * @return The Handler for the inventory
     */
    default IMEInventory getInventory(TileEntity te, ForgeDirection d, IAEStackType<?> type, BaseActionSource src) {
        if (type == ITEM_STACK_TYPE) {
            return this.getInventory(te, d, StorageChannel.ITEMS, src);
        }
        if (type == FLUID_STACK_TYPE) {
            return this.getInventory(te, d, StorageChannel.FLUIDS, src);
        }
        return null;
    }
}
