/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.texture;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public enum WirelessTextures {

    WirelessConnectorOnWhite("WirelessConnectorOnWhite"),
    WirelessConnectorOnOrange("WirelessConnectorOnOrange"),
    WirelessConnectorOnMagenta("WirelessConnectorOnMagenta"),
    WirelessConnectorOnLightBlue("WirelessConnectorOnLightBlue"),
    WirelessConnectorOnYellow("WirelessConnectorOnYellow"),
    WirelessConnectorOnLime("WirelessConnectorOnLime"),
    WirelessConnectorOnPink("WirelessConnectorOnPink"),
    WirelessConnectorOnGrey("WirelessConnectorOnGrey"),
    WirelessConnectorOnLightGrey("WirelessConnectorOnLightGrey"),
    WirelessConnectorOnCyan("WirelessConnectorOnCyan"),
    WirelessConnectorOnPurple("WirelessConnectorOnPurple"),
    WirelessConnectorOnBlue("WirelessConnectorOnBlue"),
    WirelessConnectorOnBrown("WirelessConnectorOnBrown"),
    WirelessConnectorOnGreen("WirelessConnectorOnGreen"),
    WirelessConnectorOnRed("WirelessConnectorOnRed"),
    WirelessConnectorOnBlack("WirelessConnectorOnBlack"),
    WirelessConnectorOnTransparent("WirelessConnectorOnTransparent"),

    WirelessConnectorOffWhite("WirelessConnectorOffWhite"),
    WirelessConnectorOffOrange("WirelessConnectorOffOrange"),
    WirelessConnectorOffMagenta("WirelessConnectorOffMagenta"),
    WirelessConnectorOffLightBlue("WirelessConnectorOffLightBlue"),
    WirelessConnectorOffYellow("WirelessConnectorOffYellow"),
    WirelessConnectorOffLime("WirelessConnectorOffLime"),
    WirelessConnectorOffPink("WirelessConnectorOffPink"),
    WirelessConnectorOffGrey("WirelessConnectorOffGrey"),
    WirelessConnectorOffLightGrey("WirelessConnectorOffLightGrey"),
    WirelessConnectorOffCyan("WirelessConnectorOffCyan"),
    WirelessConnectorOffPurple("WirelessConnectorOffPurple"),
    WirelessConnectorOffBlue("WirelessConnectorOffBlue"),
    WirelessConnectorOffBrown("WirelessConnectorOffBrown"),
    WirelessConnectorOffGreen("WirelessConnectorOffGreen"),
    WirelessConnectorOffRed("WirelessConnectorOffRed"),
    WirelessConnectorOffBlack("WirelessConnectorOffBlack"),
    WirelessConnectorOffTransparent("WirelessConnectorOffTransparent"),

    WirelessHubOnWhite("WirelessHubOnWhite"),
    WirelessHubOnOrange("WirelessHubOnOrange"),
    WirelessHubOnMagenta("WirelessHubOnMagenta"),
    WirelessHubOnLightBlue("WirelessHubOnLightBlue"),
    WirelessHubOnYellow("WirelessHubOnYellow"),
    WirelessHubOnLime("WirelessHubOnLime"),
    WirelessHubOnPink("WirelessHubOnPink"),
    WirelessHubOnGrey("WirelessHubOnGrey"),
    WirelessHubOnLightGrey("WirelessHubOnLightGrey"),
    WirelessHubOnCyan("WirelessHubOnCyan"),
    WirelessHubOnPurple("WirelessHubOnPurple"),
    WirelessHubOnBlue("WirelessHubOnBlue"),
    WirelessHubOnBrown("WirelessHubOnBrown"),
    WirelessHubOnGreen("WirelessHubOnGreen"),
    WirelessHubOnRed("WirelessHubOnRed"),
    WirelessHubOnBlack("WirelessHubOnBlack"),
    WirelessHubOnTransparent("WirelessHubOnTransparent"),

    WirelessHubOffWhite("WirelessHubOffWhite"),
    WirelessHubOffOrange("WirelessHubOffOrange"),
    WirelessHubOffMagenta("WirelessHubOffMagenta"),
    WirelessHubOffLightBlue("WirelessHubOffLightBlue"),
    WirelessHubOffYellow("WirelessHubOffYellow"),
    WirelessHubOffLime("WirelessHubOffLime"),
    WirelessHubOffPink("WirelessHubOffPink"),
    WirelessHubOffGrey("WirelessHubOffGrey"),
    WirelessHubOffLightGrey("WirelessHubOffLightGrey"),
    WirelessHubOffCyan("WirelessHubOffCyan"),
    WirelessHubOffPurple("WirelessHubOffPurple"),
    WirelessHubOffBlue("WirelessHubOffBlue"),
    WirelessHubOffBrown("WirelessHubOffBrown"),
    WirelessHubOffGreen("WirelessHubOffGreen"),
    WirelessHubOffRed("WirelessHubOffRed"),
    WirelessHubOffBlack("WirelessHubOffBlack"),
    WirelessHubOffTransparent("WirelessHubOffTransparent");

    private final String name;
    public IIcon IIcon;

    WirelessTextures(final String name) {
        this.name = name;
    }

    public static ResourceLocation GuiTexture(final String string) {
        return null;
    }

    @SideOnly(Side.CLIENT)
    public static IIcon getMissing() {
        return TextureUtils.getMissingBlock();
    }

    public String getName() {
        return this.name;
    }

    public IIcon getIcon() {
        return this.IIcon;
    }

    public void registerIcon(final TextureMap map) {
        this.IIcon = map.registerIcon("appliedenergistics2:Wireless/Block" + this.name);
    }
}
