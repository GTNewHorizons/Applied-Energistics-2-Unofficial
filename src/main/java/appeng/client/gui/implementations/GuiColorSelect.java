package appeng.client.gui.implementations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.AEApi;
import appeng.api.util.AEColor;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketColorSelect;
import appeng.items.tools.powered.ToolColorApplicator;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * GUI for selecting colors for the Color Applicator.
 */
@SideOnly(Side.CLIENT)
public class GuiColorSelect extends GuiScreen {

    private static final int GUI_WIDTH = 195;
    private static final int GUI_HEIGHT = 74;
    private static final int BTN_SIZE = 18;
    private static final int BTN_SPACING = 20;
    private static final int COLUMNS = 9;

    private static final int TITLE_OFFSET_Y = 6;
    private static final int GRID_OFFSET_X = 9;
    private static final int GRID_OFFSET_Y = 20;

    private static final int COLOR_BLACK = 0xFF000000;
    private static final int COLOR_BORDER_GRAY = 0xFFC6C6C6;
    private static final int COLOR_TEXT_GRAY = 0x404040;

    private final EntityPlayer player;
    private int guiLeft;
    private int guiTop;

    public GuiColorSelect(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;
        this.buttonList.clear();

        final var held = this.player.getHeldItem();

        Map<AEColor, Long> availableColors;
        AEColor activeColor;

        if (held != null && held.getItem() instanceof ToolColorApplicator applicator) {
            availableColors = applicator.getAvailableColorsCount(held);
            activeColor = applicator.getActiveColor(held);
        } else {
            availableColors = Collections.emptyMap();
            activeColor = AEColor.Transparent;
        }

        final int startX = this.guiLeft + GRID_OFFSET_X;
        final int startY = this.guiTop + GRID_OFFSET_Y;

        int col = 0;
        int row = 0;

        for (final AEColor color : AEColor.values()) {
            final long longCount = availableColors.getOrDefault(color, 0L);
            final int count = (int) Math.min(longCount, Integer.MAX_VALUE);

            final boolean isSelected = (color == activeColor);
            final boolean isEnabled = (count > 0);

            final int xPos = startX + (col * BTN_SPACING);
            final int yPos = startY + (row * BTN_SPACING);

            this.buttonList.add(new ColorButton(color.ordinal(), xPos, yPos, color, count, isEnabled, isSelected));

            col++;
            if (col >= COLUMNS) {
                col = 0;
                row++;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_LIGHTING);

        // Draw Background
        drawRect(this.guiLeft, this.guiTop, this.guiLeft + GUI_WIDTH, this.guiTop + GUI_HEIGHT, COLOR_BLACK);
        drawRect(
                this.guiLeft + 1,
                this.guiTop + 1,
                this.guiLeft + GUI_WIDTH - 1,
                this.guiTop + GUI_HEIGHT - 1,
                COLOR_BORDER_GRAY);

        GL11.glPopAttrib();

        drawTitle();

        super.drawScreen(mouseX, mouseY, partialTicks);

        drawTooltips(mouseX, mouseY);
    }

    private void drawTitle() {
        final var title = StatCollector.translateToLocal("gui.appliedenergistics2.SelectColor");
        final int titleWidth = this.fontRendererObj.getStringWidth(title);
        final int titleX = (this.width - titleWidth) / 2;
        final int titleY = this.guiTop + TITLE_OFFSET_Y;

        this.fontRendererObj.drawString(title, titleX, titleY, COLOR_TEXT_GRAY);
    }

    private void drawTooltips(int mouseX, int mouseY) {
        for (final var obj : this.buttonList) {
            if (obj instanceof ColorButton btn && btn.isMouseOver()) {
                this.func_146283_a(btn.getTooltipLines(), mouseX, mouseY); // drawHoveringText
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (btn instanceof ColorButton cBtn && cBtn.enabled) {
            NetworkHandler.instance.sendToServer(new PacketColorSelect(cBtn.getAeColor()));
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Inner class representing the Color Selection Button.
     */
    private static final class ColorButton extends GuiButton {

        private static final RenderItem ITEM_RENDERER = new RenderItem();

        // Styles
        private static final int BG_GRAY = 0xFF8B8B8B;
        private static final int BORDER_SELECTED = 0xFF38de38; // Bright Green
        private static final int BORDER_HOVER_VALID = 0xFFFFFFFF; // White
        private static final int BORDER_DEFAULT = 0xFF000000; // Black
        private static final int BORDER_DISABLED = 0xFF555555; // Dark Gray

        private static final int OVERLAY_DISABLED = 0xB0000000;
        private static final int OVERLAY_HOVER = 0x80FFFFFF;

        private static final int COLOR_TEXT_WHITE = 0xFFFFFF;

        private final AEColor aeColor;
        private final int stackCount;
        private final boolean isSelected;
        private final ItemStack displayStack;

        public ColorButton(int id, int x, int y, AEColor color, int count, boolean isEnabled, boolean isSelected) {
            super(id, x, y, BTN_SIZE, BTN_SIZE, "");
            this.aeColor = color;
            this.stackCount = count;
            this.enabled = isEnabled;
            this.isSelected = isSelected;
            this.displayStack = resolveDisplayStack(color);
        }

        private static ItemStack resolveDisplayStack(AEColor color) {
            return switch (color) {
                case Transparent -> new ItemStack(Items.snowball);
                default -> AEApi.instance().definitions().items().coloredPaintBall().stack(color, 1);
            };
        }

        public AEColor getAeColor() {
            return aeColor;
        }

        public boolean isMouseOver() {
            return this.field_146123_n;
        }

        public List<String> getTooltipLines() {
            final List<String> tooltip = new ArrayList<>();
            tooltip.add(StatCollector.translateToLocal(this.aeColor.getUnlocalized()));

            if (!this.enabled) {
                tooltip.add("§c" + StatCollector.translateToLocal("gui.appliedenergistics2.Empty"));
            } else if (this.isSelected) {
                tooltip.add("§a" + StatCollector.translateToLocal("gui.appliedenergistics2.Selected"));
            }
            return tooltip;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) return;

            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

            setupGlState();

            this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
                    && mouseX < this.xPosition + this.width
                    && mouseY < this.yPosition + this.height;

            drawBackground();
            drawIcon(mc);

            // Overlays (Disabled or Hovered)
            drawStateOverlays();

            GL11.glPopAttrib();
        }

        private void setupGlState() {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        private void drawBackground() {
            final int borderColor;

            if (this.isSelected) {
                borderColor = BORDER_SELECTED;
            } else if (!this.enabled) {
                borderColor = BORDER_DISABLED;
            } else {
                borderColor = this.field_146123_n ? BORDER_HOVER_VALID : BORDER_DEFAULT;
            }

            drawRect(
                    this.xPosition,
                    this.yPosition,
                    this.xPosition + this.width,
                    this.yPosition + this.height,
                    borderColor);
            drawRect(
                    this.xPosition + 1,
                    this.yPosition + 1,
                    this.xPosition + this.width - 1,
                    this.yPosition + this.height - 1,
                    BG_GRAY);
        }

        private void drawIcon(Minecraft mc) {
            if (this.displayStack == null) return;

            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();

            ITEM_RENDERER.zLevel = 100.0F;
            ITEM_RENDERER.renderItemAndEffectIntoGUI(
                    mc.fontRenderer,
                    mc.getTextureManager(),
                    this.displayStack,
                    this.xPosition + 1,
                    this.yPosition + 1);
            ITEM_RENDERER.zLevel = 0.0F;

            RenderHelper.disableStandardItemLighting();

            drawQuantity(mc);
        }

        private void drawQuantity(Minecraft mc) {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);

            GL11.glPushMatrix();

            final var stackSizeStr = String.valueOf(this.stackCount);
            final float scale = 0.5f;

            GL11.glScalef(scale, scale, 1.0f);
            final int stringWidth = mc.fontRenderer.getStringWidth(stackSizeStr);
            final int renderX = ((this.xPosition + 17) * 2) - stringWidth;
            final int renderY = ((this.yPosition + 17) * 2) - 8;

            mc.fontRenderer.drawStringWithShadow(stackSizeStr, renderX, renderY, COLOR_TEXT_WHITE);

            GL11.glPopMatrix();
        }

        private void drawStateOverlays() {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_TEXTURE_2D);

            if (!this.enabled) {
                drawRect(
                        this.xPosition + 1,
                        this.yPosition + 1,
                        this.xPosition + this.width - 1,
                        this.yPosition + this.height - 1,
                        OVERLAY_DISABLED);
            } else if (this.field_146123_n && !this.isSelected) {
                drawRect(
                        this.xPosition + 1,
                        this.yPosition + 1,
                        this.xPosition + this.width - 1,
                        this.yPosition + this.height - 1,
                        OVERLAY_HOVER);
            }

            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }
}
