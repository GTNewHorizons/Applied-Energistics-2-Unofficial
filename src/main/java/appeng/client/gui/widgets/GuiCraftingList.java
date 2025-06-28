package appeng.client.gui.widgets;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.event.ClickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.FileUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.AEBaseGui;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.util.ColorPickHelper;
import appeng.util.ReadableNumberConverter;
import appeng.util.RoundHelper;

public class GuiCraftingList {

    private static final int FIELD_WIDTH = 69 * 4;
    private static final int FIELD_HEIGHT = 24 * 4;
    private static final int FIELD_SECTIONLENGTH = 67 * 4;
    private static final ResourceLocation FIELD_TEXTURE = new ResourceLocation(
            AppEng.MOD_ID,
            "textures/guis/onefiled.png");

    private static final DateTimeFormatter SCREENSHOT_DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
    protected static RenderItem itemRender = new RenderItem();

    public static void saveScreenShot(AEBaseGui parent, List<IAEItemStack> visual, IItemList<IAEItemStack> storage,
            IItemList<IAEItemStack> pending, IItemList<IAEItemStack> missing) {
        // Make a better size for reading
        int visualSize = visual.size();
        int width = 3;
        int height = 0;
        while (true) {

            height = (int) Math.ceil((visualSize * 1.0) / width);
            // Make sure aspect ratio is under 0.9, like a square
            if ((width * (FIELD_WIDTH - 1) + 1) * 1.0 / (height * (FIELD_HEIGHT - 1) + 1) >= 0.9) {
                break;
            }
            width++;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        if (!OpenGlHelper.isFramebufferEnabled()) {
            AELog.error("Could not save crafting tree screenshot: FBOs disabled/unsupported");
            mc.ingameGUI.getChatGUI()
                    .printChatMessage(new ChatComponentTranslation("chat.appliedenergistics2.FBOUnsupported"));
            return;
        }

        try {

            final File screenshotsDir = new File(mc.mcDataDir, "screenshots");
            FileUtils.forceMkdir(screenshotsDir);

            int imgWidth = width * (FIELD_WIDTH - 1) + 1;
            int imgHeight = height * (FIELD_HEIGHT - 1) + 1;

            final BufferedImage outputImg = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
            final IntBuffer downloadBuffer = BufferUtils.createIntBuffer(FIELD_WIDTH * FIELD_HEIGHT);
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            final Framebuffer fb = new Framebuffer(FIELD_WIDTH, FIELD_HEIGHT, true);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, FIELD_WIDTH, FIELD_HEIGHT, 0, -3000, 3000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            try {
                fb.bindFramebuffer(true);

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        boolean need_red = false;
                        if (width * y + x < visualSize) {
                            // Draw field with string and itemstack
                            IAEItemStack refStack = visual.get(width * y + x);
                            final IAEItemStack stored = storage.findPrecise(refStack);
                            final IAEItemStack pendingStack = pending.findPrecise(refStack);
                            final IAEItemStack missingStack = missing.findPrecise(refStack);

                            if (missingStack != null && missingStack.getStackSize() > 0) {
                                need_red = true;
                            }

                            GL11.glPushMatrix();
                            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                            GL11.glScaled(4, 4, 1);
                            parent.bindTexture(FIELD_TEXTURE);
                            parent.drawTexturedModalRect(
                                    0,
                                    0,
                                    0,
                                    need_red ? FIELD_HEIGHT / 4 : 0,
                                    FIELD_WIDTH,
                                    FIELD_HEIGHT);
                            GL11.glPopMatrix();
                            GuiCraftingList.drawStringAndItem(parent, refStack, stored, pendingStack, missingStack);
                        } else {
                            // Draw empty field
                            GL11.glPushMatrix();
                            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                            GL11.glScaled(4, 4, 1);
                            parent.bindTexture(FIELD_TEXTURE);
                            parent.drawTexturedModalRect(0, 0, 0, 0, FIELD_WIDTH, FIELD_HEIGHT);
                            GL11.glPopMatrix();
                        }

                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fb.framebufferTexture);
                        GL11.glGetTexImage(
                                GL11.GL_TEXTURE_2D,
                                0,
                                GL12.GL_BGRA,
                                GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                                downloadBuffer);

                        // int backgroundColor = need_red ? 0xFFE0BABA : 0xFFDBDBDB;
                        int backgroundColor = downloadBuffer.get(FIELD_WIDTH * 7 + 7);

                        for (int i = 0; i < FIELD_WIDTH * FIELD_HEIGHT; i++) {
                            int x_ = i % FIELD_WIDTH;
                            int y_ = (int) (i / FIELD_WIDTH);
                            int rgba = downloadBuffer.get(i);

                            int a = (rgba >>> 24) & 0xFF;
                            int r = (rgba >>> 16) & 0xFF;
                            int g = (rgba >>> 8) & 0xFF;
                            int b = rgba & 0xFF;

                            int br = (backgroundColor >>> 16) & 0xFF;
                            int bg = (backgroundColor >>> 8) & 0xFF;
                            int bb = backgroundColor & 0xFF;

                            float alpha = a / 255.0f;

                            int outR = (int) (r * alpha + br * (1 - alpha));
                            int outG = (int) (g * alpha + bg * (1 - alpha));
                            int outB = (int) (b * alpha + bb * (1 - alpha));

                            int finalRGB = (0xFF << 24) | (outR << 16) | (outG << 8) | outB;

                            outputImg.setRGB(x * (FIELD_WIDTH - 1) + x_, (y + 1) * (FIELD_HEIGHT - 1) - y_, finalRGB);
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

            AELog.info("Saved crafting list screenshot to %s", filename);
            ChatComponentText chatLink = new ChatComponentText(filename);
            chatLink.getChatStyle()
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, outFile.getAbsolutePath()));
            chatLink.getChatStyle().setUnderlined(Boolean.valueOf(true));
            mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentTranslation("screenshot.success", chatLink));

        } catch (Exception e) {
            AELog.warn(e, "Could not save crafting list screenshot");
            mc.ingameGUI.getChatGUI()
                    .printChatMessage(new ChatComponentTranslation("screenshot.failure", e.getMessage()));
        }
    }

    private static void drawStringAndItem(AEBaseGui parent, IAEItemStack refStack, IAEItemStack stored,
            IAEItemStack pendingStack, IAEItemStack missingStack) {
        final int xo = 9;
        final int yo = 22;
        if (refStack != null) {
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glScaled(2.0d, 2.0d, 0.5d);

            int lines = 0;

            if (stored != null && stored.getStackSize() > 0) {
                lines++;
                if (missingStack == null && pendingStack == null) {
                    lines++;
                }
            }
            if (missingStack != null && missingStack.getStackSize() > 0) {
                lines++;
            }
            if (pendingStack != null && pendingStack.getStackSize() > 0) {
                lines += 2;
            }

            final int negY = ((lines - 1) * 5) / 2;
            int downY = 0;

            if (stored != null && stored.getStackSize() > 0) {
                String str = GuiText.FromStorage.getLocal() + ": "
                        + ReadableNumberConverter.INSTANCE.toWideReadableForm(stored.getStackSize());
                final int w = 4 + parent.getFontRenderer().getStringWidth(str);
                parent.getFontRenderer().drawString(
                        str,
                        (int) ((xo + FIELD_SECTIONLENGTH - 20 - (w * 2)) / 4),
                        (yo + 6 - negY + downY) / 2,
                        GuiColors.CraftConfirmFromStorage.getColor());
                downY += 5 * 4;
            }

            if (missingStack != null && missingStack.getStackSize() > 0) {
                String str = GuiText.Missing.getLocal() + ": "
                        + ReadableNumberConverter.INSTANCE.toWideReadableForm(missingStack.getStackSize());
                final int w = 4 + parent.getFontRenderer().getStringWidth(str);
                parent.getFontRenderer().drawString(
                        str,
                        (int) ((xo + FIELD_SECTIONLENGTH - 20 - (w * 2)) / 4),
                        (yo + 6 - negY + downY) / 2,
                        GuiColors.CraftConfirmMissing.getColor());

                downY += 5 * 4;
            }

            if (pendingStack != null && pendingStack.getStackSize() > 0) {
                String str = GuiText.ToCraft.getLocal() + ": "
                        + ReadableNumberConverter.INSTANCE.toWideReadableForm(pendingStack.getStackSize());
                int w = 4 + parent.getFontRenderer().getStringWidth(str);
                parent.getFontRenderer().drawString(
                        str,
                        (int) ((xo + FIELD_SECTIONLENGTH - 20 - (w * 2)) / 4),
                        (yo + 6 - negY + downY) / 2,
                        GuiColors.CraftConfirmToCraft.getColor());

                downY += 5 * 4;
                str = GuiText.ToCraftRequests.getLocal() + ": "
                        + ReadableNumberConverter.INSTANCE.toWideReadableForm(pendingStack.getCountRequestableCrafts());
                w = 4 + parent.getFontRenderer().getStringWidth(str);
                parent.getFontRenderer().drawString(
                        str,
                        (int) ((xo + FIELD_SECTIONLENGTH - 20 - (w * 2)) / 4),
                        (yo + 6 - negY + downY) / 2,
                        GuiColors.CraftConfirmToCraft.getColor());
            }

            if (stored != null && stored.getStackSize() > 0 && missingStack == null && pendingStack == null) {
                String str = GuiText.FromStoragePercent.getLocal() + ": "
                        + RoundHelper.toRoundedFormattedForm(stored.getUsedPercent(), 2)
                        + "%";
                int w = 4 + parent.getFontRenderer().getStringWidth(str);
                parent.getFontRenderer().drawString(
                        str,
                        (int) ((xo + FIELD_SECTIONLENGTH - 20 - (w * 2)) / 4),
                        (yo + 6 - negY + downY) / 2,
                        ColorPickHelper.selectColorFromThreshold(stored.getUsedPercent()).getColor());
            }
            GL11.glPopAttrib();
            GL11.glPopMatrix();

            GL11.glPushMatrix();
            GL11.glScaled(4.0d, 4.0d, 1.0d);
            final ItemStack is = refStack.copy().getItemStack();
            drawItem((int) (FIELD_SECTIONLENGTH / 4 - 18), (int) ((FIELD_HEIGHT / 8) - 8), is, true);
            GL11.glPopMatrix();
        }
    }

    public static void drawItem(final int x, final int y, final ItemStack is, boolean enhanceLight) {
        itemRender.zLevel = 100.0F;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glTranslatef(0.0f, 0.0f, 101.0f);
        RenderHelper.enableGUIStandardItemLighting();

        if (enhanceLight) {
            GL11.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, createColorBuffer(1.0F, 1.0F, 1.0F, 1.0F));
            GL11.glLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, createColorBuffer(1.0F, 1.0F, 1.0F, 1.0F));
            GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, createColorBuffer(0.6F, 0.6F, 0.6F, 1.0F));
        }

        Minecraft mc = Minecraft.getMinecraft();
        itemRender.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.renderEngine, is, x, y);
        GL11.glTranslatef(0.0f, 0.0f, -101.0f);
        GL11.glPopAttrib();

        itemRender.zLevel = 0.0F;
    }

    private static FloatBuffer createColorBuffer(float r, float g, float b, float a) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
        buffer.put(r).put(g).put(b).put(a);
        buffer.flip();
        return buffer;
    }

}
