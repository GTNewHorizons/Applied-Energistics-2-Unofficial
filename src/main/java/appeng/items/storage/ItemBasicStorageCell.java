/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.items.storage;

import com.google.common.base.Optional;

import appeng.api.storage.StorageChannel;
import appeng.items.AEBaseCell;
import appeng.items.materials.MaterialType;

public class ItemBasicStorageCell extends AEBaseCell {

    public ItemBasicStorageCell(MaterialType whichCell, long kilobytes) {
        super(whichCell, kilobytes);
    }

    public ItemBasicStorageCell(final Optional<String> subName) {
        super(subName);
    }

    @Override
    public StorageChannel getStorageChannel() {
        return StorageChannel.ITEMS;
    }
}
