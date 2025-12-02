package org.jugistanbul;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "ip-lookup", url = "http://ip-api.com")
public interface IPLookupClient {

    @GetMapping("/json")
    @Tool("Get location based on public IP")
    String getLocation();
}
