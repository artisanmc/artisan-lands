package fr.iban.lands.integration.claims;

import fr.iban.lands.model.SChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimRegionGeometryBuilderTest {

    private final ClaimRegionGeometryBuilder builder = new ClaimRegionGeometryBuilder();

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

    @Test
    void preservesSixCornersForAnLShapedRegion() {
        ClaimMarkerGeometry geometry = builder.build(List.of(
                chunk(0, 0), chunk(0, 1), chunk(1, 1)
        )).getFirst();

        assertEquals(6, geometry.outline().size());
        assertEquals(Set.of(
                new ClaimMarkerPoint(0, 0),
                new ClaimMarkerPoint(16, 0),
                new ClaimMarkerPoint(16, 16),
                new ClaimMarkerPoint(32, 16),
                new ClaimMarkerPoint(32, 32),
                new ClaimMarkerPoint(0, 32)
        ), Set.copyOf(geometry.outline()));
    }

    @Test
    void tracesTheMissingCenterOfADonutAsAFourCornerHole() {
        List<SChunk> chunks = IntStream.range(0, 3).boxed()
                .flatMap(x -> IntStream.range(0, 3)
                        .filter(z -> x != 1 || z != 1)
                        .mapToObj(z -> chunk(x, z)))
                .toList();

        ClaimMarkerGeometry geometry = builder.build(chunks).getFirst();

        assertEquals(4, geometry.outline().size());
        assertEquals(1, geometry.holes().size());
        assertEquals(Set.of(
                new ClaimMarkerPoint(16, 16),
                new ClaimMarkerPoint(16, 32),
                new ClaimMarkerPoint(32, 32),
                new ClaimMarkerPoint(32, 16)
        ), Set.copyOf(geometry.holes().getFirst()));
    }

    @Test
    void scalesNegativeChunkCoordinatesToBlockCoordinates() {
        ClaimMarkerGeometry geometry = builder.build(List.of(chunk(-2, -3))).getFirst();

        assertEquals(-2, geometry.anchorX());
        assertEquals(-3, geometry.anchorZ());
        assertEquals(Set.of(
                new ClaimMarkerPoint(-32, -48),
                new ClaimMarkerPoint(-16, -48),
                new ClaimMarkerPoint(-16, -32),
                new ClaimMarkerPoint(-32, -32)
        ), Set.copyOf(geometry.outline()));
    }

    @Test
    void producesTheSameOrderedGeometryForReversedInput() {
        List<SChunk> chunks = List.of(
                chunk(5, 2), chunk(5, 3), chunk(6, 3), chunk(-1, 0)
        );

        assertEquals(builder.build(chunks), builder.build(chunks.reversed()));
    }

    @Test
    void returnsNoGeometryForEmptyInput() {
        assertEquals(List.of(), builder.build(List.of()));
    }

    private SChunk chunk(int x, int z) {
        return new SChunk("survival", "world", x, z);
    }
}
