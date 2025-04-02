/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.block.storage;

import java.util.EnumSet;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.block.AEBaseTileBlock;
import appeng.client.render.blocks.RenderDrive;
import appeng.core.features.AEFeature;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.GuiBridge;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.tile.storage.TileDrive;
import appeng.util.Platform;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.util.GTUtility;

public class BlockDrive extends AEBaseTileBlock {

    public BlockDrive() {
        super(Material.iron);
        this.setTileEntity(TileDrive.class);
        this.setFeature(EnumSet.of(AEFeature.StorageCells, AEFeature.MEDrive));
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected RenderDrive getRenderer() {
        return new RenderDrive();
    }

    @Override
    public boolean onActivated(final World w, final int x, final int y, final int z, final EntityPlayer p,
            final int side, final float hitX, final float hitY, final float hitZ) {
        if (p.isSneaking()) {
            return false;
        }

        final TileDrive tg = this.getTileEntity(w, x, y, z);
        if (tg != null) {
            if (Platform.isServer()) {
                if (Loader.isModLoaded("dreamcraft") && IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.GT)
                        && GTUtility.isStackInList(p.getHeldItem(), GregTechAPI.sWireCutterList)) {
                    if (tg.toggleItemStorageCellLocking()) {
                        p.addChatMessage(PlayerMessages.DriveLockingToggled.get());
                    }
                    return true;
                }
                Platform.openGUI(p, tg, ForgeDirection.getOrientation(side), GuiBridge.GUI_DRIVE);
            }
            return true;
        }
        return false;
    }
}
