package appeng.client.texture;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TextureUtils {

    @SideOnly(Side.CLIENT)
    @Nonnull
    public static IIcon checkTexture(IIcon val) {
        return checkTexture(val, true);
    }

    @SideOnly(Side.CLIENT)
    @Nonnull
    public static IIcon checkTexture(IIcon val, boolean isBlock) {
        if (val == null) {
            val = isBlock ? getMissingBlock() : getMissingItem();
        }

        assert val != null;

        return val;
    }

    @SideOnly(Side.CLIENT)
    private static TextureAtlasSprite missingBlockTexture;
    @SideOnly(Side.CLIENT)
    private static TextureAtlasSprite missingItemTexture;

    @SideOnly(Side.CLIENT)
    public static IIcon getMissingBlock() {
        if (missingBlockTexture == null) {
            missingBlockTexture = ((TextureMap) Minecraft.getMinecraft().getTextureManager()
                    .getTexture(TextureMap.locationBlocksTexture)).getAtlasSprite("missingno");
        }

        return missingBlockTexture;
    }

    @SideOnly(Side.CLIENT)
    public static IIcon getMissingItem() {
        if (missingItemTexture == null) {
            missingItemTexture = ((TextureMap) Minecraft.getMinecraft().getTextureManager()
                    .getTexture(TextureMap.locationItemsTexture)).getAtlasSprite("missingno");
        }

        return missingItemTexture;
    }
}
