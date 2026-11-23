package fr.iban.lands.utils;

import fr.iban.lands.messages.LandMessages;
import fr.iban.lands.LandsPlugin;
import fr.iban.lands.api.LandRepository;
import fr.iban.lands.model.SChunk;
import fr.iban.lands.model.land.Land;
import fr.iban.lands.model.land.SubLand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class CuboidSelector {

    private final Player player;

    private Location pos1;
    private Location pos2;

    private final Runnable quitCallback;

    private final LandsPlugin plugin;
    private final LandRepository landRepository;
    private final SubLand land;

    public CuboidSelector(Player player, LandsPlugin plugin, SubLand land, Runnable quitCallback) {
        this.land = land;
        this.plugin = plugin;
        this.landRepository = plugin.getLandRepository();
        this.player = player;
        this.quitCallback = quitCallback;
        Cuboid currentCuboid = land.getCuboid();
        // A new sub-land has no area yet; one on another server's world neither.
        if (currentCuboid != null) {
            this.pos1 = currentCuboid.getLowerNE();
            this.pos2 = currentCuboid.getUpperSW();
            showPreview(currentCuboid);
        }
    }

    public void startSelecting() {
        LandMessages messages = plugin.getMessages();
        messages.send(player, "selection.cuboid.warning");
        messages.send(player, "selection.cuboid.help");
        plugin.getChatInputs().await(player, text -> {
            if (text.equalsIgnoreCase("pos1")) {
                clearPreview();
                pos1 = player.getLocation();
                messages.send(player, "selection.pos1");
                if (pos2 != null && pos2.getWorld() != pos1.getWorld()) {
                    pos2 = null;
                }
                Cuboid cuboid = getCuboid();
                if (cuboid != null) showPreview(cuboid);
            } else if (text.equalsIgnoreCase("pos2")) {
                clearPreview();
                pos2 = player.getLocation();
                messages.send(player, "selection.pos2");
                if (pos1 != null && pos2.getWorld() != pos1.getWorld()) {
                    pos1 = null;
                }
                Cuboid cuboid = getCuboid();
                if (cuboid != null) showPreview(cuboid);
            } else if (text.equalsIgnoreCase("sauvegarder") || text.equalsIgnoreCase("save")) {
                Cuboid cuboid = getCuboid();
                if (verif(cuboid)) {
                    clearPreview();
                    land.setCuboid(cuboid, plugin.getServerName());
                    landRepository.updateLand(land);
                    messages.send(player, "selection.cuboid.saved");
                    plugin.getChatInputs().cancel(player);
                    quitCallback.run();
                }
            } else if (text.toLowerCase(java.util.Locale.ROOT).startsWith("quit")) {
                clearPreview();
                plugin.getChatInputs().cancel(player);
                quitCallback.run();
            } else {
                messages.send(player, "selection.invalid");
                startSelecting();
            }
        });
    }

    private void clearPreview() {
        plugin.getSelectionPreview().clear(player);
    }

    private boolean arePosSet() {
        return pos1 != null && pos2 != null;
    }

    private Cuboid getCuboid() {
        if (!arePosSet()) {
            return null;
        }

        return new Cuboid(pos1, pos2);
    }

    private boolean verif(Cuboid cuboid) {
        plugin.getMessages().send(player, "selection.verifying");
        if (cuboid == null) {
            if (pos1 == null) {
                plugin.getMessages().send(player, "selection.pos1_missing");
            } else {
                plugin.getMessages().send(player, "selection.pos2_missing");
            }
            return false;
        }

        // Both corners in the parent land (MSLands only compared them with each other).
        Land parent = land.getSuperLand();
        if (landRepository.getLandAt(new SChunk(cuboid.getUpperSW())) != parent || landRepository.getLandAt(new SChunk(cuboid.getLowerNE())) != parent) {
            plugin.getMessages().send(player, "selection.cuboid.outside", "land", land.getSuperLand().getName());
            return false;
        }

        return true;
    }

    private void showPreview(Cuboid cuboid) {
        plugin.getSelectionPreview().show(player, cuboid);
    }
}
