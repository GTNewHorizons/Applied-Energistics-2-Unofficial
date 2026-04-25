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

import net.minecraft.util.IChatComponent;

public enum PlayerMessages implements Localization {

    ChestCannotReadStorageCell,
    InvalidMachine,
    LoadedSettings,
    SavedSettings,
    MachineNotPowered,
    DriveLockingToggled,
    ChestLockingToggled,
    isNowLocked,
    isNowUnlocked,
    AmmoDepleted,
    CommunicationError,
    OutOfRange,
    DeviceNotPowered,
    DeviceNotWirelessTerminal,
    DeviceSuccessLinked,
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
    CraftingCantExtract,
    MachineInOtherDim,
    MachineInOtherDimNamed,
    MachineHighlighted,
    MachineHighlightedNamed,
    StorageInOtherDim,
    StorageHighlighted,
    PickBlockTerminalNotFound,
    PatternRepeaterProvider,
    PatternRepeaterAccessor;

    @Deprecated // kept for backward compat
    public IChatComponent get() {
        return toChat();
    }

    @Deprecated // kept for backward compat
    public IChatComponent get(Object... args) {
        return toChat(args);
    }

    @Deprecated // kept for backward compat
    public String getName() {
        return getUnlocalized();
    }

    public String getUnlocalized() {
        return "chat.appliedenergistics2." + this.name();
    }
}
