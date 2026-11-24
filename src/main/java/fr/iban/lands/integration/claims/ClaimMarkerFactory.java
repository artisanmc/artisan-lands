package fr.iban.lands.integration.claims;

import fr.iban.lands.enums.LandType;
import fr.iban.lands.model.land.Land;

import java.util.Objects;
import java.util.Optional;

public final class ClaimMarkerFactory {

    private final BlueMapSettings settings;
    private final ClaimOwnerLabelResolver ownerLabelResolver;

    public ClaimMarkerFactory(BlueMapSettings settings, ClaimOwnerLabelResolver ownerLabelResolver) {
        this.settings = Objects.requireNonNull(settings);
        this.ownerLabelResolver = Objects.requireNonNull(ownerLabelResolver);
    }

    public Optional<ClaimMarkerDescriptor> create(
            String world, Land land, ClaimMarkerGeometry geometry
    ) {
        String id = "land:" + land.getId() + ":" + world + ":"
                + geometry.anchorX() + ":" + geometry.anchorZ();
        return create(id, world, land, geometry);
    }

    private Optional<ClaimMarkerDescriptor> create(
            String id, String world, Land land, ClaimMarkerGeometry geometry
    ) {
        if (land.getType() == LandType.SUBLAND) {
            return Optional.empty();
        }

        ClaimMarkerStyle style = settings.style(land.getType());
        if (style == null) {
            return Optional.empty();
        }

        return Optional.of(new ClaimMarkerDescriptor(
                id,
                world,
                geometry,
                land.getName(),
                detail(land),
                style
        ));
    }

    private String detail(Land land) {
        String name = escape(land.getName());
        return switch (land.getType()) {
            case PLAYER -> "<b>" + name + "</b><br>Type: Joueur<br>Propriétaire: "
                    + escape(ownerLabelResolver.resolve(land));
            case GUILD -> "<b>" + name + "</b><br>Type: Guilde<br>Guilde: "
                    + escape(ownerLabelResolver.resolve(land));
            case SYSTEM -> "<b>" + name + "</b><br>Type: Système";
            case SUBLAND -> throw new IllegalArgumentException("Sublands do not have claim markers.");
        };
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
