package appeng.client.render.previewBlocks;

import static appeng.util.Platform.getEyeOffset;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.parts.networking.PartCable;
import appeng.util.LookDirection;
import appeng.util.Platform;

public class ViewHelper {

    // region Variables
    // spotless:off
    private static final List<IRenderPreview> iRenderPreviews = Arrays.asList(
            new RendererCableAnchor(),
            new RendererCable(),
            new RendererTerminal(),
            new RendererToggleBus(),
            new RendererQuartzFiber(),
            new RendererLevelEmitter(),
            new RendererImportBus(),
            new RendererExportBus(),
            new RendererAnnihilationPlane()
    );
    // spotless:on
    private static boolean isValidPosition;
    private static int previewX, previewY, previewZ;
    private static ForgeDirection placementSide;
    private static float currentPartialTicks;
    private static EntityPlayer player;
    private static ItemStack cachedItemStack = null;
    private static IPart cachedPart = null;
    private static boolean isActive;
    // endregion

    // region Getters
    public static boolean getValidPosition() {
        return isValidPosition;
    }

    public static ForgeDirection getPlacementSide() {
        return placementSide;
    }

    public static int getPreviewX() {
        return previewX;
    }

    public static int getPreviewY() {
        return previewY;
    }

    public static int getPreviewZ() {
        return previewZ;
    }

    public static float getCurrentPartialTicks() {
        return currentPartialTicks;
    }

    public static World getWorld() {
        return player.worldObj;
    }

    public static EntityPlayer getPlayer() {
        return player;
    }

    private static Optional<IPart> getCachedPart(ItemStack item) {
        if (item == null || !(item.getItem() instanceof IPartItem iPartItem)) {
            clearCache();
            return Optional.empty();
        }

        if (cachedItemStack != null && areItemStacksEqual(cachedItemStack, item) && cachedPart != null) {
            return Optional.of(cachedPart);
        }

        cachedItemStack = item.copy();
        try {
            cachedPart = iPartItem.createPartFromItemStack(item);
        } catch (Exception e) {
            clearCache();
        }

        return Optional.ofNullable(cachedPart);
    }

    public static List<IRenderPreview> getRenderPreviews() {
        return iRenderPreviews;
    }

    public static AECableType getCableType(ItemStack itemStack) {
        return getCachedPart(itemStack).filter(PartCable.class::isInstance).map(PartCable.class::cast)
                .map(PartCable::getCableConnectionType).orElse(AECableType.NONE);
    }

    public static ItemStack getCachedItemStack() {
        return cachedItemStack;
    }

    public static AEColor getCableColor(ItemStack itemStack) {
        return getCachedPart(itemStack).filter(PartCable.class::isInstance).map(PartCable.class::cast)
                .map(PartCable::getCableColor).orElse(AEColor.Transparent);
    }

    // endregion

    // region Setters
    public static void setPlacementSide(ForgeDirection placementSide) {
        ViewHelper.placementSide = placementSide;
    }

    public static void setPreviewX(int previewX) {
        ViewHelper.previewX = previewX;
    }

    public static void setPreviewY(int previewY) {
        ViewHelper.previewY = previewY;
    }

    public static void setPreviewZ(int previewZ) {
        ViewHelper.previewZ = previewZ;
    }

    public static void setCurrentPartialTicks(float currentPartialTicks) {
        ViewHelper.currentPartialTicks = currentPartialTicks;
    }

    public static void setPlayer(EntityPlayer player) {
        ViewHelper.player = player;
    }

    public static void setCachedItemStack(ItemStack cachedItemStack) {
        ViewHelper.cachedItemStack = cachedItemStack;
    }
    // endregion

    // region Methods
    public static void clearCache() {
        cachedItemStack = null;
        cachedPart = null;
    }

    private static boolean areItemStacksEqual(ItemStack stack1, ItemStack stack2) {
        if (stack1 == stack2) return true;
        if (stack1 == null || stack2 == null) return false;
        return ItemStack.areItemStacksEqual(stack1, stack2) && ItemStack.areItemStackTagsEqual(stack1, stack2)
                && stack1.stackSize == stack2.stackSize;
    }

    public static List<Class<?>> getValidClasses(Class<?>... classes) {
        return Stream.of(classes).filter(clazz -> {
            if (clazz == null) return false;
            try {
                Class.forName(clazz.getName());
                return true;
            } catch (NoClassDefFoundError | ClassNotFoundException ignored) {
                return false;
            }
        }).collect(Collectors.toList());
    }

    public static void handleItem(ItemStack stack) {
        if (!isActive) return;
        ViewHelper.getCachedPart(stack).flatMap(
                part -> ViewHelper.getRenderPreviews().stream()
                        .filter(preview -> preview.validItemClass().stream().anyMatch(clazz -> clazz.isInstance(part)))
                        .findFirst())
                .ifPresent(IRenderPreview::renderPreview);
    }

    public static boolean handleItemValid(ItemStack stack) {
        return ViewHelper.getCachedPart(stack).flatMap(
                part -> ViewHelper.getRenderPreviews().stream()
                        .filter(preview -> preview.validItemClass().stream().anyMatch(clazz -> clazz.isInstance(part)))
                        .findFirst())
                .isPresent();
    }

    public static Optional<AbstractRendererPreview> getRenderPreviewFromItem(ItemStack stack) {
        return ViewHelper.getCachedPart(stack).flatMap(
                part -> ViewHelper.getRenderPreviews().stream()
                        .filter(preview -> preview.validItemClass().stream().anyMatch(clazz -> clazz.isInstance(part)))
                        .findFirst().map(preview -> (AbstractRendererPreview) preview));
    }

    public static void updatePreview(EntityPlayer player) {
        MovingObjectPosition mopBlock = getTargetedBlock(player);

        if (mopBlock == null || mopBlock.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            isActive = false;
            return;
        }

        final LookDirection dir = Platform.getPlayerRay(player, getEyeOffset(player));
        Block block = getWorld().getBlock(mopBlock.blockX, mopBlock.blockY, mopBlock.blockZ);

        if (block == null) {
            isActive = false;
            return;
        }

        final MovingObjectPosition mop = block.collisionRayTrace(
                player.worldObj,
                mopBlock.blockX,
                mopBlock.blockY,
                mopBlock.blockZ,
                dir.getA(),
                dir.getB());

        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            isActive = false;
            return;
        }

        previewX = mop.blockX;
        previewY = mop.blockY;
        previewZ = mop.blockZ;
        placementSide = ForgeDirection.getOrientation(mop.sideHit);

        if (handleItemValid(cachedItemStack)) {
            getRenderPreviewFromItem(cachedItemStack).ifPresent(
                    preview -> {
                        isValidPosition = preview.canPlace(getWorld(), placementSide, previewX, previewY, previewZ);
                    });
        } else {
            isValidPosition = false;
        }

        isActive = true;
    }

    private static MovingObjectPosition getTargetedBlock(EntityPlayer player) {
        return Platform.rayTrace(player, true, false);
    }
    // endregion
}
