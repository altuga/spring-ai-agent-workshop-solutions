package org.jugistanbul;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class LocationToolProvider {

    private final IPLookupClient ipLookupClient;

    public LocationToolProvider(IPLookupClient ipLookupClient) {
        this.ipLookupClient = ipLookupClient;
    }

    @Tool("Get the user's location based on their IP address")
    public String getLocation() {
        return ipLookupClient.getLocation();
    }
}
