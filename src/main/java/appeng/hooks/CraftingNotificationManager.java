package appeng.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;

import appeng.me.cluster.implementations.CraftingCPUCluster.CraftNotification;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;

public class CraftingNotificationManager {

    private static final List<Map<String, List<CraftNotification>>> notificationMaps = new ArrayList<>();

    @SubscribeEvent
    public void onPlayerLogin(PlayerLoggedInEvent event) {
        final EntityPlayer player = event.player;
        final String playerName = player.getCommandSenderName();
        for (Map<String, List<CraftNotification>> map : notificationMaps) {
            if (map.containsKey(playerName)) {
                List<CraftNotification> notifications = map.remove(playerName);
                for (CraftNotification notification : notifications) {
                    player.addChatMessage(notification.createMessage());
                }
                player.worldObj.playSoundAtEntity(player, "random.levelup", 1f, 1f);
            }
        }
    }

    public static void register(Map<String, List<CraftNotification>> map) {
        notificationMaps.add(map);
    }

    public static void unregister(Map<String, List<CraftNotification>> map) {
        notificationMaps.remove(map);
    }

    public static void clear() {
        notificationMaps.clear();
    }
}
