/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.block.networking;

import java.util.EnumSet;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import appeng.api.util.AEColor;
import appeng.block.AEBaseTileBlock;
import appeng.client.render.blocks.RenderBlockController;
import appeng.client.texture.ControllerLightTexture;
import appeng.client.texture.ExtraBlockTextures;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.tile.networking.TileController;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockController extends AEBaseTileBlock {

    private static final int COLORED_TEXTURE_COUNT = 4;
    private static final String COLORED_TEXTURE_PATH = "appliedenergistics2:controller/";

    @SideOnly(Side.CLIENT)
    private IIcon[][] coloredTextures;

    @SideOnly(Side.CLIENT)
    private IIcon[][] lightTextures;

    public BlockController() {
        super(Material.iron);
        this.setTileEntity(TileController.class);
        this.setHardness(6);
        this.setFeature(EnumSet.of(AEFeature.Core));
    }

    @Override
    public void onNeighborBlockChange(final World w, final int x, final int y, final int z, final Block neighborBlock) {
        final TileController tc = this.getTileEntity(w, x, y, z);
        if (tc != null) {
            tc.onNeighborChange(false);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected RenderBlockController getRenderer() {
        return new RenderBlockController();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(final IIconRegister iconRegistry) {
        super.registerBlockIcons(iconRegistry);
        this.coloredTextures = new IIcon[COLORED_TEXTURE_COUNT][AEColor.VALUES.length];

        if (!AEConfig.instance.controllerAnimation.usesOriginalTexture()) {
            this.lightTextures = new IIcon[2][AEColor.VALUES.length];
            final TextureMap map = (TextureMap) iconRegistry;
            for (final AEColor color : AEColor.VALUES) {
                for (int id = 0; id < 2; id++) {
                    final String source = id == 0 ? "BlockControllerLights" : "BlockControllerColumnLights";
                    final ControllerLightTexture lights = new ControllerLightTexture(
                            source,
                            color,
                            AEConfig.instance.controllerAnimation);
                    map.setTextureEntry(lights.getIconName(), lights);
                    this.lightTextures[id][color.ordinal()] = map.getTextureExtry(lights.getIconName());
                }
            }
        }

        for (final AEColor color : AEColor.VALID_COLORS) {
            this.coloredTextures[0][color.ordinal()] = iconRegistry
                    .registerIcon(this.getTextureName().replace(":", ":controller/") + "_" + color.name());
            for (int id = 0; id < COLORED_TEXTURE_COUNT - 1; id++) {
                this.coloredTextures[id + 1][color.ordinal()] = iconRegistry
                        .registerIcon(COLORED_TEXTURE_PATH + this.getRenderTexture(id).getName() + "_" + color.name());
            }
        }
    }

    @SideOnly(Side.CLIENT)
    public IIcon getLightTexture(final int id, final AEColor color) {
        return this.lightTextures[id][color.ordinal()];
    }

    @SideOnly(Side.CLIENT)
    public IIcon getRenderTexture(final int id, final AEColor color) {
        if (color != AEColor.Transparent && id >= -1 && id < COLORED_TEXTURE_COUNT - 1) {
            return this.coloredTextures[id + 1][color.ordinal()];
        }
        return id < 0 ? null : this.getRenderTexture(id).getIcon();
    }

    public ExtraBlockTextures getRenderTexture(int id) {
        return switch (id) {
            case 0 -> ExtraBlockTextures.BlockControllerPowered;
            case 1 -> ExtraBlockTextures.BlockControllerColumnPowered;
            case 2 -> ExtraBlockTextures.BlockControllerColumn;
            case 3 -> ExtraBlockTextures.BlockControllerInsideA;
            case 4 -> ExtraBlockTextures.BlockControllerInsideB;
            default -> throw new IllegalStateException("Unexpected value: " + id);
        };
    }
}
