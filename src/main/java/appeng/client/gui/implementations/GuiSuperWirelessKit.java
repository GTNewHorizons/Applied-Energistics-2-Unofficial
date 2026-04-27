package appeng.client.gui.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.AEApi;
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
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.container.implementations.ContainerSuperWirelessKit;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSuperWirelessToolCommand;
import appeng.helpers.SuperWirelessKitCommand;
import appeng.helpers.SuperWirelessKitCommand.PinType;
import appeng.helpers.SuperWirelessKitCommand.SubCommand;
import appeng.helpers.SuperWirelessKitCommand.SuperWirelessKitCommands;
import appeng.helpers.SuperWirelessToolDataObject;
import appeng.items.contents.SuperWirelessKitObject;
import appeng.util.IConfigManagerHost;
import cpw.mods.fml.common.Loader;

public class GuiSuperWirelessKit extends AEBaseGui implements IConfigManagerHost {

    private final int TOP_OFFSET = 22;
    private final int SCROLLBAR_HEIGHT = 229;

    private GuiImgButton sortBy;
    private GuiImgButton hideBoundedButton;
    private GuiCheckBox ae2stuffConvert;
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

    private final ArrayList<BaseUnit> unselected = new ArrayList<>();
    private final ArrayList<BaseUnit> toBind = new ArrayList<>();
    private final ArrayList<BaseUnit> target = new ArrayList<>();

    private BaseUnit toRemoveFromUnselected;
    private BaseUnit toAddToUnselected;

    private BaseUnit toRemoveFromToBind;
    private BaseUnit toAddToBind;

    private BaseUnit toRemoveFromTarget;
    private BaseUnit toAddTarget;

    private NBTTagCompound nData;
    private ArrayList<SuperWirelessToolDataObject> wData;

    private final boolean isAEStaffLoaded = Loader.isModLoaded("ae2stuff");

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
            this.nameField[i] = new MEGuiTextField(94, 12, GuiText.GuiSuperWirelessKitNameFieldDesc.getLocal()) {

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
            final ItemStack is = AEApi.instance().definitions().blocks().wirelessConnector().maybeStack(1).orNull();
            is.setItemDamage(i + 1);
            list.add(is);
        }
        final ItemStack iss = AEApi.instance().definitions().blocks().wirelessConnector().maybeStack(1).orNull();
        list.add(iss);

        for (int i = 0; i < 16; i++) {
            final ItemStack is = AEApi.instance().definitions().blocks().wirelessHub().maybeStack(1).orNull();
            is.setItemDamage(i + 1);
            list.add(is);
        }
        final ItemStack is = AEApi.instance().definitions().blocks().wirelessHub().maybeStack(1).orNull();
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

        if (isAEStaffLoaded) {
            this.ae2stuffConvert = new GuiCheckBox(
                    0.5D,
                    this.guiLeft + 244,
                    this.guiTop + 4,
                    16 * 10,
                    16 * 10,
                    ButtonToolTips.SuperWirelessKitAE2StuffName.getLocal(),
                    ButtonToolTips.SuperWirelessKitAE2StuffDesc.getLocal());

            this.buttonList.add(ae2stuffConvert);
        }

        this.madChameleonButton = new GuiCheckBox(
                0.5D,
                this.guiLeft + 90,
                this.guiTop + TOP_OFFSET - 1,
                16 * 6 + 13,
                16 * 6 + 13,
                ButtonToolTips.SuperWirelessKitMadChameleonRecolorName.getLocal(),
                ButtonToolTips.SuperWirelessKitMadChameleonRecolorDesc.getLocal());
        this.buttonList.add(this.madChameleonButton);

