package fr.iban.lands.utils;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Transformation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CuboidSelectionPreviewTest {
    private final Plugin plugin = mock(Plugin.class);
    private final World world = mock(World.class);
    private final Player player = mock(Player.class);
    private final List<BlockDisplay> spawned = new ArrayList<>();
    private final List<Location> anchors = new ArrayList<>();
    private CuboidSelectionPreview preview;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.createBlockData(any(Material.class))).thenAnswer(call -> {
            BlockData data = mock(BlockData.class);
            when(data.getMaterial()).thenReturn(call.getArgument(0));
            return data;
        });
        when(world.getName()).thenReturn("world");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 12.5, 65, -3.5, 90, 30));
        when(player.isOnline()).thenReturn(true);
        when(world.spawn(any(Location.class), eq(BlockDisplay.class), ArgumentMatchers.<Consumer<BlockDisplay>>any()))
                .thenAnswer(call -> {
                    BlockDisplay display = mock(BlockDisplay.class);
                    ((Consumer<BlockDisplay>) call.getArgument(2)).accept(display);
                    verify(display).setVisibleByDefault(false);
                    verify(display).setPersistent(false);
                    spawned.add(display);
                    anchors.add(((Location) call.getArgument(0)).clone());
                    return display;
                });
        preview = new CuboidSelectionPreview(plugin);
    }

    @Test
    void twoPrivateGlassDisplaysEncloseSelectionWithOppositeWinding() {
        preview.show(player, cuboid(29, 71, -1, 10, 64, -5));
        assertEquals(2, spawned.size());
        for (int i = 0; i < 2; i++) {
            BlockDisplay display = spawned.get(i);
            verify(player).showEntity(plugin, display);
            verify(display).setBlock(argThat(data -> data.getMaterial() == Material.LIME_STAINED_GLASS));
            ArgumentCaptor<Transformation> captor = ArgumentCaptor.forClass(Transformation.class);
            verify(display).setTransformation(captor.capture());
            Transformation transform = captor.getValue();
            Vector3f scale = transform.getScale();
            assertEquals(i == 0 ? 20 : -20, scale.x);
            assertEquals(8, scale.y);
            assertEquals(5, scale.z);
            for (float x : new float[]{0, 1}) {
                for (float y : new float[]{0, 1}) {
                    for (float z : new float[]{0, 1}) {
                        Vector3f point = new Vector3f(x, y, z).mul(scale).add(transform.getTranslation())
                                .add((float) anchors.get(i).getX(), (float) anchors.get(i).getY(), (float) anchors.get(i).getZ());
                        assertTrue(point.x == 10 || point.x == 30);
                        assertTrue(point.y == 64 || point.y == 72);
                        assertTrue(point.z == -5 || point.z == 0);
                    }
                }
            }
            assertEquals(0, anchors.get(i).getYaw());
            assertEquals(0, anchors.get(i).getPitch());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"clear", "quit", "world", "disable"})
    void endingPreviewRemovesEveryFaceOnce(String reason) {
        preview.show(player, cuboid(0, 0, 0, 1, 1, 1));
        switch (reason) {
            case "clear" -> preview.clear(player);
            case "quit" -> {
                PlayerQuitEvent event = mock(PlayerQuitEvent.class);
                when(event.getPlayer()).thenReturn(player);
                preview.onQuit(event);
            }
            case "world" -> preview.onWorldChange(new PlayerChangedWorldEvent(player, world));
            case "disable" -> preview.close();
        }
        preview.clear(player);
        preview.close();
        spawned.forEach(display -> verify(display, times(1)).remove());
    }

    @Test
    void updatingSelectionReplacesAllFaces() {
        preview.show(player, cuboid(0, 0, 0, 1, 1, 1));
        preview.show(player, cuboid(0, 0, 0, 19, 7, 4));
        assertEquals(4, spawned.size());
        spawned.subList(0, 2).forEach(display -> verify(display).remove());
        spawned.subList(2, 4).forEach(display -> verify(display, never()).remove());
    }

    @Test
    void largeSelectionUsesOnlyTwoDisplaysAndNoRemoteChunkLoads() {
        preview.show(player, cuboid(-1_000_000, -64, -1_000_000, 1_000_000, 319, 1_000_000));
        assertEquals(2, spawned.size());
        anchors.forEach(anchor -> assertEquals(player.getLocation().getX(), anchor.getX()));
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void movingFartherKeepsSelectionEntitiesNearTheViewer() {
        preview.show(player, cuboid(0, 0, 0, 19, 7, 4));
        Location destination = new Location(world, 150, 65, 0);
        preview.onMove(new PlayerMoveEvent(player, player.getLocation(), destination));
        assertEquals(4, spawned.size());
        spawned.subList(0, 2).forEach(display -> verify(display).remove());
        anchors.subList(2, 4).forEach(anchor -> assertEquals(150, anchor.getX()));
    }

    @Test
    void offlineOrWrongWorldDoesNotSpawnPanels() {
        when(player.isOnline()).thenReturn(false);
        preview.show(player, cuboid(0, 0, 0, 1, 1, 1));
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(mock(World.class));
        preview.show(player, cuboid(0, 0, 0, 1, 1, 1));
        assertTrue(spawned.isEmpty());
    }

    @Test
    void clearingOnePlayerDoesNotRemoveAnotherPlayersPanels() {
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.getWorld()).thenReturn(world);
        when(other.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(other.isOnline()).thenReturn(true);
        preview.show(player, cuboid(0, 0, 0, 1, 1, 1));
        preview.show(other, cuboid(0, 0, 0, 1, 1, 1));
        preview.clear(player);
        spawned.subList(0, 2).forEach(display -> verify(display).remove());
        spawned.subList(2, 4).forEach(display -> {
            verify(display, never()).remove();
            verify(player, never()).showEntity(plugin, display);
        });
        preview.close();
        spawned.forEach(display -> verify(display, times(1)).remove());
    }

    private Cuboid cuboid(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Cuboid(world, x1, y1, z1, x2, y2, z2) {
            @Override public World getWorld() { return world; }
        };
    }
}
