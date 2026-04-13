package com.mist.commerce;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class IndexController {

    @GetMapping("/")
    public String index() {
        log.trace("TRACE!!");
        log.debug("DEBUG!!");
        log.info("INFO!!");
        log.warn("WARN!!");
        log.error("ERROR!!");
        return "OK";
    }
}
