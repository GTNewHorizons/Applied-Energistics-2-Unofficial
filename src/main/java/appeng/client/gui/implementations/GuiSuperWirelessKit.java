package appeng.client.gui.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.config.Settings;
import appeng.api.config.SuperWirelessToolGroupBy;
import appeng.api.config.YesNo;
import appeng.api.events.GuiScrollEvent;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiAeButton;
import appeng.client.gui.widgets.GuiCheckBox;
import appeng.client.gui.widgets.GuiColorButton;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.render.BlockPosHighlighter;
import appeng.container.implementations.ContainerSuperWirelessKit;
import appeng.core.localization.GuiColors;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSuperWirelessToolCommand;
import appeng.helpers.SuperWirelessToolDataObject;
import appeng.items.contents.SuperWirelessKitObject;
import appeng.util.IConfigManagerHost;
import cpw.mods.fml.common.registry.GameRegistry;

public class GuiSuperWirelessKit extends AEBaseGui implements IConfigManagerHost {

    private final int TOP_OFFSET = 22;
    private final int SCROLLBAR_HEIGHT = 229;

    private GuiImgButton sortBy;
    private GuiImgButton hideBoundedButton;
    private GuiAeButton bind;
    private GuiAeButton unbind;
    private final GuiColorButton[] colorButtons = new GuiColorButton[17];
    private GuiCheckBox madChameleonButton;

    private final MEGuiTextField[] nameField = new MEGuiTextField[30];

    private final GuiCheckBox[] pinButtons = new GuiCheckBox[10];

    private final GuiCheckBox[] includeConnectorsButtons = new GuiCheckBox[30];
    private final GuiCheckBox[] includeHubsButtons = new GuiCheckBox[30];
    private final GuiCheckBox[] deleteButtons = new GuiCheckBox[30];

    private final IConfigManager configSrc;
    private SuperWirelessToolGroupBy mode;
    private YesNo hideBounded;

    private final GuiScrollbar unselectedColumnScroll;
    private final GuiScrollbar toBindColumnScroll;
    private final GuiScrollbar targetColumnScroll;

    private final ArrayList<ItemStack> icons;

    private final ArrayList<baseUnit> unselected = new ArrayList<>();
    private final ArrayList<baseUnit> toBind = new ArrayList<>();
    private final ArrayList<baseUnit> target = new ArrayList<>();

    private baseUnit toRemoveFromUnselected;
    private baseUnit toAddToUnselected;

    private baseUnit toRemoveFromToBind;
    private baseUnit toAddToToBind;

    private baseUnit toRemoveFromTarget;
    private baseUnit toAddToTarget;

    private NBTTagCompound dataCache;

    public GuiSuperWirelessKit(final InventoryPlayer inventoryPlayer, final SuperWirelessKitObject te) {
        this(inventoryPlayer, te, new ContainerSuperWirelessKit(inventoryPlayer, te));
    }

