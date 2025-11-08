package appeng.parts.p2p;

import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

public class DebugState {

    public static int indent = 0;

    public static <R> R doAndLog(Supplier<R> supplier, String name) {
        indent++;
        System.out.println(StringUtils.repeat("  ", indent) + "#Start " + name);
        R value = supplier.get();
        System.out.println(StringUtils.repeat("  ", indent) + "#End " + name);
        indent--;
        return value;
    }

    public static void doAndLog(Runnable runnable, String name) {
        doAndLog(() -> {
            runnable.run();
            return null;
        }, name);
    }
}
