package appeng.client.gui.widgets;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;

import org.apache.commons.io.FileUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.AEBaseGui;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingRequest.UsedResolverEntry;
import appeng.crafting.v2.resolvers.CraftableItemResolver.CraftFromPatternTask;
import appeng.crafting.v2.resolvers.EmitableItemResolver.EmitItemTask;
import appeng.crafting.v2.resolvers.ExtractItemResolver.ExtractItemTask;
import appeng.crafting.v2.resolvers.IgnoreMissingItemResolver.IgnoreMissingItemTask;
import appeng.crafting.v2.resolvers.SimulateMissingItemResolver;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;

public class GuiCraftingTree {

    private final AEBaseGui parent;
    public int widgetX, widgetY, widgetW, widgetH;

    public float scrollX = -8, scrollY = -8;
    private CraftingRequest<?> request;

    // Y -> list of nodes sorted by X
    private TreeMap<Integer, ArrayList<Node>> treeNodes = new TreeMap<>();
    private int treeWidth, treeHeight;

    public static final int X_SPACING = 24;
    public static final int REQUEST_RESOLVER_Y_SPACING = 10;
    public static final int RESOLVER_CHILD_Y_SPACING = 12;
    public final int textColor = GuiColors.SearchboxText.getColor();
    private static long animationFrame = System.currentTimeMillis() / 500;

    private abstract class Node {

        public boolean visible = true;
        public int x, y;
        public final int width = 16, height = 16;
        public final Node parentNode;
        public final List<Node> childNodes = new ArrayList<>(1);

        public final void drawParentLine() {
            if (visible) {
                if (parentNode != null) {
                    drawTreeLine(x + 8, y - 3, parentNode.x + 8, parentNode.y + 18);
                }
            }
        }

        public final void draw() {
            if (visible) {
                drawImpl();
            }
        }

        public final void drawTooltip(int mouseX, int mouseY) {
            if (visible) {
                drawTooltipImpl(mouseX, mouseY);
            }
        }

        protected abstract void drawImpl();

        protected abstract void drawTooltipImpl(int mouseX, int mouseY);

        public Node(int x, int y, Node parentNode) {
            this.x = x;
            this.y = y;
            this.parentNode = parentNode;
        }
    }

    private class RequestNode extends Node {

        public CraftingRequest<?> request;
        private String tooltip = null;

        public RequestNode(int x, int y, Node parentNode, CraftingRequest<?> request) {
            super(x, y, parentNode);
            this.request = request;
        }

        @Override
        public void drawImpl() {
            drawSlotOutline(x, y, request.wasSimulated ? 0xCCAAAA : 0xAAAAAA, false);
            drawStack(x, y, getDisplayItemForRequest(request), true);
            if (request.wasSimulated) {
                parent.bindTexture("guis/states.png");
                GL11.glScalef(0.5f, 0.5f, 1.0f);
                drawIcon(2 * x + 16, 2 * y, 8 * 16);
                GL11.glScalef(2.0f, 2.0f, 1.0f);
            }
        }

        @Override
        protected void drawTooltipImpl(int mouseX, int mouseY) {
            if (tooltip == null) {
                tooltip = request.getTooltipText();
            }
            parent.drawTooltip(mouseX, mouseY, 0, tooltip);
        }
    }

    private class TaskNode extends Node {

        public UsedResolverEntry<?> resolver;
        private String tooltip;

        public TaskNode(int x, int y, Node parentNode, UsedResolverEntry<?> resolver) {
            super(x, y, parentNode);
            this.resolver = resolver;
        }

