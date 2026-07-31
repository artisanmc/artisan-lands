package fr.iban.lands.integration.claims;

import fr.iban.lands.enums.LandType;
import fr.iban.lands.model.SChunk;
import fr.iban.lands.model.land.GuildLand;
import fr.iban.lands.model.land.PlayerLand;
import fr.iban.lands.model.land.SubLand;
import fr.iban.lands.model.land.SystemLand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimMarkerFactoryTest {

    private ClaimMarkerFactory factory;
    private final SChunk chunk = new SChunk("survival", "world", 2, -3);
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

    @ParameterizedTest
    @CsvSource({
            "2,-3,32,-48",
            "-2,3,-32,48"
    })
    void keepsCompatibilityChunkMarkersWithSingleChunkGeometry(
            int chunkX, int chunkZ, int minX, int minZ
    ) {
        SChunk chunk = new SChunk("survival", "world", chunkX, chunkZ);
        PlayerLand land = new PlayerLand(UUID.randomUUID(), UUID.randomUUID(), "Maison");

        ClaimMarkerDescriptor marker = factory.create(chunk, land).orElseThrow();

        assertAll(
                () -> assertEquals("chunk:" + chunkX + ":" + chunkZ, marker.id()),
                () -> assertEquals(new ClaimMarkerGeometry(
                        chunkX,
                        chunkZ,
                        List.of(
                                new ClaimMarkerPoint(minX, minZ),
                                new ClaimMarkerPoint(minX + 16, minZ),
                                new ClaimMarkerPoint(minX + 16, minZ + 16),
                                new ClaimMarkerPoint(minX, minZ + 16)
                        ),
                        List.of()
                ), marker.geometry())
        );
    }

    @Test
    void excludesSublands() {
        assertTrue(factory.create(chunk, new SubLand(UUID.randomUUID(), "Mine")).isEmpty());
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
                        Map.of(
                                LandType.PLAYER, style,
                                LandType.GUILD, style,
                                LandType.SYSTEM, style,
                                LandType.SUBLAND, style
                        )
                ),
                land -> "ignored"
        );

        assertTrue(configuredFactory.create(chunk, new SubLand(UUID.randomUUID(), "Mine")).isEmpty());
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
                chunk,
                new GuildLand(UUID.randomUUID(), UUID.randomUUID(), "Citadelle")
        ).orElseThrow();
        ClaimMarkerDescriptor system = factory.create(
                chunk,
                new SystemLand(UUID.randomUUID(), "Spawn")
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

        ClaimMarkerDescriptor marker = escapingFactory.create(chunk, land).orElseThrow();

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
                Map.of(
                        LandType.PLAYER, style,
                        LandType.GUILD, style,
                        LandType.SYSTEM, style
                )
        );
    }
}
