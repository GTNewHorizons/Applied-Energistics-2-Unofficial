/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.util.inv;

import java.util.Collection;
import java.util.Iterator;

import javax.annotation.Nonnull;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

public class ItemListIgnoreCrafting<StackType extends IAEStack> implements IItemList<StackType> {

    private final IItemList<StackType> target;

    public ItemListIgnoreCrafting(final IItemList<StackType> cla) {
        this.target = cla;
    }

    @Override
    public void add(@Nonnull final StackType stack) {
        this.target.add((StackType) stack.copy().setCraftable(false));
    }

    public void addAll(@Nonnull final IItemList<StackType> stacks) {
        for (StackType stack : stacks) {
            this.add(stack);
        }
    }

    @Override
    public StackType findPrecise(final StackType i) {
        return this.target.findPrecise(i);
    }

    @Override
    public Collection<StackType> findFuzzy(final StackType input, final FuzzyMode fuzzy) {
        return this.target.findFuzzy(input, fuzzy);
    }

    @Override
    public boolean isEmpty() {
        return this.target.isEmpty();
    }

    @Override
    public void addStorage(final StackType option) {
        this.target.addStorage(option);
    }

    @Override
    public void addCrafting(final StackType option) {
        // nothing.
    }

    @Override
    public void addRequestable(final StackType option) {
        this.target.addRequestable(option);
    }

    @Override
    public StackType getFirstItem() {
        return this.target.getFirstItem();
    }

    @Override
    public int size() {
        return this.target.size();
    }

    @Override
    public Iterator<StackType> iterator() {
        return this.target.iterator();
    }

    @Override
    public void resetStatus() {
        this.target.resetStatus();
    }
}
