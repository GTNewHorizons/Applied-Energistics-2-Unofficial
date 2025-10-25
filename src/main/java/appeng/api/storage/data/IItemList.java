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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Represents a list of items in AE.
 * <p>
 * Don't Implement.
 * <p>
 * Construct with Util.createItemList()
 */
public interface IItemList<StackType extends IAEStack> extends IItemContainer<StackType>, Iterable<StackType> {

    /**
     * 0 - NULL 1 - IAEItemStack 2 - IAEFluidStack 3 - IAEStack
     */
    byte LIST_NUll = 0;
    byte LIST_ITEM = 1;
    byte LIST_FLUID = 2;
    byte LIST_MIXED = 3;

    /**
     * add a stack to the list stackSize is used to add to stackSize, this will merge the stack with an item already in
     * the list if found.
     *
     * @param option stacktype option
     */
    void addStorage(StackType option); // adds a stack as stored

    /**
     * add a stack to the list as craftable, this will merge the stack with an item already in the list if found.
     *
     * @param option stacktype option
     */
    void addCrafting(StackType option);

    /**
     * add a stack to the list, stack size is used to add to requestable, this will merge the stack with an item already
     * in the list if found.
     *
     * @param option stacktype option
     */
    void addRequestable(StackType option); // adds a stack as requestable

    /**
     * @return the first item in the list
     */
    StackType getFirstItem();

    /**
     * @return the number of items in the list
     */
    int size();

    /**
     * allows you to iterate the list.
     */
    @Override
    Iterator<StackType> iterator();

    /**
     * resets stack sizes to 0.
     */
    void resetStatus();

    /** indicates whether elements can be added to the list */
    default boolean hasWriteAccess() {
        return true;
    }

    default StackType[] toArray(StackType[] zeroSizedArray) {
        int prevSize = size();

        StackType[] output = (StackType[]) Array.newInstance(zeroSizedArray.getClass().getComponentType(), prevSize);

        int i = 0;
        for (StackType stack : this) {
            output[i++] = stack;
        }

        return i != prevSize ? Arrays.copyOf(output, i) : output;
    }

    byte getStackType();

    default public boolean isSorted() {
        return false;
    }

    /**
     * sort this list in nature ordering nothing happens if sorting is not supported
     * 
     * @return this
     */
    default public IItemList<StackType> toSorted() {
        return this;
    }

}
