package fr.iban.lands.utils;

import fr.iban.lands.LandsPlugin;
import fr.iban.lands.model.land.SubLand;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class CuboidSelectorTest {
    @Test
    void openingSubLandSelectionUsesGlassPreviewWithoutParticleTask() {
        LandsPlugin plugin = mock(LandsPlugin.class);
        Player player = mock(Player.class);
        SubLand land = mock(SubLand.class);
        Cuboid cuboid = mock(Cuboid.class);
        CuboidSelectionPreview preview = mock(CuboidSelectionPreview.class);
        when(plugin.getSelectionPreview()).thenReturn(preview);
        when(land.getCuboid()).thenReturn(cuboid);
        when(cuboid.getLowerNE()).thenReturn(new Location(null, 0, 0, 0));
        when(cuboid.getUpperSW()).thenReturn(new Location(null, 19, 7, 4));

        new CuboidSelector(player, plugin, land, () -> {});

        verify(preview).show(player, cuboid);
        verify(plugin, never()).getScheduler();
        verify(cuboid, never()).getCubeEdgeLocations(anyDouble());
    }
}
