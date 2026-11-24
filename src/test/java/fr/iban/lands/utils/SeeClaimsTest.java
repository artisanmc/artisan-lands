package fr.iban.lands.utils;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.iban.lands.LandsPlugin;
import fr.iban.lands.api.LandRepository;
import fr.iban.lands.model.SChunk;
import fr.iban.lands.model.land.Land;
import fr.iban.lands.model.land.PlayerLand;
import fr.iban.lands.model.land.SystemLand;
import fr.iban.lands.model.land.SubLand;
import fr.iban.lands.model.land.GuildLand;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeeClaimsTest {
    private final LandsPlugin plugin = mock(LandsPlugin.class);
    private final LandRepository repository = mock(LandRepository.class);
    private final PlatformScheduler scheduler = mock(PlatformScheduler.class);
    private final WrappedTask task = mock(WrappedTask.class);
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final Map<SChunk, Land> claims = new HashMap<>();
    private final YamlConfiguration config = new YamlConfiguration();
    private final List<BlockDisplay> spawned = new ArrayList<>();
    private final List<Location> anchors = new ArrayList<>();
    private Runnable tick;
    private SeeClaims preview;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.createBlockData(any(Material.class))).thenAnswer(call -> {
            BlockData data = mock(BlockData.class);
            when(data.getMaterial()).thenReturn(call.getArgument(0));
            return data;
        });
        when(plugin.getServerName()).thenReturn("server");
        when(plugin.getLandRepository()).thenReturn(repository);
        when(plugin.getScheduler()).thenReturn(scheduler);
        when(plugin.getConfig()).thenReturn(config);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 8, 64, 8, 90, 30));
        when(player.isOnline()).thenReturn(true);
        when(repository.getLandAt(any(SChunk.class))).thenAnswer(invocation -> claims.get(invocation.getArgument(0)));
        when(scheduler.runAtEntityTimer(eq(player), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    tick = invocation.getArgument(1);
                    return task;
                });
        stubSpawning(world);
        preview = new SeeClaims(player, plugin);
    }

    private void stubSpawning(World targetWorld) {
        when(targetWorld.spawn(any(Location.class), eq(BlockDisplay.class),
                ArgumentMatchers.<Consumer<BlockDisplay>>any())).thenAnswer(invocation -> {
            BlockDisplay display = mock(BlockDisplay.class);
            when(display.isValid()).thenReturn(true);
            Consumer<BlockDisplay> configure = invocation.getArgument(2);
            configure.accept(display);
            verify(display).setVisibleByDefault(false);
            verify(display).setPersistent(false);
            spawned.add(display);
            anchors.add(((Location) invocation.getArgument(0)).clone());
            return display;
        });
    }

    @Test
    void isolatedClaimUsesSixteenBlockPanelsCoveringTheWholeWorldHeight() {
        PlayerLand land = mock(PlayerLand.class);
        UUID owner = player.getUniqueId();
        when(land.getOwner()).thenReturn(owner);
        claims.put(chunk(0, 0), land);

        preview.showWalls();

        assertEquals(96, spawned.size());
        for (BlockDisplay display : spawned) {
            verify(player).showEntity(plugin, display);
            verify(display).setBlock(argThat(data -> data.getMaterial() == Material.LIME_STAINED_GLASS));
            Transformation transform = transformation(display);
            assertEquals(16, transform.getScale().y, 0.0001);
            assertTrue(64 + transform.getTranslation().y >= -64);
            assertTrue(64 + transform.getTranslation().y + transform.getScale().y <= 320);
            assertTrue((transform.getScale().x == 16 && Math.abs(transform.getScale().z - 0.01f) < 0.0001f)
                    || (transform.getScale().z == 16 && Math.abs(transform.getScale().x - 0.01f) < 0.0001f));
        }
        for (int y = -64; y < 320; y += 16) {
            final int bottom = y;
            assertEquals(4, spawned.stream().filter(display ->
                    64 + transformation(display).getTranslation().y == bottom).count());
        }
        verify(world, never()).getChunkAt(anyInt(), anyInt());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        assertNotNull(tick);
    }

    private Transformation transformation(BlockDisplay display) {
        ArgumentCaptor<Transformation> captor = ArgumentCaptor.forClass(Transformation.class);
        verify(display).setTransformation(captor.capture());
        return captor.getValue();
    }

    @Test
    void adjacentChunksOfSameLandHaveNoInternalWallAndUnchangedWallsAreReused() {
        Land land = mock(Land.class);
        claims.put(chunk(0, 0), land);
        claims.put(chunk(1, 0), land);
        preview.showWalls();
        assertEquals(144, spawned.size());
        for (int i = 0; i < spawned.size(); i++) {
            Transformation transform = transformation(spawned.get(i));
            if (transform.getScale().x < 1) {
                double x = anchors.get(i).getX() + transform.getTranslation().x;
                assertTrue(x < 0.02 || x > 31, "No panel may appear on the internal x=16 border");
            }
        }

        tick.run();
        preview.forceUpdate();

        assertEquals(144, spawned.size());
        spawned.forEach(display -> verify(display, never()).remove());
    }

    private SChunk chunk(int x, int z) {
        return new SChunk("server", "world", x, z);
    }

    @Test
    void stopRemovesPanelsOnceAndLateTimerCannotRecreateThem() {
        claims.put(chunk(0, 0), mock(Land.class));
        preview.showWalls();
        preview.showWalls();
        verify(scheduler, times(1)).runAtEntityTimer(eq(player), any(Runnable.class), anyLong(), anyLong());

        preview.stop();
        preview.stop();
        tick.run();
        preview.forceUpdate();

        assertEquals(96, spawned.size());
        spawned.forEach(display -> verify(display, times(1)).remove());
        verify(task, times(1)).cancel();
    }

    @Test
    void failedVisibilitySetupDoesNotLeaveAnOrphanEntity() {
        claims.put(chunk(0, 0), mock(Land.class));
        doThrow(new IllegalStateException("visibility failed"))
                .when(player).showEntity(eq(plugin), any(BlockDisplay.class));
        assertThrows(IllegalStateException.class, preview::showWalls);
        preview.stop();
        assertEquals(1, spawned.size());
        verify(spawned.getFirst(), times(1)).remove();
    }

    @ParameterizedTest
    @ValueSource(strings = {"system", "guild", "otherGuild", "otherPlayer"})
    void preservesTerritoryColors(String type) {
        Land land;
        Material expected;
        if (type.equals("system")) {
            land = mock(SystemLand.class);
            expected = Material.RED_STAINED_GLASS;
        } else if (type.equals("otherPlayer")) {
            PlayerLand owned = mock(PlayerLand.class);
            when(owned.getOwner()).thenReturn(UUID.randomUUID());
            land = owned;
            expected = Material.WHITE_STAINED_GLASS;
        } else {
            GuildLand guild = mock(GuildLand.class);
            when(guild.isGuildMember(any(UUID.class))).thenReturn(type.equals("guild"));
            land = guild;
            expected = type.equals("guild") ? Material.LIGHT_BLUE_STAINED_GLASS : Material.WHITE_STAINED_GLASS;
        }
        SubLand sub = subland(world, "server", 2, 65, 2, 9, 72, 9);
        when(land.getSubLands()).thenReturn(Map.of(UUID.randomUUID(), sub));
        claims.put(chunk(0, 0), land);
        preview.showWalls();
        assertEquals(102, spawned.size());
        spawned.forEach(display -> verify(display).setBlock(argThat(data -> data.getMaterial() == expected)));
    }

    @Test
    void ownershipChangeUpdatesColorWithoutRespawningPanels() {
        PlayerLand land = mock(PlayerLand.class);
        UUID owner = player.getUniqueId();
        when(land.getOwner()).thenReturn(owner);
        SubLand sub = subland(world, "server", 2, 65, 2, 9, 72, 9);
        when(land.getSubLands()).thenReturn(Map.of(UUID.randomUUID(), sub));
        claims.put(chunk(0, 0), land);
        preview.showWalls();
        when(land.getOwner()).thenReturn(UUID.randomUUID());

        tick.run();

        assertEquals(102, spawned.size());
        spawned.forEach(display -> {
            verify(display).setBlock(argThat(data -> data.getMaterial() == Material.LIME_STAINED_GLASS));
            verify(display).setBlock(argThat(data -> data.getMaterial() == Material.WHITE_STAINED_GLASS));
            verify(display, never()).remove();
        });
    }

    @Test
    void removedClaimsDisappearAndMovementRevealsOnlyNearbyClaims() {
        claims.put(chunk(0, 0), mock(Land.class));
        claims.put(chunk(11, 0), mock(Land.class));
        preview.showWalls();
        assertEquals(96, spawned.size());
        List<BlockDisplay> old = List.copyOf(spawned);
        claims.remove(chunk(0, 0));
        tick.run();
        old.forEach(display -> verify(display).remove());
        assertEquals(96, spawned.size());

        when(player.getLocation()).thenReturn(new Location(world, 24, 64, 8));
        tick.run();

        assertEquals(192, spawned.size());
    }

    @Test
    void worldChangeRemovesOldPanelsBeforeShowingNewWorld() {
        claims.put(chunk(0, 0), mock(Land.class));
        preview.showWalls();
        List<BlockDisplay> old = List.copyOf(spawned);
        World otherWorld = mock(World.class);
        when(otherWorld.getName()).thenReturn("other");
        when(otherWorld.getMinHeight()).thenReturn(0);
        when(otherWorld.getMaxHeight()).thenReturn(256);
        stubSpawning(otherWorld);
        claims.put(new SChunk("server", "other", 0, 0), mock(Land.class));
        when(player.getWorld()).thenReturn(otherWorld);
        when(player.getLocation()).thenReturn(new Location(otherWorld, 8, 64, 8));

        tick.run();

        assertEquals(160, spawned.size());
        old.forEach(display -> verify(display).remove());
        for (int i = 96; i < 160; i++) {
            assertSame(otherWorld, anchors.get(i).getWorld());
            assertEquals(16, transformation(spawned.get(i)).getScale().y);
        }
    }

    @Test
    void disconnectStopsTimerAndRemovesAllPanels() {
        claims.put(chunk(0, 0), mock(Land.class));
        preview.showWalls();
        when(player.isOnline()).thenReturn(false);
        tick.run();
        verify(task).cancel();
        spawned.forEach(display -> verify(display).remove());
        tick.run();
        assertEquals(96, spawned.size());
    }

    @Test
    void panelsStayInsideNegativeChunkCoordinatesAndIgnorePlayerRotation() {
        when(player.getLocation()).thenReturn(new Location(world, -8, 64, -8, 123, 45));
        claims.put(chunk(-1, -1), mock(Land.class));
        preview.showWalls();
        assertEquals(96, spawned.size());
        for (int i = 0; i < spawned.size(); i++) {
            Location origin = anchors.get(i);
            Transformation transform = transformation(spawned.get(i));
            double x = origin.getX() + transform.getTranslation().x;
            double z = origin.getZ() + transform.getTranslation().z;
            assertTrue(x >= -16 && x + transform.getScale().x <= 0);
            assertTrue(z >= -16 && z + transform.getScale().z <= 0);
            assertEquals(0, origin.getYaw());
            assertEquals(0, origin.getPitch());
        }
    }

    @Test
    void defaultRangeReachesTenChunks() {
        claims.put(chunk(10, 0), mock(Land.class));
        claims.put(chunk(11, 0), mock(Land.class));
        preview.showWalls();
        assertEquals(96, spawned.size());
        spawned.forEach(display -> verify(display).setViewRange(16));
    }

    @Test
    void partialTopSegmentStaysWithinWorldHeight() {
        when(world.getMaxHeight()).thenReturn(325);
        claims.put(chunk(0, 0), mock(Land.class));
        preview.showWalls();
        assertEquals(100, spawned.size());
        assertEquals(4, spawned.stream().filter(display ->
                transformation(display).getScale().y == 5).count());
        spawned.forEach(display -> {
            Transformation transform = transformation(display);
            assertTrue(64 + transform.getTranslation().y + transform.getScale().y <= 325);
        });
    }

    @Test
    void panelBudgetKeepsTheNearestFaces() {
        when(player.getLocation()).thenReturn(new Location(world, 8, 72, 8));
        config.set("visualization.max-panels", 4);
        preview = new SeeClaims(player, plugin);
        claims.put(chunk(0, 0), mock(Land.class));
        claims.put(chunk(8, 0), mock(Land.class));
        preview.showWalls();
        assertEquals(4, spawned.size());
        for (int i = 0; i < 4; i++) {
            assertTrue(anchors.get(i).getX() + transformation(spawned.get(i)).getTranslation().x < 17);
            assertEquals(64, anchors.get(i).getY() + transformation(spawned.get(i)).getTranslation().y);
        }

        // Moving less than the reanchor threshold must still update the vertical priority.
        when(player.getLocation()).thenReturn(new Location(world, 8, 88, 8));
        tick.run();
        assertEquals(8, spawned.size());
        spawned.subList(0, 4).forEach(display -> verify(display).remove());
        for (int i = 4; i < 8; i++) {
            assertEquals(80, anchors.get(i).getY() + transformation(spawned.get(i)).getTranslation().y);
        }
        tick.run();
        assertEquals(8, spawned.size());
    }

    private SubLand subland(World target, String server, int x, int y, int z, int x2, int y2, int z2) {
        SubLand sub = mock(SubLand.class);
        Cuboid cuboid = new Cuboid(target, x, y, z, x2, y2, z2) {
            @Override public World getWorld() { return target; }
        };
        when(sub.getCuboid()).thenReturn(cuboid);
        when(sub.getServer()).thenReturn(server);
        return sub;
    }

    @Test
    void nearbySublandOfAnotherOwnerHasSixFacesAndIsNotDuplicatedAcrossChunks() {
        PlayerLand land = mock(PlayerLand.class);
        when(land.getOwner()).thenReturn(UUID.randomUUID());
        SubLand sub = subland(world, "server", 2, 65, 2, 9, 72, 9);
        Map<UUID, SubLand> subs = new HashMap<>();
        subs.put(UUID.randomUUID(), sub);
        when(land.getSubLands()).thenReturn(subs);
        claims.put(chunk(0, 0), land);
        claims.put(chunk(1, 0), land);
        preview.showWalls();
        assertEquals(150, spawned.size());
        List<BlockDisplay> subPanels = spawned.stream().filter(display -> transformation(display).getScale().y < 16).toList();
        assertEquals(6, subPanels.size());
        subPanels.forEach(display -> {
            verify(display).setBlock(argThat(data -> data.getMaterial() == Material.WHITE_STAINED_GLASS));
            Transformation t = transformation(display);
            assertTrue(t.getScale().x <= 16 && t.getScale().y <= 16 && t.getScale().z <= 16);
            assertTrue(t.getScale().x > 0 && t.getScale().y > 0 && t.getScale().z > 0);
            verify(player).showEntity(plugin, display);
        });
        tick.run();
        assertEquals(150, spawned.size());
        subs.clear();
        tick.run();
        subPanels.forEach(display -> verify(display).remove());
    }

    @Test
    void ignoresForeignWorldServerAndDistantSublands() {
        Land land = mock(Land.class);
        Map<UUID, SubLand> subs = Map.of(
                UUID.randomUUID(), subland(mock(World.class), "server", 2, 65, 2, 9, 72, 9),
                UUID.randomUUID(), subland(world, "other", 2, 65, 2, 9, 72, 9),
                UUID.randomUUID(), subland(world, "server", 10000, 65, 10000, 10010, 72, 10010));
        when(land.getSubLands()).thenReturn(subs);
        claims.put(chunk(0, 0), land);
        preview.showWalls();
        assertEquals(96, spawned.size());
    }

    @Test
    void hugeSublandSurfacesStayBoundedAndShareTheEntityBudget() {
        config.set("visualization.max-panels", 12);
        preview = new SeeClaims(player, plugin);
        Land land = mock(Land.class);
        SubLand sub = subland(world, "server", -1000000, 65, -1000000, 1000000, 72, 1000000);
        when(land.getSubLands()).thenReturn(Map.of(UUID.randomUUID(), sub));
        claims.put(chunk(0, 0), land);
        preview.showWalls();
        assertEquals(12, spawned.size());
        spawned.forEach(display -> {
            Transformation t = transformation(display);
            assertTrue(t.getScale().x <= 16 && t.getScale().y <= 16 && t.getScale().z <= 16);
        });
        preview.stop();
        spawned.forEach(display -> verify(display).remove());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }
}
