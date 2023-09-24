package appeng.me.pathfinding;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.me.cache.PathGridCache;

public class BackbonePathSegment extends PathSegment {

    IPathItem startNode;
    private Set<IPathItem> controllerRoutes = new HashSet<>();
    Map<BackbonePathSegment, List<IPathItem>> neighbours = new HashMap<>();

    public BackbonePathSegment(IPathItem node, final PathGridCache myPGC, final Set<IPathItem> semiOpen,
            final Set<IPathItem> closed) {
        super(myPGC, new LinkedList<>(), semiOpen, closed);
        startNode = node;
    }

    void addControllerRoute(IPathItem pi) {
        controllerRoutes.add(pi);
    }

    void addPathToNeighbour(BackbonePathSegment nb, IPathItem connection) {
        List<IPathItem> path = new LinkedList<>();
        for (IPathItem pi = connection; pi != null && pi != nb.startNode; pi = pi.getControllerRoute()) path.add(0, pi);
        neighbours.put(nb, path);
    }

    public void selectControllerRoute() {
        startNode.setControllerRoute(controllerRoutes.iterator().next(), false);
    }

    public boolean switchControllerRoute() {
        if (controllerRoutes.isEmpty() || startNode.getControllerRoute() == null) return false;
        if (startNode.getControllerRoute().getControllerRoute() != null
                && startNode.getControllerRoute().getControllerRoute().canSupportMoreChannels())
            return true;
        controllerRoutes.remove(startNode.getControllerRoute());
        if (controllerRoutes.isEmpty()) return false;
        startNode.setControllerRoute(controllerRoutes.iterator().next(), false);
        return true;
    }

    void removeNeigbour(BackbonePathSegment bs) {
        List<IPathItem> pathToRemoved = neighbours.get(bs);
        for (BackbonePathSegment nb : bs.neighbours.keySet()) {
            if (nb == this || neighbours.containsKey(nb)) continue;
            List<IPathItem> path = bs.neighbours.get(nb);
            path.addAll(pathToRemoved);
            neighbours.put(nb, path);
        }

        neighbours.remove(bs);
    }

    public void transferToNeighbours() {
        if (neighbours.isEmpty()) return;
        for (BackbonePathSegment nb : neighbours.keySet()) nb.removeNeigbour(this);
        BackbonePathSegment nb = neighbours.keySet().iterator().next();
        List<IPathItem> path = neighbours.get(nb);
        IPathItem controller = nb.startNode;
        for (IPathItem pi : path) {
            pi.setControllerRoute(controller, false);
            controller = pi;
        }
        startNode.setControllerRoute(controller, false);
    }

    private void reset() {
        open.add(startNode);
        closed.add(startNode);
        closed.addAll(controllerRoutes);
    }

    public static void reset(Map<IPathItem, BackbonePathSegment> backbone) {
        if (backbone.isEmpty()) return;
        backbone.values().iterator().next().closed.clear();
        for (BackbonePathSegment bs : backbone.values()) bs.reset();
    }

    public boolean isValid() {
        return !controllerRoutes.isEmpty();
    }
}
