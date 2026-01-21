/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.block.spatial;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import appeng.block.AEBaseBlock;
import appeng.client.render.blocks.RenderNull;
import appeng.core.features.AEFeature;
import appeng.helpers.ICustomCollision;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockMatrixFrame extends AEBaseBlock implements ICustomCollision {

    public BlockMatrixFrame() {
        super(Material.anvil);
        this.setResistance(6000000.0F);
        this.setBlockUnbreakable();
        this.setLightOpacity(0);
        this.isOpaque = false;
        this.setFeature(EnumSet.of(AEFeature.SpatialIO));

        this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected RenderNull getRenderer() {
        return new RenderNull();
    }

    @Override
    public void registerBlockIcons(final IIconRegister iconRegistry) {}

    @Override
    @SideOnly(Side.CLIENT)
    public void getCheckedSubBlocks(final Item item, final CreativeTabs tabs, final List<ItemStack> itemStacks) {
        // do nothing
    }

    @Override
    public Iterable<AxisAlignedBB> getSelectedBoundingBoxesFromPool(final World w, final int x, final int y,
            final int z, final Entity e, final boolean isVisual) {
        return Collections.singletonList(AxisAlignedBB.getBoundingBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
    }

    @Override
    public void addCollidingBlockToList(final World w, final int x, final int y, final int z, final AxisAlignedBB bb,
            final List<AxisAlignedBB> out, final Entity e) {
        out.add(AxisAlignedBB.getBoundingBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(final World w, final int x, final int y, final int z) {
        return AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isNormalCube() {
        return true;
    }

    @Override
    public boolean isSideSolid(final IBlockAccess w, final int x, final int y, final int z,
            final net.minecraftforge.common.util.ForgeDirection side) {
        return true;
    }

    @Override
    public boolean canPlaceBlockAt(final World world, final int x, final int y, final int z) {
        return true;
    }

    @Override
    public void onBlockExploded(final World world, final int x, final int y, final int z, final Explosion explosion) {
        // Don't explode.
    }

    @Override
    public boolean canEntityDestroy(final IBlockAccess world, final int x, final int y, final int z,
            final Entity entity) {
        return false;
    }
}