        @Override
        public void drawImpl() {
            parent.bindTexture("guis/states.png");
            drawSlotOutline(x, y, 0x777777, true);
            List<CraftingRequest<IAEItemStack>> children = null;
            long displayCount = resolver.resolvedStack.getStackSize();
            if (resolver.task instanceof ExtractItemTask) {
                final ExtractItemTask task = (ExtractItemTask) resolver.task;
                drawIcon(x, y, task.removedFromSystem.isEmpty() ? (1 * 16 + 2) : (1 * 16 + 1));
            } else if (resolver.task instanceof CraftFromPatternTask) {
                final CraftFromPatternTask task = (CraftFromPatternTask) resolver.task;
                IAEItemStack icon = task.craftingMachine;
                if (icon != null) {
                    drawStack(x, y, icon, false);
                    GL11.glScalef(0.5f, 0.5f, 1.0f);
                    drawIcon(x * 2 - 4, y * 2 - 4, 1 * 16 + 3);
                    GL11.glScalef(2.0f, 2.0f, 1.0f);
                } else {
                    drawIcon(x, y, 1 * 16 + 3);
                }
                children = task.getChildRequests();
                displayCount = task.getTotalCraftsDone();
            } else if (resolver.task instanceof EmitItemTask) {
                drawIcon(x, y, 1);
            } else if (resolver.task instanceof SimulateMissingItemResolver.ConjureItemTask) {
                drawIcon(x, y, 8 * 16);
            } else if (resolver.task instanceof IgnoreMissingItemTask) {
                drawIcon(x, y, 9 * 16 + 1);
            }
            drawSmallStackCount(x, y, displayCount, textColor);
        }

        @Override
        protected void drawTooltipImpl(int mouseX, int mouseY) {
            if (tooltip == null) {
                tooltip = resolver.task.getTooltipText();
            }
            parent.drawTooltip(mouseX, mouseY, 0, tooltip);
        }
    }

    public GuiCraftingTree(AEBaseGui parent, int widgetX, int widgetY, int widgetW, int widgetH) {
        this.parent = parent;
        this.widgetX = widgetX;
        this.widgetY = widgetY;
        this.widgetW = widgetW;
        this.widgetH = widgetH;
    }

    private <T extends IAEStack<T>> IAEStack<T> getDisplayItemForRequest(CraftingRequest<T> request) {
        if (request.usedResolvers.isEmpty()) {
            return request.stack;
        } else {
            return request.usedResolvers.get((int) (animationFrame % request.usedResolvers.size())).resolvedStack.copy()
                    .setStackSize(request.stack.getStackSize());
        }
    }

    private abstract class NodeBuilderTask {

        public abstract void step(List<NodeBuilderTask> stack);
    }

    private class NodeBuilderRequestWalker extends NodeBuilderTask {

        public final int x, y;
        public final CraftingRequest<?> request;
        private int currentChild = 0;
        private RequestNode myNode;
        private Node parentNode;

        private NodeBuilderRequestWalker(int x, int y, Node parentNode, CraftingRequest<?> request) {
            this.x = x;
            this.y = y;
            this.parentNode = parentNode;
            this.request = request;
        }

        @Override
        public void step(List<NodeBuilderTask> stack) {
            if (myNode == null) {
                myNode = new RequestNode(x, y, parentNode, request);
                treeNodes.computeIfAbsent(y, ignored -> new ArrayList<>()).add(myNode);
            }
            if (currentChild >= request.usedResolvers.size()) {
                stack.remove(stack.size() - 1);
            } else {
                if (currentChild > 0) {
                    treeWidth += X_SPACING;
                }
                UsedResolverEntry<?> resolver = request.usedResolvers.get(currentChild);
                if (resolver != null && resolver.resolvedStack != null) {
                    stack.add(
                            new NodeBuilderTaskWalker(
                                    treeWidth,
                                    y + 16 + REQUEST_RESOLVER_Y_SPACING,
                                    myNode,
                                    resolver));
                }
                currentChild++;
            }
        }
    }

    private class NodeBuilderTaskWalker extends NodeBuilderTask {

        public final int x, y;
        public final UsedResolverEntry<?> resolver;
        private int currentChild = 0;
        private List<CraftingRequest<IAEItemStack>> children = null;
        private TaskNode myNode;
        private Node parentNode;

        private NodeBuilderTaskWalker(int x, int y, Node parentNode, UsedResolverEntry<?> resolver) {
            this.x = x;
            this.y = y;
            this.resolver = resolver;
            this.parentNode = parentNode;
        }

