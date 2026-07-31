# BlueMap Grouped Claim Regions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-chunk BlueMap markers with simplified polygons for contiguous regions and publish dirty snapshots at most once every five minutes.

**Architecture:** A pure-Java polygonizer converts four-way-connected chunk sets into deterministic outer contours and holes. The synchronizer snapshots claims, groups them by land and world, marks itself dirty on mutations, and atomically replaces complete BlueMap marker sets from a scheduled refresh guarded by the existing circuit breaker.

**Tech Stack:** Java 21, Paper API 1.21.11, BlueMap API 2.7.7, FoliaLib 0.5.1, Gradle, JUnit 5.14, Mockito 5.23.

## Global Constraints

- Group only chunks with the same land UUID and world.
- Use four-way adjacency; diagonal-only chunks remain disconnected.
- Preserve land label, owner detail, style, and HTML escaping.
- Represent enclosed areas with BlueMap holes and remove collinear contour vertices.
- Force a rebuild at startup and when BlueMap is re-enabled.
- Delay ordinary BlueMap updates by `bluemap.refresh-interval-minutes`, default `5`, minimum `1`.
- Never let a `RuntimeException` or `LinkageError` from BlueMap or the refresh task escape into MSLands.
- Keep `de.bluecolored:bluemap-api:2.7.7` compile-only in the production artifact.

---

### Task 1: Deterministic chunk-region polygonizer

**Files:**
- Create: `src/main/java/fr/iban/lands/integration/claims/ClaimMarkerPoint.java`
- Create: `src/main/java/fr/iban/lands/integration/claims/ClaimMarkerGeometry.java`
- Create: `src/main/java/fr/iban/lands/integration/claims/ClaimRegionGeometryBuilder.java`
- Create: `src/test/java/fr/iban/lands/integration/claims/ClaimRegionGeometryBuilderTest.java`

**Interfaces:**
- Produces: `record ClaimMarkerPoint(int x, int z)` in block coordinates.
- Produces: `record ClaimMarkerGeometry(int anchorX, int anchorZ, List<ClaimMarkerPoint> outline, List<List<ClaimMarkerPoint>> holes)` with defensive immutable copies.
- Produces: `List<ClaimMarkerGeometry> ClaimRegionGeometryBuilder.build(Collection<SChunk> chunks)` sorted by `(anchorX, anchorZ)`.

- [ ] **Step 1: Write failing connectivity and simplification tests**

Create tests that build chunks with `new SChunk("survival", "world", x, z)` and assert:

```java
@Test
void collapsesThirtySixThousandRectangleChunksToFourCorners() {
    List<SChunk> chunks = IntStream.range(0, 200).boxed()
            .flatMap(x -> IntStream.range(0, 180)
                    .mapToObj(z -> chunk(x, z)))
            .toList();

    List<ClaimMarkerGeometry> regions = builder.build(chunks);

    assertEquals(1, regions.size());
    assertEquals(4, regions.getFirst().outline().size());
    assertEquals(Set.of(
            new ClaimMarkerPoint(0, 0),
            new ClaimMarkerPoint(3200, 0),
            new ClaimMarkerPoint(3200, 2880),
            new ClaimMarkerPoint(0, 2880)
    ), Set.copyOf(regions.getFirst().outline()));
}

@Test
void edgeNeighboursMergeButDiagonalNeighboursDoNot() {
    assertEquals(1, builder.build(List.of(chunk(0, 0), chunk(1, 0))).size());
    assertEquals(2, builder.build(List.of(chunk(0, 0), chunk(1, 1))).size());
}
```

Add an L-shaped region assertion with six outline corners, a donut made from a `3 x 3` square without `(1, 1)` asserting one four-corner hole, negative-coordinate coverage, deterministic output for reversed input, and empty-input coverage.

- [ ] **Step 2: Run the new test and verify RED**

Run:

```powershell
rtk .\gradlew.bat test --tests "fr.iban.lands.integration.claims.ClaimRegionGeometryBuilderTest" --no-daemon
```

