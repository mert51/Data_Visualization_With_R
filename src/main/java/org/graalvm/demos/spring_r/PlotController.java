package org.graalvm.demos.spring_r;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.util.function.Function;

@Controller
public class PlotController {

    @Value("${app.column}")
    private String column;

    @Autowired
    private Function<DataHolder, String> plotFunction;

    @Autowired
    private MongoCollection<Document> collection;

    // Mongo'dan okunacak siradaki satir: 0..99 (100'e gelince basa doner)
    private int index = 0;

    @Bean
    static Function<DataHolder, String> getPlotFunction(
            @Autowired Context ctx,
            @Value("classpath:plot.R") Resource rSource) throws IOException {
        Source source = Source.newBuilder("R", rSource.getURL()).build();
        return ctx.eval(source).as(Function.class);
    }

    @Bean
    static MongoCollection<Document> getCollection(
            MongoClient client,
            @Value("${app.mongo.database}") String database,
            @Value("${app.mongo.collection}") String coll) {
        return client.getDatabase(database).getCollection(coll);
    }

    @Bean
    static Context getGraalVMContext() {
        return Context.newBuilder().allowAllAccess(true).build();
    }

    // Siradaki satirdan secili sutunu okur, son satirdan sonra basa doner
    private synchronized double readNext() {
        long total = collection.countDocuments();
        if (index >= total) {
            index = 0;
        }
        Document doc = collection.find()
                .sort(Sorts.ascending("_id"))
                .skip(index)
                .limit(1)
                .first();
        index = (index + 1) % (int) total;
        return doc.get(column, Number.class).doubleValue();
    }

    @RequestMapping(value = "/plot", produces = "image/svg+xml")
    public ResponseEntity<String> load() {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Refresh", "1");

        String svg = "";
        synchronized (plotFunction) {
            svg = plotFunction.apply(new DataHolder(readNext()));
        }
        return new ResponseEntity<String>(svg, responseHeaders, HttpStatus.OK);
    }
}