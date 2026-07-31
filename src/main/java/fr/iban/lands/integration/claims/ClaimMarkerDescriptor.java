package fr.iban.lands.integration.claims;

import java.util.List;

public record ClaimMarkerDescriptor(
        String id,
        String world,
        ClaimMarkerGeometry geometry,
        String label,
        String detail,
        ClaimMarkerStyle style
) {

    public ClaimMarkerDescriptor(
            String id,
            String world,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            String label,
            String detail,
            ClaimMarkerStyle style
    ) {
        this(
                id,
                world,
                new ClaimMarkerGeometry(
                        Math.floorDiv(minX, 16),
                        Math.floorDiv(minZ, 16),
                        List.of(
                                new ClaimMarkerPoint(minX, minZ),
                                new ClaimMarkerPoint(maxX, minZ),
                                new ClaimMarkerPoint(maxX, maxZ),
                                new ClaimMarkerPoint(minX, maxZ)
                        ),
                        List.of()
                ),
                label,
                detail,
                style
        );
    }
}
