package fr.iban.lands.integration.claims;

import fr.iban.lands.enums.LandType;
import fr.iban.lands.model.land.GuildLand;
import fr.iban.lands.model.land.PlayerLand;
import fr.iban.lands.model.land.SubLand;
import fr.iban.lands.model.land.SystemLand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimMarkerFactoryTest {

    private ClaimMarkerFactory factory;
    private final ClaimMarkerGeometry geometry = new ClaimMarkerGeometry(
            2, -3,
            List.of(
                    new ClaimMarkerPoint(32, -48),
                    new ClaimMarkerPoint(80, -48),
                    new ClaimMarkerPoint(80, 0),
                    new ClaimMarkerPoint(32, 0)
            ),
            List.of(List.of(
                    new ClaimMarkerPoint(48, -32),
                    new ClaimMarkerPoint(48, -16),
                    new ClaimMarkerPoint(64, -16),
                    new ClaimMarkerPoint(64, -32)
            ))
    );

    @BeforeEach
    void setUp() {
        factory = new ClaimMarkerFactory(settings(), land -> "Alice & Bob");
    }

    @Test
    void createsRegionDescriptorWithGeometryAndEscapedMetadata() {
        UUID landId = UUID.randomUUID();
        PlayerLand land = new PlayerLand(landId, UUID.randomUUID(), "Maison <nord>");

        ClaimMarkerDescriptor marker = factory.create("world", land, geometry).orElseThrow();

        assertAll(
                () -> assertEquals("land:" + landId + ":world:2:-3", marker.id()),
                () -> assertEquals("world", marker.world()),
                () -> assertEquals(geometry, marker.geometry()),
                () -> assertEquals("Maison <nord>", marker.label()),
                () -> assertEquals(
                        "<b>Maison &lt;nord&gt;</b><br>Type: Joueur<br>Propriétaire: Alice &amp; Bob",
                        marker.detail()
                ),
                () -> assertEquals(settings().style(LandType.PLAYER), marker.style())
        );
    }

    @Test
    void excludesSublands() {
        assertTrue(factory.create(
                "world", new SubLand(UUID.randomUUID(), "Mine"), geometry).isEmpty());
    }

    @Test
    void excludesSublandsEvenWhenTheyHaveAConfiguredStyle() {
        ClaimMarkerStyle style = new ClaimMarkerStyle(
                new RgbColor(1, 2, 3),
                new RgbColor(4, 5, 6),
                0.25F
        );
        ClaimMarkerFactory configuredFactory = new ClaimMarkerFactory(
                new BlueMapSettings(
                        true,
                        "Territoires",
                        false,
                        2,
                        5,
                        Map.of(
                                LandType.PLAYER, style,
                                LandType.GUILD, style,
                                LandType.SYSTEM, style,
                                LandType.SUBLAND, style
                        )
                ),
                land -> "ignored"
        );

        assertTrue(configuredFactory.create(
                "world", new SubLand(UUID.randomUUID(), "Mine"), geometry).isEmpty());
    }

    @Test
    void createsEscapedPlayerDetail() {
        PlayerLand land = new PlayerLand(UUID.randomUUID(), UUID.randomUUID(), "Maison <nord>");

        ClaimMarkerDescriptor marker = factory.create("world", land, geometry).orElseThrow();

        assertEquals(
                "<b>Maison &lt;nord&gt;</b><br>Type: Joueur<br>Propriétaire: Alice &amp; Bob",
                marker.detail()
        );
    }

    @Test
    void createsGuildAndSystemDetails() {
        ClaimMarkerFactory guildFactory = new ClaimMarkerFactory(settings(), land -> "Les Bleus");

        ClaimMarkerDescriptor guild = guildFactory.create(
                "world",
                new GuildLand(UUID.randomUUID(), UUID.randomUUID(), "Citadelle"),
                geometry
        ).orElseThrow();
        ClaimMarkerDescriptor system = factory.create(
                "world",
                new SystemLand(UUID.randomUUID(), "Spawn"),
                geometry
        ).orElseThrow();

        assertAll(
                () -> assertEquals("<b>Citadelle</b><br>Type: Guilde<br>Guilde: Les Bleus", guild.detail()),
                () -> assertEquals("<b>Spawn</b><br>Type: Système", system.detail())
        );
    }

    @Test
    void escapesEveryDynamicHtmlCharacter() {
        ClaimMarkerFactory escapingFactory = new ClaimMarkerFactory(settings(), land -> "Bob &<>\"'");
        PlayerLand land = new PlayerLand(UUID.randomUUID(), UUID.randomUUID(), "Maison &<>\"'");

        ClaimMarkerDescriptor marker = escapingFactory.create("world", land, geometry).orElseThrow();

        assertEquals(
                "<b>Maison &amp;&lt;&gt;&quot;&#39;</b><br>Type: Joueur<br>Propriétaire: Bob &amp;&lt;&gt;&quot;&#39;",
                marker.detail()
        );
    }

    private BlueMapSettings settings() {
        ClaimMarkerStyle style = new ClaimMarkerStyle(
                new RgbColor(1, 2, 3),
                new RgbColor(4, 5, 6),
                0.25F
        );
        return new BlueMapSettings(
                true,
                "Territoires",
                false,
                2,
                5,
                Map.of(
                        LandType.PLAYER, style,
                        LandType.GUILD, style,
                        LandType.SYSTEM, style
                )
        );
    }
}
