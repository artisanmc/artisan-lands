package fr.iban.lands.integration.claims;

import fr.iban.lands.model.SChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ClaimRegionGeometryBuilder {

    private static final List<Cell> NEIGHBOUR_OFFSETS = List.of(
            new Cell(-1, 0), new Cell(1, 0), new Cell(0, -1), new Cell(0, 1)
    );

    public List<ClaimMarkerGeometry> build(Collection<SChunk> chunks) {
        NavigableSet<Cell> remaining = chunks.stream()
                .map(chunk -> new Cell(chunk.getX(), chunk.getZ()))
                .collect(Collectors.toCollection(() -> new TreeSet<>(Cell.ORDER)));
        List<ClaimMarkerGeometry> result = new ArrayList<>();

        while (!remaining.isEmpty()) {
            Cell anchor = remaining.first();
            Set<Cell> component = floodFill(anchor, remaining);
            List<List<GridPoint>> loops = traceLoops(boundaryEdges(component));
            List<GridPoint> outline = loops.stream()
                    .filter(loop -> signedArea(loop) > 0)
                    .max(Comparator.comparingLong(loop -> Math.abs(signedArea(loop))))
                    .orElseThrow();
            List<List<ClaimMarkerPoint>> holes = loops.stream()
                    .filter(loop -> signedArea(loop) < 0)
                    .map(this::toBlockPoints)
                    .toList();
            result.add(new ClaimMarkerGeometry(
                    anchor.x(), anchor.z(), toBlockPoints(outline), holes));
        }

        return result.stream()
                .sorted(Comparator.comparingInt(ClaimMarkerGeometry::anchorX)
                        .thenComparingInt(ClaimMarkerGeometry::anchorZ))
                .toList();
    }

    private Set<Cell> floodFill(Cell anchor, Set<Cell> remaining) {
        Set<Cell> component = new TreeSet<>(Cell.ORDER);
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        pending.add(anchor);
        remaining.remove(anchor);

        while (!pending.isEmpty()) {
            Cell cell = pending.removeFirst();
            component.add(cell);
            for (Cell offset : NEIGHBOUR_OFFSETS) {
                Cell neighbour = new Cell(cell.x() + offset.x(), cell.z() + offset.z());
                if (remaining.remove(neighbour)) {
                    pending.addLast(neighbour);
                }
            }
        }
        return component;
    }

    private NavigableSet<Edge> boundaryEdges(Set<Cell> component) {
        NavigableSet<Edge> edges = new TreeSet<>(Edge.ORDER);
        for (Cell cell : component) {
            GridPoint topLeft = new GridPoint(cell.x(), cell.z());
            GridPoint topRight = new GridPoint(cell.x() + 1, cell.z());
            GridPoint bottomRight = new GridPoint(cell.x() + 1, cell.z() + 1);
            GridPoint bottomLeft = new GridPoint(cell.x(), cell.z() + 1);
            addOrCancel(edges, new Edge(topLeft, topRight));
            addOrCancel(edges, new Edge(topRight, bottomRight));
            addOrCancel(edges, new Edge(bottomRight, bottomLeft));
            addOrCancel(edges, new Edge(bottomLeft, topLeft));
        }
        return edges;
    }

    private void addOrCancel(Set<Edge> edges, Edge edge) {
        if (!edges.remove(edge.reverse())) {
            edges.add(edge);
        }
    }

    private List<List<GridPoint>> traceLoops(NavigableSet<Edge> edges) {
        Map<GridPoint, NavigableSet<Edge>> outgoing = new TreeMap<>(GridPoint.ORDER);
        for (Edge edge : edges) {
            outgoing.computeIfAbsent(edge.from(), ignored -> new TreeSet<>(Edge.ORDER)).add(edge);
        }

        List<List<GridPoint>> loops = new ArrayList<>();
        while (!edges.isEmpty()) {
            Edge edge = edges.first();
            removeEdge(edges, outgoing, edge);
            GridPoint start = edge.from();
            List<GridPoint> loop = new ArrayList<>();
            loop.add(start);

            while (!edge.to().equals(start)) {
                GridPoint current = edge.to();
                loop.add(current);
                Edge next = chooseRightmost(edge, outgoing.get(current));
                if (next == null) {
                    throw new IllegalStateException("Unclosed claim boundary");
                }
                removeEdge(edges, outgoing, next);
                edge = next;
            }
            loops.add(removeCollinearPoints(loop));
        }
        return loops;
    }

    private void removeEdge(Set<Edge> edges, Map<GridPoint, NavigableSet<Edge>> outgoing, Edge edge) {
        edges.remove(edge);
        NavigableSet<Edge> edgesFromPoint = outgoing.get(edge.from());
        edgesFromPoint.remove(edge);
        if (edgesFromPoint.isEmpty()) {
            outgoing.remove(edge.from());
        }
    }

    private Edge chooseRightmost(Edge incoming, NavigableSet<Edge> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .min(Comparator.comparingInt((Edge edge) -> turnPriority(incoming.direction(), edge.direction()))
                        .thenComparing(Edge.ORDER))
                .orElseThrow();
    }

    private int turnPriority(int incomingDirection, int outgoingDirection) {
        return switch (Math.floorMod(outgoingDirection - incomingDirection, 4)) {
            case 1 -> 0;
            case 0 -> 1;
            case 3 -> 2;
            default -> 3;
        };
    }

    private List<GridPoint> removeCollinearPoints(List<GridPoint> loop) {
        List<GridPoint> simplified = new ArrayList<>();
        for (int index = 0; index < loop.size(); index++) {
            GridPoint previous = loop.get(Math.floorMod(index - 1, loop.size()));
            GridPoint current = loop.get(index);
            GridPoint next = loop.get((index + 1) % loop.size());
            if (!isCollinear(previous, current, next)) {
                simplified.add(current);
            }
        }
        return simplified;
    }

    private boolean isCollinear(GridPoint previous, GridPoint current, GridPoint next) {
        return previous.x() == current.x() && current.x() == next.x()
                || previous.z() == current.z() && current.z() == next.z();
    }

    private long signedArea(List<GridPoint> loop) {
        long area = 0;
        for (int index = 0; index < loop.size(); index++) {
            GridPoint current = loop.get(index);
            GridPoint next = loop.get((index + 1) % loop.size());
            area += (long) current.x() * next.z() - (long) next.x() * current.z();
        }
        return area;
    }

    private List<ClaimMarkerPoint> toBlockPoints(List<GridPoint> points) {
        return points.stream()
                .map(point -> new ClaimMarkerPoint(
                        Math.multiplyExact(point.x(), 16),
                        Math.multiplyExact(point.z(), 16)))
                .toList();
    }

    private record Cell(int x, int z) {
        private static final Comparator<Cell> ORDER = Comparator.comparingInt(Cell::x)
                .thenComparingInt(Cell::z);
    }

    private record GridPoint(int x, int z) {
        private static final Comparator<GridPoint> ORDER = Comparator.comparingInt(GridPoint::x)
                .thenComparingInt(GridPoint::z);
    }

    private record Edge(GridPoint from, GridPoint to) {
        private static final Comparator<Edge> ORDER = Comparator.comparing(Edge::from, GridPoint.ORDER)
                .thenComparing(Edge::to, GridPoint.ORDER);

        private Edge reverse() {
            return new Edge(to, from);
        }

        private int direction() {
            if (to.x() > from.x()) {
                return 0;
            }
            if (to.z() > from.z()) {
                return 1;
            }
            if (to.x() < from.x()) {
                return 2;
            }
            return 3;
        }
    }
}
