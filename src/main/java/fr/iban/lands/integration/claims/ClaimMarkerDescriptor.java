package fr.iban.lands.integration.claims;

public record ClaimMarkerDescriptor(
        String id,
        String world,
        ClaimMarkerGeometry geometry,
        String label,
        String detail,
        ClaimMarkerStyle style
) {
}
