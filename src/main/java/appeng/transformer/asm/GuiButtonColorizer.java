package appeng.transformer.asm;

import javax.annotation.Nullable;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.Level;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import appeng.helpers.Reflected;
import cpw.mods.fml.relauncher.FMLRelaunchLog;

@Reflected
public final class GuiButtonColorizer implements IClassTransformer {

    private static final String GUI_BUTTON = "net.minecraft.client.gui.GuiButton";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String SCREEN_COLOR = "appeng/client/gui/ScreenColor";
    private static final String COLOR_DESC = "(FFFF)V";

    @Reflected
    public GuiButtonColorizer() {}

    @Nullable
    @Override
    public byte[] transform(final String name, final String transformedName, final byte[] basicClass) {
        if (basicClass == null || !transformedName.equals(GUI_BUTTON)) {
            return basicClass;
        }

        try {
            final ClassNode classNode = new ClassNode();
            final ClassReader classReader = new ClassReader(basicClass);
            classReader.accept(classNode, 0);

            boolean changed = false;
            for (final MethodNode method : classNode.methods) {
                if (this.isDrawButton(method) && this.redirectGlColor(method)) {
                    changed = true;
                    break;
                }
            }

            if (changed) {
                final ClassWriter writer = new ClassWriter(0);
                classNode.accept(writer);
                return writer.toByteArray();
            }
        } catch (final Throwable ignored) {}

        return basicClass;
    }

    private boolean isDrawButton(final MethodNode method) {
        if (!method.name.equals("drawButton") && !method.name.equals("func_146112_a") && !method.name.equals("a")) {
            return false;
        }

        return method.desc.endsWith(";II)V");
    }

    private boolean redirectGlColor(final MethodNode method) {
        for (final AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(GL11)
                    && call.name.equals("glColor4f")
                    && call.desc.equals(COLOR_DESC)) {
                call.owner = SCREEN_COLOR;
                call.name = "applyButtonColorHook";
                call.itf = false;
                this.log("Redirected GuiButton color in " + method.name + method.desc);
                return true;
            }
        }

        return false;
    }

    private void log(final String string) {
        FMLRelaunchLog.log("AE2-CORE", Level.INFO, string);
    }
}
