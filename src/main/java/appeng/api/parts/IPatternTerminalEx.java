package appeng.api.parts;

public interface IPatternTerminalEx extends IPatternTerminal {

    boolean isInverted();

    void setInverted(boolean inverted);

    int getActivePage();

    void setActivePage(int activePage);
}
