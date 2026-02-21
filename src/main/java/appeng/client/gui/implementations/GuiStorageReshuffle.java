/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.TypeToggleButton;
import appeng.container.implementations.ContainerStorageReshuffle;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.tile.misc.TileStorageReshuffle;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;

public class GuiStorageReshuffle extends AEBaseGui {

    private static final int TEXTURE_HEIGHT = 512;
    private static final int MAX_VISIBLE_LINES = 13;
    private static final int SCROLLBAR_HEIGHT = 104;
    private static final int KNOB_HEIGHT = 15;
    private static final int REPORT_LINE_HEIGHT = 7;
    private static final float REPORT_TEXT_SCALE = 0.8f;

    private final ContainerStorageReshuffle container;
    private final Map<TypeToggleButton, IAEStackType<?>> typeToggleButtons = new IdentityHashMap<>();

    private GuiImgButton voidProtectionButton;
    private GuiButton startButton;
    private GuiButton scanButton;

    private int scrollPosition = 0;
    private boolean isDraggingScrollbar = false;
    private int dragStartY = 0;
    private int dragStartScrollPos = 0;
    private List<String> reportLines = new ArrayList<>();

    public GuiStorageReshuffle(final InventoryPlayer inventoryPlayer, final TileStorageReshuffle te) {
        super(new ContainerStorageReshuffle(inventoryPlayer, te));
        this.container = (ContainerStorageReshuffle) this.inventorySlots;
        this.xSize = 232;
        this.ySize = 360;
    }

    @Override
    public void initGui() {
        super.initGui();

        final int offset = this.guiTop + 8;

        initTypeToggleButtons(this.guiLeft - 18, offset);

        this.voidProtectionButton = new GuiImgButton(this.guiLeft - 36, offset, Settings.VOID_PROTECTION, YesNo.YES);
        this.buttonList.add(this.voidProtectionButton);

        this.startButton = new GuiButton(
                2,
                this.guiLeft + 24,
                this.guiTop + 130,
                96,
                20,
                GuiText.ReshuffleStart.getLocal());
        this.buttonList.add(this.startButton);

        this.scanButton = new GuiButton(
                3,
                this.guiLeft + 132,
                this.guiTop + 130,
                72,
                20,
                GuiText.ReshuffleScan.getLocal());
        this.buttonList.add(this.scanButton);
    }

    private void initTypeToggleButtons(final int x, final int yStart) {
        this.typeToggleButtons.clear();
        final Reference2BooleanMap<IAEStackType<?>> filters = this.container.getTypeFilters();

        int y = yStart;
        for (final IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            final ResourceLocation texture = type.getButtonTexture();
            final IIcon icon = type.getButtonIcon();
            if (texture == null || icon == null) continue;

            final TypeToggleButton btn = new TypeToggleButton(x, y, texture, icon, type.getDisplayName());
            btn.setEnabled(filters != null && filters.getBoolean(type));
            this.typeToggleButtons.put(btn, type);
            this.buttonList.add(btn);

            y += 20;
        }
    }

    public void onUpdateTypeFilters() {
        final Reference2BooleanMap<IAEStackType<?>> filters = this.container.getTypeFilters();
        if (filters == null) return;
        for (final Map.Entry<TypeToggleButton, IAEStackType<?>> entry : this.typeToggleButtons.entrySet()) {
            final boolean enabled = filters.getBoolean(entry.getValue());
            entry.getKey().setEnabled(enabled);
        }
    }

