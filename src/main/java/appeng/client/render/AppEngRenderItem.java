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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import appeng.api.config.TerminalFontSize;
import appeng.api.storage.IItemDisplayRegistry.ItemRenderHook;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.me.SlotME;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;

/**
 * @author AlgorithmX2
 * @author thatsIch
 * @version rv2
 * @since rv0
 */
public class AppEngRenderItem extends AERenderItem {

    private IAEItemStack aeStack = null;
    private Slot slot = null;

    /**
     * Post render hooks. All are called.
     */
    public static List<ItemRenderHook> POST_HOOKS = new ArrayList<>();

    public void renderItemOverlayIntoGUI(final FontRenderer fontRenderer, final TextureManager textureManager,
            final ItemStack is, final int par4, final int par5, final String par6Str, Slot slotIn) {
        this.slot = slotIn;
        this.renderItemOverlayIntoGUI(fontRenderer, textureManager, is, par4, par5, par6Str);

    }

    @Override
    public void renderItemOverlayIntoGUI(final FontRenderer fontRenderer, final TextureManager textureManager,
            final ItemStack is, final int par4, final int par5, final String par6Str) {
        if (is != null) {
            boolean skip = false;
            boolean showDurabilitybar = true;
            boolean showStackSize = true;
            boolean showCraftLabelText = true;
            for (ItemRenderHook hook : POST_HOOKS) {
                skip |= hook.renderOverlay(fontRenderer, textureManager, is, par4, par5);
                showDurabilitybar &= hook.showDurability(is);
                showStackSize &= hook.showStackSize(is);
                showCraftLabelText &= hook.showCraftLabelText(is);
            }
            if (skip) {
                return;
            }
            final boolean unicodeFlag = fontRenderer.getUnicodeFlag();
            final TerminalFontSize fontSize = AEConfig.instance.getTerminalFontSize();
            fontRenderer.setUnicodeFlag(false);

            if (showDurabilitybar && is.getItem().showDurabilityBar(is)) {
                final double health = is.getItem().getDurabilityForDisplay(is);
                final int j1 = (int) Math.round(13.0D - health * 13.0D);
                final int k = (int) Math.round(255.0D - health * 255.0D);

                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_BLEND);

                final Tessellator tessellator = Tessellator.instance;
                final int l = 255 - k << 16 | k << 8;
                final int i1 = (255 - k) / 4 << 16 | 16128;

                this.renderQuad(tessellator, par4 + 2, par5 + 13, 13, 2, 0);
                this.renderQuad(tessellator, par4 + 2, par5 + 13, 12, 1, i1);
                this.renderQuad(tessellator, par4 + 2, par5 + 13, j1, 1, l);

                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }

            if (is.stackSize == 0 && showCraftLabelText && aeStack != null && aeStack.isCraftable()) {
                final String craftLabelText = fontSize == TerminalFontSize.SMALL ? GuiText.SmallFontCraft.getLocal()
                        : GuiText.LargeFontCraft.getLocal();

                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glPushMatrix();
                this.drawStackSize(par4, par5, craftLabelText, fontRenderer, fontSize);
                GL11.glPopMatrix();
                GL11.glEnable(GL11.GL_LIGHTING);
            }

            final long amount = this.aeStack != null ? this.aeStack.getStackSize() : is.stackSize;

            if ((amount != 0 && showStackSize) || (slot != null && slot instanceof SlotME
                    && ((SlotME) slot).getAEStack() != null
                    && !((SlotME) slot).getAEStack().isCraftable())) {

                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glPushMatrix();
                this.drawStackSize(par4, par5, amount, fontRenderer, fontSize);
                GL11.glPopMatrix();
                GL11.glEnable(GL11.GL_LIGHTING);
            }

            fontRenderer.setUnicodeFlag(unicodeFlag);
        }
    }

    public IAEItemStack getAeStack() {
        return this.aeStack;
    }

    public void setAeStack(@Nonnull final IAEItemStack aeStack) {
        this.aeStack = aeStack;
    }
}
