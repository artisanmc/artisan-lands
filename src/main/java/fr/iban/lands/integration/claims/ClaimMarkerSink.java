package fr.iban.lands.integration.claims;

import java.util.Collection;

public interface ClaimMarkerSink extends AutoCloseable {

    void replace(Collection<ClaimMarkerDescriptor> markers);

    @Override
    void close();
}
