# BlueMap claim visibility distance

## Goal

Reduce browser rendering load by hiding MSLands claim polygons when the BlueMap camera is too far away, while keeping large claims visible when the camera is close to one of their edges.

## Configuration

MSLands owns the setting because it creates the dynamic BlueMap markers:

```yaml
bluemap:
  max-visible-distance: 2000
```

- The value is expressed in blocks.
- The default is `2000`.
- `0` disables the limit and keeps markers visible at every distance.
- Negative, fractional, non-numeric, or excessively large values fall back to `2000` and emit the existing BlueMap configuration warning.

## Marker behavior

BlueMap measures marker distance from the marker position, which is the center of a shape's bounding box. Applying a fixed 2,000-block limit would incorrectly hide the edge of a very large claim when its center is farther away.

For each bounded marker, MSLands therefore applies:

`BlueMap max distance = configured visible distance + shape bounding radius`

The bounding radius is the half-diagonal of the polygon bounding box. This guarantees that a camera within the configured distance of any point in the bounding box is not hidden because of the marker's center position. When the configured value is `0`, MSLands does not override BlueMap's unlimited default.

## Alternatives considered

1. A fixed distance from the marker center is simpler, but produces missing claim borders for large regions.
2. The selected size-adjusted distance preserves border visibility and still culls ordinary distant regions.
3. True spatial lazy loading would require a custom BlueMap web addon and server endpoint. It would reduce network transfer too, but is substantially larger in scope and is not part of this change.

## Integration

`BlueMapSettings` loads and validates the new value. `BlueMapClaimVisualization` passes it to `BlueMapShapeMarkerFactory`, which computes the effective distance and sets it on each `ShapeMarker`.

No claim synchronization or failure-isolation behavior changes. Invalid configuration remains non-fatal and uses the documented fallback.

## Verification

- Settings tests cover the default, a valid value, `0`, and invalid values.
- Shape marker factory tests cover the effective distance, large-shape adjustment, and unlimited mode.
- The complete test suite and shaded JAR build must pass.
