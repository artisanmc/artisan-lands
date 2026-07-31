package fr.iban.lands.integration.claims;

import fr.iban.lands.model.SChunk;
import fr.iban.lands.model.land.Land;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClaimMarkerSynchronizer implements ClaimVisualization {

    private final String serverName;
    private final ClaimSource source;
    private final ClaimMarkerSink sink;
    private final ClaimMarkerFactory markerFactory;
    private final ClaimRegionGeometryBuilder geometryBuilder;
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public ClaimMarkerSynchronizer(
            String serverName,
            ClaimSource source,
            ClaimMarkerSink sink,
            ClaimMarkerFactory markerFactory
    ) {
        this.serverName = Objects.requireNonNull(serverName);
        this.source = Objects.requireNonNull(source);
        this.sink = Objects.requireNonNull(sink);
        this.markerFactory = Objects.requireNonNull(markerFactory);
        this.geometryBuilder = new ClaimRegionGeometryBuilder();
    }

    @Override
    public synchronized void rebuild() {
        if (closed.get()) {
            return;
        }
        dirty.set(false);
        publishSnapshot();
    }

    @Override
    public void syncChunk(SChunk chunk) {
        if (!closed.get() && serverName.equals(chunk.getServer())) {
            dirty.set(true);
        }
    }

    @Override
    public void syncLand(Land land) {
        if (!closed.get()) {
            dirty.set(true);
        }
    }

    public synchronized void refreshIfDirty() {
        if (closed.get()) {
            return;
        }
        if (dirty.compareAndSet(true, false)) {
            publishSnapshot();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (this) {
            sink.close();
        }
    }

    private void publishSnapshot() {
        try {
            List<Map.Entry<SChunk, Land>> snapshot = source.claims().entrySet().stream()
                    .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                    .toList();
            Map<LandWorldKey, ClaimGroup> groups = new LinkedHashMap<>();
            for (Map.Entry<SChunk, Land> claim : snapshot) {
                SChunk chunk = claim.getKey();
                Land land = claim.getValue();
                if (!serverName.equals(chunk.getServer()) || source.isWilderness(land)) {
                    continue;
                }
                LandWorldKey key = new LandWorldKey(land.getId(), chunk.getWorld());
                groups.computeIfAbsent(key, ignored -> new ClaimGroup(land)).chunks().add(chunk);
            }
            List<ClaimMarkerDescriptor> markers = groups.entrySet().stream()
                    .flatMap(entry -> geometryBuilder.build(entry.getValue().chunks()).stream()
                            .map(geometry -> markerFactory.create(
                                    entry.getKey().world(), entry.getValue().land(), geometry)))
                    .flatMap(java.util.Optional::stream)
                    .sorted(Comparator.comparing(ClaimMarkerDescriptor::world)
                            .thenComparing(ClaimMarkerDescriptor::id))
                    .toList();
            sink.replace(markers);
        } catch (RuntimeException | LinkageError failure) {
            dirty.set(true);
            throw failure;
        }
    }

    private record LandWorldKey(UUID landId, String world) {
    }

    private static final class ClaimGroup {

        private final Land land;
        private final List<SChunk> chunks = new ArrayList<>();

        private ClaimGroup(Land land) {
            this.land = land;
        }

        private Land land() {
            return land;
        }

        private List<SChunk> chunks() {
            return chunks;
        }
    }
}