Expected: compilation fails because `ClaimRegionGeometryBuilder`, `ClaimMarkerGeometry`, and `ClaimMarkerPoint` do not exist.

- [ ] **Step 3: Implement immutable geometry records**

Use deep defensive copies so later refreshes cannot mutate published descriptors:

```java
public record ClaimMarkerPoint(int x, int z) {
}

public record ClaimMarkerGeometry(
        int anchorX,
        int anchorZ,
        List<ClaimMarkerPoint> outline,
        List<List<ClaimMarkerPoint>> holes
) {
    public ClaimMarkerGeometry {
        outline = List.copyOf(outline);
        holes = holes.stream().map(List::copyOf).toList();
    }
}
```

- [ ] **Step 4: Implement four-way components and contour tracing**

In `ClaimRegionGeometryBuilder`, normalize input to coordinate cells, repeatedly flood-fill components with the four offsets `(-1,0)`, `(1,0)`, `(0,-1)`, `(0,1)`, and sort components by their smallest coordinate.

For each component, add the four directed edges of each unit chunk cell and cancel reverse edges. Trace the remaining edges into closed loops, choosing the rightmost available continuation at an ambiguous vertex so diagonally touching boundaries do not cross. Remove any middle point whose incoming and outgoing edges share the same axis.

Use signed area to identify the outer loop and holes, then scale points with `Math.multiplyExact(coordinate, 16)`:

```java
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
```

Keep `Cell`, `GridPoint`, `Edge`, direction ordering, flood-fill, signed-area, and collinearity helpers private to the builder.

- [ ] **Step 5: Run the focused geometry tests and verify GREEN**

Run the Task 1 test command again. Expected: all `ClaimRegionGeometryBuilderTest` cases pass, including the 36,000-chunk case.

- [ ] **Step 6: Commit the geometry slice**

```powershell
rtk git add src/main/java/fr/iban/lands/integration/claims/ClaimMarkerPoint.java src/main/java/fr/iban/lands/integration/claims/ClaimMarkerGeometry.java src/main/java/fr/iban/lands/integration/claims/ClaimRegionGeometryBuilder.java src/test/java/fr/iban/lands/integration/claims/ClaimRegionGeometryBuilderTest.java
rtk git commit -m "feat: polygonize contiguous claim regions"
```

---

### Task 2: Polygon descriptors and BlueMap shape conversion

**Files:**
- Modify: `src/main/java/fr/iban/lands/integration/claims/ClaimMarkerDescriptor.java:3-13`
- Modify: `src/main/java/fr/iban/lands/integration/claims/ClaimMarkerFactory.java:10-68`
- Modify: `src/main/java/fr/iban/lands/integration/bluemap/BlueMapShapeMarkerFactory.java:9-40`
- Modify: `src/test/java/fr/iban/lands/integration/claims/ClaimMarkerFactoryTest.java:21-150`
- Modify: `src/test/java/fr/iban/lands/integration/bluemap/BlueMapShapeMarkerFactoryTest.java:15-50`

**Interfaces:**
- Consumes: `ClaimMarkerGeometry` from Task 1.
- Produces: `Optional<ClaimMarkerDescriptor> ClaimMarkerFactory.create(String world, Land land, ClaimMarkerGeometry geometry)`.
- Temporarily preserves: `create(SChunk, Land)` and `markerId(SChunk)` so the existing incremental synchronizer remains buildable until Task 4.
- Produces: one BlueMap `ShapeMarker` with its outer `Shape` and `ShapeMarker.getHoles()` populated.

- [ ] **Step 1: Write failing region descriptor and shape tests**

Use this geometry in both tests:

```java
private final ClaimMarkerGeometry geometry = new ClaimMarkerGeometry(
        2, -3,
        List.of(
                new ClaimMarkerPoint(32, -48),
                new ClaimMarkerPoint(80, -48),
                new ClaimMarkerPoint(80, 0),
                new ClaimMarkerPoint(32, 0)
        ),
        List.of(List.of(
                new ClaimMarkerPoint(48, -32),
                new ClaimMarkerPoint(48, -16),
                new ClaimMarkerPoint(64, -16),
                new ClaimMarkerPoint(64, -32)
        ))
);
```

