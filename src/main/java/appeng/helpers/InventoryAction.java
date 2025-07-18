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

public enum InventoryAction {
    // standard vanilla mechanics.
    PICKUP_OR_SET_DOWN,
    SPLIT_OR_PLACE_SINGLE,
    CREATIVE_DUPLICATE,
    SHIFT_CLICK,

    // crafting term
    CRAFT_STACK,
    CRAFT_ITEM,
    CRAFT_SHIFT,

    // extra...
    MOVE_REGION,
    PICKUP_SINGLE,
    UPDATE_HAND,
    ROLL_UP,
    ROLL_DOWN,
    AUTO_CRAFT,
    PLACE_SINGLE,
    SET_PATTERN_VALUE,
    SET_PATTERN_MULTI,
    RENAME_PATTERN_ITEM,
    SET_PIN
}