        @Override
        public void step(List<NodeBuilderTask> stack) {
            if (myNode == null) {
                myNode = new TaskNode(x, y, parentNode, resolver);
                if (resolver.task instanceof CraftFromPatternTask) {
                    children = ((CraftFromPatternTask) resolver.task).getChildRequests();
                } else {
                    children = Collections.emptyList();
                }
                treeNodes.computeIfAbsent(y, ignored -> new ArrayList<>()).add(myNode);
            }
            if (currentChild >= children.size()) {
                stack.remove(stack.size() - 1);
            } else {
                if (currentChild > 0) {
                    treeWidth += X_SPACING;
                }
                stack.add(
                        new NodeBuilderRequestWalker(
                                treeWidth,
                                y + 16 + RESOLVER_CHILD_Y_SPACING,
                                myNode,
                                children.get(currentChild)));
                currentChild++;
            }
        }
    }

    public void setRequest(final CraftingRequest<?> request) {
        final boolean isDifferent = (request != this.request);
        this.request = request;
        if (isDifferent) {
            this.treeNodes.clear();
            this.treeWidth = 0;
            this.treeHeight = 0;
            final ArrayList<NodeBuilderTask> tasks = new ArrayList<>();
            tasks.add(new NodeBuilderRequestWalker(0, 0, null, request));
            while (!tasks.isEmpty()) {
                tasks.get(tasks.size() - 1).step(tasks);
            }
            for (ArrayList<Node> row : treeNodes.values()) {
                for (Node node : row) {
                    if (node.parentNode != null) {
                        node.parentNode.childNodes.add(node);
                    }
                    treeWidth = Math.max(treeWidth, node.x + node.width);
                    treeHeight = Math.max(treeHeight, node.y + node.height);
                }
            }
        }
    }

    private float zoomLevel = 1.0f;
    private float lastDragX = Float.NEGATIVE_INFINITY;
    private float lastDragY = Float.NEGATIVE_INFINITY;
    private boolean wasLmbPressed;

    private Node tooltipNode;

    public void onMouseWheel(int mouseX, int mouseY, int wheel) {
        mouseX -= widgetX;
        mouseY -= widgetY;
        final float oldZoom = zoomLevel;
        zoomLevel *= (float) Math.pow(1.4, wheel);
        zoomLevel = MathHelper.clamp_float(zoomLevel, 0.1f, 8.0f);
        // Zoom into the mouse cursor position, instead of top-left corner
        scrollX += mouseX / oldZoom - mouseX / zoomLevel;
        scrollY += mouseY / oldZoom - mouseY / zoomLevel;
    }

    public void draw(int guiMouseX, int guiMouseY) {
        draw(guiMouseX, guiMouseY, false);
    }