Assert the descriptor contains the same immutable geometry, world `world`, ID `land:<land UUID>:world:2:-3`, and existing escaped metadata. In `BlueMapShapeMarkerFactoryTest`, assert exact `Vector2d` outer points and one exact hole through `marker.getHoles()`.

Keep a compatibility test for `create(SChunk, Land)` asserting ID `chunk:2:-3` and a four-corner single-chunk geometry. Keep both subland exclusion tests.

- [ ] **Step 2: Run both focused tests and verify RED**

```powershell
rtk .\gradlew.bat test --tests "fr.iban.lands.integration.claims.ClaimMarkerFactoryTest" --tests "fr.iban.lands.integration.bluemap.BlueMapShapeMarkerFactoryTest" --no-daemon
```

Expected: compilation fails because descriptors still expose rectangle coordinates.

- [ ] **Step 3: Change the descriptor and add the region factory path**

```java
public record ClaimMarkerDescriptor(
        String id,
        String world,
        ClaimMarkerGeometry geometry,
        String label,
        String detail,
        ClaimMarkerStyle style
) {
}
```

Implement the region path and route both overloads through one private metadata method:

```java
public Optional<ClaimMarkerDescriptor> create(
        String world, Land land, ClaimMarkerGeometry geometry
) {
    String id = "land:" + land.getId() + ":" + world + ":"
            + geometry.anchorX() + ":" + geometry.anchorZ();
    return create(id, world, land, geometry);
}

public Optional<ClaimMarkerDescriptor> create(SChunk chunk, Land land) {
    int minX = Math.multiplyExact(chunk.getX(), 16);
    int minZ = Math.multiplyExact(chunk.getZ(), 16);
    ClaimMarkerGeometry geometry = new ClaimMarkerGeometry(
            chunk.getX(), chunk.getZ(),
            List.of(
                    new ClaimMarkerPoint(minX, minZ),
                    new ClaimMarkerPoint(Math.addExact(minX, 16), minZ),
                    new ClaimMarkerPoint(Math.addExact(minX, 16), Math.addExact(minZ, 16)),
                    new ClaimMarkerPoint(minX, Math.addExact(minZ, 16))
            ), List.of());
    return create(markerId(chunk), chunk.getWorld(), land, geometry);
}
```

The private method performs the existing subland/style checks and detail escaping. Retain `markerId(SChunk)` until Task 4.

- [ ] **Step 4: Convert geometry and holes to BlueMap shapes**

```java
public ShapeMarker create(ClaimMarkerDescriptor descriptor) {
    ClaimMarkerGeometry geometry = descriptor.geometry();
    return ShapeMarker.builder()
            .label(descriptor.label())
            .detail(descriptor.detail())
            .shape(toShape(geometry.outline()), 64)
            .holes(geometry.holes().stream().map(this::toShape).toArray(Shape[]::new))
            .lineWidth(lineWidth)
            .lineColor(toColor(descriptor.style().lineColor(), 1.0F))
            .fillColor(toColor(descriptor.style().fillColor(), descriptor.style().fillOpacity()))
            .depthTestEnabled(false)
            .build();
}

private Shape toShape(List<ClaimMarkerPoint> points) {
    return new Shape(points.stream()
            .map(point -> new Vector2d(point.x(), point.z()))
            .toList());
}
```

- [ ] **Step 5: Run the focused tests and the complete test suite**

Run the Task 2 command, then:

```powershell
rtk .\gradlew.bat test --no-daemon
```

Expected: region geometry, compatibility, metadata, and BlueMap hole tests pass, with no production compilation regression.

- [ ] **Step 6: Commit the descriptor and conversion slice**

