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

import net.minecraft.util.EnumChatFormatting;

public enum WailaText implements Localization {

    Crafting,

    DeviceOnline,
    DeviceOffline,
    DeviceMissingChannel,

    P2PUnlinked,
    P2PInputOneOutput,
    P2PInputManyOutputs,
    P2POutput,

    Locked,
    Unlocked,
    Showing,

    CraftingLockedByRedstoneSignal,
    CraftingLockedByLackOfRedstoneSignal,
    CraftingLockedUntilPulse,
    CraftingLockedUntilResult,

    Contains,
    Channels,
    ChannelsUnbound,
    Booting,

    wireless_connected,
    wireless_connected_multiple,
    wireless_connected_detailsTitle,
    wireless_connected_details,
    wireless_notconnected,
    wireless_channels,
    wireless_power,
    wireless_name,
    CoordinateX,
    CoordinateY,
    CoordinateZ,
    Dimension,
    Id,
    ConnectedTo,
    Disconnected,
    Singularity;

    private final String formatedName;

    WailaText() {
        this.formatedName = this.name().replace("_", ".");
    }

    WailaText(final String name) {
        this.formatedName = name;
    }

    public String getUnlocalized() {
        return "waila.appliedenergistics2." + this.formatedName;
    }

    /**
     * Gets corresponding tooltip for different values of {@code #isActive}, {@code #isPowered} and {@code #isBooting}
     *
     * @param isActive  if part is active
     * @param isPowered if part is powered
     * @param isBooting if part is booting
     * @return tooltip of the state
     */
    public static String getPowerState(final boolean isActive, final boolean isPowered, final boolean isBooting) {
        if (isBooting) {
            return WailaText.Booting.getLocal(EnumChatFormatting.GREEN);
        } else if (isActive && isPowered) {
            return WailaText.DeviceOnline.getLocal(EnumChatFormatting.GREEN);
        } else if (isPowered) {
            return WailaText.DeviceMissingChannel.getLocal(EnumChatFormatting.RED);
        } else {
            return WailaText.DeviceOffline.getLocal(EnumChatFormatting.RED);
        }
    }
}
