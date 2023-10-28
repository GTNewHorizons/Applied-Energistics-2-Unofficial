package appeng.client.render;

import static appeng.client.render.AppEngRenderItem.POST_HOOKS;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import appeng.api.storage.IItemDisplayRegistry.ItemRenderHook;

/**
 * Uses translations instead of depth test to perform rendering.
 */
public class TranslatedRenderItem extends RenderItem {

    @Override
    public void renderItemOverlayIntoGUI(FontRenderer font, TextureManager texManager, ItemStack stack, int x, int y,
            String customText) {
        if (stack != null) {
            boolean skip = false;
            boolean showDurabilitybar = true;
            boolean showStackSize = true;
            boolean showCraftLabelText = true;
            for (ItemRenderHook hook : POST_HOOKS) {
                skip |= hook.renderOverlay(font, texManager, stack, x, y);
                showDurabilitybar &= hook.showDurability(stack);
                showStackSize &= hook.showStackSize(stack);
                showCraftLabelText &= hook.showCraftLabelText(stack);
            }
            if (skip) {
                return;
            }
            GL11.glPushMatrix();
            if ((showStackSize && stack.stackSize > 1) || (showCraftLabelText && customText != null)) {
                GL11.glTranslatef(0.0f, 0.0f, this.zLevel);
                String s1 = customText == null ? String.valueOf(stack.stackSize) : customText;
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_BLEND);
                font.drawStringWithShadow(s1, x + 19 - 2 - font.getStringWidth(s1), y + 6 + 3, 16777215);
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glTranslatef(0.0f, 0.0f, -this.zLevel);
            }

            if (showDurabilitybar && stack.getItem().showDurabilityBar(stack)) {
                GL11.glTranslatef(0.0f, 0.0f, this.zLevel - 1f);
                double health = stack.getItem().getDurabilityForDisplay(stack);
                int j1 = (int) Math.round(13.0D - health * 13.0D);
                int k = (int) Math.round(255.0D - health * 255.0D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_BLEND);
                Tessellator tessellator = Tessellator.instance;
                int l = 255 - k << 16 | k << 8;
                int i1 = (255 - k) / 4 << 16 | 16128;
                this.renderQuad(tessellator, x + 2, y + 13, 13, 2, 0);
                this.renderQuad(tessellator, x + 2, y + 13, 12, 1, i1);
                this.renderQuad(tessellator, x + 2, y + 13, j1, 1, l);
                // GL11.glEnable(GL11.GL_BLEND); // Forge: Disable Bled because it screws with a lot of things down the
                // line.
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glTranslatef(0.0f, 0.0f, -(this.zLevel - 1f));
            }
            GL11.glPopMatrix();
        }
    }
}