    public void onReportUpdated() {
        this.reportLines = this.container.getReportLines();
        final int maxScroll = Math.max(0, this.reportLines.size() - MAX_VISIBLE_LINES);
        if (this.scrollPosition > maxScroll) this.scrollPosition = maxScroll;
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        if (btn instanceof final TypeToggleButton tbtn) {
            final IAEStackType<?> type = this.typeToggleButtons.get(tbtn);
            final Reference2BooleanMap<IAEStackType<?>> filters = this.container.getTypeFilters();
            if (type != null && filters != null) {
                final boolean next = !filters.getBoolean(type);
                filters.put(type, next);
                tbtn.setEnabled(next);

                try {
                    NetworkHandler.instance
                            .sendToServer(new PacketValueConfig("Reshuffle.TypeFilter", type.getId()));
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            }
            return;
        }

        try {
            if (btn == this.voidProtectionButton) {
                final boolean backwards = Mouse.isButtonDown(1);
                NetworkHandler.instance.sendToServer(new PacketConfigButton(Settings.VOID_PROTECTION, backwards));
            } else if (btn == this.startButton) {
                if (this.container.reshuffleRunning) {
                    NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.Cancel", ""));
                } else {
                    final boolean confirmed = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                            || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                    NetworkHandler.instance
                            .sendToServer(new PacketValueConfig("Reshuffle.Start", confirmed ? "confirmed" : ""));
                }
            } else if (btn == this.scanButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.Scan", ""));
            }
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            final int totalLines = this.reportLines.size();

            if (totalLines > MAX_VISIBLE_LINES) {
                final int scrollbarX = this.guiLeft + 216;
                final int scrollbarY = this.guiTop + 242;
                final int scrollbarWidth = 12;
                final int maxScroll = Math.max(0, totalLines - MAX_VISIBLE_LINES);
                final int knobOffset = maxScroll > 0 ? (scrollPosition * (SCROLLBAR_HEIGHT - KNOB_HEIGHT) / maxScroll)
                        : 0;
                final int knobY = scrollbarY + knobOffset;

                if (mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth
                        && mouseY >= knobY
                        && mouseY <= knobY + KNOB_HEIGHT) {
                    this.isDraggingScrollbar = true;
                    this.dragStartY = mouseY;
                    this.dragStartScrollPos = scrollPosition;
                }
            }
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);
        if (mouseButton == 0) this.isDraggingScrollbar = false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceClick) {
        super.mouseClickMove(mouseX, mouseY, mouseButton, timeSinceClick);
        if (this.isDraggingScrollbar && mouseButton == 0) {
            final int maxScroll = Math.max(0, this.reportLines.size() - MAX_VISIBLE_LINES);
            if (maxScroll > 0) {
                final int scrollDelta = ((mouseY - this.dragStartY) * maxScroll) / (SCROLLBAR_HEIGHT - KNOB_HEIGHT);
                this.scrollPosition = Math.max(0, Math.min(maxScroll, this.dragStartScrollPos + scrollDelta));
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        final int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            final int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            final int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            final int reportX = this.guiLeft + 12;
            final int reportY = this.guiTop + 242;
            if (mouseX >= reportX && mouseX <= reportX + 200 && mouseY >= reportY && mouseY <= reportY + 104) {
                final int maxScroll = Math.max(0, this.reportLines.size() - MAX_VISIBLE_LINES);
                this.scrollPosition = Math.max(0, Math.min(maxScroll, this.scrollPosition + (wheel > 0 ? -1 : 1)));
            }
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        final int relX = mouseX - this.guiLeft;
        final int relY = mouseY - this.guiTop;

        if (relX >= 214 && relX <= 226 && relY >= 6 && relY <= 18) {
            final List<String> helpLines = new ArrayList<>();
            helpLines.add(GuiText.ReshuffleHelpTitle.getLocal());
            helpLines.add("");
            helpLines.add(GuiText.ReshuffleHelpDesc1.getLocal());
            helpLines.add(GuiText.ReshuffleHelpDesc2.getLocal());
            helpLines.add(GuiText.ReshuffleHelpDesc3.getLocal());
            this.drawHoveringText(helpLines, mouseX, mouseY, this.fontRendererObj);
            return;
        }

        if (relX >= 24 && relX <= 120 && relY >= 130 && relY <= 150) {
            final List<String> lines = new ArrayList<>();
            if (this.container.reshuffleRunning) {
                lines.add(GuiText.ReshuffleTooltipCancelTitle.getLocal());
                lines.add(GuiText.ReshuffleTooltipCancelDesc1.getLocal());
            } else {
                lines.add(GuiText.ReshuffleTooltipStartTitle.getLocal());
                lines.add(GuiText.ReshuffleTooltipStartDesc1.getLocal());
                lines.add(GuiText.ReshuffleTooltipStartDesc2.getLocal());
            }
            this.drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
            return;
        }

        if (relX >= 132 && relX <= 204 && relY >= 130 && relY <= 150) {
            final List<String> lines = new ArrayList<>();
            lines.add(GuiText.ReshuffleTooltipScanTitle.getLocal());
            lines.add(GuiText.ReshuffleTooltipScanDesc1.getLocal());
            lines.add(GuiText.ReshuffleTooltipScanDesc2.getLocal());
            lines.add(GuiText.ReshuffleTooltipScanDesc3.getLocal());
            this.drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.voidProtectionButton.set(this.container.voidProtection ? YesNo.YES : YesNo.NO);

        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.StorageReshuffle.getLocal()),
                8,
                6,
                GuiColors.ReshuffleTitle.getColor());

        if (this.container.reshuffleRunning) {
            this.fontRendererObj.drawString(
                    GuiText.ReshuffleStatusRunning.getLocal(),
                    12,
                    166,
                    GuiColors.ReshuffleStatusRunning.getColor());
            this.startButton.displayString = GuiText.ReshuffleCancel.getLocal();
            this.startButton.enabled = true;
        } else {
            this.fontRendererObj.drawString(
                    GuiText.ReshuffleStatusIdle.getLocal(),
                    12,
                    166,
                    GuiColors.ReshuffleStatusIdle.getColor());
            this.startButton.displayString = GuiText.ReshuffleStart.getLocal();
            this.startButton.enabled = true;
        }

        this.fontRendererObj.drawString(
                GuiText.ReshuffleTotalItems
                        .getLocal(this.container.reshuffleProcessedItems, this.container.reshuffleTotalItems),
                12,
                204,
                GuiColors.ReshuffleTotalItems.getColor());

        this.fontRendererObj.drawString("?", 217, 8, GuiColors.ReshuffleTitle.getColor());

        this.fontRendererObj
                .drawString(GuiText.ReshuffleReport.getLocal(), 12, 230, GuiColors.ReshuffleReport.getColor());

        if (!this.reportLines.isEmpty()) {
            final int reportX = 14;
            final int reportY = 248;

            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            final int scale = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight)
                    .getScaleFactor();
            GL11.glScissor(
                    (this.guiLeft + reportX) * scale,
                    this.mc.displayHeight - (this.guiTop + reportY + 98) * scale,
                    196 * scale,
                    98 * scale);

            GL11.glPushMatrix();
            GL11.glScalef(REPORT_TEXT_SCALE, REPORT_TEXT_SCALE, 1.0f);
            final float inv = 1.0f / REPORT_TEXT_SCALE;
            for (int i = 0; i < MAX_VISIBLE_LINES && (scrollPosition + i) < this.reportLines.size(); i++) {
                this.fontRendererObj.drawString(
                        this.reportLines.get(scrollPosition + i),
                        (int) (reportX * inv),
                        (int) ((reportY + i * REPORT_LINE_HEIGHT) * inv),
                        GuiColors.ReshuffleReport.getColor());
            }
            GL11.glPopMatrix();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final Tessellator tessellator = Tessellator.instance;
        this.bindTexture("guis/reshuffle.png");

        tessellator.startDrawingQuads();
        this.addTexturedRectToTesselator(
                offsetX,
                offsetY,
                offsetX + this.xSize,
                offsetY + this.ySize,
                this.zLevel,
                0.0f,
                0.0f,
                this.xSize / 256.0f,
                (float) this.ySize / TEXTURE_HEIGHT);
        tessellator.draw();

        if (this.container.reshuffleProgress > 0) {
            final int barX = offsetX + 12;
            final int barY = offsetY + 184;
            final int barWidth = 176;
            final int barHeight = 7;
            final int fillWidth = (int) Math.floor(this.container.reshuffleProgress * barWidth / 100.0);
            final int alpha = 255 - this.container.reshuffleProgress * 255 / 100;
            this.drawGradientRect(
                    barX + 1,
                    barY + 1,
                    barX + 1 + fillWidth,
                    barY + barHeight - 1,
                    0xFF00FFFF,
                    0xFF00FF00 | alpha);
            drawRect(barX + fillWidth, barY, barX + fillWidth + 3, barY + barHeight, 0xFFFFFFFF);
        }

        if (this.reportLines.size() > MAX_VISIBLE_LINES) {
            final int scrollbarX = offsetX + 216;
            final int scrollbarY = offsetY + 242;
            final int maxScroll = Math.max(0, this.reportLines.size() - MAX_VISIBLE_LINES);
            final int knobOffset = maxScroll > 0 ? (scrollPosition * (SCROLLBAR_HEIGHT - KNOB_HEIGHT) / maxScroll) : 0;

            this.bindTexture("minecraft", "gui/container/creative_inventory/tabs.png");
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            this.drawTexturedModalRect(scrollbarX, scrollbarY + knobOffset, 232, 0, 12, KNOB_HEIGHT);
        }
    }
}
