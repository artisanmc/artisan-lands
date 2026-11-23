package fr.iban.lands.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CuboidTest {
    private Cuboid cuboid(int size) {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        return new Cuboid(world, 0, 0, 0, size - 1, size - 1, size - 1) {
            @Override
            public World getWorld() {
                return world;
            }
        };
    }

    @Test
    void largeSelectionHasBoundedParticleCount() {
        var points = cuboid(200).getCubeEdgeLocations(0.5);
        assertTrue(points.size() <= 4096, "Particle preview must stay bounded: " + points.size());
    }

    @Test
    void enormousSelectionAndTinySpacingRemainFastAndBounded() {
        Cuboid cuboid = cuboid(30_000_000);
        assertTimeout(java.time.Duration.ofSeconds(1), () -> {
            var points = cuboid.getCubeEdgeLocations(Double.MIN_VALUE);
            assertTrue(points.size() <= 4096);
            assertTrue(points.contains(cuboid.getUpperSW().add(1, 1, 1)));
        });
    }

    @Test
    void invalidSpacingIsRejected() {
        Cuboid cuboid = cuboid(1);
        for (double spacing : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> cuboid.getCubeEdgeLocations(spacing));
        }
    }

    @Test
    void singleBlockRetainsAllEdgePointsWithoutDuplicates() {
        var points = cuboid(1).getCubeEdgeLocations(0.5);
        assertEquals(20, points.size());
        assertEquals(points.size(), new HashSet<>(points).size());
    }

    @Test
    void unevenSpacingIncludesEveryCornerAndOnlyEdges() {
        Cuboid cuboid = cuboid(2);
        var points = cuboid.getCubeEdgeLocations(0.7);
        for (int x : new int[]{0, 2}) {
            for (int y : new int[]{0, 2}) {
                for (int z : new int[]{0, 2}) {
                    assertTrue(points.contains(new Location(cuboid.getWorld(), x, y, z)));
                }
            }
        }
        for (Location point : points) {
            int boundaries = (point.getX() == 0 || point.getX() == 2 ? 1 : 0)
                    + (point.getY() == 0 || point.getY() == 2 ? 1 : 0)
                    + (point.getZ() == 0 || point.getZ() == 2 ? 1 : 0);
            assertTrue(boundaries >= 2);
        }
    }
}
