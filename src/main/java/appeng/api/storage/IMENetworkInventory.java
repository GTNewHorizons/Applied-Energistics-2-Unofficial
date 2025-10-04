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

import appeng.api.storage.data.IAEStack;

public interface IMENetworkInventory<StackType extends IAEStack>
        extends IMEInventoryHandler<StackType>, IMENetworkAwareInventory<StackType> {

    /**
     * Return a list of items that are available in this network but were not read because of any filter. The intention
     * is to use this in any network-to-network read operations which can filter items. This list SHOULD be modified if
     * a filter did read any of its items, failing to do so will cause items to be shown double.
     *
     * @param source    the network inventory where the read request comes from
     * @param iteration numeric id for this iteration, use {@link appeng.util.IterationCounter#fetchNewId()} to avoid
     *                  conflicts
     * @return the list of available items from this network that were not yet read
     */
    Iterable<StackType> getUnreadAvailableItems(IMENetworkInventory<StackType> source, int iteration);

    /**
     * Determine if this network was already read once or is currently being read.
     * 
     * @param iteration numeric id for this iteration, use {@link appeng.util.IterationCounter#fetchNewId()} to avoid
     *                  conflicts
     * @return true if this network was already read once or is currently being read
     */
    boolean networkIsRead(int iteration);
}
