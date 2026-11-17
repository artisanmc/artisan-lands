package fr.iban.lands.utils;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.iban.lands.LandsPlugin;
import fr.iban.lands.api.LandRepository;
import fr.iban.lands.model.SChunk;
import fr.iban.lands.model.land.GuildLand;
import fr.iban.lands.model.land.Land;
import fr.iban.lands.model.land.PlayerLand;
import fr.iban.lands.model.land.SystemLand;
import fr.iban.lands.model.land.SubLand;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.LinkedHashMap;

public class SeeClaims {
    private final GlassDisplay.Settings settings;

    private final LandsPlugin plugin;
    private final LandRepository landRepository;
    private final Player player;
    private final Map<Panel, RenderedWall> walls = new HashMap<>();
    private WrappedTask proximityTask;
    private Location anchor;
    private boolean stopped;

    public SeeClaims(Player player, LandsPlugin plugin) {
        this.player = player;
        this.plugin = plugin;
        this.landRepository = plugin.getLandRepository();
        this.settings = GlassDisplay.Settings.from(plugin.getConfig());
    }

    public void showWalls() {
        stopped = false;
        forceUpdate();
        startProximityCheck();
    }

    public void startProximityCheck() {
        if (!stopped && proximityTask == null) {
            proximityTask = plugin.getScheduler().runAtEntityTimer(player, this::forceUpdate, 5L, 10L);
        }
    }

    public void forceUpdate() {
        if (stopped) {
            return;
        }
        if (!player.isOnline()) {
            stop();
            return;
        }
        Location location = player.getLocation();
        if (anchor == null || !anchor.getWorld().equals(location.getWorld())
                || anchor.distanceSquared(location) >= 32 * 32) {
            clearWalls();
            anchor = location.clone();
            anchor.setYaw(0);
            anchor.setPitch(0);
        }
        Map<Panel, Material> desired = calculateFaces();
        walls.entrySet().removeIf(entry -> {
            if (!desired.containsKey(entry.getKey()) || !entry.getValue().display.isValid()) {
                entry.getValue().display.remove();
                return true;
            }
            return false;
        });
        try {
            desired.forEach((face, color) -> {
                RenderedWall existing = walls.get(face);
                if (existing == null) {
                    walls.put(face, spawnWall(face, color));
                } else if (existing.color != color) {
                    existing.display.setBlock(plugin.getServer().createBlockData(color));
                    walls.put(face, new RenderedWall(existing.display, color));
                }
            });
        } catch (RuntimeException failure) {
            stop();
            throw failure;
        }
    }

