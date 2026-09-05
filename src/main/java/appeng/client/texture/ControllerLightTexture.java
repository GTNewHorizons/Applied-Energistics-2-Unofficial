package appeng.client.texture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;

import appeng.api.util.AEColor;
import appeng.core.settings.ControllerAnimation;
import cpw.mods.fml.client.FMLClientHandler;

public class ControllerLightTexture extends TextureAtlasSprite {

    private final ResourceLocation source;
    private final AEColor color;
    private final ControllerAnimation style;

    public ControllerLightTexture(final String source, final AEColor color, final ControllerAnimation style) {
        super("appliedenergistics2:" + source + "_" + color.name());
        this.source = new ResourceLocation("appliedenergistics2", "textures/blocks/" + source + ".png");
        this.color = color;
        this.style = style;
    }

    @Override
    public boolean hasCustomLoader(final IResourceManager manager, final ResourceLocation location) {
        return true;
    }

    @Override
    public boolean load(final IResourceManager manager, final ResourceLocation location) {
        try (InputStream stream = manager.getResource(this.source).getInputStream()) {
            final BufferedImage mask = ImageIO.read(stream);
            if (mask == null || mask.getHeight() < mask.getWidth()) {
                throw new IOException("Invalid controller light texture");
            }
            final int size = mask.getWidth();
            final BufferedImage[] mipmaps = new BufferedImage[32 - Integer.numberOfLeadingZeros(size)];
            mipmaps[0] = createAnimation(mask, this.color, this.style);
            this.loadSprite(mipmaps, new AnimationMetadataSection(Collections.emptyList(), size, size, 1), false);
            return false; // Forge stitches custom sprites when load returns false.
        } catch (IOException e) {
            FMLClientHandler.instance().trackBrokenTexture(this.source, e.getMessage());
            return true;
        }
    }

    static BufferedImage createAnimation(final BufferedImage mask, final AEColor color,
            final ControllerAnimation style) {
        final int size = mask.getWidth();
        final int frames = style.frameCount();
        final BufferedImage animation = new BufferedImage(size, size * frames, BufferedImage.TYPE_INT_ARGB);
        final double[][] circuitDistances = style.followsCircuitPaths() ? circuitDistances(mask, size) : null;
        for (int frame = 0; frame < frames; frame++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    final double time = frame / 48.0;
                    final double wave = circuitDistances == null
                            ? style.brightness(x / (double) size, y / (double) size, time)
                            : 0.25 + 0.75 * pulse(circuitDistances[y][x] - time * 3, 5);
                    int pixel = mask.getRGB(x, y) & 0xFF000000;
                    for (int shift = 0; shift <= 16; shift += 8) {
                        final int base = color.mediumVariant >> shift & 255;
                        final int highlight = color.whiteVariant >> shift & 255;
                        pixel |= (int) Math.round(base * 0.65 * (1 - wave) + highlight * wave) << shift;
                    }
                    animation.setRGB(x, frame * size + y, pixel);
                }
            }
        }
        return animation;
    }

    private static double[][] circuitDistances(final BufferedImage mask, final int size) {
        final int[][] distances = new int[size][size];
        final int[][] directions = new int[size][size];
        for (int[] row : distances) Arrays.fill(row, -1);
        final ArrayDeque<Integer> queue = new ArrayDeque<>();
        while (true) {
            int seed = -1;
            double closestToCenter = Double.MAX_VALUE;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    final double distanceToCenter = Math.hypot(x - (size - 1) / 2.0, y - (size - 1) / 2.0);
                    if (distances[y][x] == -1 && mask.getRGB(x, y) >>> 24 != 0
                            && distanceToCenter < closestToCenter) {
                        seed = y * size + x;
                        closestToCenter = distanceToCenter;
                    }
                }
            }
            if (seed == -1) break;
            distances[seed / size][seed % size] = 0;
            directions[seed / size][seed % size] = 1;
            queue.add(seed);
            while (!queue.isEmpty()) {
                final int point = queue.remove();
                final int x = point % size;
                final int y = point / size;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        final int nextX = x + dx;
                        final int nextY = y + dy;
                        if ((dx != 0 || dy != 0) && nextX >= 0 && nextX < size && nextY >= 0 && nextY < size
                                && distances[nextY][nextX] == -1 && mask.getRGB(nextX, nextY) >>> 24 != 0) {
                            distances[nextY][nextX] = distances[y][x] + 1;
                            directions[nextY][nextX] = distances[y][x] == 0
                                    ? ((nextX * 31 + nextY * 17 & 1) == 0 ? -1 : 1)
                                    : directions[y][x];
                            queue.add(nextY * size + nextX);
                        }
                    }
                }
            }
        }
        final double[][] pathLengths = new double[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) pathLengths[y][x] = directions[y][x] * distances[y][x] / 5.0;
        }
        return pathLengths;
    }

    private static double pulse(final double phase, final int sharpness) {
        return Math.pow((1 + Math.cos(2 * Math.PI * phase)) / 2, sharpness);
    }
}