        for (int i = 0; i < 10; i++) {
            this.pinButtons[i] = new GuiCheckBox(
                    0.25D,
                    this.guiLeft + 232,
                    this.guiTop + (i * 23) + TOP_OFFSET + 6,
                    16 * 7 + 13,
                    16 * 6 + 14,
                    ButtonToolTips.SuperWirelessKitPinButtonName.getLocal(),
                    ButtonToolTips.SuperWirelessKitPinButtonDesc.getLocal());
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
                    ButtonToolTips.SuperWirelessKitIncludeHubsName.getLocal(),
                    ButtonToolTips.SuperWirelessKitIncludeHubsDesc.getLocal());
            this.includeConnectorsButtons[i] = new GuiCheckBox(
                    0.25D,
                    x - 5,
                    y,
                    ButtonToolTips.SuperWirelessKitIncludeConnectorsName.getLocal(),
                    ButtonToolTips.SuperWirelessKitIncludeConnectorsDesc.getLocal());
            this.deleteButtons[i] = new GuiCheckBox(
                    0.25D,
                    x - 55,
                    y - 17,
                    ButtonToolTips.SuperWirelessKitDeleteName.getLocal(),
                    ButtonToolTips.SuperWirelessKitDeleteDesc.getLocal());
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
                ButtonToolTips.SuperWirelessKitBindName.getLocal(),
                ButtonToolTips.SuperWirelessKitBindDesc.getLocal());

        this.unbind = new GuiAeButton(
                0,
                this.guiLeft + 81,
                this.guiTop + 4,
                44,
                16,
                ButtonToolTips.SuperWirelessKitUnbindName.getLocal(),
                ButtonToolTips.SuperWirelessKitUnbindDesc.getLocal());

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
        for (BaseUnit bs : unselected) {
            if (bs.keyTyped(typedChar, keyCode)) return;
        }

        for (BaseUnit bs : toBind) {
            if (bs.keyTyped(typedChar, keyCode)) return;
        }

        for (BaseUnit bs : target) {
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
            sendCommand(SuperWirelessKitCommands.BIND, null);
        } else if (btn == this.unbind) {
            sendCommand(SuperWirelessKitCommands.UNBIND, null);
        }

        if (isAEStaffLoaded && btn == ae2stuffConvert) {
            sendCommand(SuperWirelessKitCommands.AE2STUFF_REPLACE, null);
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

        for (BaseUnit bs : unselected) {
            bs.mouseClicked(xCoord, yCoord, btn);
        }

        for (BaseUnit bs : toBind) {
            bs.mouseClicked(xCoord, yCoord, btn);
        }

        for (BaseUnit bs : target) {
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
        drawUnselected(mouseX, mouseY);
        drawToBind(mouseX, mouseY);
        drawTarget(mouseX, mouseY);
    }

    private void reset() {
        for (int i = 0; i < 30; i++) {
            this.includeConnectorsButtons[i].visible = false;
            this.includeHubsButtons[i].visible = false;
            this.deleteButtons[i].visible = false;
            if (i < 10) this.pinButtons[i].visible = false;
        }

        for (BaseUnit bu : this.unselected) {
            bu.isVisible = false;
        }

        for (BaseUnit bu : this.toBind) {
            bu.isVisible = false;
        }

        for (BaseUnit bu : this.target) {
            bu.isVisible = false;
        }
    }

    private void drawUnselected(final int mouseX, final int mouseY) {
        final int viewStart = this.unselectedColumnScroll.getCurrentScroll();
        final int viewEnd = viewStart + 10;

        int y = 0;

        for (int z = viewStart; z < Math.min(viewEnd, unselected.size()); z++) {
            unselected.get(z).setXY(5, y, 0);
            unselected.get(z).draw(mouseX, mouseY);

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

    private void drawToBind(final int mouseX, final int mouseY) {
        final int viewStart = this.toBindColumnScroll.getCurrentScroll();
        final int viewEnd = viewStart + 10;

        int y = 0;

        for (int z = viewStart; z < Math.min(viewEnd, toBind.size()); z++) {
            toBind.get(z).setXY(100, y, 1);
            toBind.get(z).draw(mouseX, mouseY);

            y++;
        }

        if (toRemoveFromToBind != null) {
            toBind.remove(toRemoveFromToBind);
            toRemoveFromToBind = null;
            setScrollBar();
        }

        if (toAddToBind != null) {
            toBind.add(toAddToBind);
            toAddToBind = null;
            setScrollBar();
        }
    }

    private void drawTarget(final int mouseX, final int mouseY) {
        final int viewStart = this.targetColumnScroll.getCurrentScroll();
        final int viewEnd = viewStart + 10;

        int y = 0;

        for (int z = viewStart; z < Math.min(viewEnd, target.size()); z++) {
            target.get(z).setXY(177, y, 2);
            target.get(z).draw(mouseX, mouseY);

            y++;
        }

        if (toRemoveFromTarget != null) {
            target.remove(toRemoveFromTarget);
            toRemoveFromTarget = null;
            setScrollBar();
        }

        if (toAddTarget != null) {
            target.add(toAddTarget);
            toAddTarget = null;
            setScrollBar();
        }
    }

    private class singleUnit extends BaseUnit {

        singleUnit(SuperWirelessToolDataObject data, String customNetworkName, String customColorName) {
            super(data, customNetworkName, customColorName);
        }

        @Override
        public void draw(final int mouseX, final int mouseY) {
            final int posY = yo * offY + TOP_OFFSET;
            final int descPosX = xo * 3 + 46;

            GL11.glPushMatrix();
            GL11.glScaled(0.7666, 0.7666, 0.7666);
            drawItem(
                    (int) Math.round(xo / 0.7666) + 1,
                    yo * (int) Math.round(offY / 0.7666) + TOP_OFFSET + 9,
                    icons.get(data.isHub ? data.color.ordinal() + 17 : data.color.ordinal()));
            GL11.glPopMatrix();

            drawTextBox(mouseX, mouseY);

            drawNetworkName(descPosX, posY);

            GL11.glPushMatrix();
            GL11.glScaled(0.333, 0.333, 0.333);

            fontRendererObj.drawString(
                    GuiText.GuiSuperWirelessKitColor.getLocal(this.customColorName),
                    descPosX,
                    posY * 3 + 28,
                    GuiColors.CraftingCPUStored.getColor());
            fontRendererObj.drawString(
                    GuiText.GuiSuperWirelessKitSelfPos.getLocal(data.cord.getGuiTextShort()),
                    descPosX,
                    posY * 3 + 38,
                    GuiColors.CraftingCPUStored.getColor());
            if (data.isConnected) fontRendererObj.drawString(
                    GuiText.GuiSuperWirelessKitTargetPos.getLocal(data.targets.get(0).getGuiTextShort()),
                    descPosX,
                    posY * 3 + 48,
                    GuiColors.CraftingCPUStored.getColor());
            GL11.glPopMatrix();

            int indicatorColor = GuiColors.SuperWirelessKitGood.getColor();
            String str;
            if (data.isHub) {
                if (data.slots == 0) {
                    indicatorColor = GuiColors.SuperWirelessKitBad.getColor();
                } else if (data.slots < 32) {
                    indicatorColor = GuiColors.SuperWirelessKitNeutral.getColor();
                }
                str = 32 - data.slots + "/32" + " | " + data.channels;
            } else {
                if (data.slots == 0) {
                    indicatorColor = GuiColors.SuperWirelessKitBad.getColor();
                }
                str = 1 - data.slots + "/1" + " | " + data.channels;
            }
            drawSlotsIndicator(str, posY, indicatorColor);
            super.draw(mouseX, mouseY);
        }

        @Override
        protected void renameCommand() {
            sendCommand(SuperWirelessKitCommands.RENAME_SINGLE, this);
        }
    }

    private class GroupUnit extends BaseUnit {

        private final ArrayList<SuperWirelessToolDataObject> wsList = new ArrayList<>();
        private final ArrayList<DimensionalCoord> cordList = new ArrayList<>();
        private boolean includeHubs;
        private boolean includeConnectors;
        private int channels;
        private int slots;
        private int usedSlots;

        GroupUnit(SuperWirelessToolDataObject data, String customNetworkName) {
            this(data, customNetworkName, false, true, true, false);
        }

        GroupUnit(SuperWirelessToolDataObject data, String customNetworkName, boolean byColor, String customColorName) {
            this(data, customNetworkName, customColorName, byColor, true, true, false);
        }

        GroupUnit(SuperWirelessToolDataObject data, String customNetworkName, boolean byColor, String customColorName,
                boolean includeConnectors, boolean includeHubs, boolean isPinned) {
            this(data, customNetworkName, customColorName, byColor, includeConnectors, includeHubs, isPinned);
        }

        GroupUnit(SuperWirelessToolDataObject data, String customNetworkName, boolean byColor,
                boolean includeConnectors, boolean includeHubs, boolean isPinned) {
            this(data, customNetworkName, "", byColor, includeConnectors, includeHubs, isPinned);
        }

        GroupUnit(SuperWirelessToolDataObject data, String customNetworkName, String customColorName, boolean byColor,
                boolean includeConnectors, boolean includeHubs, boolean isPinned) {
            super(data, customNetworkName, customColorName);
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
        protected String getTitleName() {
            if (this.byColor) return this.customColorName;
            return this.customNetworkName;
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
            sendCommand(SuperWirelessKitCommands.RENAME_GROUP, this);
        }

        @Override
        public void draw(final int mouseX, final int mouseY) {
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

            drawTextBox(mouseX, mouseY);

            drawNetworkName(descPosX, posY);

            GL11.glPushMatrix();
            GL11.glScaled(0.333, 0.333, 0.333);
            if (byColor) {
                fontRendererObj.drawString(
                        GuiText.GuiSuperWirelessKitColor.getLocal(this.customColorName),
                        descPosX,
                        posY * 3 + 28,
                        GuiColors.CraftingCPUStored.getColor());
            }
            fontRendererObj.drawString(
                    GuiText.GuiSuperWirelessKitChannelsUsage.getLocal(channels),
                    descPosX,
                    posY * 3 + 38,
                    GuiColors.CraftingCPUStored.getColor());
            GL11.glPopMatrix();

            int indicatorColor = GuiColors.SuperWirelessKitGood.getColor();
            String str = usedSlots + "/" + slots;

            if (usedSlots > 0) {
                if (slots == usedSlots) {
                    indicatorColor = GuiColors.SuperWirelessKitBad.getColor();
                } else {
                    indicatorColor = GuiColors.SuperWirelessKitNeutral.getColor();
                }
            }

            drawSlotsIndicator(str, posY, indicatorColor);

            super.draw(mouseX, mouseY);
        }

        @Override
        public void mouseClicked(int xPos, int yPos, int button) {
            if (this.isMouseIn(xPos, yPos)) {
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                    BlockPosHighlighter.highlightBlocks(
                            mc.thePlayer,
                            cordList,
                            PlayerMessages.InterfaceHighlighted.getUnlocalized(),
                            PlayerMessages.InterfaceInOtherDim.getUnlocalized());
                    mc.thePlayer.closeScreen();
                } else {
                    if (includeConnectorsButtons[totalPos].mousePressed(null, xPos, yPos)) {
                        this.includeConnectors ^= true;
                        if (!this.includeConnectors && !this.includeHubs) {
                            this.includeHubs = true;
                        }
                        reCalc();
                        if (isPinned) {
                            sendCommand(SuperWirelessKitCommands.PIN, this);
                        }
                    } else if (includeHubsButtons[totalPos].mousePressed(null, xPos, yPos)) {
                        this.includeHubs ^= true;
                        if (!this.includeConnectors && !this.includeHubs) {
                            this.includeConnectors = true;
                        }
                        reCalc();
                        if (isPinned) {
                            sendCommand(SuperWirelessKitCommands.PIN, this);
                        }
                    } else if (deleteButtons[totalPos].mousePressed(null, xPos, yPos)) {
                        sendCommand(SuperWirelessKitCommands.DELETE, this);
                    } else super.mouseClicked(xPos, yPos, button);
                }
            }
        }
    }

    private class BaseUnit {

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
        protected String customNetworkName;
        protected String customColorName;
        protected boolean isVisible = false;
        protected boolean byColor = false;

        BaseUnit(SuperWirelessToolDataObject data, String customNetworkName, String customColorName) {
            this.data = data;
            this.customNetworkName = !Objects.equals(customNetworkName, "") ? customNetworkName
                    : data.network.x + ", "
                            + data.network.y
                            + ", "
                            + data.network.z
                            + ", "
                            + data.network.getDimension();
            this.customColorName = !Objects.equals(customColorName, "") ? customColorName : data.color.toString();
        }

        public void setXY(int x, int y, int column) {
            this.x = guiLeft + x;
            this.y = guiTop + (y * (h + 1)) + TOP_OFFSET;
            this.xo = x;
            this.yo = y;
            this.totalPos = y + column * 10;
            this.isVisible = true;
        }

        protected String getTitleName() {
            return this.data.customName;
        }

        protected void handleButtons() {
            if (totalPos > 19 && data.isHub) {
                pinButtons[yo].visible = true;
                pinButtons[yo].setState(this.isPinned);
            }
        }

        public void draw(final int mouseX, final int mouseY) {
            handleButtons();
        }

        public void drawTextBox(final int mouseX, final int mouseY) {
            GL11.glPushMatrix();
            GL11.glScaled(0.5, 0.5, 0.5);
            nameField[totalPos].drawTextBox();
            if (nameField[totalPos].isVisible()
                    && nameField[totalPos].isMouseIn((mouseX - guiLeft) * 2, (mouseY - guiTop) * 2)) {
                drawTooltip(mouseX + 11, Math.max(mouseY, 15) + 4, nameField[totalPos].getMessage());
            }

            if (!nameField[totalPos].isFocused()) nameField[totalPos].setText(this.getTitleName());

            GL11.glPopMatrix();
        }

        public void drawNetworkName(int descPosX, int posY) {
            GL11.glPushMatrix();
            GL11.glScaled(0.333, 0.333, 0.333);
            fontRendererObj.drawString(
                    GuiText.GuiSuperWirelessKitNetwork.getLocal(this.customNetworkName),
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
            } else if (isFocused && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)) {
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
                            data.targets.isEmpty() ? Collections.singletonList(data.cord)
                                    : new ArrayList<>(Arrays.asList(data.cord, data.targets.get(0))),
                            PlayerMessages.MachineHighlighted.getUnlocalized(),
                            PlayerMessages.MachineInOtherDim.getUnlocalized());
                    mc.thePlayer.closeScreen();
                    return;
                }

                nameField[totalPos].mouseClicked(xPos, yPos, button);
                if (nameField[totalPos].isMouseIn((xPos - guiLeft) * 2, (yPos - guiTop) * 2)) {
                    // no
                } else if (pinButtons[yo].mousePressed(null, xPos, yPos)) {
                    isPinned ^= true;
                    sendCommand(SuperWirelessKitCommands.PIN, this);
                } else if (isPinned) {
                    // no
                } else if (this.inToBind) {
                    toRemoveFromToBind = this;
                    this.inToBind = false;
                    if (button == 0) {
                        toAddToUnselected = this;
                    } else if (button == 1) {
                        toAddTarget = this;
                        this.inTarget = true;
                    }
                } else if (this.inTarget) {
                    toRemoveFromTarget = this;
                    this.inTarget = false;
                    if (button == 0) {
                        toAddToBind = this;
                        this.inToBind = true;
                    } else if (button == 1) {
                        toAddToUnselected = this;
                    }
                } else {
                    if (button == 0) {
                        toRemoveFromUnselected = this;
                        toAddToBind = this;
                        this.inToBind = true;
                    } else if (button == 1) {
                        toRemoveFromUnselected = this;
                        toAddTarget = this;
                        this.inTarget = true;
                    }
                }
            } else nameField[totalPos].mouseClicked(xPos, yPos, button);
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
        this.setData(null, null);
    }

    private void reColorCommand(AEColor color) {
        final SuperWirelessKitCommand command = new SuperWirelessKitCommand(SuperWirelessKitCommands.RECOLOR);
        this.toBind.forEach(bu -> command.addToBind(this.getSubCommand(bu)));
        command.setColor(color);
        try {
            NetworkHandler.instance.sendToServer(new PacketSuperWirelessToolCommand(command));
        } catch (IOException ignored) {}
    }

    private void sendCommand(SuperWirelessKitCommands commandType, BaseUnit bu) {
        final SuperWirelessKitCommand command = new SuperWirelessKitCommand(commandType);

        switch (commandType) {
            case RENAME_SINGLE, RENAME_GROUP -> {
                command.setName(this.nameField[bu.totalPos].getText());
                command.setCommand(this.getSubCommand(bu));
            }
            case PIN -> command.setPinCommand(getSubCommand(bu));
            case DELETE -> command.setNetworkPos(bu.data.network);
            case BIND -> {
                this.toBind.forEach(butb -> command.addToBind(getSubCommand(butb)));
                this.target.forEach(but -> command.addTarget(getSubCommand(but)));
            }
            case UNBIND -> this.toBind.forEach(butb -> command.addToBind(getSubCommand(butb)));
            default -> {}
        }
        try {
            NetworkHandler.instance.sendToServer(new PacketSuperWirelessToolCommand(command));
        } catch (IOException ignored) {}
    }

    @Nonnull
    private SubCommand getSubCommand(BaseUnit butb) {
        final SubCommand subCommand = new SubCommand();
        if (butb instanceof GroupUnit gu) {
            subCommand.setNetworkPos(gu.data.network);
            if (gu.includeConnectors) subCommand.includeConnectors();
            if (gu.includeHubs) subCommand.includeHubs();
            if (gu.byColor) {
                subCommand.setGroupBy(PinType.COLOR);
            } else {
                subCommand.setGroupBy(PinType.NETWORK);
            }

            subCommand.setColor(gu.data.color);
        } else {
            subCommand.setGroupBy(PinType.SINGLE);
            subCommand.setCoord(butb.data.cord);
        }
        return subCommand;
    }

    private static class SavedName {

        final DimensionalCoord network;
        final String name;
        final AEColor color;
        final boolean byColor;
        final String colorName;

        SavedName(DimensionalCoord network, String name, AEColor color, boolean byColor, String colorName) {
            this.network = network;
            this.name = name;
            this.color = color;
            this.byColor = byColor;
            this.colorName = colorName;
        }
    }

    private static class SavedPin {

        final DimensionalCoord network;
        final AEColor color;
        final PinType type;
        final DimensionalCoord coord;
        final boolean includeConnectors;
        final boolean includeHubs;

        SavedPin(DimensionalCoord network, AEColor color, PinType type, DimensionalCoord coord,
                boolean includeConnectors, boolean includeHubs) {
            this.network = network;
            this.color = color;
            this.type = type;
            this.coord = coord;
            this.includeConnectors = includeConnectors;
            this.includeHubs = includeHubs;
        }
    }

    private GroupUnit getNetworkUnitFormResolver(ArrayList<GroupUnit> list, DimensionalCoord network, AEColor color,
            boolean isPin) {
        for (GroupUnit gu : list) {
            if (gu.data.network.equals(network) && gu.isPinned == isPin) {
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

    public void setData(final NBTTagCompound nData, final ArrayList<SuperWirelessToolDataObject> wData) {
        if (nData != null && wData != null) {
            this.nData = nData;
            this.wData = wData;
        }

        if (this.nData == null || this.wData == null) return;

        this.unselected.clear();
        this.toBind.clear();
        this.target.clear();

        ArrayList<GroupUnit> networkGroupResolver = new ArrayList<>();

        final NBTTagList names = this.nData.getTagList("names", 10);
        final ArrayList<SavedName> savedNames = new ArrayList<>();

        for (int i = 0; i < names.tagCount(); i++) {
            final NBTTagCompound tag = names.getCompoundTagAt(i);
            savedNames.add(
                    new SavedName(
                            DimensionalCoord.readFromNBT(tag.getCompoundTag("network")),
                            tag.getString("networkName"),
                            AEColor.values()[tag.getInteger("color")],
                            tag.hasKey("color"),
                            tag.getString("colorName")));
        }

        final NBTTagList pins = this.nData.getTagList("pins", 10);
        final ArrayList<SavedPin> savedPins = new ArrayList<>();

        for (int i = 0; i < pins.tagCount(); i++) {
            final NBTTagCompound tag = pins.getCompoundTagAt(i);
            savedPins.add(
                    new SavedPin(
                            DimensionalCoord.readFromNBT(tag.getCompoundTag("network")),
                            AEColor.values()[tag.getInteger("color")],
                            PinType.values()[tag.getInteger("type")],
                            DimensionalCoord.readFromNBT(tag.getCompoundTag("coord")),
                            !tag.hasKey("incCon"),
                            !tag.hasKey("incHub")));
        }

        for (final SuperWirelessToolDataObject wdo : this.wData) {
            boolean isPinned = false;
            boolean isGroup = false;
            boolean isSameColor = false;
            boolean includeConnectors = true;
            boolean includeHubs = true;
            String networkName = "";
            String colorName = "";

            if (hideBounded == YesNo.YES && wdo.isConnected && !(wdo.isHub && wdo.slots != 0)) continue;

            for (final SavedName savedName : savedNames) {
                if (savedName.network.equals(wdo.network)) {
                    if (savedName.byColor) {
                        if (wdo.color == savedName.color) {
                            colorName = savedName.colorName;
                        }
                    } else {
                        networkName = savedName.name;
                    }
                }
            }

            for (final SavedPin savedPin : savedPins) {
                switch (savedPin.type) {
                    case SINGLE -> {
                        if (savedPin.coord.equals(wdo.cord)) {
                            isPinned = true;
                        }
                    }

                    case NETWORK, COLOR -> {
                        if (savedPin.network.equals(wdo.network)) {
                            if (savedPin.type == PinType.COLOR) {
                                if (savedPin.color == wdo.color) {
                                    isSameColor = true;
                                    isPinned = true;
                                    isGroup = true;
                                }
                            } else {
                                isPinned = true;
                                isGroup = true;
                            }
                        }
                    }
                }

                if (isPinned) {
                    includeConnectors = savedPin.includeConnectors;
                    includeHubs = savedPin.includeHubs;
                    break;
                }
            }

            if (isPinned) {
                if (isGroup) {
                    if (isSameColor) {
                        GroupUnit gu = getNetworkUnitFormResolver(networkGroupResolver, wdo.network, wdo.color, true);
                        if (gu == null) {
                            GroupUnit newUnit = new GroupUnit(
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
                        GroupUnit gu = getNetworkUnitFormResolver(networkGroupResolver, wdo.network, null, true);
                        if (gu != null) {
                            gu.addToGroup(wdo);
                        } else {
                            GroupUnit newUnit = new GroupUnit(
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
                    this.target.add(new singleUnit(wdo, networkName, colorName));
                }
            } else {
                if (mode == SuperWirelessToolGroupBy.Single) {
                    this.unselected.add(new singleUnit(wdo, networkName, colorName));
                } else if (mode == SuperWirelessToolGroupBy.Network) {
                    GroupUnit gu = getNetworkUnitFormResolver(networkGroupResolver, wdo.network, null, false);
                    if (gu != null) {
                        gu.addToGroup(wdo);
                    } else {
                        GroupUnit newUnit = new GroupUnit(wdo, networkName);
                        networkGroupResolver.add(newUnit);
                        this.unselected.add(newUnit);
                    }
                } else {
                    GroupUnit gu = getNetworkUnitFormResolver(networkGroupResolver, wdo.network, wdo.color, false);
                    if (gu != null) {
                        gu.addToGroup(wdo);
                    } else {
                        GroupUnit newUnit = new GroupUnit(wdo, networkName, true, colorName);
                        networkGroupResolver.add(newUnit);
                        this.unselected.add(newUnit);
                    }
                }
            }
        }
        setScrollBar();
    }
}