    private Map<Panel, Material> calculateFaces() {
        Map<Panel, Material> result = new HashMap<>();
        Location location = player.getLocation();
        Set<Land> visited = new HashSet<>();
        SChunk center = new SChunk(plugin.getServerName(), location.getWorld().getName(),
                location.getBlockX() >> 4, location.getBlockZ() >> 4);
        for (int x = -settings.radius(); x <= settings.radius(); x++) {
            for (int z = -settings.radius(); z <= settings.radius(); z++) {
                SChunk chunk = center.getRelativeChunk(x, z);
                Land land = landRepository.getLandAt(chunk);
                if (land == null || landRepository.isWilderness(land)) {
                    continue;
                }
                if (visited.add(land)) {
                    for (SubLand sub : land.getSubLands().values()) {
                        addSublandPanels(result, sub, location, getLandMaterial(land));
                    }
                }
                Material color = getLandMaterial(land);
                for (Direction direction : Direction.values()) {
                    if (landRepository.getLandAt(chunk.getRelativeChunk(direction.dx, direction.dz)) != land) {
                        for (int y = location.getWorld().getMinHeight(); y < location.getWorld().getMaxHeight(); y += 16) {
                            int height = Math.min(16, location.getWorld().getMaxHeight() - y);
                            result.put(chunkPanel(chunk.getX(), chunk.getZ(), direction, y, height), color);
                        }
                    }
                }
            }
        }
        Map<Panel, Material> limited = new LinkedHashMap<>();
        result.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Panel, Material>>comparingDouble(entry ->
                                entry.getKey().distanceSquared(location))
                        .thenComparingDouble(entry -> entry.getKey().x)
                        .thenComparingDouble(entry -> entry.getKey().z)
                        .thenComparingDouble(entry -> entry.getKey().y)
                        .thenComparingDouble(entry -> entry.getKey().sx)
                        .thenComparingDouble(entry -> entry.getKey().sy)
                        .thenComparingDouble(entry -> entry.getKey().sz))
                .limit(settings.maxPanels())
                .forEach(entry -> limited.put(entry.getKey(), entry.getValue()));
        return limited;
    }

    private Material getLandMaterial(Land land) {
        if (land instanceof PlayerLand owned && owned.getOwner().equals(player.getUniqueId())) {
            return Material.LIME_STAINED_GLASS;
        }
        if (land instanceof SystemLand) {
            return Material.RED_STAINED_GLASS;
        }
        if (land instanceof GuildLand guild && guild.isGuildMember(player.getUniqueId())) {
            return Material.LIGHT_BLUE_STAINED_GLASS;
        }
        return Material.WHITE_STAINED_GLASS;
    }

    private static Panel chunkPanel(int chunkX, int chunkZ, Direction direction, int y, int height) {
        boolean alongX = direction.dx == 0;
        double x = (double) chunkX * 16 + (direction == Direction.EAST ? 15.99 : 0.01);
        double z = (double) chunkZ * 16 + (direction == Direction.SOUTH ? 15.99 : 0.01);
        if (alongX) x = (double) chunkX * 16;
        else z = (double) chunkZ * 16;
        return new Panel(x, y, z, alongX ? 16 : 0.01, height, alongX ? 0.01 : 16);
    }

    private void addSublandPanels(Map<Panel, Material> panels, SubLand sub, Location viewer, Material material) {
        Cuboid cuboid = sub.getCuboid();
        if (cuboid == null || !plugin.getServerName().equals(sub.getServer())
                || !viewer.getWorld().equals(cuboid.getWorld())) return;
        Location lower = cuboid.getLowerNE();
        Location upper = cuboid.getUpperSW().add(1, 1, 1);
        double[] min = {lower.getX(), lower.getY(), lower.getZ()};
        double[] max = {upper.getX(), upper.getY(), upper.getZ()};
        double[] eye = {viewer.getX(), viewer.getY(), viewer.getZ()};
        double range = settings.radius() * 16.0;
        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3;
            int v = (axis + 2) % 3;
            // Restrict iteration before tiling, even for cuboids millions of blocks wide.
            double fromU = min[u] + Math.max(0, Math.floor((eye[u] - range - min[u]) / 16)) * 16;
            double fromV = min[v] + Math.max(0, Math.floor((eye[v] - range - min[v]) / 16)) * 16;
            double toU = Math.min(max[u], eye[u] + range);
            double toV = Math.min(max[v], eye[v] + range);
            for (int side = 0; side < 2; side++) {
                double plane = side == 0 ? min[axis] : max[axis];
                if (Math.abs(plane - eye[axis]) > range) continue;
                for (double u0 = fromU; u0 < toU; u0 += 16) {
                    for (double v0 = fromV; v0 < toV; v0 += 16) {
                        double[] origin = new double[3];
                        double[] size = new double[3];
                        origin[axis] = side == 0 ? plane : plane - 0.01;
                        size[axis] = 0.01;
                        origin[u] = u0;
                        origin[v] = v0;
                        size[u] = Math.min(16, max[u] - u0);
                        size[v] = Math.min(16, max[v] - v0);
                        panels.put(new Panel(origin[0], origin[1], origin[2], size[0], size[1], size[2]),
                                material);
                    }
                }
            }
        }
    }

    private RenderedWall spawnWall(Panel face, Material color) {
        BlockDisplay display = GlassDisplay.spawn(plugin, anchor,
                new Vector3f((float) (face.x - anchor.getX()), (float) (face.y - anchor.getY()),
                        (float) (face.z - anchor.getZ())),
                new Vector3f((float) face.sx, (float) face.sy, (float) face.sz), color);
        try {
            player.showEntity(plugin, display);
        } catch (RuntimeException failure) {
            display.remove();
            throw failure;
        }
        return new RenderedWall(display, color);
    }

    public void stop() {
        stopped = true;
        if (proximityTask != null) {
            proximityTask.cancel();
            proximityTask = null;
        }
        clearWalls();
        anchor = null;
    }

    private void clearWalls() {
        walls.values().forEach(wall -> wall.display.remove());
        walls.clear();
    }

    private enum Direction {
        NORTH(0, -1), SOUTH(0, 1), EAST(1, 0), WEST(-1, 0);

        private final int dx;
        private final int dz;

        Direction(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }

    private record Panel(double x, double y, double z, double sx, double sy, double sz) {
        double distanceSquared(Location location) {
            double dx = Math.clamp(location.getX(), x, x + sx) - location.getX();
            double dy = Math.clamp(location.getY(), y, y + sy) - location.getY();
            double dz = Math.clamp(location.getZ(), z, z + sz) - location.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }
    private record RenderedWall(BlockDisplay display, Material color) {}
}