```powershell
rtk git add src/main/java/fr/iban/lands/integration/claims/ClaimMarkerDescriptor.java src/main/java/fr/iban/lands/integration/claims/ClaimMarkerFactory.java src/main/java/fr/iban/lands/integration/bluemap/BlueMapShapeMarkerFactory.java src/test/java/fr/iban/lands/integration/claims/ClaimMarkerFactoryTest.java src/test/java/fr/iban/lands/integration/bluemap/BlueMapShapeMarkerFactoryTest.java
rtk git commit -m "feat: describe BlueMap claim polygons"
```

---

### Task 3: Atomic marker-set replacement

**Files:**
- Modify: `src/main/java/fr/iban/lands/integration/claims/ClaimMarkerSink.java:3-13`
- Modify: `src/main/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSink.java:14-210`
- Modify: `src/test/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSinkTest.java:43-532`

**Interfaces:**
- Produces: `void ClaimMarkerSink.replace(Collection<ClaimMarkerDescriptor> markers)` while temporarily retaining incremental methods until Task 4.
- Produces: one complete replacement `MarkerSet` per resolved BlueMap map.

- [ ] **Step 1: Write failing atomic-swap tests**

Add:

```java
@Test
void replaceAtomicallySwapsCompleteMarkerSetAndDropsStaleMarkers() {
    Map<String, MarkerSet> sets = new HashMap<>();
    MarkerSet previous = MarkerSet.builder().label("old").build();
    previous.put("stale", mock(Marker.class));
    sets.put(BlueMapMarkerSink.MARKER_SET_ID, previous);
    BlueMapMap map = map("surface", sets);
    when(api.getMaps()).thenReturn(List.of(map));
    when(resolver.resolve(api, "world")).thenReturn(List.of(map));
    BlueMapMarkerSink sink = sink(false);
    sink.attach(api);

    sink.replace(List.of(descriptor("region-a"), descriptor("region-b")));

    MarkerSet replacement = sets.get(BlueMapMarkerSink.MARKER_SET_ID);
    assertNotSame(previous, replacement);
    assertEquals(Set.of("region-a", "region-b"), replacement.getMarkers().keySet());
}
```

Add tests asserting an empty replacement removes stale sets from every active map, a shape-factory failure while building the second marker leaves the old set object installed, and publication failures attempt every map and retain contextual map IDs. Keep all existing incremental and lifecycle tests passing during this transition.

- [ ] **Step 2: Run the sink test and verify RED**

```powershell
rtk .\gradlew.bat test --tests "fr.iban.lands.integration.bluemap.BlueMapMarkerSinkTest" --no-daemon
```

Expected: compilation fails because `replace` does not exist.

- [ ] **Step 3: Add replacement to the sink contract and build off-map sets**

Add `replace(Collection<ClaimMarkerDescriptor> markers)` to `ClaimMarkerSink`. Implement it as one synchronized method:

```java
public synchronized void replace(Collection<ClaimMarkerDescriptor> markers) {
    BlueMapAPI api = activeApi;
    if (api == null) return;

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
            if (replacement == null) map.getMarkerSets().remove(MARKER_SET_ID);
            else map.getMarkerSets().put(MARKER_SET_ID, replacement);
        } catch (RuntimeException failure) {
            firstFailure = collectFailure(firstFailure, replacementFailure(map, failure));
        }
    }
    throwIfFailed(firstFailure);
}
```

`newMarkerSet()` applies label, toggleability, and default-hidden settings. Marker conversion must finish before the publication loop begins. Keep identity-safe attach/detach, failure aggregation, and synchronized close unchanged.

- [ ] **Step 4: Run sink tests and the complete suite**

```powershell
rtk .\gradlew.bat test --tests "fr.iban.lands.integration.bluemap.BlueMapMarkerSinkTest" --no-daemon
rtk .\gradlew.bat test --no-daemon
```

Expected: both atomic replacement and legacy incremental behavior pass, including failure and concurrency tests.

- [ ] **Step 5: Commit atomic replacement**

