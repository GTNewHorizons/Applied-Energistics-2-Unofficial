/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.render;

import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.block.AEBaseBlock;
import appeng.client.texture.FlippableIcon;
import appeng.client.texture.TmpFlippableIcon;
import appeng.tile.AEBaseTile;

public class BlockRenderInfo {

    private final BaseBlockRender<? extends AEBaseBlock, ? extends AEBaseTile> rendererInstance;

    public static class TextureSet {

        private FlippableIcon topIcon;
        private FlippableIcon bottomIcon;
        private FlippableIcon southIcon;
        private FlippableIcon northIcon;
        private FlippableIcon eastIcon;
        private FlippableIcon westIcon;

        private TextureSet() {}

        private TextureSet(final FlippableIcon bottom, final FlippableIcon top, final FlippableIcon north,
                final FlippableIcon south, final FlippableIcon east, final FlippableIcon west) {
            this.update(bottom, top, north, south, east, west);
        }

        private void update(final FlippableIcon bottom, final FlippableIcon top, final FlippableIcon north,
                final FlippableIcon south, final FlippableIcon east, final FlippableIcon west) {
            this.topIcon = top;
            this.bottomIcon = bottom;
            this.southIcon = south;
            this.northIcon = north;
            this.eastIcon = east;
            this.westIcon = west;
        }

        public FlippableIcon get(final ForgeDirection dir) {
            return switch (dir) {
                case DOWN -> this.bottomIcon;
                case UP -> this.topIcon;
                case NORTH -> this.northIcon;
                case SOUTH -> this.southIcon;
                case EAST -> this.eastIcon;
                case WEST -> this.westIcon;
                default -> this.topIcon;
            };
        }

        private boolean isValid() {
            return this.topIcon != null && this.bottomIcon != null
                    && this.southIcon != null
                    && this.northIcon != null
                    && this.eastIcon != null
                    && this.westIcon != null;
        }
    }

    private static class ThreadState extends TextureSet {

        private boolean useTmp = false;

        private ThreadState() {
            super(
                    new TmpFlippableIcon(),
                    new TmpFlippableIcon(),
                    new TmpFlippableIcon(),
                    new TmpFlippableIcon(),
                    new TmpFlippableIcon(),
                    new TmpFlippableIcon());
        }

        private void setOriginal(final ForgeDirection direction, final IIcon icon) {
            ((TmpFlippableIcon) this.get(direction)).setOriginal(icon);
        }
    }

    private final ThreadLocal<ThreadState> threadState = ThreadLocal.withInitial(ThreadState::new);
    private final TextureSet textures = new TextureSet();

    public BlockRenderInfo(final BaseBlockRender<? extends AEBaseBlock, ? extends AEBaseTile> inst) {
        this.rendererInstance = inst;
    }

    public void updateIcons(final FlippableIcon bottom, final FlippableIcon top, final FlippableIcon north,
            final FlippableIcon south, final FlippableIcon east, final FlippableIcon west) {
        this.textures.update(bottom, top, north, south, east, west);
    }

    public void setTemporaryRenderIcon(final IIcon icon) {
        final ThreadState state = this.threadState.get();
        if (icon == null) {
            state.useTmp = false;
        } else {
            state.useTmp = true;
            for (final ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                state.setOriginal(direction, icon);
            }
        }
    }

    public void setTemporaryRenderIcons(final IIcon nTopIcon, final IIcon nBottomIcon, final IIcon nSouthIcon,
            final IIcon nNorthIcon, final IIcon nEastIcon, final IIcon nWestIcon) {
        final ThreadState state = this.threadState.get();
        final TextureSet current = state.useTmp ? state : this.textures;
        state.setOriginal(ForgeDirection.UP, nTopIcon == null ? current.get(ForgeDirection.UP) : nTopIcon);
        state.setOriginal(ForgeDirection.DOWN, nBottomIcon == null ? current.get(ForgeDirection.DOWN) : nBottomIcon);
        state.setOriginal(ForgeDirection.SOUTH, nSouthIcon == null ? current.get(ForgeDirection.SOUTH) : nSouthIcon);
        state.setOriginal(ForgeDirection.NORTH, nNorthIcon == null ? current.get(ForgeDirection.NORTH) : nNorthIcon);
        state.setOriginal(ForgeDirection.EAST, nEastIcon == null ? current.get(ForgeDirection.EAST) : nEastIcon);
        state.setOriginal(ForgeDirection.WEST, nWestIcon == null ? current.get(ForgeDirection.WEST) : nWestIcon);
        state.useTmp = true;
    }

    public boolean hasTemporaryRenderIcons() {
        return this.threadState.get().useTmp;
    }

    public FlippableIcon getTexture(final ForgeDirection dir) {
        return this.resolveTextures().get(dir);
    }

    public TextureSet resolveTextures() {
        final ThreadState state = this.threadState.get();
        return state.useTmp ? state : this.textures;
    }

    boolean isValid() {
        return this.textures.isValid();
    }

    public BaseBlockRender getRendererInstance() {
        return this.rendererInstance;
    }
}
