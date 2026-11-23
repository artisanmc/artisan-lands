package fr.iban.lands.integration.claims;

import java.util.List;

public record ClaimMarkerGeometry(
        int anchorX,
        int anchorZ,
        List<ClaimMarkerPoint> outline,
        List<List<ClaimMarkerPoint>> holes
) {
    public ClaimMarkerGeometry {
        outline = List.copyOf(outline);
        holes = holes.stream().map(List::copyOf).toList();
    }
}
