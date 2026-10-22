package fr.iban.lands.listeners;

import fr.iban.lands.LandsPlugin;
import fr.iban.lands.api.LandRepository;
import fr.iban.lands.enums.Action;
import fr.iban.lands.model.land.Land;
import fr.iban.lands.enums.Flag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.PortalCreateEvent.CreateReason;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class PortalListeners implements Listener {

    private static final int NETHER_ROOF_Y = 128;

    private final LandsPlugin plugin;
    private final LandRepository landRepository;

    public PortalListeners(LandsPlugin plugin) {
        this.plugin = plugin;
        this.landRepository = plugin.getLandRepository();
    }

    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getEntity() instanceof Player player) {

            if (event.getReason() == CreateReason.NETHER_PAIR) {
                for (BlockState block : event.getBlocks()) {
                    Land land = landRepository.getLandAt(block.getLocation());

                    if (!land.isBypassing(player, Action.BLOCK_PLACE)) {
                        event.setCancelled(true);
                        player.sendMessage("§cLa création du portail a été annulée, celui-ci se trouvant sur un claim où vous n'êtes pas autorisé à poser des blocs.");
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNetherRoofPortalCreate(PortalCreateEvent event) {
        if (event.getReason() != CreateReason.FIRE
                || event.getWorld().getEnvironment() != Environment.NETHER
                || event.getBlocks().isEmpty()) {
            return;
        }

        Location location = event.getBlocks().getFirst().getLocation();
        if (location.getY() < NETHER_ROOF_Y || !hasDecayFlag(location)) {
            return;
        }

        long delay = plugin.getConfig().getLong("nether-roof-portals.break-delay-minutes", 10);
        if (event.getEntity() instanceof Player player) {
            player.sendMessage("§cCe portail est sur le toit du Nether hors d'un claim : il sera détruit dans "
                    + delay + " minutes. Claimez la zone pour le conserver.");
        }

        List<BlockState> blocks = event.getBlocks();
        plugin.getScheduler().runAtLocationLater(location, () -> {
            if (!hasDecayFlag(location)) {
                return;
            }
            blocks.stream()
                    .filter(block -> block.getType() == Material.NETHER_PORTAL)
                    .findFirst()
                    .ifPresent(block -> block.getBlock().breakNaturally());
        }, delay, TimeUnit.MINUTES);
    }

    private boolean hasDecayFlag(Location location) {
        return landRepository.getLandAt(location).hasFlag(Flag.NETHER_ROOF_PORTAL_DECAY);
    }
}
