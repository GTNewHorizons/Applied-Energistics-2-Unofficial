package appeng.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.ChatComponentText;

import appeng.core.CommonHelper;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class KeyBindHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (CommonHelper.proxy.isKeyPressed(ActionKey.PICK_BLOCK)) {
            handlePickBlock();
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        if (CommonHelper.proxy.isKeyPressed(ActionKey.PICK_BLOCK)) {
            handlePickBlock();
        }
    }

    private void handlePickBlock() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen != null) return; // Don't act if a GUI is open

        EntityClientPlayerMP player = minecraft.thePlayer;
        if (player == null) return;

        player.addChatMessage(new ChatComponentText("Pick Block KeyBind pressed."));
    }

}
