package fr.iban.lands.enums;

import fr.iban.lands.model.land.Land;
import fr.iban.lands.model.land.SubLand;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum Flag {
    INVINCIBLE("Active l'invincibilité pour les joueurs", Material.IRON_SWORD, LandType.SYSTEM),
    PVP("Active le pvp", Material.DIAMOND_SWORD, LandType.SYSTEM),
    AUTO_REPLANT("Active l'auto replantation", Material.GOLDEN_SHOVEL, LandType.SYSTEM),
    DOORS_AUTOCLOSE("Active la fermeture automatique des portes", Material.OAK_DOOR, LandType.SYSTEM),
    NO_MOB_SPAWNING("Désactive le spawn des mobs", Material.ZOMBIE_SPAWN_EGG, LandType.SYSTEM),
    SILENT_ENTER_QUIT("Désactive le message d'entrée/sortie du territoire.", Material.BARRIER, LandType.SYSTEM),
    INVISIBLE("Rend les joueurs à l'intérieur invisibles.", Material.POTION, LandType.SYSTEM),
    INSTANT_TELEPORT("Supprime le délais des téléportation depuis ce territoire.", Material.ENDER_PEARL, LandType.SYSTEM),
    NETHER_ROOF_PORTAL_DECAY("Détruit les portails allumés sur le toit du Nether après un délai.", Material.OBSIDIAN, LandType.SYSTEM),
    EXPLOSIONS("Active les explosions", Material.TNT),
    BLOCK_DAMAGES_BY_ENTITY("Active les dégâts aux blocs par les entités (endermans, withers...)", Material.DIRT),
    PRESSURE_PLATE_BY_ENTITY("Active l'utilisation des plaques de pression par les entités", Material.OAK_PRESSURE_PLATE),
    TRIPWIRE_BY_ENTITY("Active l'utilisation des crochets par les entités", Material.TRIPWIRE_HOOK),
    FARMLAND_GRIEF("Active la destruction des terres labourées", Material.FARMLAND),
    FIRE("Active les dégâts du feu et sa propagation.", Material.FLINT_AND_STEEL),
    SILENT_MOBS("Désactive le bruit des mobs de ce territoire", Material.JUKEBOX),
    LIQUID_SPREAD("Autorise les liquides extérieurs au claim à se propager.", Material.LAVA_BUCKET),
    SHOP_MONEY_TO_GUILD_BANK("Rediriger l'argent des shops à la banque de la guilde.", Material.GOLD_INGOT, LandType.GUILD);


    private final String displayName;
    private final Material material;
    private final LandType[] enabledLandTypes;

    Flag(String displayName, Material material, LandType... enabledTypes) {
        this.displayName = displayName;
        this.material = material;
        this.enabledLandTypes = enabledTypes;
    }

    Flag(String displayName, Material material) {
        this.displayName = displayName;
        this.material = material;
        this.enabledLandTypes = LandType.values();
    }

    public String getDisplayName() {
        return displayName;
    }

    public ItemStack getItem() {
        return new ItemStack(material);
    }

    public static Flag getByDisplayName(String displayName) {
        for (Flag action : values()) {
            if (displayName.contains(action.getDisplayName())) return action;
        }

        return null;
    }

    public boolean isEnabled(Land land) {
        if (land instanceof SubLand subLand) {
            return isEnabled(subLand.getSuperLand());
        }
        
        for (LandType value : enabledLandTypes) {
            if (value == land.getType()) {
                return true;
            }
        }

        return false;
    }
}
