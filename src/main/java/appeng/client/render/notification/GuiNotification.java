
package appeng.client.render.notification;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Original source copied from BeyondRealityCore and YamCore. All credits go to pauljoda for this code
 *
 * @author pauljoda
 *
 */
@SideOnly(Side.CLIENT)
public class GuiNotification extends Gui {

    public static final ResourceLocation NOTIFICATION_TEXTURE = new ResourceLocation(
            "textures/gui/achievement/achievement_background.png");

    public static long DISPLAY_DURATION = 4500L;
    public static long ANIM_TIME = 750L;
    public static final int PADDING_HORIZONTAL = 10;
    public static final int TEXT_X_OFFSET = 30;

    public final Minecraft mc;
    public int screenWidth;
    public int screenHeight;
    public String currentTitle;
    public String currentDescription;
    public Notification currentNotification;
    public long taskStartTime;
    public final RenderItem itemRenderer;
    public final List<Notification> notificationQueue = new ArrayList<>();

    public GuiNotification(Minecraft mc) {
        this.mc = mc;
        this.itemRenderer = new RenderItem();
    }

    public void queueNotification(Notification notification) {
        queueNotification(notification, false);
    }

    public void queueNotification(Notification notification, boolean checkAlreadyQueued) {
        if (checkAlreadyQueued) {
            for (Notification noti : notificationQueue) {
                if (noti.equals(notification)) {
                    return;
                }
            }
        }
        for (Notification n : notificationQueue) {
            if (n.equals(notification)) return;
        }

        if (notificationQueue.isEmpty()) {
            setupNotification(notification);
        }
        notificationQueue.add(notification);
    }

    public void setupNotification(Notification notification) {
        this.currentTitle = notification.getTitle();
        this.currentDescription = notification.getDescription();
        this.taskStartTime = Minecraft.getSystemTime();
        this.currentNotification = notification;
    }

    public void moveToNextNotification() {
        if (!notificationQueue.isEmpty()) {
            notificationQueue.remove(0);
            if (!notificationQueue.isEmpty()) {
                setupNotification(notificationQueue.get(0));
            } else {
                this.currentNotification = null;
                this.taskStartTime = 0L;
            }
        }
    }

    public void updateLayoutScale() {
        ScaledResolution res = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
        this.screenWidth = res.getScaledWidth();
        this.screenHeight = res.getScaledHeight();

        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, this.screenWidth, this.screenHeight, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
    }

    public void updateNotificationWindow() {
        if (this.currentNotification == null || this.taskStartTime == 0L || mc.thePlayer == null) return;

        long elapsedTime = Minecraft.getSystemTime() - this.taskStartTime;

        if (elapsedTime > DISPLAY_DURATION) {
            moveToNextNotification();
            return;
        }

        this.updateLayoutScale();

        double animationProgress;
        if (elapsedTime < ANIM_TIME) {
            animationProgress = (double) elapsedTime / ANIM_TIME;
        } else if (elapsedTime > DISPLAY_DURATION - ANIM_TIME) {
            animationProgress = (double) (DISPLAY_DURATION - elapsedTime) / ANIM_TIME;
        } else {
            animationProgress = 1.0;
        }

        animationProgress = Math.sin(animationProgress * Math.PI / 2.0);

        int titleWidth = mc.fontRenderer.getStringWidth(this.currentTitle);
        int descWidth = mc.fontRenderer.getStringWidth(this.currentDescription);
        int textContentWidth = Math.max(titleWidth, descWidth);
        int totalBoxWidth = textContentWidth + TEXT_X_OFFSET + PADDING_HORIZONTAL;

        int renderX = screenWidth - (int) (animationProgress * totalBoxWidth);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        this.mc.getTextureManager().bindTexture(NOTIFICATION_TEXTURE);
        GL11.glDisable(GL11.GL_LIGHTING);

        int textureU = 96;
        int textureV = 202;
        int sideWidth = 10;
        int centerPartWidth = totalBoxWidth - (sideWidth * 2);

        this.drawTexturedModalRect(renderX, 0, textureU, textureV, sideWidth, 32);
        for (int step = 0; step < centerPartWidth; step++) {
            this.drawTexturedModalRect(renderX + sideWidth + step, 0, textureU + sideWidth, textureV, 1, 32);
        }
        this.drawTexturedModalRect(
                renderX + sideWidth + centerPartWidth,
                0,
                textureU + 160 - sideWidth,
                textureV,
                sideWidth,
                32);

        this.mc.fontRenderer.drawString(this.currentTitle, renderX + TEXT_X_OFFSET, 7, -256);
        this.mc.fontRenderer.drawString(this.currentDescription, renderX + TEXT_X_OFFSET, 18, -1);

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glEnable(GL11.GL_LIGHTING);

        this.currentNotification.getIcon().drawInGui(this.mc, renderX + 8, 8);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
