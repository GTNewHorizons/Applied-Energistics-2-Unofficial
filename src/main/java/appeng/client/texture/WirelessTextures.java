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

    WirelessConnectorWhite("WirelessConnectorOnWhite", "WirelessConnectorOffWhite"),
    WirelessConnectorOrange("WirelessConnectorOnOrange", "WirelessConnectorOffOrange"),
    WirelessConnectorMagenta("WirelessConnectorOnMagenta", "WirelessConnectorOffMagenta"),
    WirelessConnectorLightBlue("WirelessConnectorOnLightBlue", "WirelessConnectorOffLightBlue"),
    WirelessConnectorYellow("WirelessConnectorOnYellow", "WirelessConnectorOffYellow"),
    WirelessConnectorLime("WirelessConnectorOnLime", "WirelessConnectorOffLime"),
    WirelessConnectorPink("WirelessConnectorOnPink", "WirelessConnectorOffPink"),
    WirelessConnectorGrey("WirelessConnectorOnGrey", "WirelessConnectorOffGrey"),
    WirelessConnectorLightGrey("WirelessConnectorOnLightGrey", "WirelessConnectorOffLightGrey"),
    WirelessConnectorCyan("WirelessConnectorOnCyan", "WirelessConnectorOffCyan"),
    WirelessConnectorPurple("WirelessConnectorOnPurple", "WirelessConnectorOffPurple"),
    WirelessConnectorBlue("WirelessConnectorOnBlue", "WirelessConnectorOffBlue"),
    WirelessConnectorBrown("WirelessConnectorOnBrown", "WirelessConnectorOffBrown"),
    WirelessConnectorGreen("WirelessConnectorOnGreen", "WirelessConnectorOffGreen"),
    WirelessConnectorRed("WirelessConnectorOnRed", "WirelessConnectorOffRed"),
    WirelessConnectorBlack("WirelessConnectorOnBlack", "WirelessConnectorOffBlack"),
    WirelessConnectorTransparent("WirelessConnectorOnTransparent", "WirelessConnectorOffTransparent"),

    WirelessHubWhite("WirelessHubOnWhite", "WirelessHubOffWhite"),
    WirelessHubOrange("WirelessHubOnOrange", "WirelessHubOffOrange"),
    WirelessHubMagenta("WirelessHubOnMagenta", "WirelessHubOffMagenta"),
    WirelessHubLightBlue("WirelessHubOnLightBlue", "WirelessHubOffLightBlue"),
    WirelessHubYellow("WirelessHubOnYellow", "WirelessHubOffYellow"),
    WirelessHubLime("WirelessHubOnLime", "WirelessHubOffLime"),
    WirelessHubPink("WirelessHubOnPink", "WirelessHubOffPink"),
    WirelessHubGrey("WirelessHubOnGrey", "WirelessHubOffGrey"),
    WirelessHubLightGrey("WirelessHubOnLightGrey", "WirelessHubOffLightGrey"),
    WirelessHubCyan("WirelessHubOnCyan", "WirelessHubOffCyan"),
    WirelessHubPurple("WirelessHubOnPurple", "WirelessHubOffPurple"),
    WirelessHubBlue("WirelessHubOnBlue", "WirelessHubOffBlue"),
    WirelessHubBrown("WirelessHubOnBrown", "WirelessHubOffBrown"),
    WirelessHubGreen("WirelessHubOnGreen", "WirelessHubOffGreen"),
    WirelessHubRed("WirelessHubOnRed", "WirelessHubOffRed"),
    WirelessHubBlack("WirelessHubOnBlack", "WirelessHubOffBlack"),
    WirelessHubTransparent("WirelessHubOnTransparent", "WirelessHubOffTransparent");

    private final String onName;
    private final String offName;
    public IIcon onIIcon;
    public IIcon offIIcon;

    WirelessTextures(final String onName, final String offName) {
        this.onName = onName;
        this.offName = offName;
    }

    public static ResourceLocation GuiTexture(final String string) {
        return null;
    }

    @SideOnly(Side.CLIENT)
    public static IIcon getMissing() {
        return TextureUtils.getMissingBlock();
    }

    public String getOnName() {
        return this.onName;
    }

    public String getOffName() {
        return this.offName;
    }

    public IIcon getOnIcon() {
        return this.onIIcon;
    }

    public IIcon getOffIcon() {
        return this.offIIcon;
    }

    public void registerIcon(final TextureMap map) {
        this.onIIcon = map.registerIcon("appliedenergistics2:Wireless/Block" + this.onName);
        this.offIIcon = map.registerIcon("appliedenergistics2:Wireless/Block" + this.offName);
    }
}
