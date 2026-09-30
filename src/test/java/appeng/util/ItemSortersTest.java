package appeng.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.regex.Pattern;

import org.junit.Test;

public class ItemSortersTest {

    @Test
    public void stripFormattingMatchesRegex() {
        final Pattern formattingPattern = Pattern.compile("(?s)\u00a7.");

        final String noFormatting = "Iron Ingot";
        assertSame(noFormatting, ItemSorters.stripFormatting(noFormatting));

        final String[] inputs = { "Iron Ingot", "\u00a7aRed", "\u00a7\u00a7a", "abc\u00a7", "\u00a7", "a\u00a7\nb",
                "\u00a7\ud83d\ude00x", "\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a70\u00a70Hex", "" };

        for (final String input : inputs) {
            assertEquals(formattingPattern.matcher(input).replaceAll(""), ItemSorters.stripFormatting(input));
        }
    }
}
