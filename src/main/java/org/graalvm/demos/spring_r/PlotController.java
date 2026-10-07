package org.graalvm.demos.spring_r;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.function.Function;

@RestController
public class PlotController {

    private final MongoDataService dataService;
    private final Function<DataHolder, String> plotFunction;

    @SuppressWarnings("unchecked")
    public PlotController(MongoDataService dataService,
                          @Value("classpath:plot.R") Resource rSource) throws IOException {
        this.dataService = dataService;
        Context ctx = Context.newBuilder().allowAllAccess(true).build();
        Source source = Source.newBuilder("R", rSource.getURL()).build();
        this.plotFunction = ctx.eval(source).as(Function.class);
    }

    @GetMapping(value = "/plot", produces = "image/svg+xml")
    public ResponseEntity<String> plot() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Cache-Control", "no-cache");   // tarayıcı önbelleğini kullanmamasını sağlar
        headers.set("Refresh", "1");   // tarayıcı sayfayı her saniye yeniler

        String svg;
        synchronized (plotFunction) {
            svg = plotFunction.apply(new DataHolder(dataService.next()));
        }
        return new ResponseEntity<>(svg, headers, HttpStatus.OK);
    }
}