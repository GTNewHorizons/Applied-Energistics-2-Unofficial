package appeng.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.event.PickBlockEvent;

import appeng.core.AEConfig;
import appeng.core.CommonHelper;
import appeng.core.settings.ControllerAnimation;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPickBlock;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Handles the pick block feature for any case where the vanilla pick block key and the ME Pick Block key are different.
 * For when they're the same, see {@link ClientHelper#onPickBlockEvent(PickBlockEvent)}.
 */
@SideOnly(Side.CLIENT)
public class KeyBindHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        handleControllerAnimationKey();
        if (CommonHelper.proxy.isKeyPressed(ActionKey.PICK_BLOCK) && !arePickBlockBindsEqual()) {
            handlePickBlock();
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        handleControllerAnimationKey();
        if (CommonHelper.proxy.isKeyPressed(ActionKey.PICK_BLOCK) && !arePickBlockBindsEqual()) {
            handlePickBlock();
        }
    }

    static boolean arePickBlockBindsEqual() {
        return Minecraft.getMinecraft().gameSettings.keyBindPickBlock.getKeyCode()
                == CommonHelper.proxy.getKeybind(ActionKey.PICK_BLOCK);
    }

    private static void handleControllerAnimationKey() {
        if (!CommonHelper.proxy.isKeyPressed(ActionKey.CYCLE_CONTROLLER_ANIMATION)) return;
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen != null || minecraft.thePlayer == null) return;

        final AEConfig config = AEConfig.instance;
        config.controllerAnimation = config.controllerAnimation.next();
        config.get("Client", "controllerAnimation", ControllerAnimation.WAVE.name())
                .set(config.controllerAnimation.name());
        config.save();
        minecraft.refreshResources();
        minecraft.thePlayer.addChatMessage(
                new ChatComponentTranslation(
                        "chat.appliedenergistics2.ControllerAnimation",
                        config.controllerAnimation.name()));
    }

    static boolean handlePickBlock() {
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = minecraft.theWorld;
        if (minecraft.currentScreen != null) return false; // Don't act if a GUI is open

        EntityClientPlayerMP player = minecraft.thePlayer;
        if (player == null) return false;

        // Use vanilla pick block when in creative
        if (player.capabilities.isCreativeMode) {
            return false;
        }

        // Get the block the player is currently looking at
        MovingObjectPosition target = minecraft.objectMouseOver;
        if (target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }

        int x = target.blockX;
        int y = target.blockY;
        int z = target.blockZ;
        Block block = world.getBlock(x, y, z);

        if (block.isAir(world, x, y, z)) {
            return false;
        }

        ItemStack pickedBlock = block.getPickBlock(target, world, x, y, z, player);
        if (pickedBlock == null) {
            return false;
        }

        final var packet = new PacketPickBlock(pickedBlock);
        NetworkHandler.instance.sendToServer(packet);
        return true;
    }

}
