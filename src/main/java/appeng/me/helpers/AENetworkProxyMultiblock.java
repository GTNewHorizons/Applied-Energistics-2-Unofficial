/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.helpers;

import java.util.Iterator;

import net.minecraft.item.ItemStack;

import com.google.common.collect.Iterators;

import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.me.cluster.IAECluster;
import appeng.me.cluster.IAEMultiBlock;
import appeng.util.iterators.ProxyNodeIterator;

public class AENetworkProxyMultiblock extends AENetworkProxy implements IGridMultiblock {

    // Wish this can fix Memory leak.
    private static final WeakHashMap<AENetworkProxyMultiblock, IAEMultiBlock> Map = new WeakHashMap<AENetworkProxyMultiblock, IAEMultiBlock>();

    public AENetworkProxyMultiblock(final IGridProxyable te, final String nbtName, final ItemStack itemStack,
            final boolean inWorld) {
        super(te, nbtName, itemStack, inWorld);
        Map.put(this, (IAEMultiBlock) te);
    }

    @Override
    public Iterator<IGridNode> getMultiblockNodes() {
        if (this.getCluster() == null) {
            return Iterators.emptyIterator();
        }

        return new ProxyNodeIterator(this.getCluster().getTiles());
    }

    private IAECluster getCluster() {
        IAEMultiBlock te = Map.get(this);
        if (te == null) {
            AELog.error(
                    "[AppEng_Patch] Cannot get te from Map. Use this.getMachine() instead but this can cause memory leak.");
            te = (IAEMultiBlock) this.getMachine();
        }
        return te.getCluster();
    }
}
