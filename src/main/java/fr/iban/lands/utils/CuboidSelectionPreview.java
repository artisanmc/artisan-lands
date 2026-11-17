package fr.iban.lands.utils;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CuboidSelectionPreview implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final Map<UUID, Preview> previews = new HashMap<>();

    public CuboidSelectionPreview(Plugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, Cuboid cuboid) {
        show(player, cuboid, player.getLocation());
    }

    private void show(Player player, Cuboid cuboid, Location location) {
        clear(player);
        if (!player.isOnline() || !player.getWorld().equals(cuboid.getWorld())
                || !location.getWorld().equals(cuboid.getWorld())) {
            return;
        }
        Location anchor = location.clone();
        anchor.setYaw(0);
        anchor.setPitch(0);
        Location lower = cuboid.getLowerNE();
        Location upper = cuboid.getUpperSW().add(1, 1, 1);
        Vector3f origin = new Vector3f((float) (lower.getX() - anchor.getX()),
                (float) (lower.getY() - anchor.getY()), (float) (lower.getZ() - anchor.getZ()));
        Vector3f x = new Vector3f((float) (upper.getX() - lower.getX()), 0, 0);
        Vector3f y = new Vector3f(0, (float) (upper.getY() - lower.getY()), 0);
        Vector3f z = new Vector3f(0, 0, (float) (upper.getZ() - lower.getZ()));
        List<BlockDisplay> panels = new ArrayList<>(2);
        previews.put(player.getUniqueId(), new Preview(cuboid, anchor, panels));
        try {
            Vector3f size = new Vector3f(x.x, y.y, z.z);
            panels.add(GlassDisplay.spawn(plugin, anchor, origin, size, Material.LIME_STAINED_GLASS));
            // Mirroring one axis reverses winding so the glass is also visible from inside.
            panels.add(GlassDisplay.spawn(plugin, anchor, new Vector3f(origin).add(x),
                    new Vector3f(-size.x, size.y, size.z), Material.LIME_STAINED_GLASS));
            panels.forEach(display -> player.showEntity(plugin, display));
        } catch (RuntimeException failure) {
            clear(player);
            throw failure;
        }
    }

    public void clear(Player player) {
        Preview preview = previews.remove(player.getUniqueId());
        if (preview != null) preview.panels.forEach(BlockDisplay::remove);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Preview preview = previews.get(event.getPlayer().getUniqueId());
        Location destination = event.getTo();
        if (preview != null && destination != null
                && preview.anchor.getWorld().equals(destination.getWorld())
                && preview.anchor.distanceSquared(destination) >= 32 * 32) {
            show(event.getPlayer(), preview.cuboid, destination);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clear(event.getPlayer());
    }

    @Override
    public void close() {
        previews.values().forEach(preview -> preview.panels.forEach(BlockDisplay::remove));
        previews.clear();
    }

    private record Preview(Cuboid cuboid, Location anchor, List<BlockDisplay> panels) {}
}
