package appeng.client.gui.widgets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import appeng.core.localization.ColorUtils;
import codechicken.nei.FormattedTextField.TextFormatter;

public class MEGuiMultilineTextField extends MEGuiTextField {

    private final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
    private TextFormatter formatter;
    private int scrollLine;

    public MEGuiMultilineTextField(int width, int height) {
        super(width, height);
    }

    @Override
    public void setFormatter(Object formatter) {
        super.setFormatter(formatter);
        if (formatter instanceof TextFormatter textFormatter) this.formatter = textFormatter;
    }

    @Override
    public void drawTextBox() {
        GuiTextField.drawRect(
                x + 1,
                y + 1,
                x + w - 1,
                y + h - 1,
                isFocused() ? ColorUtils.searchboxFocused.getColor() : ColorUtils.searchboxUnfocused.getColor());

        String text = getText();
        List<Line> lines = wrap(text);
        scrollLine = Math.min(scrollLine, Math.max(0, lines.size() - visibleLines()));
        int selectionStart = Math.min(field.getCursorPosition(), field.getSelectionEnd());
        int selectionEnd = Math.max(field.getCursorPosition(), field.getSelectionEnd());

        for (int row = scrollLine; row < Math.min(lines.size(), scrollLine + visibleLines()); row++) {
            Line line = lines.get(row);
            int textY = y + 3 + (row - scrollLine) * lineHeight();
            if (selectionStart < line.end && selectionEnd > line.start) {
                int from = Math.max(selectionStart, line.start);
                int to = Math.min(selectionEnd, line.end);
                GuiTextField.drawRect(
                        x + 3 + font.getStringWidth(text.substring(line.start, from)),
                        textY,
                        x + 3 + font.getStringWidth(text.substring(line.start, to)),
                        textY + font.FONT_HEIGHT,
                        0x663D89C9);
            }

            String content = text.substring(line.start, line.end);
            font.drawString(
                    formatter == null ? content : formatter.format(content),
                    x + 3,
                    textY,
                    ColorUtils.searchboxText.getColor());
        }

        if (isFocused() && System.currentTimeMillis() / 500 % 2 == 0) {
            int cursor = field.getSelectionEnd();
            int row = cursorLine(lines, cursor);
            if (row >= scrollLine && row < scrollLine + visibleLines()) {
                Line line = lines.get(row);
                int cursorX = x + 3 + font.getStringWidth(text.substring(line.start, Math.min(cursor, line.end)));
                int cursorY = y + 3 + (row - scrollLine) * lineHeight();
                GuiTextField.drawRect(cursorX, cursorY, cursorX + 1, cursorY + font.FONT_HEIGHT, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (!isMouseIn(mouseX, mouseY)) {
            setFocused(false);
            return;
        }

        setFocused(true);
        if (button == 1) {
            setText("");
        } else if (button == 0) {
            int position = positionAt(mouseX, mouseY);
            if (GuiScreen.isShiftKeyDown()) field.setSelectionPos(position);
            else field.setCursorPosition(position);
        }
        ensureCursorVisible();
    }

    public void mouseDragged(int mouseX, int mouseY) {
        if (isFocused()) field.setSelectionPos(positionAt(mouseX, mouseY));
    }

    public void scroll(int wheel) {
        scrollLine = Math.max(
                0,
                Math.min(wrap(getText()).size() - visibleLines(), scrollLine - (wheel > 0 ? 3 : -3)));
    }

    @Override
    public boolean textboxKeyTyped(char character, int key) {
        if (!isFocused()) return false;

        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && GuiScreen.isShiftKeyDown()) {
            insert("\n");
            return true;
        }
        if (GuiScreen.isCtrlKeyDown() && key == Keyboard.KEY_V) {
            String clipboard = GuiScreen.getClipboardString();
            if (clipboard != null) insert(clipboard.replace("\r\n", "\n").replace('\r', '\n'));
            return true;
        }
        if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
            moveVertically(key == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }

        boolean handled = super.textboxKeyTyped(character, key);
        if (handled) ensureCursorVisible();
        return handled;
    }

    @Override
    public void setText(String text, boolean ignoreTrigger) {
        super.setText(text, ignoreTrigger);
        ensureCursorVisible();
    }

    private void insert(String insertion) {
        String oldText = getText();
        int start = Math.min(field.getCursorPosition(), field.getSelectionEnd());
        int end = Math.max(field.getCursorPosition(), field.getSelectionEnd());
        int available = getMaxStringLength() - oldText.length() + end - start;
        if (available <= 0) return;
        if (insertion.length() > available) insertion = insertion.substring(0, available);
        field.setText(oldText.substring(0, start) + insertion + oldText.substring(end));
        field.setCursorPosition(start + insertion.length());
        onTextChange(oldText);
        ensureCursorVisible();
    }

    private void moveVertically(int direction) {
        String text = getText();
        List<Line> lines = wrap(text);
        int cursor = field.getSelectionEnd();
        Line current = lines.get(cursorLine(lines, cursor));
        int targetRow = Math.max(0, Math.min(lines.size() - 1, cursorLine(lines, cursor) + direction));
        int column = font.getStringWidth(text.substring(current.start, Math.min(cursor, current.end)));
        int target = positionInLine(lines.get(targetRow), x + 3 + column);
        if (GuiScreen.isShiftKeyDown()) field.setSelectionPos(target);
        else field.setCursorPosition(target);
        ensureCursorVisible();
    }

    private void ensureCursorVisible() {
        List<Line> lines = wrap(getText());
        int row = cursorLine(lines, field.getSelectionEnd());
        if (row < scrollLine) scrollLine = row;
        if (row >= scrollLine + visibleLines()) scrollLine = row - visibleLines() + 1;
    }

    private int positionAt(int mouseX, int mouseY) {
        List<Line> lines = wrap(getText());
        int row = Math.max(0, Math.min(lines.size() - 1, scrollLine + (mouseY - y - 3) / lineHeight()));
        return positionInLine(lines.get(row), mouseX);
    }

    private int positionInLine(Line line, int mouseX) {
        String text = getText();
        for (int position = line.start; position < line.end; position++) {
            int midpoint = x + 3 + font.getStringWidth(text.substring(line.start, position))
                    + font.getCharWidth(text.charAt(position)) / 2;
            if (mouseX < midpoint) return position;
        }
        return line.end;
    }

    private int cursorLine(List<Line> lines, int cursor) {
        int row = 0;
        for (int i = 1; i < lines.size() && lines.get(i).start <= cursor; i++) row = i;
        return row;
    }

    private List<Line> wrap(String text) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        while (true) {
            int newline = text.indexOf('\n', start);
            int end = newline < 0 ? text.length() : newline;
            if (start == end) lines.add(new Line(start, end));
            while (start < end) {
                int length = Math.max(1, font.trimStringToWidth(text.substring(start, end), w - 6).length());
                int next = Math.min(end, start + length);
                lines.add(new Line(start, next));
                start = next;
            }
            if (newline < 0) return lines;
            start = newline + 1;
        }
    }

    private int lineHeight() {
        return font.FONT_HEIGHT + 2;
    }

    private int visibleLines() {
        return Math.max(1, (h - 4) / lineHeight());
    }

    private static class Line {

        final int start;
        final int end;

        Line(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
