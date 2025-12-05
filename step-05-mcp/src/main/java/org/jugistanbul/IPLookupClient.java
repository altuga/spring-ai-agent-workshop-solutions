package org.jugistanbul;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "ip-lookup", url = "http://ip-api.com")
public interface IPLookupClient {

    @GetMapping("/json")
    String getLocation();
}