    public GuiSuperWirelessKit(final InventoryPlayer inventoryPlayer, final SuperWirelessKitObject te,
            final ContainerSuperWirelessKit c) {
        super(c);
        this.configSrc = ((IConfigurableObject) this.inventorySlots).getConfigManager();
        ((ContainerSuperWirelessKit) this.inventorySlots).setGui(this);
        this.mode = (SuperWirelessToolGroupBy) this.configSrc.getSetting(Settings.SUPER_WIRELESS_TOOL_GROUP_BY);
        this.hideBounded = (YesNo) this.configSrc.getSetting(Settings.SUPER_WIRELESS_TOOL_HIDE_BOUNDED);
        this.xSize = 256;
        this.ySize = 256;
        this.unselectedColumnScroll = new GuiScrollbar();
        this.toBindColumnScroll = new GuiScrollbar();
        this.targetColumnScroll = new GuiScrollbar();
        this.icons = setIcons();

        for (int i = 0; i < 30; i++) {
            this.nameField[i] = new MEGuiTextField(
                    94,
                    12,
                    StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.namefield.desc")) {

                @Override
                public void mouseClicked(int xPos, int yPos, int button) {
                    xPos = (xPos - guiLeft) * 2;
                    yPos = (yPos - guiTop) * 2;

                    super.mouseClicked(xPos, yPos, button);
                }

            };

            if (i >= 20) {
                this.nameField[i].x = 177 * 2 + 26;
                this.nameField[i].y = ((i - 20) * 23 + TOP_OFFSET) * 2;
            } else if (i >= 10) {
                this.nameField[i].x = 100 * 2 + 26;
                this.nameField[i].y = ((i - 10) * 23 + TOP_OFFSET) * 2;
            } else {
                this.nameField[i].x = 5 * 2 + 26;
                this.nameField[i].y = (i * 23 + TOP_OFFSET) * 2;
            }
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        setScrollBar();
        addButtons();
    }

    private ArrayList<ItemStack> setIcons() {
        ArrayList<ItemStack> list = new ArrayList<>();

        for (int i = 0; i < 16; i++) {
            final ItemStack is = GameRegistry.findItemStack("appliedenergistics2", "tile.BlockWirelessConnector", 1);
            is.setItemDamage(i + 1);
            list.add(is);
        }
        final ItemStack iss = GameRegistry.findItemStack("appliedenergistics2", "tile.BlockWirelessConnector", 1);
        list.add(iss);

        for (int i = 0; i < 16; i++) {
            final ItemStack is = GameRegistry.findItemStack("appliedenergistics2", "tile.BlockWirelessHub", 1);
            is.setItemDamage(i + 1);
            list.add(is);
        }
        final ItemStack is = GameRegistry.findItemStack("appliedenergistics2", "tile.BlockWirelessHub", 1);
        list.add(is);
        return list;
    }

    private void setScrollBar() {
        this.unselectedColumnScroll.setTop(TOP_OFFSET).setLeft(67).setHeight(SCROLLBAR_HEIGHT);
        this.toBindColumnScroll.setTop(TOP_OFFSET).setLeft(162).setHeight(SCROLLBAR_HEIGHT);
        this.targetColumnScroll.setTop(TOP_OFFSET).setLeft(239).setHeight(SCROLLBAR_HEIGHT);

        this.unselectedColumnScroll.setRange(0, this.unselected.size() - 10, 1);
        this.toBindColumnScroll.setRange(0, this.toBind.size() - 10, 1);
        this.targetColumnScroll.setRange(0, this.target.size() - 10, 1);
    }

    protected void addButtons() {
        for (int i = 0; i < 8; i++) {
            this.colorButtons[i] = new GuiColorButton(
                    0,
                    this.guiLeft + 81,
                    this.guiTop + TOP_OFFSET + 8 + (9 * i),
                    8,
                    8,
                    AEColor.values()[i],
                    AEColor.values()[i].name());
            this.buttonList.add(this.colorButtons[i]);
        }

        for (int i = 8; i < 16; i++) {
            this.colorButtons[i] = new GuiColorButton(
                    0,
                    this.guiLeft + 90,
                    this.guiTop + TOP_OFFSET + 8 + (9 * (i - 8)),
                    8,
                    8,
                    AEColor.values()[i],
                    AEColor.values()[i].name());
            this.buttonList.add(this.colorButtons[i]);
        }

        this.colorButtons[16] = new GuiColorButton(
                0,
                this.guiLeft + 81,
                this.guiTop + TOP_OFFSET - 1,
                8,
                8,
                AEColor.values()[16],
                AEColor.values()[16].name());

        this.buttonList.add(this.colorButtons[16]);

        this.madChameleonButton = new GuiCheckBox(
                0.5D,
                this.guiLeft + 90,
                this.guiTop + TOP_OFFSET - 1,
                16 * 7 + 13,
                16 * 7 + 13,
                StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.madchameleonrecolor.name"),
                StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.madchameleonrecolor.desc"));
        this.buttonList.add(this.madChameleonButton);

        for (int i = 0; i < 10; i++) {
            this.pinButtons[i] = new GuiCheckBox(
                    0.25D,
                    this.guiLeft + 232,
                    this.guiTop + (i * 23) + TOP_OFFSET + 6,
                    16 * 6 + 14,
                    16 * 7 + 14,
                    StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.pinbutton.name"),
                    StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.pinbutton.desc"));
            this.pinButtons[i].visible = false;
            this.buttonList.add(this.pinButtons[i]);
        }

        for (int i = 0; i < 30; i++) {
            int x;
            int y;
            if (i >= 20) {
                x = this.guiLeft + 232;
                y = this.guiTop + ((i - 20) * 23) + TOP_OFFSET + 17;
            } else if (i >= 10) {
                x = this.guiLeft + 155;
                y = this.guiTop + ((i - 10) * 23) + TOP_OFFSET + 17;
            } else {
                x = this.guiLeft + 60;
                y = this.guiTop + (i * 23) + TOP_OFFSET + 17;
            }
            this.includeHubsButtons[i] = new GuiCheckBox(
                    0.25D,
                    x,
                    y,
                    StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.includehubs.name"),
                    StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.includehubs.desc"));
            this.includeConnectorsButtons[i] = new GuiCheckBox(
                    0.25D,
                    x - 5,
                    y,
                    StatCollector
                            .translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.includeconnectors.name"),
                    StatCollector
                            .translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.includeconnectors.desc"));
            this.deleteButtons[i] = new GuiCheckBox(
                    0.25D,
                    x - 55,
                    y - 17,
                    StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.delete.name"),
                    StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.delete.desc"));
            this.includeConnectorsButtons[i].visible = false;
            this.includeHubsButtons[i].visible = false;
            this.deleteButtons[i].visible = false;
            this.buttonList.add(this.includeHubsButtons[i]);
            this.buttonList.add(this.includeConnectorsButtons[i]);
            this.buttonList.add(this.deleteButtons[i]);
        }

        this.bind = new GuiAeButton(
                0,
                this.guiLeft + 131,
                this.guiTop + 4,
                44,
                16,
                StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.bind.name"),
                StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.bind.desc"));

        this.unbind = new GuiAeButton(
                0,
                this.guiLeft + 81,
                this.guiTop + 4,
                44,
                16,
                StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.unbind.name"),
                StatCollector.translateToLocal("gui.appliedenergistics2.GuiSuperWirelessKit.unbind.desc"));

        this.sortBy = new GuiImgButton(
                this.guiLeft + 4,
                this.guiTop + 4,
                Settings.SUPER_WIRELESS_TOOL_GROUP_BY,
                SuperWirelessToolGroupBy.Single);

        this.hideBoundedButton = new GuiImgButton(
                this.guiLeft + 24,
                this.guiTop + 4,
                Settings.SUPER_WIRELESS_TOOL_HIDE_BOUNDED,
                YesNo.NO);

        this.buttonList.add(this.bind);
        this.buttonList.add(this.unbind);
        this.buttonList.add(this.sortBy);
        this.buttonList.add(this.hideBoundedButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        for (baseUnit bs : unselected) {
            if (bs.keyTyped(typedChar, keyCode)) return;
        }

        for (baseUnit bs : toBind) {
            if (bs.keyTyped(typedChar, keyCode)) return;
        }

        for (baseUnit bs : target) {
            if (bs.keyTyped(typedChar, keyCode)) return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        if (btn == this.sortBy) {
            final boolean backwards = Mouse.isButtonDown(1);
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.sortBy.getSetting(), backwards));
        } else if (btn == this.hideBoundedButton) {
            final boolean backwards = Mouse.isButtonDown(1);
            NetworkHandler.instance
                    .sendToServer(new PacketConfigButton(this.hideBoundedButton.getSetting(), backwards));
        } else if (btn == this.madChameleonButton) {
            reColorCommand(null);
        } else if (btn == this.bind) {
            sendCommand("bind", null);
        } else if (btn == this.unbind) {
            sendCommand("unbind", null);
        }

        for (GuiColorButton colorButton : colorButtons) {
            if (btn == colorButton) {
                reColorCommand(colorButton.getColor());
            }
        }
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        if (this.unselectedColumnScroll != null) {
            this.unselectedColumnScroll.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);
        }

        if (this.toBindColumnScroll != null) {
            this.toBindColumnScroll.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);
        }

        if (this.targetColumnScroll != null) {
            this.targetColumnScroll.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);
        }

        for (baseUnit bs : unselected) {
            bs.mouseClicked(xCoord, yCoord, btn);
        }

        for (baseUnit bs : toBind) {
            bs.mouseClicked(xCoord, yCoord, btn);
        }

        for (baseUnit bs : target) {
            bs.mouseClicked(xCoord, yCoord, btn);
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int c, final long d) {
        if (this.unselectedColumnScroll != null) {
            this.unselectedColumnScroll.clickMove(y - this.guiTop);
        }

        if (this.toBindColumnScroll != null) {
            this.toBindColumnScroll.clickMove(y - this.guiTop);
        }

        if (this.targetColumnScroll != null) {
            this.targetColumnScroll.clickMove(y - this.guiTop);
        }

        super.mouseClickMove(x, y, c, d);

    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();

        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }

        final int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
        final int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

        if (MinecraftForge.EVENT_BUS.post(new GuiScrollEvent(this, x, y, wheel))) {
            return;
        }

        if (!this.mouseWheelEvent(x, y, wheel)) {
            if (this.unselectedColumnScroll != null
                    && (x > guiLeft + 3 && x < guiLeft + 79 && y > guiTop + TOP_OFFSET)) {
                final GuiScrollbar scrollBar = this.unselectedColumnScroll;
                if (x > this.guiLeft && y - this.guiTop > scrollBar.getTop()
                        && x <= this.guiLeft + this.xSize
                        && y - this.guiTop <= scrollBar.getTop() + scrollBar.getHeight()) {
                    this.unselectedColumnScroll.wheel(wheel);
                }
            }
            if (this.toBindColumnScroll != null && (x > guiLeft + 98 && x < guiLeft + 174 && y > guiTop + TOP_OFFSET)) {
                final GuiScrollbar scrollBar = this.toBindColumnScroll;
                if (x > this.guiLeft && y - this.guiTop > scrollBar.getTop()
                        && x <= this.guiLeft + this.xSize
                        && y - this.guiTop <= scrollBar.getTop() + scrollBar.getHeight()) {
                    this.toBindColumnScroll.wheel(wheel);
                }
            }
            if (this.targetColumnScroll != null
                    && (x > guiLeft + 175 && x < guiLeft + 251 && y > guiTop + TOP_OFFSET)) {
                final GuiScrollbar scrollBar = this.targetColumnScroll;
                if (x > this.guiLeft && y - this.guiTop > scrollBar.getTop()
                        && x <= this.guiLeft + this.xSize
                        && y - this.guiTop <= scrollBar.getTop() + scrollBar.getHeight()) {
                    this.targetColumnScroll.wheel(wheel);
                }
            }
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {

        this.unselectedColumnScroll.draw(this);
        this.toBindColumnScroll.draw(this);
        this.targetColumnScroll.draw(this);

        reset();
        drawUnselected();
        drawToBind();
        drawTarget();
    }

    private void reset() {
        for (int i = 0; i < 30; i++) {
            this.includeConnectorsButtons[i].visible = false;
            this.includeHubsButtons[i].visible = false;
            this.deleteButtons[i].visible = false;
            if (i < 10) this.pinButtons[i].visible = false;
        }

        for (baseUnit bu : this.unselected) {
            bu.isVisible = false;
        }

        for (baseUnit bu : this.toBind) {
            bu.isVisible = false;
        }

        for (baseUnit bu : this.target) {
            bu.isVisible = false;
        }
    }

    private void drawUnselected() {
        final int viewStart = this.unselectedColumnScroll.getCurrentScroll();
        final int viewEnd = viewStart + 10;

        int y = 0;

        for (int z = viewStart; z < Math.min(viewEnd, unselected.size()); z++) {
            unselected.get(z).setXY(5, y, 0);
            unselected.get(z).draw();

            y++;
        }

        if (toRemoveFromUnselected != null) {
            unselected.remove(toRemoveFromUnselected);
            toRemoveFromUnselected = null;
            setScrollBar();
        }

        if (toAddToUnselected != null) {
            unselected.add(toAddToUnselected);
            toAddToUnselected = null;
            setScrollBar();
        }
    }

    private void drawToBind() {
        final int viewStart = this.toBindColumnScroll.getCurrentScroll();
        final int viewEnd = viewStart + 10;

        int y = 0;

        for (int z = viewStart; z < Math.min(viewEnd, toBind.size()); z++) {
            toBind.get(z).setXY(100, y, 1);
            toBind.get(z).draw();

            y++;
        }

        if (toRemoveFromToBind != null) {
            toBind.remove(toRemoveFromToBind);
            toRemoveFromToBind = null;
            setScrollBar();
        }

        if (toAddToToBind != null) {
            toBind.add(toAddToToBind);
            toAddToToBind = null;
            setScrollBar();
        }
    }

    private void drawTarget() {
        final int viewStart = this.targetColumnScroll.getCurrentScroll();
        final int viewEnd = viewStart + 10;

        int y = 0;

        for (int z = viewStart; z < Math.min(viewEnd, target.size()); z++) {
            target.get(z).setXY(177, y, 2);
            target.get(z).draw();

            y++;
        }

        if (toRemoveFromTarget != null) {
            target.remove(toRemoveFromTarget);
            toRemoveFromTarget = null;
            setScrollBar();
        }

        if (toAddToTarget != null) {
            target.add(toAddToTarget);
            toAddToTarget = null;
            setScrollBar();
        }
    }

    private class singleUnit extends baseUnit {

        singleUnit(SuperWirelessToolDataObject data, String customNetworkName) {
            super(data, customNetworkName);
        }

        @Override
        public void draw() {
            final int posY = yo * offY + TOP_OFFSET;
            final int descPosX = xo * 3 + 46;

            GL11.glPushMatrix();
            GL11.glScaled(0.7666, 0.7666, 0.7666);
            drawItem(
                    (int) Math.round(xo / 0.7666) + 1,
                    yo * (int) Math.round(offY / 0.7666) + TOP_OFFSET + 9,
                    icons.get(data.isHub ? data.color.ordinal() + 17 : data.color.ordinal()));
            GL11.glPopMatrix();

            drawTextBox();

            drawNetworkName(descPosX, posY);

            GL11.glPushMatrix();
            GL11.glScaled(0.333, 0.333, 0.333);

            fontRendererObj.drawString(
                    StatCollector.translateToLocalFormatted(
                            "gui.appliedenergistics2.GuiSuperWirelessKit.color",
                            data.color.toString()),
                    descPosX,
                    posY * 3 + 28,
                    GuiColors.CraftingCPUStored.getColor());
            fontRendererObj.drawString(
                    StatCollector.translateToLocalFormatted(
                            "gui.appliedenergistics2.GuiSuperWirelessKit.selfpos",
                            String.valueOf(data.cord.x),
                            String.valueOf(data.cord.y),
                            String.valueOf(data.cord.z)),
                    descPosX,
                    posY * 3 + 38,
                    GuiColors.CraftingCPUStored.getColor());
            if (data.isConnected) fontRendererObj.drawString(
                    StatCollector.translateToLocalFormatted(
                            "gui.appliedenergistics2.GuiSuperWirelessKit.targetpos",
                            String.valueOf(data.targetCord.x),
                            String.valueOf(data.targetCord.y),
                            String.valueOf(data.targetCord.z)),
                    descPosX,
                    posY * 3 + 48,
                    GuiColors.CraftingCPUStored.getColor());
            GL11.glPopMatrix();

            int indicatorColor = AEColor.Lime.mediumVariant;
            String str;
            if (data.isHub) {
                if (data.slots == 0) {
                    indicatorColor = AEColor.Red.mediumVariant;
                } else if (data.slots < 32) {
                    indicatorColor = AEColor.Orange.mediumVariant;
                }
                str = 32 - data.slots + "/32" + " | " + data.channels;
            } else {
                if (data.slots == 0) {
                    indicatorColor = AEColor.Red.mediumVariant;
                }
                str = data.slots + "/1" + " | " + data.channels;
            }
            drawSlotsIndicator(str, posY, indicatorColor);
            super.draw();
        }

        @Override
        protected void renameCommand() {
            sendCommand("renameSingle", this);
        }
    }

    private class groupUnit extends baseUnit {

        private final ArrayList<SuperWirelessToolDataObject> wsList = new ArrayList<>();
        private final ArrayList<DimensionalCoord> cordList = new ArrayList<>();
        private boolean includeHubs;
        private boolean includeConnectors;
        private int channels;
        private int slots;
        private int usedSlots;
        private String customColorName = "";

        groupUnit(SuperWirelessToolDataObject data, String customNetworkName) {
            this(data, customNetworkName, false, true, true, false);
        }

        groupUnit(SuperWirelessToolDataObject data, String customNetworkName, boolean byColor, String customColorName) {
            this(data, customNetworkName, byColor, true, true, false);
            data.customName = Objects.equals(customColorName, "") ? data.color.name() : customColorName;
            this.customColorName = customColorName;
        }

        groupUnit(SuperWirelessToolDataObject data, String customNetworkName, boolean byColor, String customColorName,
                boolean includeConnectors, boolean includeHubs, boolean isPinned) {
            this(data, customNetworkName, byColor, includeConnectors, includeHubs, isPinned);
            data.customName = Objects.equals(customColorName, "") ? data.color.name() : customColorName;
            this.customColorName = customColorName;
        }

        groupUnit(SuperWirelessToolDataObject data, String customNetworkName, boolean byColor,
                boolean includeConnectors, boolean includeHubs, boolean isPinned) {
            super(data, customNetworkName);
            this.byColor = byColor;
            this.wsList.add(data);
            this.includeHubs = includeHubs;
            this.includeConnectors = includeConnectors;
            this.isPinned = isPinned;

            if (includeConnectors && !data.isHub) {
                this.cordList.add(data.cord);
                this.channels = data.channels;
                this.slots = 1;
                this.usedSlots = 1 - data.slots;
            }

            if (includeHubs && data.isHub) {
                this.cordList.add(data.cord);
                this.channels = data.channels;
                this.slots = 32;
                this.usedSlots = 32 - data.slots;
            }

            if (!byColor) {
                data.customName = !Objects.equals(customNetworkName, "") ? customNetworkName
                        : String.valueOf(data.network);
            }
        }

        public void addToGroup(SuperWirelessToolDataObject data) {
            this.wsList.add(data);

            if (includeConnectors && !data.isHub) {
                this.cordList.add(data.cord);
                this.channels += data.channels;
                this.slots += 1;
                this.usedSlots += 1 - data.slots;
            }

            if (includeHubs && data.isHub) {
                this.cordList.add(data.cord);
                this.channels += data.channels;
                this.slots += 32;
                this.usedSlots += 32 - data.slots;
            }
        }

        private void reCalc() {
            this.cordList.clear();
            this.channels = 0;
            this.slots = 0;
            this.usedSlots = 0;

            for (SuperWirelessToolDataObject s : wsList) {
                if (includeConnectors && !s.isHub) {
                    this.cordList.add(s.cord);
                    this.channels += s.channels;
                    this.slots += 1;
                    this.usedSlots += 1 - s.slots;
                }

                if (includeHubs && s.isHub) {
                    this.cordList.add(s.cord);
                    this.channels += s.channels;
                    this.slots += 32;
                    this.usedSlots += 32 - s.slots;
                }
            }
        }

        @Override
        protected void handleButtons() {
            if (totalPos > 19) {
                pinButtons[yo].visible = true;
                pinButtons[yo].setState(this.isPinned);
            }
            if (!byColor) {
                deleteButtons[totalPos].visible = true;
            }
            includeConnectorsButtons[totalPos].visible = true;
            includeConnectorsButtons[totalPos].setState(this.includeConnectors);
            includeHubsButtons[totalPos].visible = true;
            includeHubsButtons[totalPos].setState(this.includeHubs);
        }

        @Override
        protected void renameCommand() {
            sendCommand("renameGroup", this);
        }

        @Override
        public void draw() {
            final int offY = 23;
            final int posY = yo * offY + TOP_OFFSET;
            final int descPosX = xo * 3 + 46;

            GL11.glPushMatrix();
            GL11.glScaled(0.45, 0.45, 0.45);
            if (byColor) {
                if (includeHubs) {
                    drawItem(
                            (int) Math.round(xo / 0.45) + 7,
                            (int) Math.round(posY / 0.45) + 2,
                            icons.get(data.color.ordinal() + 17));
                } else {
                    drawItem(
                            (int) Math.round(xo / 0.45) + 7,
                            (int) Math.round(posY / 0.45) + 2,
                            icons.get(data.color.ordinal()));
                }
                if (includeConnectors) {
                    drawItem(
                            (int) Math.round(xo / 0.45) + 0,
                            (int) Math.round(posY / 0.45) + 14,
                            icons.get(data.color.ordinal()));
                    drawItem(
                            (int) Math.round(xo / 0.45) + 14,
                            (int) Math.round(posY / 0.45) + 14,
                            icons.get(data.color.ordinal()));
                } else {
                    drawItem(
                            (int) Math.round(xo / 0.45) + 0,
                            (int) Math.round(posY / 0.45) + 14,
                            icons.get(data.color.ordinal() + 17));
                    drawItem(
                            (int) Math.round(xo / 0.45) + 14,
                            (int) Math.round(posY / 0.45) + 14,
                            icons.get(data.color.ordinal() + 17));
                }
            } else {
                if (includeHubs) {
                    drawItem((int) Math.round(xo / 0.45) + 7, (int) Math.round(posY / 0.45) + 2, icons.get(17));
                } else {
                    drawItem((int) Math.round(xo / 0.45) + 7, (int) Math.round(posY / 0.45) + 2, icons.get(0));
                }
                if (includeConnectors) {
                    drawItem((int) Math.round(xo / 0.45) + 0, (int) Math.round(posY / 0.45) + 14, icons.get(1));
                    drawItem((int) Math.round(xo / 0.45) + 14, (int) Math.round(posY / 0.45) + 14, icons.get(2));
                } else {
                    drawItem((int) Math.round(xo / 0.45) + 0, (int) Math.round(posY / 0.45) + 14, icons.get(1 + 17));
                    drawItem((int) Math.round(xo / 0.45) + 14, (int) Math.round(posY / 0.45) + 14, icons.get(2 + 17));
                }
            }

            GL11.glPopMatrix();

            drawTextBox();

            drawNetworkName(descPosX, posY);

            GL11.glPushMatrix();
            GL11.glScaled(0.333, 0.333, 0.333);
            if (byColor) {
                fontRendererObj
                        .drawString(
                                StatCollector.translateToLocalFormatted(
                                        "gui.appliedenergistics2.GuiSuperWirelessKit.color",
                                        (!Objects.equals(customColorName, "") ? customColorName
                                                : data.color.toString())),
                                descPosX,
                                posY * 3 + 28,
                                GuiColors.CraftingCPUStored.getColor());
            }
            fontRendererObj.drawString(
                    StatCollector.translateToLocalFormatted(
                            "gui.appliedenergistics2.GuiSuperWirelessKit.channelsUsage",
                            channels),
                    descPosX,
                    posY * 3 + 38,
                    GuiColors.CraftingCPUStored.getColor());
            GL11.glPopMatrix();

            int indicatorColor = AEColor.Lime.mediumVariant;
            String str = usedSlots + "/" + slots;

            if (usedSlots > 0) {
                if (slots == usedSlots) {
                    indicatorColor = AEColor.Red.mediumVariant;
                } else {
                    indicatorColor = AEColor.Orange.mediumVariant;
                }
            }

            drawSlotsIndicator(str, posY, indicatorColor);

            super.draw();
        }

        @Override
        public void mouseClicked(int xPos, int yPos, int button) {
            if (this.isMouseIn(xPos, yPos)) {
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                    BlockPosHighlighter.highlightBlocks(
                            mc.thePlayer,
                            cordList,
                            PlayerMessages.InterfaceHighlighted.getName(),
                            PlayerMessages.InterfaceInOtherDim.getName());
                    mc.thePlayer.closeScreen();
                } else {
                    if (includeConnectorsButtons[totalPos].mousePressed(null, xPos, yPos)) {
                        this.includeConnectors ^= true;
                        if (!this.includeConnectors && !this.includeHubs) {
                            this.includeHubs = true;
                        }
                        reCalc();
                        if (isPinned) {
                            sendCommand("pin", this);
                        }
                    } else if (includeHubsButtons[totalPos].mousePressed(null, xPos, yPos)) {
                        this.includeHubs ^= true;
                        if (!this.includeConnectors && !this.includeHubs) {
                            this.includeConnectors = true;
                        }
                        reCalc();
                        if (isPinned) {
                            sendCommand("pin", this);
                        }
                    } else if (deleteButtons[totalPos].mousePressed(null, xPos, yPos)) {
                        sendCommand("delete", this);
                    } else super.mouseClicked(xPos, yPos, button);
                }
            }
        }
    }

    private class baseUnit {

        protected final int w = 60;
        protected final int h = 22;
        protected final int offY = h + 1;
        protected int x = 0;
        protected int y = 0;
        protected int xo = 0;
        protected int yo = 0;
        protected int totalPos = 0;
        protected final SuperWirelessToolDataObject data;
        protected boolean inToBind = false;
        protected boolean inTarget = false;
        protected boolean isPinned = false;
        protected String customNetworkName = "";
        protected boolean isVisible = false;
        protected boolean byColor = false;

        baseUnit(SuperWirelessToolDataObject data, String customNetworkName) {
            this.data = data;
            this.customNetworkName = customNetworkName;
        }

        public void setXY(int x, int y, int column) {
            this.x = guiLeft + x;
            this.y = guiTop + (y * (h + 1)) + TOP_OFFSET;
            this.xo = x;
            this.yo = y;
            this.totalPos = y + column * 10;
            this.isVisible = true;
        }

        protected void handleButtons() {
            if (totalPos > 19 && data.isHub) {
                pinButtons[yo].visible = true;
                pinButtons[yo].setState(this.isPinned);
            }
        }

        public void draw() {
            handleButtons();
        }

        public void drawTextBox() {
            GL11.glPushMatrix();
            GL11.glScaled(0.5, 0.5, 0.5);
            nameField[totalPos].drawTextBox();
            if (!nameField[totalPos].isFocused()) nameField[totalPos].setText(data.customName);
            GL11.glPopMatrix();
        }

        public void drawNetworkName(int descPosX, int posY) {
            GL11.glPushMatrix();
            GL11.glScaled(0.333, 0.333, 0.333);
            fontRendererObj.drawString(
                    StatCollector.translateToLocalFormatted(
                            "gui.appliedenergistics2.GuiSuperWirelessKit.network",
                            (!Objects.equals(customNetworkName, "") ? customNetworkName
                                    : String.valueOf(data.network))),
                    descPosX,
                    posY * 3 + 18,
                    GuiColors.CraftingCPUStored.getColor());
            GL11.glPopMatrix();
        }

        public void drawSlotsIndicator(String str, int posY, int indicatorColor) {
            drawRect(xo, posY + 15, xo + 14, posY + 20, indicatorColor - 16777216);
            GL11.glPushMatrix();
            GL11.glScaled(0.25, 0.25, 0.25);
            fontRendererObj.drawString(
                    str,
                    (xo + 7) * 4 - (fontRendererObj.getStringWidth(str) / 2),
                    posY * 4 + 66,
                    GuiColors.CraftingCPUStored.getColor());
            GL11.glPopMatrix();
        }

        protected void renameCommand() {}

        public boolean keyTyped(char typedChar, int keyCode) {
            if (!isVisible) return false;
            boolean isFocused = nameField[totalPos].isFocused();
            if (nameField[totalPos].textboxKeyTyped(typedChar, keyCode)) {
                return true;
            } else if (isFocused && keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                this.renameCommand();
                return true;
            }
            return false;
        }

        public void mouseClicked(final int xPos, final int yPos, final int button) {
            if (this.isMouseIn(xPos, yPos)) {
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                    BlockPosHighlighter.highlightBlocks(
                            mc.thePlayer,
                            data.targetCord == null ? Collections.singletonList(data.cord)
                                    : new ArrayList<>(Arrays.asList(data.cord, data.targetCord)),
                            PlayerMessages.InterfaceHighlighted.getName(),
                            PlayerMessages.InterfaceInOtherDim.getName());
                    mc.thePlayer.closeScreen();
                    return;
                }

                nameField[totalPos].mouseClicked(xPos, yPos, button);
                if (nameField[totalPos].isMouseIn((xPos - guiLeft) * 2, (yPos - guiTop) * 2)) {
                    // no
                } else if (pinButtons[yo].mousePressed(null, xPos, yPos)) {
                    isPinned ^= true;
                    sendCommand("pin", this);
                } else if (isPinned) {
                    // no
                } else if (this.inToBind) {
                    toRemoveFromToBind = this;
                    this.inToBind = false;
                    if (button == 0) {
                        toAddToUnselected = this;
                    } else if (button == 1) {
                        toAddToTarget = this;
                        this.inTarget = true;
                    }
                } else if (this.inTarget) {
                    toRemoveFromTarget = this;
                    this.inTarget = false;
                    if (button == 0) {
                        toAddToToBind = this;
                        this.inToBind = true;
                    } else if (button == 1) {
                        toAddToUnselected = this;
                    }
                } else {
                    if (button == 0) {
                        toRemoveFromUnselected = this;
                        toAddToToBind = this;
                        this.inToBind = true;
                    } else if (button == 1) {
                        toRemoveFromUnselected = this;
                        toAddToTarget = this;
                        this.inTarget = true;
                    }
                }
            }
        }

        public boolean isMouseIn(final int xCoord, final int yCoord) {
            if (!isVisible) return false;
            final boolean withinXRange = this.x <= xCoord && xCoord < this.x + this.w;
            final boolean withinYRange = this.y <= yCoord && yCoord < this.y + this.h;

            return withinXRange && withinYRange;
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/superwirelesskit.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (this.sortBy != null) {
            this.sortBy.set(this.configSrc.getSetting(Settings.SUPER_WIRELESS_TOOL_GROUP_BY));
            this.mode = (SuperWirelessToolGroupBy) this.configSrc.getSetting(Settings.SUPER_WIRELESS_TOOL_GROUP_BY);
            this.hideBoundedButton.set(this.configSrc.getSetting(Settings.SUPER_WIRELESS_TOOL_HIDE_BOUNDED));
            this.hideBounded = (YesNo) this.configSrc.getSetting(Settings.SUPER_WIRELESS_TOOL_HIDE_BOUNDED);
        }
        if (dataCache != null) {
            setData(dataCache);
        }
    }

    private void reColorCommand(AEColor color) {
        NBTTagList tagList = new NBTTagList();
        ArrayList<DimensionalCoord> dc = new ArrayList<>();
        for (baseUnit bu : this.toBind) {
            if (bu instanceof groupUnit) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("network", bu.data.network);
                if (bu.byColor) {
                    tag.setInteger("color", bu.data.color.ordinal());
                }
                tagList.appendTag(tag);
            } else {
                dc.add(bu.data.cord);
            }
        }
        NBTTagCompound cords = new NBTTagCompound();
        DimensionalCoord.writeListToNBT(cords, dc);

        NBTTagCompound command = new NBTTagCompound();
        command.setString("command", "recolor");
        command.setTag("cords", cords);
        command.setTag("group", tagList);
        command.setInteger("color", color == null ? -1 : color.ordinal());

        try {
            NetworkHandler.instance.sendToServer(new PacketSuperWirelessToolCommand(command));
        } catch (IOException ignored) {}
    }

    private void sendCommand(String command, baseUnit bu) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("command", command);

        switch (command) {
            case "renameSingle" -> {
                String name = this.nameField[bu.totalPos].getText();
                tag.setString("name", name);
                NBTTagCompound pos = new NBTTagCompound();
                bu.data.cord.writeToNBT(pos);
                tag.setTag("cord", pos);
                try {
                    NetworkHandler.instance.sendToServer(new PacketSuperWirelessToolCommand(tag));
                    bu.data.customName = name;
                } catch (IOException ignored) {}
            }
            case "renameGroup" -> {
                String name = this.nameField[bu.totalPos].getText();
                tag.setInteger("network", bu.data.network);
                tag.setString("name", name);
                if (bu.byColor) {
                    tag.setInteger("color", bu.data.color.ordinal());
                }
                try {
                    NetworkHandler.instance.sendToServer(new PacketSuperWirelessToolCommand(tag));
                    bu.data.customName = name;
                } catch (IOException ignored) {}
            }
            case "pin" -> {
                tag.setBoolean("pin", bu.isPinned);
                tag.setInteger("network", bu.data.network);

                if (bu instanceof groupUnit gu) {
                    if (bu.byColor) {
                        tag.setInteger("color", bu.data.color.ordinal());
                        tag.setString("type", "color");
                    } else {
                        tag.setString("type", "network");
                    }
                    if (!gu.includeConnectors) {
                        tag.setBoolean("incCon", false);
                    }
                    if (!gu.includeHubs) {
                        tag.setBoolean("incHub", false);
                    }
                } else {
                    NBTTagCompound cord = new NBTTagCompound();
                    bu.data.cord.writeToNBT(cord);
                    tag.setTag("cord", cord);
                    tag.setString("type", "single");
                }
                try {
                    NetworkHandler.instance.sendToServer(new PacketSuperWirelessToolCommand(tag));
                } catch (IOException ignored) {}
            }
            case "delete" -> {
                tag.setInteger("network", bu.data.network);
                try {
                    NetworkHandler.instance.sendToServer(new PacketSuperWirelessToolCommand(tag));
                } catch (IOException ignored) {}
            }
            case "bind" -> {
                NBTTagList tagListToBind = new NBTTagList();

                for (baseUnit butb : this.toBind) {
                    NBTTagCompound tagToBind = new NBTTagCompound();
                    if (butb instanceof groupUnit gu) {
                        tagToBind.setInteger("network", gu.data.network);
                        if (!gu.includeConnectors) {
                            tagToBind.setBoolean("incCon", false);
                        }
                        if (!gu.includeHubs) {
                            tagToBind.setBoolean("incHub", false);
                        }
                        if (gu.byColor) {
                            tagToBind.setInteger("color", gu.data.color.ordinal());
                        }
                    } else {
                        NBTTagCompound cord = new NBTTagCompound();
                        butb.data.cord.writeToNBT(cord);
                        tagToBind.setTag("cord", cord);
                    }
                    tagListToBind.appendTag(tagToBind);
                }
                tag.setTag("toBind", tagListToBind);

                NBTTagList tagListTarget = new NBTTagList();
                for (baseUnit but : this.target) {
                    NBTTagCompound tagTarget = new NBTTagCompound();
                    if (but instanceof groupUnit gu) {
                        tagTarget.setInteger("network", gu.data.network);
                        if (!gu.includeConnectors) {
                            tagTarget.setBoolean("incCon", false);
                        }
                        if (!gu.includeHubs) {
                            tagTarget.setBoolean("incHub", false);
                        }
                        if (gu.byColor) {
                            tagTarget.setInteger("color", gu.data.color.ordinal());
                        }
                    } else {
                        but.data.cord.writeToNBT(tagTarget);
                    }
                    tagListTarget.appendTag(tagTarget);
                }
                tag.setTag("target", tagListTarget);

                try {
                    NetworkHandler.instance.sendToServer(new PacketSuperWirelessToolCommand(tag));
                } catch (IOException ignored) {}
            }
            case "unbind" -> {
                NBTTagList tagListToBind = new NBTTagList();

                for (baseUnit butb : this.toBind) {
                    NBTTagCompound tagToBind = new NBTTagCompound();
                    if (butb instanceof groupUnit gu) {
                        tagToBind.setInteger("network", gu.data.network);
                        if (!gu.includeConnectors) {
                            tagToBind.setBoolean("incCon", false);
                        }
                        if (!gu.includeHubs) {
                            tagToBind.setBoolean("incHub", false);
                        }
                        if (gu.byColor) {
                            tagToBind.setInteger("color", gu.data.color.ordinal());
                        }
                    } else {
                        NBTTagCompound cord = new NBTTagCompound();
                        butb.data.cord.writeToNBT(cord);
                        tagToBind.setTag("cord", cord);;
                    }
                    tagListToBind.appendTag(tagToBind);
                }
                tag.setTag("toBind", tagListToBind);

                try {
                    NetworkHandler.instance.sendToServer(new PacketSuperWirelessToolCommand(tag));
                } catch (IOException ignored) {}
            }

            default -> {}
        }
    }

    private groupUnit getNetworkUnitFormResolver(ArrayList<groupUnit> list, int network, AEColor color, boolean isPin) {
        for (groupUnit gu : list) {
            if (gu.data.network == network && gu.isPinned == isPin) {
                if (color == null) {
                    return gu;
                } else {
                    if (gu.byColor && gu.data.color == color) {
                        return gu;
                    }
                }
            }
        }
        return null;
    }

    public void setData(NBTTagCompound newData) {
        dataCache = (NBTTagCompound) newData.copy();
        ArrayList<SuperWirelessToolDataObject> data = SuperWirelessToolDataObject.readFromNBTasList(newData);

        this.unselected.clear();
        this.toBind.clear();
        this.target.clear();

        ArrayList<groupUnit> networkGroupResolver = new ArrayList<>();

        NBTTagList names = newData.getTagList("names", 10);
        int[] namesNetworkCache = new int[names.tagCount()];
        String[] networkNameCache = new String[names.tagCount()];
        boolean[] isByColor = new boolean[names.tagCount()];
        AEColor[] namesColorCache = new AEColor[names.tagCount()];
        String[] colorNameCache = new String[names.tagCount()];

        for (int i = 0; i < names.tagCount(); i++) {
            NBTTagCompound tag = names.getCompoundTagAt(i);
            namesNetworkCache[i] = tag.getInteger("network");
            networkNameCache[i] = tag.getString("networkName");
            isByColor[i] = tag.hasKey("color");
            namesColorCache[i] = AEColor.values()[tag.getInteger("color")];
            colorNameCache[i] = tag.getString("colorName");
        }

        NBTTagList pins = newData.getTagList("pins", 10);
        int[] networkIdCache = new int[pins.tagCount()];
        AEColor[] colorCache = new AEColor[pins.tagCount()];
        String[] type = new String[pins.tagCount()];
        DimensionalCoord[] cord = new DimensionalCoord[pins.tagCount()];
        boolean[] includeConnectorsCache = new boolean[pins.tagCount()];
        boolean[] includeHubsCache = new boolean[pins.tagCount()];

        for (int i = 0; i < pins.tagCount(); i++) {
            NBTTagCompound tag = pins.getCompoundTagAt(i);
            networkIdCache[i] = tag.getInteger("network");
            colorCache[i] = AEColor.values()[tag.getInteger("color")];
            type[i] = tag.getString("type");
            cord[i] = DimensionalCoord.readFromNBT(tag.getCompoundTag("cord"));
            includeConnectorsCache[i] = !tag.hasKey("incCon");
            includeHubsCache[i] = !tag.hasKey("incHub");
        }

        for (final SuperWirelessToolDataObject wdo : data) {
            boolean isPinned = false;
            boolean isGroup = false;
            boolean isSameColor = false;
            boolean includeConnectors = true;
            boolean includeHubs = true;
            String networkName = "";
            String colorName = "";

            if (hideBounded == YesNo.YES && wdo.isConnected && !(wdo.isHub && wdo.slots != 0)) {
                continue;
            }

            for (int i = 0; i < names.tagCount(); i++) {
                if (wdo.network == namesNetworkCache[i]) {
                    if (isByColor[i]) {
                        if (wdo.color == namesColorCache[i]) {
                            colorName = colorNameCache[i];
                        }
                    } else {
                        networkName = networkNameCache[i];
                    }
                }
            }

            for (int i = 0; i < pins.tagCount(); i++) {
                if (wdo.network == networkIdCache[i]) {
                    switch (type[i]) {
                        case "single" -> {
                            if (wdo.cord.isEqual(cord[i])) {
                                isPinned = true;
                            }
                        }
                        case "network" -> {
                            isPinned = true;
                            isGroup = true;
                        }
                        case "color" -> {
                            if (wdo.color == colorCache[i]) {
                                isSameColor = true;
                                isPinned = true;
                                isGroup = true;
                            }
                        }
                        default -> {}
                    }
                    if (isPinned) {
                        includeConnectors = includeConnectorsCache[i];
                        includeHubs = includeHubsCache[i];
                        break;
                    }
                }
            }

            if (isPinned) {
                if (isGroup) {
                    if (isSameColor) {
                        groupUnit gu = getNetworkUnitFormResolver(networkGroupResolver, wdo.network, wdo.color, true);
                        if (gu == null) {
                            groupUnit newUnit = new groupUnit(
                                    wdo,
                                    networkName,
                                    true,
                                    colorName,
                                    includeConnectors,
                                    includeHubs,
                                    true);
                            networkGroupResolver.add(newUnit);
                            this.target.add(newUnit);
                        } else {
                            gu.addToGroup(wdo);
                        }

                    } else {
                        groupUnit gu = getNetworkUnitFormResolver(networkGroupResolver, wdo.network, null, true);
                        if (gu != null) {
                            gu.addToGroup(wdo);
                        } else {
                            groupUnit newUnit = new groupUnit(
                                    wdo,
                                    networkName,
                                    false,
                                    includeConnectors,
                                    includeHubs,
                                    true);
                            networkGroupResolver.add(newUnit);
                            this.target.add(newUnit);
                        }
                    }
                } else {
                    this.target.add(new singleUnit(wdo, networkName));
                }
            } else {
                if (mode == SuperWirelessToolGroupBy.Single) {
                    this.unselected.add(new singleUnit(wdo, networkName));
                } else if (mode == SuperWirelessToolGroupBy.Network) {
                    groupUnit gu = getNetworkUnitFormResolver(networkGroupResolver, wdo.network, null, false);
                    if (gu != null) {
                        gu.addToGroup(wdo);
                    } else {
                        groupUnit newUnit = new groupUnit(wdo, networkName);
                        networkGroupResolver.add(newUnit);
                        this.unselected.add(newUnit);
                    }
                } else {
                    groupUnit gu = getNetworkUnitFormResolver(networkGroupResolver, wdo.network, wdo.color, false);
                    if (gu != null) {
                        gu.addToGroup(wdo);
                    } else {
                        groupUnit newUnit = new groupUnit(wdo, networkName, true, colorName);
                        networkGroupResolver.add(newUnit);
                        this.unselected.add(newUnit);
                    }
                }
            }
        }
        setScrollBar();
    }
}
