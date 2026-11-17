package fr.iban.lands.integration.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import fr.iban.lands.enums.LandType;
import fr.iban.lands.integration.claims.BlueMapSettings;
import fr.iban.lands.integration.claims.ClaimMarkerDescriptor;
import fr.iban.lands.integration.claims.ClaimMarkerGeometry;
import fr.iban.lands.integration.claims.ClaimMarkerPoint;
import fr.iban.lands.integration.claims.ClaimMarkerStyle;
import fr.iban.lands.integration.claims.RgbColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BlueMapMarkerSinkTest {

    private BlueMapAPI api;
    private BlueMapMapResolver resolver;
    private Logger logger;
    private List<LogRecord> records;

    @BeforeEach
    void setUp() {
        api = mock(BlueMapAPI.class);
        resolver = mock(BlueMapMapResolver.class);
        records = new ArrayList<>();
        logger = Logger.getLogger(getClass().getName() + "." + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @Test
    void replaceCreatesConfiguredMarkerSetAndMarkerOnEveryResolvedMap() {
        Map<String, MarkerSet> firstSets = new HashMap<>();
        Map<String, MarkerSet> secondSets = new HashMap<>();
        BlueMapMap first = map("surface", firstSets);
        BlueMapMap second = map("caves", secondSets);
        when(api.getMaps()).thenReturn(List.of(first, second));
        when(resolver.resolve(api, "world")).thenReturn(List.of(first, second));
        BlueMapMarkerSink sink = sink(true);
        sink.attach(api);

        sink.replace(List.of(descriptor("region-a")));

        MarkerSet firstSet = firstSets.get(BlueMapMarkerSink.MARKER_SET_ID);
        MarkerSet secondSet = secondSets.get(BlueMapMarkerSink.MARKER_SET_ID);
        assertAll(
                () -> assertNotNull(firstSet),
                () -> assertNotNull(secondSet),
                () -> assertEquals("Territoires", firstSet.getLabel()),
                () -> assertTrue(firstSet.isToggleable()),
                () -> assertTrue(firstSet.isDefaultHidden()),
                () -> assertNotNull(firstSet.get("region-a")),
                () -> assertNotNull(secondSet.get("region-a"))
        );
    }

    @Test
    void replaceAtomicallySwapsCompleteMarkerSetAndDropsStaleMarkers() {
        Map<String, MarkerSet> sets = new HashMap<>();
        MarkerSet previous = MarkerSet.builder().label("old").build();
        previous.put("stale", mock(de.bluecolored.bluemap.api.markers.Marker.class));
        sets.put(BlueMapMarkerSink.MARKER_SET_ID, previous);
        BlueMapMap map = map("surface", sets);
        when(api.getMaps()).thenReturn(List.of(map));
        when(resolver.resolve(api, "world")).thenReturn(List.of(map));
        BlueMapMarkerSink sink = sink(false);
        sink.attach(api);

        sink.replace(List.of(descriptor("region-a"), descriptor("region-b")));

        MarkerSet replacement = sets.get(BlueMapMarkerSink.MARKER_SET_ID);
        assertAll(
                () -> assertNotSame(previous, replacement),
                () -> assertEquals(Set.of("region-a", "region-b"), replacement.getMarkers().keySet())
        );
    }

    @Test
    void emptyReplacementRemovesStaleMarkerSetsFromEveryActiveMap() {
        Map<String, MarkerSet> firstSets = setsWithStaleClaims();
        Map<String, MarkerSet> secondSets = setsWithStaleClaims();
        BlueMapMap first = map("surface", firstSets);
        BlueMapMap second = map("caves", secondSets);
        when(api.getMaps()).thenReturn(List.of(first, second));
        BlueMapMarkerSink sink = sink(false);
        sink.attach(api);

        sink.replace(List.of());

        assertAll(
                () -> assertFalse(firstSets.containsKey(BlueMapMarkerSink.MARKER_SET_ID)),
                () -> assertFalse(secondSets.containsKey(BlueMapMarkerSink.MARKER_SET_ID))
        );
    }

    @Test
    void replacementFactoryFailureLeavesExistingMarkerSetInstalled() {
        Map<String, MarkerSet> sets = new HashMap<>();
        MarkerSet previous = MarkerSet.builder().label("old").build();
        sets.put(BlueMapMarkerSink.MARKER_SET_ID, previous);
        BlueMapMap map = map("surface", sets);
        ClaimMarkerDescriptor first = descriptor("region-a");
        ClaimMarkerDescriptor second = descriptor("region-b");
        BlueMapShapeMarkerFactory factory = mock(BlueMapShapeMarkerFactory.class);
        when(factory.create(first)).thenReturn(mock(de.bluecolored.bluemap.api.markers.ShapeMarker.class));
        when(factory.create(second)).thenThrow(new IllegalStateException("invalid shape"));
        when(api.getMaps()).thenReturn(List.of(map));
        when(resolver.resolve(api, "world")).thenReturn(List.of(map));
        BlueMapMarkerSink sink = sink(false, resolver, factory);
        sink.attach(api);

        assertThrows(IllegalStateException.class, () -> sink.replace(List.of(first, second)));

        assertSame(previous, sets.get(BlueMapMarkerSink.MARKER_SET_ID));
    }

    @Test
    void replacementPublicationAttemptsEveryMapThenThrowsContextualFailures() {
        IllegalStateException firstFailure = new IllegalStateException("first unavailable");
        IllegalStateException laterFailure = new IllegalStateException("later unavailable");
        BlueMapMap firstFailing = failingMap("broken-first", firstFailure);
        Map<String, MarkerSet> healthySets = new HashMap<>();
        BlueMapMap healthy = map("surface", healthySets);
        BlueMapMap laterFailing = failingMap("broken-later", laterFailure);
        when(api.getMaps()).thenReturn(List.of(firstFailing, healthy, laterFailing));
        when(resolver.resolve(api, "world")).thenReturn(List.of(healthy));
        BlueMapMarkerSink sink = sink(false);
        sink.attach(api);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> sink.replace(List.of(descriptor("region-a")))
        );

        assertAll(
                () -> assertNotNull(healthySets.get(BlueMapMarkerSink.MARKER_SET_ID).get("region-a")),
                () -> assertTrue(thrown.getMessage().contains("replace")),
                () -> assertTrue(thrown.getMessage().contains("broken-first")),
                () -> assertSame(firstFailure, thrown.getCause()),
                () -> assertEquals(1, thrown.getSuppressed().length),
                () -> assertTrue(thrown.getSuppressed()[0].getMessage().contains("broken-later")),
                () -> assertSame(laterFailure, thrown.getSuppressed()[0].getCause())
        );
    }

    @Test
    void unmappedBlueMapWorldProducesNoWarning() {
        Map<String, MarkerSet> sets = setsWithStaleClaims();
        BlueMapMap map = map("surface", sets);
        when(api.getMaps()).thenReturn(List.of(map));
        when(resolver.resolve(api, "missing")).thenReturn(List.of());
        BlueMapMarkerSink sink = sink(false);
        sink.attach(api);

        sink.replace(List.of(descriptor("region-a", "missing")));

        assertAll(
                () -> assertFalse(sets.containsKey(BlueMapMarkerSink.MARKER_SET_ID)),
                () -> assertTrue(records.stream().noneMatch(record -> record.getLevel() == Level.WARNING))
        );
    }

    @Test
    void replaceResolvesEachClaimWorldOnlyOnce() {
        when(api.getMaps()).thenReturn(List.of());
        when(resolver.resolve(api, "missing")).thenReturn(List.of());
        BlueMapMarkerSink sink = sink(false);
        sink.attach(api);

        sink.replace(List.of(
                descriptor("region-a", "missing"),
                descriptor("region-b", "missing")
        ));

        verify(resolver).resolve(api, "missing");
    }

    @Test
    void unavailableMapIdDoesNotAbortReplacementBeforeHealthyMap() {
        IllegalStateException markerFailure = new IllegalStateException("markers unavailable");
        IllegalStateException idFailure = new IllegalStateException("id unavailable");
        BlueMapMap failing = failingMapWithUnavailableId(markerFailure, idFailure);
        Map<String, MarkerSet> healthySets = new HashMap<>();
        BlueMapMap healthy = map("surface", healthySets);
        when(api.getMaps()).thenReturn(List.of(failing, healthy));
        when(resolver.resolve(api, "world")).thenReturn(List.of(healthy));
        BlueMapMarkerSink sink = sink(false);
        sink.attach(api);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> sink.replace(List.of(descriptor("region-a")))
        );

        assertAll(
                () -> assertNotNull(healthySets.get(BlueMapMarkerSink.MARKER_SET_ID).get("region-a")),
                () -> assertTrue(thrown.getMessage().contains("<unavailable>")),
                () -> assertSame(markerFailure, thrown.getCause()),
                () -> assertEquals(1, markerFailure.getSuppressed().length),
                () -> assertSame(idFailure, markerFailure.getSuppressed()[0])
        );
    }

    @Test
    void concurrentReplacementAndStaleDetachNeverDiscardNewApi() throws Exception {
        BlueMapAPI oldApi = mock(BlueMapAPI.class);
        BlueMapAPI newApi = mock(BlueMapAPI.class);
        AtomicReference<BlueMapAPI> resolvedApi = new AtomicReference<>();
        BlueMapMap map = map("surface", new HashMap<>());
        BlueMapMapResolver recordingResolver = new BlueMapMapResolver() {
            @Override
            public Collection<BlueMapMap> resolve(BlueMapAPI resolved, String worldName) {
                resolvedApi.set(resolved);
                return List.of(map);
            }
        };
        BlueMapMarkerSink sink = sink(false, recordingResolver);
        int iterations = 2_000;
        CyclicBarrier barrier = new CyclicBarrier(3);
        AtomicInteger staleTransitions = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> attaches = executor.submit(() -> repeat(iterations, barrier, () -> sink.attach(newApi)));
            Future<?> detaches = executor.submit(() -> repeat(iterations, barrier, () -> sink.detach(oldApi)));

            for (int iteration = 0; iteration < iterations; iteration++) {
                sink.attach(oldApi);
                resolvedApi.set(null);
                barrier.await();
                barrier.await();
                sink.replace(List.of(descriptor("region-a")));
                if (resolvedApi.get() != newApi) {
                    staleTransitions.incrementAndGet();
                }
            }

            attaches.get();
            detaches.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(0, staleTransitions.get());
    }

    @Test
    void closeWaitsForInFlightReplacementThenRemovesItsMarkerSet() throws Exception {
        Map<String, MarkerSet> markerSets = new HashMap<>();
        BlueMapMap map = map("surface", markerSets);
        when(api.getMaps()).thenReturn(List.of(map));
        CountDownLatch replaceEntered = new CountDownLatch(1);
        CountDownLatch releaseReplace = new CountDownLatch(1);
        BlueMapMapResolver blockingResolver = new BlueMapMapResolver() {
            @Override
            public Collection<BlueMapMap> resolve(BlueMapAPI ignored, String worldName) {
                replaceEntered.countDown();
                await(releaseReplace);
                return List.of(map);
            }
        };
        BlueMapMarkerSink sink = sink(false, blockingResolver);
        sink.attach(api);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> replace = executor.submit(() -> sink.replace(List.of(descriptor("region-a"))));
            assertTrue(replaceEntered.await(1, TimeUnit.SECONDS));
            Future<?> close = executor.submit(sink::close);

            assertThrows(TimeoutException.class, () -> close.get(100, TimeUnit.MILLISECONDS));

            releaseReplace.countDown();
            replace.get(1, TimeUnit.SECONDS);
            close.get(1, TimeUnit.SECONDS);
            assertFalse(markerSets.containsKey(BlueMapMarkerSink.MARKER_SET_ID));
        } finally {
            releaseReplace.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void closeAttemptsEveryMapThenDiscardsApiWhenCleanupFails() {
        IllegalStateException firstFailure = new IllegalStateException("first unavailable");
        IllegalStateException laterFailure = new IllegalStateException("later unavailable");
        BlueMapMap firstFailing = failingMap("broken-first", firstFailure);
        Map<String, MarkerSet> healthySets = setsWithStaleClaims();
        BlueMapMap healthy = map("surface", healthySets);
        BlueMapMap laterFailing = failingMap("broken-later", laterFailure);
        when(api.getMaps()).thenReturn(List.of(firstFailing, healthy, laterFailing));
        BlueMapMarkerSink sink = sink(false);
        sink.attach(api);

        RuntimeException thrown = assertThrows(RuntimeException.class, sink::close);

        assertAll(
                () -> assertFalse(healthySets.containsKey(BlueMapMarkerSink.MARKER_SET_ID)),
                () -> assertTrue(thrown.getMessage().contains("clear")),
                () -> assertSame(firstFailure, thrown.getCause()),
                () -> assertEquals(1, thrown.getSuppressed().length),
                () -> assertSame(laterFailure, thrown.getSuppressed()[0].getCause())
        );
        assertDoesNotThrow(() -> sink.replace(List.of(descriptor("region-a"))));
        verifyNoInteractions(resolver);
    }

    private Map<String, MarkerSet> setsWithStaleClaims() {
        Map<String, MarkerSet> sets = new HashMap<>();
        sets.put(BlueMapMarkerSink.MARKER_SET_ID, MarkerSet.builder().label("old").build());
        return sets;
    }

    private BlueMapMarkerSink sink(boolean defaultHidden) {
        return sink(defaultHidden, resolver);
    }

    private BlueMapMarkerSink sink(boolean defaultHidden, BlueMapMapResolver mapResolver) {
        return sink(
                defaultHidden,
                mapResolver,
                new BlueMapShapeMarkerFactory(2)
        );
    }

    private BlueMapMarkerSink sink(
            boolean defaultHidden,
            BlueMapMapResolver mapResolver,
            BlueMapShapeMarkerFactory markerFactory
    ) {
        ClaimMarkerStyle style = new ClaimMarkerStyle(
                new RgbColor(52, 152, 219),
                new RgbColor(52, 152, 219),
                0.25F
        );
        BlueMapSettings settings = new BlueMapSettings(
                true,
                "Territoires",
                defaultHidden,
                2,
                5,
                Map.of(LandType.PLAYER, style)
        );
        return new BlueMapMarkerSink(settings, markerFactory, mapResolver, logger);
    }

    private BlueMapMap map(String id, Map<String, MarkerSet> markerSets) {
        BlueMapMap map = mock(BlueMapMap.class);
        when(map.getId()).thenReturn(id);
        when(map.getMarkerSets()).thenReturn(markerSets);
        return map;
    }

    private BlueMapMap failingMap(String id, RuntimeException failure) {
        BlueMapMap map = mock(BlueMapMap.class);
        when(map.getId()).thenReturn(id);
        when(map.getMarkerSets()).thenThrow(failure);
        return map;
    }

    private BlueMapMap failingMapWithUnavailableId(
            RuntimeException markerFailure,
            RuntimeException idFailure
    ) {
        BlueMapMap map = mock(BlueMapMap.class);
        when(map.getMarkerSets()).thenThrow(markerFailure);
        when(map.getId()).thenThrow(idFailure);
        return map;
    }

    private ClaimMarkerDescriptor descriptor(String id) {
        return descriptor(id, "world");
    }

    private ClaimMarkerDescriptor descriptor(String id, String world) {
        return new ClaimMarkerDescriptor(
                id,
                world,
                new ClaimMarkerGeometry(
                        2,
                        -3,
                        List.of(
                                new ClaimMarkerPoint(32, -48),
                                new ClaimMarkerPoint(48, -48),
                                new ClaimMarkerPoint(48, -32),
                                new ClaimMarkerPoint(32, -32)
                        ),
                        List.of()
                ),
                "Maison",
                "<b>Maison</b>",
                new ClaimMarkerStyle(
                        new RgbColor(52, 152, 219),
                        new RgbColor(46, 204, 113),
                        0.35F
                )
        );
    }

    private void repeat(int iterations, CyclicBarrier barrier, Runnable action) {
        try {
            for (int iteration = 0; iteration < iterations; iteration++) {
                barrier.await();
                action.run();
                barrier.await();
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }
}
