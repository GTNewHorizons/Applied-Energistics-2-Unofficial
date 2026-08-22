/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.helpers;

import static appeng.helpers.PatternHelper.convertToCondensedAEList;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;

public interface IResolvablePatternDetails extends ICraftingPatternDetails {

    IAEStack<?>[] getEncodedAEInputs();

    void setResolvedAEInputs(IAEStack<?>[] inputs);

    void resetResolvedAEInputs();
}

final class PatternInputResolver {

    private final IAEStack<?>[] encodedInputs;
    private volatile ResolvedInputs resolvedInputs;

    PatternInputResolver(final IAEStack<?>[] encodedInputs) {
        this.encodedInputs = encodedInputs;
        this.reset();
    }

    IAEStack<?>[] getEncodedInputs() {
        return encodedInputs;
    }

    IAEStack<?>[] getInputs() {
        return resolvedInputs.inputs;
    }

    IAEStack<?>[] getCondensedInputs() {
        return resolvedInputs.condensed;
    }

    void set(final IAEStack<?>[] inputs) {
        resolvedInputs = new ResolvedInputs(inputs);
    }

    void reset() {
        this.set(encodedInputs);
    }

    private static final class ResolvedInputs {

        private final IAEStack<?>[] inputs;
        private final IAEStack<?>[] condensed;

        private ResolvedInputs(final IAEStack<?>[] inputs) {
            this.inputs = inputs;
            this.condensed = convertToCondensedAEList(inputs);
        }
    }
}