```powershell
rtk git add src/main/java/fr/iban/lands/integration/claims/ClaimMarkerSink.java src/main/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSink.java src/test/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSinkTest.java
rtk git commit -m "feat: replace BlueMap marker sets atomically"
```

---

### Task 4: Grouped dirty snapshot synchronizer

**Files:**
- Modify: `src/main/java/fr/iban/lands/integration/claims/ClaimMarkerSink.java:3-15`
- Modify: `src/main/java/fr/iban/lands/integration/claims/ClaimMarkerSynchronizer.java:9-61`
- Modify: `src/main/java/fr/iban/lands/integration/claims/ClaimMarkerFactory.java`
- Modify: `src/main/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSink.java`
- Modify: `src/test/java/fr/iban/lands/integration/claims/ClaimMarkerSynchronizerTest.java:24-202`
- Modify: `src/test/java/fr/iban/lands/integration/claims/ClaimMarkerFactoryTest.java`
- Modify: `src/test/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSinkTest.java`

**Interfaces:**
- Consumes: `ClaimRegionGeometryBuilder.build(Collection<SChunk>)`, region `ClaimMarkerFactory.create`, and atomic `ClaimMarkerSink.replace`.
- Produces: `void ClaimMarkerSynchronizer.refreshIfDirty()`.
- Finalizes: `ClaimMarkerSink` with only `replace` and `close`; removes obsolete per-chunk factory and sink operations.

- [ ] **Step 1: Write failing grouped-snapshot and dirty-state tests**

Replace per-chunk sink assertions with a `RecordingSink` storing replacement collections. Add:

```java
@Test
void rebuildGroupsByLandAndWorldAndIgnoresRemoteClaims() {
    source.put(chunk(0, 0), playerLand);
    source.put(chunk(1, 0), playerLand);
    source.put(chunk(2, 0), otherLand);
    source.put(new SChunk("creative", "world", 3, 0), playerLand);

    synchronizer.rebuild();

    assertEquals(1, sink.replacements().size());
    assertEquals(2, sink.replacements().getFirst().size());
}

@Test
void thousandsOfMutationsProduceOneScheduledRefresh() {
    IntStream.range(0, 10_000).forEach(x -> synchronizer.syncChunk(chunk(x, 0)));
    synchronizer.refreshIfDirty();
    synchronizer.refreshIfDirty();
    assertEquals(1, sink.replacements().size());
}
```

Also assert `syncLand` marks dirty without immediate publication, unsupported sublands and wilderness are absent, same-land chunks in different worlds remain separate, touching chunks from different lands remain separate, a sink failure leaves dirty state available for retry, and a source callback invoking `syncChunk` during `rebuild()` causes the next `refreshIfDirty()` to publish again.

- [ ] **Step 2: Run the synchronizer test and verify RED**

```powershell
rtk .\gradlew.bat test --tests "fr.iban.lands.integration.claims.ClaimMarkerSynchronizerTest" --no-daemon
```

Expected: assertions fail because the synchronizer still publishes one marker per chunk immediately.

- [ ] **Step 3: Implement synchronized dirty refreshes**

Add a `ClaimRegionGeometryBuilder` field and `AtomicBoolean dirty`. Keep the public constructor signature stable by constructing the pure geometry builder internally. Implement:

```java
@Override
public void syncChunk(SChunk chunk) {
    if (serverName.equals(chunk.getServer())) dirty.set(true);
}

@Override
public void syncLand(Land land) {
    dirty.set(true);
}

public synchronized void refreshIfDirty() {
    if (dirty.compareAndSet(true, false)) publishSnapshot();
}

@Override
public synchronized void rebuild() {
    dirty.set(false);
    publishSnapshot();
}
```

Make `close` synchronized. In `publishSnapshot`, catch `RuntimeException | LinkageError`, restore `dirty` to `true`, and rethrow. Because the dirty flag is cleared before snapshotting, a mutation during construction remains set for the next scheduled pass.

- [ ] **Step 4: Snapshot, group, polygonize, and publish once**

