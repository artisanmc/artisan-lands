package fr.iban.lands.integration.bluemap;

import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import fr.iban.lands.integration.claims.ClaimMarkerDescriptor;
import fr.iban.lands.integration.claims.ClaimMarkerGeometry;
import fr.iban.lands.integration.claims.ClaimMarkerPoint;
import fr.iban.lands.integration.claims.ClaimMarkerStyle;
import fr.iban.lands.integration.claims.RgbColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BlueMapShapeMarkerFactoryTest {

    @Test
    void convertsClaimGeometryAndStyleToShapeMarker() {
        ClaimMarkerGeometry geometry = new ClaimMarkerGeometry(
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
        ClaimMarkerDescriptor descriptor = new ClaimMarkerDescriptor(
                "land:00000000-0000-0000-0000-000000000001:world:2:-3",
                "world",
                geometry,
                "Maison",
                "<b>Maison</b>",
                new ClaimMarkerStyle(
                        new RgbColor(52, 152, 219),
                        new RgbColor(46, 204, 113),
                        0.35F
                )
        );
        BlueMapShapeMarkerFactory factory = new BlueMapShapeMarkerFactory(2);

        ShapeMarker marker = factory.create(descriptor);

        assertAll(
                () -> assertEquals("Maison", marker.getLabel()),
                () -> assertEquals(64.0F, marker.getShapeY()),
                () -> assertArrayEquals(new Vector2d[]{
                        new Vector2d(32, -48),
                        new Vector2d(80, -48),
                        new Vector2d(80, 0),
                        new Vector2d(32, 0)
                }, marker.getShape().getPoints()),
                () -> assertEquals(1, marker.getHoles().size()),
                () -> assertArrayEquals(new Vector2d[]{
                        new Vector2d(48, -32),
                        new Vector2d(48, -16),
                        new Vector2d(64, -16),
                        new Vector2d(64, -32)
                }, marker.getHoles().iterator().next().getPoints()),
                () -> assertEquals(2, marker.getLineWidth()),
                () -> assertFalse(marker.isDepthTestEnabled()),
                () -> assertEquals("<b>Maison</b>", marker.getDetail()),
                () -> assertEquals(new Color(52, 152, 219, 1.0F), marker.getLineColor()),
                () -> assertEquals(new Color(46, 204, 113, 0.35F), marker.getFillColor())
        );
    }
}
