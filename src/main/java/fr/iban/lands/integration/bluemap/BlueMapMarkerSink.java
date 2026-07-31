package fr.iban.lands.integration.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import fr.iban.lands.integration.claims.BlueMapSettings;
import fr.iban.lands.integration.claims.ClaimMarkerDescriptor;
import fr.iban.lands.integration.claims.ClaimMarkerSink;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

public final class BlueMapMarkerSink implements ClaimMarkerSink {

    static final String MARKER_SET_ID = "mslands-claims";

    private final BlueMapSettings settings;
    private final BlueMapShapeMarkerFactory markerFactory;
    private final BlueMapMapResolver mapResolver;
    private final Logger logger;
    private volatile BlueMapAPI activeApi;
    private boolean closed;

    public BlueMapMarkerSink(
            BlueMapSettings settings,
            BlueMapShapeMarkerFactory markerFactory,
            BlueMapMapResolver mapResolver,
            Logger logger
    ) {
        this.settings = Objects.requireNonNull(settings);
        this.markerFactory = Objects.requireNonNull(markerFactory);
        this.mapResolver = Objects.requireNonNull(mapResolver);
        this.logger = Objects.requireNonNull(logger);
    }

    public synchronized void attach(BlueMapAPI api) {
        if (closed) {
            return;
        }
        activeApi = Objects.requireNonNull(api);
    }

    public synchronized void detach(BlueMapAPI api) {
        if (activeApi == api) {
            activeApi = null;
        }
    }

    @Override
    public synchronized void replace(Collection<ClaimMarkerDescriptor> markers) {
        BlueMapAPI api = activeApi;
        if (api == null) {
            return;
        }

        Set<BlueMapMap> targetMaps = new LinkedHashSet<>(api.getMaps());
        Map<BlueMapMap, MarkerSet> replacements = new LinkedHashMap<>();
        for (ClaimMarkerDescriptor marker : markers) {
            Collection<BlueMapMap> resolved = mapResolver.resolve(api, marker.world());
            if (resolved.isEmpty()) {
                warnUnresolved(marker.world(), marker.id());
                continue;
            }

            targetMaps.addAll(resolved);
            for (BlueMapMap map : resolved) {
                MarkerSet replacement = replacements.computeIfAbsent(map, ignored -> newMarkerSet());
                replacement.put(marker.id(), markerFactory.create(marker));
            }
        }

        RuntimeException firstFailure = null;
        for (BlueMapMap map : targetMaps) {
            try {
                MarkerSet replacement = replacements.get(map);
                if (replacement == null) {
                    map.getMarkerSets().remove(MARKER_SET_ID);
                } else {
                    map.getMarkerSets().put(MARKER_SET_ID, replacement);
                }
            } catch (RuntimeException exception) {
                firstFailure = collectFailure(firstFailure, replacementFailure(map, exception));
            }
        }
        throwIfFailed(firstFailure);
    }

    private void cleanup(BlueMapAPI api) {
        RuntimeException firstFailure = null;
        for (BlueMapMap map : api.getMaps()) {
            try {
                map.getMarkerSets().remove(MARKER_SET_ID);
            } catch (RuntimeException exception) {
                firstFailure = collectFailure(
                        firstFailure,
                        new IllegalStateException(
                                "Failed to clear BlueMap marker set '" + MARKER_SET_ID
                                        + "' from map '" + mapId(map, exception) + "'.",
                                exception
                        )
                );
            }
        }
        throwIfFailed(firstFailure);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;
        BlueMapAPI api = activeApi;
        activeApi = null;
        if (api != null) {
            cleanup(api);
        }
    }

    private MarkerSet newMarkerSet() {
        return MarkerSet.builder()
                .label(settings.markerSetLabel())
                .toggleable(true)
                .defaultHidden(settings.defaultHidden())
                .build();
    }

    private void warnUnresolved(String world, String markerId) {
        logger.warning(
                "Could not resolve BlueMap maps for world '" + world
                        + "' while updating marker '" + markerId + "'."
        );
    }

    private RuntimeException replacementFailure(BlueMapMap map, RuntimeException exception) {
        return new IllegalStateException(
                "Failed to replace BlueMap marker set '" + MARKER_SET_ID
                        + "' on map '" + mapId(map, exception) + "'.",
                exception
        );
    }

    private String mapId(BlueMapMap map, RuntimeException primaryFailure) {
        try {
            return map.getId();
        } catch (RuntimeException idFailure) {
            if (idFailure != primaryFailure) {
                primaryFailure.addSuppressed(idFailure);
            }
            return "<unavailable>";
        }
    }

    private RuntimeException collectFailure(
            RuntimeException firstFailure,
            RuntimeException failure
    ) {
        if (firstFailure == null) {
            return failure;
        }
        firstFailure.addSuppressed(failure);
        return firstFailure;
    }

    private void throwIfFailed(RuntimeException failure) {
        if (failure != null) {
            throw failure;
        }
    }
}