Create immutable entries rather than retaining live `ConcurrentHashMap` entry views:

```java
List<Map.Entry<SChunk, Land>> snapshot = source.claims().entrySet().stream()
        .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
        .toList();
Map<LandWorldKey, ClaimGroup> groups = new LinkedHashMap<>();
for (Map.Entry<SChunk, Land> claim : snapshot) {
    SChunk chunk = claim.getKey();
    Land land = claim.getValue();
    if (!serverName.equals(chunk.getServer()) || source.isWilderness(land)) continue;
    LandWorldKey key = new LandWorldKey(land.getId(), chunk.getWorld());
    groups.computeIfAbsent(key, ignored -> new ClaimGroup(land)).chunks().add(chunk);
}
List<ClaimMarkerDescriptor> markers = groups.entrySet().stream()
        .flatMap(entry -> geometryBuilder.build(entry.getValue().chunks()).stream()
                .map(geometry -> markerFactory.create(
                        entry.getKey().world(), entry.getValue().land(), geometry)))
        .flatMap(Optional::stream)
        .sorted(Comparator.comparing(ClaimMarkerDescriptor::world)
                .thenComparing(ClaimMarkerDescriptor::id))
        .toList();
sink.replace(markers);
```

Use private `LandWorldKey(UUID landId, String world)` and this mutable accumulator, which never escapes snapshot construction:

```java
private static final class ClaimGroup {
    private final Land land;
    private final List<SChunk> chunks = new ArrayList<>();

    private ClaimGroup(Land land) {
        this.land = land;
    }

    private Land land() { return land; }
    private List<SChunk> chunks() { return chunks; }
}
```

- [ ] **Step 5: Remove obsolete incremental APIs and tests**

Reduce the final sink contract to:

```java
public interface ClaimMarkerSink extends AutoCloseable {
    void replace(Collection<ClaimMarkerDescriptor> markers);
    @Override void close();
}
```

Remove `ClaimMarkerFactory.create(SChunk, Land)`, `markerId(SChunk)`, and their compatibility test. Remove public `clear`, `put`, and `remove` from `BlueMapMarkerSink`; retain a private cleanup method used by `close`. Replace legacy sink tests with replacement equivalents for unresolved worlds, partial map failures, unavailable map IDs, stale detach, in-flight close, and failed cleanup.

- [ ] **Step 6: Run focused and complete tests**

```powershell
rtk .\gradlew.bat test --tests "fr.iban.lands.integration.claims.ClaimMarkerSynchronizerTest" --tests "fr.iban.lands.integration.claims.ClaimMarkerFactoryTest" --tests "fr.iban.lands.integration.bluemap.BlueMapMarkerSinkTest" --no-daemon
rtk .\gradlew.bat test --no-daemon
```

Expected: grouped snapshots, dirty coalescing, retry behavior, atomic sink behavior, and all existing integration tests pass.

- [ ] **Step 7: Commit grouped synchronization**

```powershell
rtk git add src/main/java/fr/iban/lands/integration/claims/ClaimMarkerSink.java src/main/java/fr/iban/lands/integration/claims/ClaimMarkerSynchronizer.java src/main/java/fr/iban/lands/integration/claims/ClaimMarkerFactory.java src/main/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSink.java src/test/java/fr/iban/lands/integration/claims/ClaimMarkerSynchronizerTest.java src/test/java/fr/iban/lands/integration/claims/ClaimMarkerFactoryTest.java src/test/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSinkTest.java
rtk git commit -m "feat: coalesce grouped claim refreshes"
```

---

### Task 5: Configurable five-minute refresh lifecycle

