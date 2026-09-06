/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.block.misc;

import java.util.EnumSet;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.util.AEColor;
import appeng.api.util.IOrientable;
import appeng.block.AEBaseTileBlock;
import appeng.client.render.blocks.RenderBlockInterface;
import appeng.client.texture.ExtraBlockTextures;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.tile.misc.TileInterface;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockInterface extends AEBaseTileBlock {

    private static final int COLORED_TEXTURE_COUNT = 3;
    private static final String COLORED_TEXTURE_PATH = "appliedenergistics2:interface/";

    @SideOnly(Side.CLIENT)
    private IIcon[][] coloredTextures;

    public BlockInterface() {
        super(Material.iron);

        this.setTileEntity(TileInterface.class);
        this.setFeature(EnumSet.of(AEFeature.Core));
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected RenderBlockInterface getRenderer() {
        return new RenderBlockInterface();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(final IIconRegister iconRegistry) {
        super.registerBlockIcons(iconRegistry);
        this.coloredTextures = new IIcon[COLORED_TEXTURE_COUNT][AEColor.VALUES.length];

        for (final AEColor color : AEColor.VALID_COLORS) {
            this.coloredTextures[0][color.ordinal()] = iconRegistry
                    .registerIcon(this.getTextureName().replace(":", ":interface/") + "_" + color.name());
            for (int id = 0; id < COLORED_TEXTURE_COUNT - 1; id++) {
                this.coloredTextures[id + 1][color.ordinal()] = iconRegistry
                        .registerIcon(COLORED_TEXTURE_PATH + this.getRenderTexture(id).getName() + "_" + color.name());
            }
        }
    }

    @SideOnly(Side.CLIENT)
    public IIcon getRenderTexture(final int id, final AEColor color) {
        if (color != AEColor.Transparent && id >= -1 && id < COLORED_TEXTURE_COUNT - 1) {
            return this.coloredTextures[id + 1][color.ordinal()];
        }
        return id < 0 ? this.getIcon(0, 0) : this.getRenderTexture(id).getIcon();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(final IBlockAccess world, final int x, final int y, final int z, final int side) {
        final TileInterface tile = this.getTileEntity(world, x, y, z);
        if (tile == null || tile.getColor() == AEColor.Transparent
                || AEConfig.instance.highlightWhenSomethingStuckInInterface && tile.isStuck()) {
            return super.getIcon(world, x, y, z, side);
        }

        if (tile.getForward() == ForgeDirection.UNKNOWN) {
            return this.getRenderTexture(-1, tile.getColor());
        }

        return this.getRenderTexture(switch (this.mapRotation(tile, ForgeDirection.getOrientation(side))) {
            case DOWN -> -1;
            case UP -> 0;
            default -> 1;
        }, tile.getColor());
    }

    private ExtraBlockTextures getRenderTexture(final int id) {
        return switch (id) {
            case 0 -> ExtraBlockTextures.BlockInterfaceAlternate;
            case 1 -> ExtraBlockTextures.BlockInterfaceAlternateArrow;
            default -> throw new IllegalStateException("Unexpected value: " + id);
        };
    }

    @Override
    public boolean onActivated(final World w, final int x, final int y, final int z, final EntityPlayer p,
            final int side, final float hitX, final float hitY, final float hitZ) {
        if (p.isSneaking()) {
            return false;
        }

        final TileInterface tg = this.getTileEntity(w, x, y, z);
        if (tg != null) {
            if (Platform.isServer()) {
                Platform.openGUI(p, tg, ForgeDirection.getOrientation(side), GuiBridge.GUI_INTERFACE);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onNeighborBlockChange(World worldIn, int x, int y, int z, Block neighbor) {
        TileInterface tile = this.getTileEntity(worldIn, x, y, z);
        if (tile != null) {
            tile.getInterfaceDuality().updateRedstoneState();
        }
    }

    @Override
    protected boolean hasCustomRotation() {
        return true;
    }

    @Override
    protected void customRotateBlock(final IOrientable rotatable, final ForgeDirection axis) {
        if (rotatable instanceof TileInterface) {
            ((TileInterface) rotatable).setSide(axis);
        }
    }
}
