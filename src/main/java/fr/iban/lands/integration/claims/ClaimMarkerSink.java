package fr.iban.lands.integration.claims;

import java.util.Collection;

public interface ClaimMarkerSink extends AutoCloseable {

    void replace(Collection<ClaimMarkerDescriptor> markers);

    void clear();

    void put(ClaimMarkerDescriptor marker);

    void remove(String world, String markerId);

    @Override
    void close();
}