    public void draw(int guiMouseX, int guiMouseY, boolean inScreenshotMode) {
        if (request == null) {
            return;
        }
        animationFrame = System.currentTimeMillis() / 500;

        // Drag'n'drop handling here, because mouse movement events don't get fired often enough
        final float mouseX = (float) Mouse.getX() * (float) parent.width / (float) parent.mc.displayWidth;
        final float mouseY = parent.height
                - (float) Mouse.getY() * (float) parent.height / (float) parent.mc.displayHeight
                - 1;
        final boolean lmbPressed = Mouse.isButtonDown(0);
        if (lmbPressed && !wasLmbPressed) {
            if (isPointInWidget(guiMouseX - parent.getGuiLeft(), guiMouseY - parent.getGuiTop())) {
                lastDragX = mouseX;
                lastDragY = mouseY;
            }
        } else if (!lmbPressed) {
            lastDragX = Float.NEGATIVE_INFINITY;
            lastDragY = Float.NEGATIVE_INFINITY;
        } else if (lmbPressed && lastDragX != Float.NEGATIVE_INFINITY) {
            scrollX -= 1.0f / zoomLevel * (mouseX - lastDragX);
            scrollY -= 1.0f / zoomLevel * (mouseY - lastDragY);
            lastDragX = mouseX;
            lastDragY = mouseY;
        }
        scrollX = MathHelper.clamp_float(scrollX, -widgetW / zoomLevel + 4, treeWidth - 4);
        scrollY = MathHelper.clamp_float(scrollY, -widgetH / zoomLevel + 4, treeHeight - 4);
        wasLmbPressed = lmbPressed;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        final float guiScaleFactor = inScreenshotMode ? 1.0f : ((float) parent.mc.displayWidth / (float) parent.width);
        final float zoomLevel = inScreenshotMode ? 1.0f : this.zoomLevel;
        final float scrollX = inScreenshotMode ? -8.0f : this.scrollX;
        final float scrollY = inScreenshotMode ? -8.0f : this.scrollY;
        final int widgetBottomY = parent.getYSize() - widgetY - widgetH;

        final float zScrollX = scrollX * zoomLevel;
        final float zScrollY = scrollY * zoomLevel;
        final int cropYMin = (int) ((zScrollY - 32) / zoomLevel) - 16;
        final int cropYMax = (int) ((zScrollY + 32) / zoomLevel) + (int) (widgetH / zoomLevel) + 16;
        final int cropXMin = (int) ((zScrollX - 32) / zoomLevel);
        final int cropXMax = (int) ((zScrollX + 32) / zoomLevel) + (int) (widgetW / zoomLevel);

        // Scissor rect 0,0 is in the bottom left corner of the screen
        if (!inScreenshotMode) {
            GL11.glScissor(
                    (int) ((parent.getGuiLeft() + widgetX) * guiScaleFactor),
                    (int) ((parent.getGuiTop() + widgetBottomY) * guiScaleFactor),
                    (int) (widgetW * guiScaleFactor),
                    (int) (widgetH * guiScaleFactor));
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
        GL11.glPushMatrix();

        // Round to the nearest real pixel
        if (!inScreenshotMode) {
            GL11.glTranslatef(widgetX - zScrollX, widgetY - zScrollY, 0.0f);
            GL11.glScalef(zoomLevel, zoomLevel, 1.0f);
        }

        SortedMap<Integer, ArrayList<Node>> rows = inScreenshotMode ? treeNodes : treeNodes.subMap(cropYMin, cropYMax);
        tooltipNode = null;
        for (Entry<Integer, ArrayList<Node>> row : rows.entrySet()) {
            for (Node node : row.getValue()) {
                if (!node.visible) {
                    continue;
                }
                if (inScreenshotMode || node.x > cropXMin) {
                    if (!inScreenshotMode && node.x > cropXMax) {
                        if (node.parentNode != null && node.parentNode.x < cropXMax) {
                            node.drawParentLine();
                        }
                        break;
                    }
                    node.draw();
                    node.drawParentLine();
                    final int widgetLeft = parent.getGuiLeft() + widgetX;
                    final int nodeX = widgetLeft + (int) (node.x * zoomLevel) - (int) zScrollX;
                    final int widgetTop = parent.getGuiTop() + widgetY;
                    final int nodeY = widgetTop + (int) (node.y * zoomLevel) - (int) zScrollY;
                    if (!inScreenshotMode && guiMouseX >= nodeX
                            && guiMouseY >= nodeY
                            && guiMouseX <= (nodeX + (int) (node.width * zoomLevel))
                            && guiMouseY <= (nodeY + (int) (node.height * zoomLevel))
                            && guiMouseX >= widgetLeft
                            && guiMouseX <= (widgetLeft + widgetW)
                            && guiMouseY >= widgetTop
                            && guiMouseY <= (widgetTop + widgetH)) {
                        tooltipNode = node;
                    }
                }
            }
        }
        GL11.glPopMatrix();
        if (!inScreenshotMode) {
            float scrollXPct = MathHelper.clamp_float(scrollX / treeWidth, 0.0f, 1.0f);
            float scrollYPct = MathHelper.clamp_float(scrollY / treeHeight, 0.0f, 1.0f);
            parent.drawHorizontalLine(
                    (int) (widgetX + scrollXPct * (widgetW - 24)),
                    (int) (widgetX + scrollXPct * (widgetW - 24)) + 24,
                    widgetY + widgetH - 2,
                    0xFFDDDDDD);
            parent.drawVerticalLine(
                    widgetX + widgetW - 2,
                    (int) (widgetY + scrollYPct * (widgetH - 24)),
                    (int) (widgetY + scrollYPct * (widgetH - 24)) + 24,
                    0xFFDDDDDD);
        }
        GL11.glPopAttrib();
    }

    private static final DateTimeFormatter SCREENSHOT_DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    public void saveScreenshot() {
        if (this.request == null) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        if (!OpenGlHelper.isFramebufferEnabled()) {
            AELog.error("Could not save crafting tree screenshot: FBOs disabled/unsupported");
            mc.ingameGUI.getChatGUI()
                    .printChatMessage(new ChatComponentTranslation("chat.appliedenergistics2.FBOUnsupported"));
            return;
        }
        try {
            final int screenshotZoom = 2;
            final File screenshotsDir = new File(mc.mcDataDir, "screenshots");
            FileUtils.forceMkdir(screenshotsDir);
            final int maxGlTexSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) / 2; // Divide by 2 to be safe
            int imgWidth = screenshotZoom * (treeWidth + 32);
            int imgHeight = screenshotZoom * (treeHeight + 32);
            // Make sure the image can be actually allocated, worst case it'll be cropped
            while ((long) imgWidth * (long) imgHeight >= (long) Integer.MAX_VALUE / 4) {
                if (imgWidth > imgHeight) {
                    imgWidth /= 2;
                } else {
                    imgHeight /= 2;
                }
            }
            final int xChunks = (int) Platform.ceilDiv(imgWidth, maxGlTexSize);
            final int yChunks = (int) Platform.ceilDiv(imgHeight, maxGlTexSize);
            final int fbWidth = Math.min(imgWidth, maxGlTexSize);
            final int fbHeight = Math.min(imgHeight, maxGlTexSize);
            final BufferedImage outputImg = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
            final IntBuffer downloadBuffer = BufferUtils.createIntBuffer(fbWidth * fbHeight);
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            final Framebuffer fb = new Framebuffer(fbWidth, fbHeight, true);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, fbWidth / (float) screenshotZoom, fbHeight / (float) screenshotZoom, 0, 1000, 3000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            try {
                fb.bindFramebuffer(true);
                for (int xChunk = 0; xChunk < xChunks; xChunk++) {
                    final int xStart = xChunk * maxGlTexSize;
                    final int xEnd = Math.min((xChunk + 1) * maxGlTexSize, imgWidth);
                    final int xChunkSize = xEnd - xStart;
                    for (int yChunk = 0; yChunk < yChunks; yChunk++) {
                        final int yStart = yChunk * maxGlTexSize;
                        final int yEnd = Math.min((yChunk + 1) * maxGlTexSize, imgHeight);
                        final int yChunkSize = yEnd - yStart;

                        GL11.glPushMatrix();
                        GL11.glTranslatef(
                                8 - xStart / (float) screenshotZoom,
                                8 - yStart / (float) screenshotZoom,
                                -2000.0f);
                        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                        draw(-1000, -1000, true);
                        GL11.glPopMatrix();

                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fb.framebufferTexture);
                        GL11.glGetTexImage(
                                GL11.GL_TEXTURE_2D,
                                0,
                                GL12.GL_BGRA,
                                GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                                downloadBuffer);

                        for (int y = 0; y < yChunkSize; y++) {
                            for (int x = 0; x < xChunkSize; x++) {
                                outputImg.setRGB(
                                        x + xStart,
                                        y + yStart,
                                        downloadBuffer.get((fbHeight - 1 - y) * fbWidth + x));
                            }
                        }
                    }
                }
            } finally {
                fb.deleteFramebuffer();
                GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
            }
            GL11.glPopAttrib();
            GL11.glPopMatrix();

            final String date = SCREENSHOT_DATE_FORMAT.format(LocalDateTime.now());
            String filename = String.format("%s-ae2.png", date);
            File outFile = new File(screenshotsDir, filename);
            for (int i = 1; outFile.exists() && i < 99; i++) {
                filename = String.format("%s-ae2-%d.png", date, i);
                outFile = new File(screenshotsDir, filename);
            }
            if (outFile.exists()) {
                throw new FileAlreadyExistsException(filename);
            }
            ImageIO.write(outputImg, "png", outFile);

            AELog.info("Saved crafting tree screenshot to %s", filename);
            ChatComponentText chatLink = new ChatComponentText(filename);
            chatLink.getChatStyle()
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, outFile.getAbsolutePath()));
            chatLink.getChatStyle().setUnderlined(Boolean.valueOf(true));
            mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentTranslation("screenshot.success", chatLink));
        } catch (Exception e) {
            AELog.warn(e, "Could not save crafting tree screenshot");
            mc.ingameGUI.getChatGUI()
                    .printChatMessage(new ChatComponentTranslation("screenshot.failure", e.getMessage()));
        }
    }

    public void drawTooltip(int guiMouseX, int guiMouseY) {
        if (tooltipNode != null) {
            tooltipNode.drawTooltip(guiMouseX, guiMouseY);
        }
    }

    private void drawTreeLine(final int x0, final int y0, final int x1, final int y1) {
        final int color = 0xFFDDDDDD;
        if (x0 != x1) {
            final int midY = (y0 + y1) / 2;
            parent.drawVerticalLine(x0, y0, midY, color);
            parent.drawHorizontalLine(x0, x1, midY, color);
            parent.drawVerticalLine(x1, midY, y1, color);
        } else {
            parent.drawVerticalLine(x0, y0, y1, color);
        }
    }

    private void drawIcon(final int x, final int y, final int iconIndex) {
        parent.bindTexture("guis/states.png");
        final int uv_y = iconIndex / 16;
        final int uv_x = iconIndex - uv_y * 16;

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        parent.drawTexturedModalRect(x, y, uv_x * 16, uv_y * 16, 16, 16);
    }

    private void drawSlotOutline(final int x, final int y, final int rgb, final boolean isOperation) {
        parent.bindTexture("guis/states.png");
        GL11.glColor4f(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f, 1.0F);
        parent.drawTexturedModalRect(x - 3, y - 3, 192, isOperation ? 32 : 56, 24, 24);
    }

    private void drawStack(final int x, final int y, final IAEStack<?> stack, boolean drawCount) {
        final int textColor = GuiColors.SearchboxText.getColor();
        if (stack instanceof IAEItemStack) {
            parent.drawItem(x, y, ((IAEItemStack) stack).getItemStack());
        } else if (stack instanceof IAEFluidStack) {}
        if (drawCount) {
            drawSmallStackCount(x, y, (stack == null) ? 0L : stack.getStackSize(), textColor);
        }
    }

    private void drawSmallStackCount(final int x, final int y, long count, int textColor) {
        if (count < 0) {
            count = -count;
            textColor = 0xFF0000;
        }
        final String countText = ReadableNumberConverter.INSTANCE.toWideReadableForm(count);
        drawSmallStackCount(x, y, countText, textColor);
    }

    private void drawSmallStackCount(final int x, final int y, final String countText, final int textColor) {
        final int countWidth = parent.getFontRenderer().getStringWidth(countText);
        GL11.glScalef(0.5f, 0.5f, 1.0f);
        parent.getFontRenderer().drawString(countText, x * 2 + 32 - countWidth, y * 2 + 22, textColor, true);
        GL11.glScalef(2.0f, 2.0f, 1.0f);
    }

    public boolean isPointInWidget(int x, int y) {
        return x >= widgetX && y >= widgetY && x < (widgetX + widgetW) && y < (widgetY + widgetH);
    }
}