**Files:**
- Modify: `src/main/java/fr/iban/lands/integration/claims/BlueMapSettings.java:9-159`
- Modify: `src/main/resources/config.yml:14-32`
- Modify: `src/main/java/fr/iban/lands/integration/bluemap/BlueMapClaimVisualization.java:23-189`
- Modify: `src/test/java/fr/iban/lands/integration/claims/BlueMapSettingsTest.java:15-61`
- Modify: `src/test/java/fr/iban/lands/integration/bluemap/BlueMapClaimVisualizationTest.java:27-253`
- Modify for the added `BlueMapSettings` constructor argument: `src/test/java/fr/iban/lands/integration/claims/ClaimMarkerFactoryTest.java`
- Modify for the added `BlueMapSettings` constructor argument: `src/test/java/fr/iban/lands/integration/claims/ClaimMarkerSynchronizerTest.java`
- Modify for the added `BlueMapSettings` constructor argument: `src/test/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSinkTest.java`
- Modify for the added `BlueMapSettings` constructor argument: `src/test/java/fr/iban/lands/integration/bluemap/FaultTolerantClaimVisualizationTest.java`

**Interfaces:**
- Produces: `int BlueMapSettings.refreshIntervalMinutes()`.
- Produces: package-private `BlueMapClaimVisualization.startRefresh(RefreshScheduler scheduler, long periodTicks)`.
- Produces: nested `RefreshScheduler.schedule(Runnable action, long delayTicks, long periodTicks)` returning nested `Cancellable.cancel()`.

- [ ] **Step 1: Write failing setting and scheduling tests**

Extend settings tests:

```java
assertEquals(5, BlueMapSettings.load(Map.<String, Object>of()::get, ignored -> {})
        .refreshIntervalMinutes());

Map<String, Object> values = Map.of("bluemap.refresh-interval-minutes", 0);
List<String> warnings = new ArrayList<>();
BlueMapSettings settings = BlueMapSettings.load(values::get, warnings::add);
assertEquals(5, settings.refreshIntervalMinutes());
assertEquals(1, warnings.size());
```

In `BlueMapClaimVisualizationTest`, add a recording scheduler and cancellable. Assert a scheduled callback calls `synchronizer.refreshIfDirty()`, repeated callback execution delegates each check, a refresh exception is contained and opens the breaker, scheduling failure is rethrown for `LandsPlugin`'s existing initialization guard, and `close()` cancels exactly once before closing the synchronizer. Retain the existing tests proving later BlueMap enable forces `rebuild()` and synchronous initial enable only attaches.

- [ ] **Step 2: Run settings and lifecycle tests and verify RED**

Run:

```powershell
rtk .\gradlew.bat test --tests "fr.iban.lands.integration.claims.BlueMapSettingsTest" --tests "fr.iban.lands.integration.bluemap.BlueMapClaimVisualizationTest" --no-daemon
```

Expected: compilation fails because the interval and refresh scheduler do not exist.

- [ ] **Step 3: Add the validated refresh interval**

Add `int refreshIntervalMinutes` after `lineWidth` in `BlueMapSettings`, load it with:

```java
int refreshMinutes = positiveInt(valueAt, "refresh-interval-minutes", 5, warning);
```

Pass it into the record constructor and add this documented default to `config.yml`:

```yaml
bluemap:
  enabled: true
  refresh-interval-minutes: 5
```

Update every direct test construction of `BlueMapSettings` with `5`.

- [ ] **Step 4: Own and cancel the scheduled refresh in the lifecycle**

Add nested testable abstractions and a nullable scheduled handle:

```java
interface RefreshScheduler {
    Cancellable schedule(Runnable action, long delayTicks, long periodTicks);
}

interface Cancellable {
    void cancel();
}

void startRefresh(RefreshScheduler scheduler, long periodTicks) {
    Cancellable scheduled = scheduler.schedule(
            () -> breaker.execute("scheduled refresh", synchronizer::refreshIfDirty),
            periodTicks,
            periodTicks
    );
    synchronized (this) {
        if (closed) scheduled.cancel();
        else refreshTask = scheduled;
    }
}
```

Make `close()` idempotently mark the lifecycle closed and capture/clear `refreshTask` under synchronization. Cancel it with `breaker.cleanup("scheduled refresh cancel", scheduled::cancel)`, then perform the existing listener and synchronizer cleanup through separate `breaker.cleanup` calls so one failed cleanup cannot prevent the others.

