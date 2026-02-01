package appeng.client.render.notification;

import java.util.Objects;

import appeng.api.storage.data.IAEStack;

/**
 * Original source copied from BeyondRealityCore and YamCore. All credits go to pauljoda for this code
 *
 * @author pauljoda
 */
public class Notification {

    private final IAEStack<?> icon;
    private final String title;
    private final String description;

    public Notification(IAEStack<?> stack, String title, String desc) {
        icon = stack;
        this.title = title;
        description = desc;
    }

    public IAEStack<?> getIcon() {
        return icon;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notification that)) return false;
        return title.equals(that.title) && description.equals(that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, description);
    }
}
