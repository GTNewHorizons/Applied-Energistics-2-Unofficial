/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.localization;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

public enum PlayerMessages {

    ChestCannotReadStorageCell,
    InvalidMachine,
    LoadedSettings,
    SavedSettings,
    MachineNotPowered,
    DriveLocked,
    ChestLocked,
    isNowLocked,
    isNowUnlocked,
    AmmoDepleted,
    CommunicationError,
    OutOfRange,
    DeviceNotPowered,
    DeviceNotWirelessTerminal,
    DeviceNotLinked,
    StationCanNotBeLocated,
    SettingCleared,
    TunnelNotConnected,
    TunnelInputIsAt,
    TunnelHasNoOutputs,
    TunnelOutputsAreAt,
    InterfaceInOtherDim,
    InterfaceHighlighted,
    LevelEmitterInAnotherDim,
    LevelEmitterHighlighted,
    P2PInAnotherDim,
    P2PHighlighted,
    CraftingItemsWentMissing,
    PriorityInvalidTarget,
    PriorityReadout,
    PriorityConfigured,
    FinishCraftingRemind,
    CraftingCantExtract;

    public IChatComponent get() {
        return new ChatComponentTranslation(this.getName());
    }

    public IChatComponent get(Object... args) {
        return new ChatComponentTranslation(this.getName(), args);
    }

    public String getName() {
        return "chat.appliedenergistics2." + this.name();
    }
}
