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

    private static class ThreadState {

        private boolean useTmp = false;
        private final TmpFlippableIcon tmpTopIcon = new TmpFlippableIcon();
        private final TmpFlippableIcon tmpBottomIcon = new TmpFlippableIcon();
        private final TmpFlippableIcon tmpSouthIcon = new TmpFlippableIcon();
        private final TmpFlippableIcon tmpNorthIcon = new TmpFlippableIcon();
        private final TmpFlippableIcon tmpEastIcon = new TmpFlippableIcon();
        private final TmpFlippableIcon tmpWestIcon = new TmpFlippableIcon();
    }

    private final ThreadLocal<ThreadState> threadState = ThreadLocal.withInitial(ThreadState::new);
    private FlippableIcon topIcon = null;
    private FlippableIcon bottomIcon = null;
    private FlippableIcon southIcon = null;
    private FlippableIcon northIcon = null;
    private FlippableIcon eastIcon = null;
    private FlippableIcon westIcon = null;

    public BlockRenderInfo(final BaseBlockRender<? extends AEBaseBlock, ? extends AEBaseTile> inst) {
        this.rendererInstance = inst;
    }

    public void updateIcons(final FlippableIcon bottom, final FlippableIcon top, final FlippableIcon north,
            final FlippableIcon south, final FlippableIcon east, final FlippableIcon west) {
        this.topIcon = top;
        this.bottomIcon = bottom;
        this.southIcon = south;
        this.northIcon = north;
        this.eastIcon = east;
        this.westIcon = west;
    }

    public void setTemporaryRenderIcon(final IIcon icon) {
        final ThreadState state = this.threadState.get();
        if (icon == null) {
            state.useTmp = false;
        } else {
            state.useTmp = true;
            state.tmpTopIcon.setOriginal(icon);
            state.tmpBottomIcon.setOriginal(icon);
            state.tmpSouthIcon.setOriginal(icon);
            state.tmpNorthIcon.setOriginal(icon);
            state.tmpEastIcon.setOriginal(icon);
            state.tmpWestIcon.setOriginal(icon);
        }
    }

    public void setTemporaryRenderIcons(final IIcon nTopIcon, final IIcon nBottomIcon, final IIcon nSouthIcon,
            final IIcon nNorthIcon, final IIcon nEastIcon, final IIcon nWestIcon) {
        final ThreadState state = this.threadState.get();
        state.tmpTopIcon.setOriginal(nTopIcon == null ? this.getTexture(ForgeDirection.UP) : nTopIcon);
        state.tmpBottomIcon.setOriginal(nBottomIcon == null ? this.getTexture(ForgeDirection.DOWN) : nBottomIcon);
        state.tmpSouthIcon.setOriginal(nSouthIcon == null ? this.getTexture(ForgeDirection.SOUTH) : nSouthIcon);
        state.tmpNorthIcon.setOriginal(nNorthIcon == null ? this.getTexture(ForgeDirection.NORTH) : nNorthIcon);
        state.tmpEastIcon.setOriginal(nEastIcon == null ? this.getTexture(ForgeDirection.EAST) : nEastIcon);
        state.tmpWestIcon.setOriginal(nWestIcon == null ? this.getTexture(ForgeDirection.WEST) : nWestIcon);
        state.useTmp = true;
    }

    public boolean hasTemporaryRenderIcons() {
        return this.threadState.get().useTmp;
    }

    public FlippableIcon getTexture(final ForgeDirection dir) {
        final ThreadState state = this.threadState.get();
        if (state.useTmp) {
            switch (dir) {
                case DOWN -> {
                    return state.tmpBottomIcon;
                }
                case UP -> {
                    return state.tmpTopIcon;
                }
                case NORTH -> {
                    return state.tmpNorthIcon;
                }
                case SOUTH -> {
                    return state.tmpSouthIcon;
                }
                case EAST -> {
                    return state.tmpEastIcon;
                }
                case WEST -> {
                    return state.tmpWestIcon;
                }
                default -> {}
            }
        }

        switch (dir) {
            case DOWN -> {
                return this.bottomIcon;
            }
            case UP -> {
                return this.topIcon;
            }
            case NORTH -> {
                return this.northIcon;
            }
            case SOUTH -> {
                return this.southIcon;
            }
            case EAST -> {
                return this.eastIcon;
            }
            case WEST -> {
                return this.westIcon;
            }
            default -> {}
        }

        return this.topIcon;
    }

    boolean isValid() {
        return this.topIcon != null && this.bottomIcon != null
                && this.southIcon != null
                && this.northIcon != null
                && this.eastIcon != null
                && this.westIcon != null;
    }

    public BaseBlockRender getRendererInstance() {
        return this.rendererInstance;
    }
}
