package fr.iban.lands.integration.bluemap;

import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import fr.iban.lands.integration.claims.ClaimMarkerDescriptor;
import fr.iban.lands.integration.claims.ClaimMarkerGeometry;
import fr.iban.lands.integration.claims.ClaimMarkerPoint;
import fr.iban.lands.integration.claims.RgbColor;

import java.util.List;

public final class BlueMapShapeMarkerFactory {

    private final int lineWidth;

    public BlueMapShapeMarkerFactory(int lineWidth) {
        this.lineWidth = lineWidth;
    }

    public ShapeMarker create(ClaimMarkerDescriptor descriptor) {
        ClaimMarkerGeometry geometry = descriptor.geometry();
        return ShapeMarker.builder()
                .label(descriptor.label())
                .detail(descriptor.detail())
                .shape(toShape(geometry.outline()), 64)
                .holes(geometry.holes().stream().map(this::toShape).toArray(Shape[]::new))
                .lineWidth(lineWidth)
                .lineColor(toColor(descriptor.style().lineColor(), 1.0F))
                .fillColor(toColor(
                        descriptor.style().fillColor(),
                        descriptor.style().fillOpacity()
                ))
                .depthTestEnabled(false)
                .build();
    }

    private Shape toShape(List<ClaimMarkerPoint> points) {
        return new Shape(points.stream()
                .map(point -> new Vector2d(point.x(), point.z()))
                .toList());
    }

    private Color toColor(RgbColor color, float alpha) {
        return new Color(color.red(), color.green(), color.blue(), alpha);
    }
}
