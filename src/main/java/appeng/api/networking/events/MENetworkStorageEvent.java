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

package appeng.api.networking.events;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStackType;

/**
 * posted by the network when the networks Storage Changes, you can use the currentItems list to check levels, and
 * update status.
 * <p>
 * this is the least useful method of getting info about changes in the network.
 * <p>
 * Do not modify the list or its contents in anyway.
 */
public class MENetworkStorageEvent extends MENetworkEvent {

    public final IMEMonitor monitor;
    public final IAEStackType<?> type;

    @Deprecated
    public final StorageChannel channel;

    @Deprecated
    public MENetworkStorageEvent(final IMEMonitor o, final StorageChannel chan) {
        this.monitor = o;
        this.channel = chan;

        if (chan == StorageChannel.ITEMS) {
            type = ITEM_STACK_TYPE;
        } else if (chan == StorageChannel.FLUIDS) {
            type = FLUID_STACK_TYPE;
        } else {
            type = null;
        }
    }

    public MENetworkStorageEvent(final IMEMonitor o, final IAEStackType<?> type) {
        this.monitor = o;
        this.type = type;

        if (type == ITEM_STACK_TYPE) {
            channel = StorageChannel.ITEMS;
        } else if (type == FLUID_STACK_TYPE) {
            channel = StorageChannel.FLUIDS;
        } else {
            channel = null;
        }
    }
}
