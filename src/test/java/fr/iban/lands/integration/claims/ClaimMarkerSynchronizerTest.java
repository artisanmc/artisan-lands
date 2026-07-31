package fr.iban.lands.integration.claims;

import fr.iban.lands.enums.LandType;
import fr.iban.lands.model.SChunk;
import fr.iban.lands.model.land.Land;
import fr.iban.lands.model.land.PlayerLand;
import fr.iban.lands.model.land.SubLand;
import fr.iban.lands.model.land.SystemLand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimMarkerSynchronizerTest {

    private final SChunk chunk = chunk(4, -2);
    private final Land wilderness = new SystemLand(UUID.randomUUID(), "Wilderness");
    private final Land playerLand = playerLand(UUID.randomUUID(), "Maison");

    private FakeClaimSource source;
    private RecordingSink sink;
    private ClaimMarkerSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        source = new FakeClaimSource(wilderness);
        sink = new RecordingSink();
        synchronizer = new ClaimMarkerSynchronizer(
                "survival",
                source,
                sink,
                new ClaimMarkerFactory(settings(), land -> "Alice")
        );
    }

    @Test
    void rebuildGroupsByLandAndWorldAndIgnoresRemoteClaims() {
        Land otherLand = playerLand(UUID.randomUUID(), "Voisin");
        source.put(chunk(0, 0), playerLand);
        source.put(chunk(1, 0), playerLand);
        source.put(chunk(2, 0), otherLand);
        source.put(new SChunk("creative", "world", 3, 0), playerLand);

        synchronizer.rebuild();

        assertEquals(1, sink.replacements().size());
        assertEquals(2, sink.replacements().getFirst().size());
        assertEquals(Set.of(
                "land:" + playerLand.getId() + ":world:0:0",
                "land:" + otherLand.getId() + ":world:2:0"
        ), markerIds(sink.replacements().getFirst()));
    }

    @Test
    void sameLandChunksInDifferentWorldsRemainSeparate() {
        source.put(chunk(0, 0), playerLand);
        source.put(new SChunk("survival", "world_nether", 1, 0), playerLand);

        synchronizer.rebuild();

        assertEquals(Set.of("world", "world_nether"), sink.replacements().getFirst().stream()
                .map(ClaimMarkerDescriptor::world)
                .collect(Collectors.toSet()));
    }

    @Test
    void distinctLandInstancesWithTheSameIdFormOneGroup() {
        UUID landId = UUID.randomUUID();
        source.put(chunk(0, 0), playerLand(landId, "Maison"));
        source.put(chunk(1, 0), playerLand(landId, "Maison renommée"));

        synchronizer.rebuild();

        assertEquals(1, sink.replacements().getFirst().size());
        assertEquals("land:" + landId + ":world:0:0", sink.replacements().getFirst().getFirst().id());
    }

    @Test
    void rebuildExcludesWildernessAndUnsupportedSublands() {
        source.put(chunk(0, 0), wilderness);
        source.put(chunk(1, 0), new SubLand(UUID.randomUUID(), "Mine"));

        synchronizer.rebuild();

        assertTrue(sink.replacements().getFirst().isEmpty());
    }

    @Test
    void thousandsOfMutationsProduceOneScheduledRefresh() {
        IntStream.range(0, 10_000).forEach(x -> synchronizer.syncChunk(chunk(x, 0)));

        synchronizer.refreshIfDirty();
        synchronizer.refreshIfDirty();

        assertEquals(1, sink.replacements().size());
    }

    @Test
    void remoteChunkMutationDoesNotScheduleARefresh() {
        synchronizer.syncChunk(new SChunk("creative", "world", 0, 0));

        synchronizer.refreshIfDirty();

        assertTrue(sink.replacements().isEmpty());
    }

    @Test
    void syncLandMarksDirtyWithoutPublishingImmediately() {
        source.put(chunk, playerLand);

        synchronizer.syncLand(playerLand);

        assertTrue(sink.replacements().isEmpty());
        synchronizer.refreshIfDirty();
        assertEquals(1, sink.replacements().size());
    }

    @Test
    void runtimeSinkFailureKeepsDirtyStateAvailableForRetry() {
        sink.failNext(new IllegalStateException("BlueMap unavailable"));
        synchronizer.syncChunk(chunk);

        assertThrows(IllegalStateException.class, synchronizer::refreshIfDirty);
        synchronizer.refreshIfDirty();

        assertEquals(2, sink.replaceAttempts());
        assertEquals(1, sink.replacements().size());
    }

    @Test
    void linkageSinkFailureKeepsDirtyStateAvailableForRetry() {
        sink.failNext(new LinkageError("BlueMap linkage unavailable"));
        synchronizer.syncChunk(chunk);

        assertThrows(LinkageError.class, synchronizer::refreshIfDirty);
        synchronizer.refreshIfDirty();

        assertEquals(2, sink.replaceAttempts());
        assertEquals(1, sink.replacements().size());
    }

    @Test
    void mutationDuringRebuildSchedulesAnotherPublication() {
        source.put(chunk, playerLand);
        source.onNextClaims(() -> synchronizer.syncChunk(chunk));

        synchronizer.rebuild();
        synchronizer.refreshIfDirty();

        assertEquals(2, sink.replacements().size());
    }

    @Test
    void rebuildWaitsForInFlightRefresh() throws Exception {
        BlockingClaims blocking = source.blockNextClaims();
        synchronizer.syncChunk(chunk);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> refresh = executor.submit(synchronizer::refreshIfDirty);
            assertTrue(blocking.entered().await(1, TimeUnit.SECONDS));
            Future<?> rebuild = executor.submit(synchronizer::rebuild);

            assertThrows(TimeoutException.class, () -> rebuild.get(100, TimeUnit.MILLISECONDS));

            blocking.release().countDown();
            refresh.get(1, TimeUnit.SECONDS);
            rebuild.get(1, TimeUnit.SECONDS);
            assertEquals(2, sink.replacements().size());
        } finally {
            blocking.release().countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void closeWaitsForInFlightRebuild() throws Exception {
        BlockingClaims blocking = source.blockNextClaims();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> rebuild = executor.submit(synchronizer::rebuild);
            assertTrue(blocking.entered().await(1, TimeUnit.SECONDS));
            Future<?> close = executor.submit(synchronizer::close);

            assertThrows(TimeoutException.class, () -> close.get(100, TimeUnit.MILLISECONDS));

            blocking.release().countDown();
            rebuild.get(1, TimeUnit.SECONDS);
            close.get(1, TimeUnit.SECONDS);
            assertTrue(sink.closed());
        } finally {
            blocking.release().countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void noOpVisualizationAcceptsEveryLifecycleCall() {
        ClaimVisualization visualization = ClaimVisualization.noop();

        assertDoesNotThrow(() -> {
            visualization.rebuild();
            visualization.syncChunk(chunk);
            visualization.syncLand(playerLand);
            visualization.close();
        });
    }

    private Set<String> markerIds(Collection<ClaimMarkerDescriptor> markers) {
        return markers.stream().map(ClaimMarkerDescriptor::id).collect(Collectors.toSet());
    }

    private SChunk chunk(int x, int z) {
        return new SChunk("survival", "world", x, z);
    }

    private PlayerLand playerLand(UUID id, String name) {
        return new PlayerLand(id, UUID.randomUUID(), name);
    }

    private BlueMapSettings settings() {
        ClaimMarkerStyle style = new ClaimMarkerStyle(
                new RgbColor(1, 2, 3),
                new RgbColor(4, 5, 6),
                0.25F
        );
        return new BlueMapSettings(
                true,
                "Territoires",
                false,
                2,
                5,
                Map.of(
                        LandType.PLAYER, style,
                        LandType.GUILD, style,
                        LandType.SYSTEM, style
                )
        );
    }

    private static final class FakeClaimSource implements ClaimSource {

        private final Land wilderness;
        private final Map<SChunk, Land> claims = new ConcurrentHashMap<>();
        private Runnable nextClaims = () -> { };

        private FakeClaimSource(Land wilderness) {
            this.wilderness = wilderness;
        }

        private void put(SChunk chunk, Land land) {
            claims.put(chunk, land);
        }

        private void onNextClaims(Runnable callback) {
            nextClaims = callback;
        }

        private BlockingClaims blockNextClaims() {
            BlockingClaims blocking = new BlockingClaims(new CountDownLatch(1), new CountDownLatch(1));
            onNextClaims(() -> {
                blocking.entered().countDown();
                await(blocking.release());
            });
            return blocking;
        }

        @Override
        public Map<SChunk, Land> claims() {
            Runnable callback = nextClaims;
            nextClaims = () -> { };
            callback.run();
            return claims;
        }

        @Override
        public Land landAt(SChunk chunk) {
            return claims.getOrDefault(chunk, wilderness);
        }

        @Override
        public Collection<SChunk> chunks(Land land) {
            return claims.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(land))
                    .map(Map.Entry::getKey)
                    .toList();
        }

        @Override
        public boolean isWilderness(Land land) {
            return wilderness.equals(land);
        }

        private static void await(CountDownLatch latch) {
            try {
                latch.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
        }
    }

    private static final class RecordingSink implements ClaimMarkerSink {

        private final List<List<ClaimMarkerDescriptor>> replacements = new ArrayList<>();
        private Throwable nextFailure;
        private int replaceAttempts;
        private boolean closed;

        @Override
        public synchronized void replace(Collection<ClaimMarkerDescriptor> markers) {
            replaceAttempts++;
            Throwable failure = nextFailure;
            nextFailure = null;
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof LinkageError linkageError) {
                throw linkageError;
            }
            replacements.add(List.copyOf(markers));
        }

        @Override
        public synchronized void close() {
            closed = true;
        }

        private synchronized void failNext(Throwable failure) {
            nextFailure = failure;
        }

        private synchronized List<List<ClaimMarkerDescriptor>> replacements() {
            return List.copyOf(replacements);
        }

        private synchronized int replaceAttempts() {
            return replaceAttempts;
        }

        private synchronized boolean closed() {
            return closed;
        }
    }

    private record BlockingClaims(CountDownLatch entered, CountDownLatch release) {
    }
}
