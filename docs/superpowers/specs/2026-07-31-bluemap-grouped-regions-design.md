# BlueMap Grouped Claim Regions Design

**Date:** 2026-07-31

## Context

MSLands currently publishes one BlueMap `ShapeMarker` for every claimed chunk. A server with about 36,000 claimed chunks therefore sends and renders about 36,000 interactive markers, which causes severe browser-side performance problems.

The claim system itself must remain immediate and independent from BlueMap. BlueMap may display changes with a delay of up to five minutes. A BlueMap failure must never stop or crash MSLands.

## Goals

- Reduce BlueMap marker count from one marker per chunk to one marker per contiguous region of a land.
- Preserve each land's name, owner label, color, and tooltip.
- Represent irregular outlines and enclosed holes accurately.
- Coalesce large claim/unclaim batches into one refresh at most every five minutes.
- Keep startup and BlueMap re-enable behavior predictable.
- Preserve the existing failure circuit breaker and optional dependency isolation.

## Non-goals

- Immediate BlueMap updates after every claim mutation.
- Merging regions owned by different lands, even when they touch.
- Changing MSLands claim persistence or gameplay behavior.
- Guaranteeing a low marker count when every claimed chunk is isolated.

## Chosen Approach

### Region grouping

The synchronizer takes a snapshot of all claims for the local server and groups them by `(land UUID, world)`. Within each group, it finds connected components using four-way chunk adjacency. Diagonally touching chunks remain separate components.

Each connected component becomes one marker. This preserves land-specific metadata while reducing marker cardinality to the number of disconnected land regions instead of the number of chunks.

### Polygon construction

For every chunk in a component, the geometry builder adds its four directed grid edges and cancels edges shared with another chunk in the same component. The remaining edges form the component boundary.

Boundary edges are traced into closed loops. Collinear vertices are removed before conversion from chunk coordinates to block coordinates. The outer loop is used as the BlueMap marker shape and enclosed loops are supplied as BlueMap holes.

This naturally removes internal chunk borders. A solid rectangular territory, regardless of area, becomes a four-corner polygon.

### Marker identity and metadata

Each marker ID is deterministic and based on the land UUID, world, and a stable component anchor such as its lexicographically smallest chunk coordinate. A disconnected land creates one marker for each connected component.

Every marker keeps the existing land label, owner label, fill and border style. Touching regions from different lands are never combined.

### Refresh model

The visualization maintains a thread-safe dirty flag:

- `syncChunk` and `syncLand` only mark the visualization dirty.
- The initial startup rebuild is forced after the repository is available.
- A repeating task checks the dirty flag every five minutes and rebuilds only when necessary.
- A later BlueMap enable/reload attaches the marker sink and forces a rebuild.
- Plugin shutdown cancels the repeating task and unregisters BlueMap listeners.

The five-minute interval is configurable as `bluemap.refresh-interval-minutes`, with a default of `5` and a safe minimum of `1`.

### Atomic publication

A refresh builds complete replacement marker sets off-map. Once all descriptors and BlueMap shapes for a map are ready, the integration swaps the marker set into that map in one operation.

This avoids exposing a clear-then-refill intermediate state to browser clients and automatically removes stale component IDs after claims are removed, split, merged, or transferred.

### Concurrency

The repository stores claims in a `ConcurrentHashMap`, so the refresh can copy its entries into an immutable snapshot before grouping. All geometry work operates only on that snapshot. A claim mutation occurring during the copy may appear in the following refresh, which is acceptable under the five-minute consistency window.

Only one rebuild may run at a time. If a mutation happens during a rebuild, the dirty flag remains set so the next scheduled pass includes it.

## Failure Isolation

The existing `BlueMapFailureCircuitBreaker` remains around lifecycle callbacks, scheduled refreshes, marker conversion, publication, and cleanup. A `RuntimeException` or `LinkageError` disables BlueMap visualization for the current session and logs the failure once. It does not affect claim storage, claim commands, or the rest of MSLands.

The scheduler callback must not allow an integration exception to escape into the server scheduler.

## Alternatives Considered

### Greedy rectangles

Combining chunks into maximal rectangles is simpler but creates multiple overlapping or adjacent shapes for irregular lands and retains internal seams. It reduces marker count less effectively than contour polygons.

### Immediate per-land contour rebuilds

Rebuilding only the changed land provides fresher map data but requires tracking old land ownership and stale component IDs for unclaims, deletes, transfers, splits, and merges. The accepted five-minute delay makes a dirty full-snapshot rebuild safer and simpler.

### One marker per land

A single marker cannot directly represent multiple disconnected outer regions. Using one marker per connected component matches BlueMap's shape model while preserving correct geometry.

## Verification

Automated tests will cover:

- A 36,000-chunk solid rectangle produces one marker with four simplified outer vertices.
- An irregular contiguous region produces the expected boundary.
- A donut produces one marker with one hole.
- Edge-connected chunks merge; diagonal-only chunks do not.
- Disconnected components of one land produce separate markers.
- Adjacent chunks belonging to different lands remain separate.
- Claim, unclaim, rename, and delete events mark the visualization dirty without publishing immediately.
- Thousands of mutations before a timer tick produce one rebuild.
- A mutation during a rebuild schedules a later rebuild instead of being lost.
- Startup and BlueMap re-enable force a rebuild.
- Marker sets are atomically replaced and stale markers disappear.
- Scheduler, geometry, and BlueMap publication failures are contained by the circuit breaker.
- Plugin shutdown cancels scheduling and removes listeners safely.

The final verification runs the complete test suite and builds the shaded plugin JAR, then confirms that no BlueMap API classes are bundled into it.
