package fr.iban.lands.listeners;

import com.tcoded.folialib.impl.PlatformScheduler;
import fr.iban.lands.LandsPlugin;
import fr.iban.lands.api.LandRepository;
import fr.iban.lands.enums.Flag;
import fr.iban.lands.model.land.Land;
import fr.iban.lands.model.land.PlayerLand;
import fr.iban.lands.model.land.SystemLand;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.PortalCreateEvent.CreateReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalListenersTest {

    private final LandsPlugin plugin = mock(LandsPlugin.class);
    private final LandRepository landRepository = mock(LandRepository.class);
    private final PlatformScheduler scheduler = mock(PlatformScheduler.class);
    private final World nether = mock(World.class);
    private final Player player = mock(Player.class);
    private final Block portalBlock = mock(Block.class);
    private final YamlConfiguration config = new YamlConfiguration();
    private final fr.iban.lands.messages.LandMessages messages = mock(fr.iban.lands.messages.LandMessages.class);
    private PortalListeners listener;

    @BeforeEach
    void setUp() {
        when(plugin.getLandRepository()).thenReturn(landRepository);
        when(plugin.getScheduler()).thenReturn(scheduler);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getMessages()).thenReturn(messages);
        when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);
        listener = new PortalListeners(plugin);
    }

    @Test
    void roofPortalInFlaggedLandIsBrokenAfterDelayAndPlayerWarned() {
        Location location = new Location(nether, 0, 130, 0);
        Land land = land(true);
        when(landRepository.getLandAt(location)).thenReturn(land);

        listener.onNetherRoofPortalCreate(fireEvent(location));

        verify(messages).send(player, "portal.nether_roof", "minutes", 10L);
        runScheduledBreak(10);
        verify(portalBlock).breakNaturally();
    }

    @Test
    void roofPortalClaimedBeforeDelayIsKept() {
        Location location = new Location(nether, 0, 130, 0);
        Land flagged = land(true);
        Land unflagged = land(false);
        when(landRepository.getLandAt(location)).thenReturn(flagged, unflagged);

        listener.onNetherRoofPortalCreate(fireEvent(location));

        runScheduledBreak(10);
        verify(portalBlock, never()).breakNaturally();
    }

    @Test
    void roofPortalInUnflaggedLandIsKept() {
        Location location = new Location(nether, 0, 130, 0);
        Land land = land(false);
        when(landRepository.getLandAt(location)).thenReturn(land);

        listener.onNetherRoofPortalCreate(fireEvent(location));

        verify(messages, never()).send(any(), anyString(), any(Object[].class));
        verify(scheduler, never()).runAtLocationLater(any(Location.class), any(Runnable.class), anyLong(), any());
    }

    @Test
    void portalBelowRoofIsKept() {
        Location location = new Location(nether, 0, 64, 0);
        Land land = land(true);
        when(landRepository.getLandAt(location)).thenReturn(land);

        listener.onNetherRoofPortalCreate(fireEvent(location));

        verify(scheduler, never()).runAtLocationLater(any(Location.class), any(Runnable.class), anyLong(), any());
    }

    @Test
    void flagIsOnlyAvailableOnSystemLands() {
        assertTrue(Flag.NETHER_ROOF_PORTAL_DECAY.isEnabled(new SystemLand(UUID.randomUUID(), "test")));
        assertFalse(Flag.NETHER_ROOF_PORTAL_DECAY.isEnabled(new PlayerLand(UUID.randomUUID(), UUID.randomUUID(), "test")));
    }

    private Land land(boolean decayFlag) {
        Land land = mock(Land.class);
        when(land.hasFlag(Flag.NETHER_ROOF_PORTAL_DECAY)).thenReturn(decayFlag);
        return land;
    }

    private PortalCreateEvent fireEvent(Location location) {
        BlockState state = mock(BlockState.class);
        when(state.getLocation()).thenReturn(location);
        when(state.getType()).thenReturn(Material.NETHER_PORTAL);
        when(state.getBlock()).thenReturn(portalBlock);
        return new PortalCreateEvent(new ArrayList<>(List.of(state)), nether, player, CreateReason.FIRE);
    }

    private void runScheduledBreak(long expectedMinutes) {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runAtLocationLater(any(Location.class), task.capture(), eq(expectedMinutes), eq(TimeUnit.MINUTES));
        task.getValue().run();
    }
}
