/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.render.blocks;

import java.util.Set;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.block.misc.BlockStorageReshuffle;
import appeng.client.render.BaseBlockRender;
import appeng.client.texture.ExtraBlockTextures;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStackType;

/**
 * Custom renderer for the Storage Reshuffle block. Handles different textures based on: - Active/inactive state - Type
 * filter mode (All/Items/Fluids) - Directional orientation
 */
public class RendererStorageReshuffle extends BaseBlockRender<BlockStorageReshuffle, TileStorageReshuffle> {

    public RendererStorageReshuffle() {
        super(false, 20);
    }

    @Override
    public void renderInventory(final BlockStorageReshuffle block, final ItemStack is, final RenderBlocks renderer,
            final ItemRenderType type, final Object[] obj) {
        // Use the standard inventory rendering from BaseBlockRender
        // The textures are set via BlockRenderInfo.updateIcons() in BlockStorageReshuffle.registerBlockIcons()
        super.renderInventory(block, is, renderer, type, obj);
    }

    @Override
    public boolean renderInWorld(final BlockStorageReshuffle block, final IBlockAccess world, final int x, final int y,
            final int z, final RenderBlocks renderer) {
        final TileStorageReshuffle tile = block.getTileEntity(world, x, y, z);

        if (tile == null) {
            return false;
        }

        this.preRenderInWorld(block, world, x, y, z, renderer);

        final ForgeDirection forward = tile.getForward();
        final ForgeDirection up = tile.getUp();

        // Render all sides with appropriate textures
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            IIcon icon = getIconForSide(tile, side, forward, up);
            if (icon != null) {
                renderer.overrideBlockTexture = icon;
                this.renderFace(x, y, z, block, icon, renderer, side);
            }
        }

        renderer.overrideBlockTexture = null;
        this.postRenderInWorld(renderer);

        return true;
    }

    /**
     * Gets the appropriate icon for a specific side of the block.
     */
    private IIcon getIconForSide(TileStorageReshuffle tile, ForgeDirection side, ForgeDirection forward,
            ForgeDirection up) {
        // Front face - varies based on state and filter
        if (side == forward) {
            return getFrontIcon(tile);
        }

        // Back face
        if (side == forward.getOpposite()) {
            return ExtraBlockTextures.BlockReshuffleBack.getIcon();
        }

        // Top face
        if (side == up) {
            return ExtraBlockTextures.BlockReshuffleTop.getIcon();
        }

        // Bottom face
        if (side == up.getOpposite()) {
            return ExtraBlockTextures.BlockReshuffleBottom.getIcon();
        }

        // Side faces (left/right)
        return ExtraBlockTextures.BlockReshuffleSide.getIcon();
    }

    /**
     * Gets the front icon based on active state and type filter.
     */
    private IIcon getFrontIcon(TileStorageReshuffle tile) {
        boolean isActive = tile.isReshuffleRunning();
        Set<IAEStackType<?>> allowedTypes = tile.getAllowedTypes();

        // Determine filter mode
        boolean hasItems = allowedTypes.contains(AEItemStackType.ITEM_STACK_TYPE);
        boolean hasFluids = allowedTypes.contains(AEFluidStackType.FLUID_STACK_TYPE);

        // Check for other types (essentia, etc.)
        int totalTypes = AEStackTypeRegistry.getAllTypes().size();
        boolean hasAll = allowedTypes.size() >= totalTypes || (allowedTypes.size() > 2);

        if (hasAll || (hasItems && hasFluids)) {
            // All types mode
            return isActive ? ExtraBlockTextures.BlockReshuffleFrontAllActive.getIcon()
                    : ExtraBlockTextures.BlockReshuffleFrontAll.getIcon();
        } else if (hasItems && !hasFluids) {
            // Items only mode
            return isActive ? ExtraBlockTextures.BlockReshuffleFrontItemsActive.getIcon()
                    : ExtraBlockTextures.BlockReshuffleFrontItems.getIcon();
        } else if (hasFluids && !hasItems) {
            // Fluids only mode
            return isActive ? ExtraBlockTextures.BlockReshuffleFrontFluidsActive.getIcon()
                    : ExtraBlockTextures.BlockReshuffleFrontFluids.getIcon();
        } else {
            // Default/fallback to All mode
            return isActive ? ExtraBlockTextures.BlockReshuffleFrontAllActive.getIcon()
                    : ExtraBlockTextures.BlockReshuffleFrontAll.getIcon();
        }
    }
}