In `create(LandsPlugin)`, after constructing `lifecycleOwner`, schedule FoliaLib's asynchronous timer and roll back with `lifecycleOwner.close()` if scheduling fails:

```java
long periodTicks = Math.multiplyExact(settings.refreshIntervalMinutes(), 60L * 20L);
try {
    lifecycleOwner.startRefresh((action, delay, period) -> {
        WrappedTask task = plugin.getScheduler().runTimerAsync(action, delay, period);
        return task::cancel;
    }, periodTicks);
} catch (RuntimeException | LinkageError failure) {
    lifecycleOwner.close();
    throw failure;
}
```

Keep the startup `queueInitialClaimVisualizationRebuild()` and later BlueMap enable forced `synchronizer.rebuild()` behavior unchanged.

- [ ] **Step 5: Run settings, lifecycle, and circuit-breaker tests**

Run:

```powershell
rtk .\gradlew.bat test --tests "fr.iban.lands.LandsPluginTest" --tests "fr.iban.lands.integration.claims.BlueMapSettingsTest" --tests "fr.iban.lands.integration.bluemap.BlueMapClaimVisualizationTest" --tests "fr.iban.lands.integration.bluemap.FaultTolerantClaimVisualizationTest" --no-daemon
```

Expected: all tests pass, scheduled exceptions do not escape, startup still queues the forced rebuild, later BlueMap enable rebuilds, and shutdown cancels the task.

- [ ] **Step 6: Commit scheduled refresh lifecycle**

```powershell
rtk git add src/main/java/fr/iban/lands/integration/claims/BlueMapSettings.java src/main/resources/config.yml src/main/java/fr/iban/lands/integration/bluemap/BlueMapClaimVisualization.java src/test/java/fr/iban/lands/integration/claims/BlueMapSettingsTest.java src/test/java/fr/iban/lands/integration/claims/ClaimMarkerFactoryTest.java src/test/java/fr/iban/lands/integration/claims/ClaimMarkerSynchronizerTest.java src/test/java/fr/iban/lands/integration/bluemap/BlueMapClaimVisualizationTest.java src/test/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSinkTest.java src/test/java/fr/iban/lands/integration/bluemap/FaultTolerantClaimVisualizationTest.java
rtk git commit -m "feat: refresh BlueMap claims every five minutes"
```

---

### Task 6: Full regression, artifact inspection, and performance acceptance

**Files:**
- Modify only if a regression assertion reveals a real defect in Tasks 1-5.

**Interfaces:**
- Consumes: all grouped-region and refresh lifecycle behavior from Tasks 1-5.
- Produces: verified `build/libs/MSLands-1.1.1.jar` without bundled BlueMap API classes.

- [ ] **Step 1: Run the complete clean verification build**

Run:

```powershell
rtk .\gradlew.bat clean check shadowJar --no-daemon
```

Expected: `BUILD SUCCESSFUL`, with every existing and new JUnit test passing.

- [ ] **Step 2: Inspect the shaded artifact**

Run:

```powershell
rtk jar tf build/libs/MSLands-1.1.1.jar
```

Confirm the listing contains MSLands integration classes and contains no path beginning with `de/bluecolored/`.

- [ ] **Step 3: Re-run the 36,000-chunk acceptance test in isolation**

Run:

```powershell
rtk .\gradlew.bat test --tests "fr.iban.lands.integration.claims.ClaimRegionGeometryBuilderTest.collapsesThirtySixThousandRectangleChunksToFourCorners" --no-daemon
```

Expected: one region, four outline points, no timeout, and PASS.

- [ ] **Step 4: Confirm repository scope and commit any verification-only correction**

Run:

```powershell
rtk git status --short
rtk git diff --check
```

Expected: no uncommitted files and no whitespace errors. If a real defect was corrected during verification, stage only those correction files and commit with `fix: correct grouped BlueMap verification regression` before repeating Steps 1-4.
