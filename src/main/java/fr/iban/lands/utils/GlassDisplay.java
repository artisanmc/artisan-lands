package fr.iban.lands.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class GlassDisplay {
    private GlassDisplay() {}

    static BlockDisplay spawn(Plugin plugin, Location anchor, Vector3f origin, Vector3f scale,
                              Material material) {
        return anchor.getWorld().spawn(anchor, BlockDisplay.class, entity -> {
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setBlock(plugin.getServer().createBlockData(material));
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setTransformation(new Transformation(origin, new Quaternionf(), scale, new Quaternionf()));
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setDisplayWidth(0);
            entity.setDisplayHeight(0);
            entity.setViewRange(16);
        });
    }

    record Settings(int radius, int maxPanels) {
        static Settings from(ConfigurationSection config) {
            int radius = config == null ? 10 : config.getInt("visualization.view-distance-chunks", 10);
            int maxPanels = config == null ? 512 : config.getInt("visualization.max-panels", 512);
            return new Settings(Math.clamp(radius, 1, 16), Math.clamp(maxPanels, 4, 1024));
        }
    }
}
