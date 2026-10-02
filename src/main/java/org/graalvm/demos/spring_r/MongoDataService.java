package org.graalvm.demos.spring_r;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import jakarta.annotation.PreDestroy;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MongoDataService {

    private final MongoClient client;
    private final MongoCollection<Document> collection;
    private final String column;
    private int index = 0;   // siradaki satir: 0..99

    public MongoDataService(@Value("${app.mongo.uri}") String uri,
                            @Value("${app.mongo.database}") String database,
                            @Value("${app.mongo.collection}") String coll,
                            @Value("${app.column}") String column) {
        this.client = MongoClients.create(uri);
        this.collection = client.getDatabase(database).getCollection(coll);
        this.column = column;
    }

    /** Siradaki satirdan secili sutunu okur, son satirdan sonra basa doner. */
    public synchronized double next() {
        long total = collection.countDocuments();
        if (total == 0) {
            throw new IllegalStateException("Koleksiyon bos");
        }
        if (index >= total) {
            index = 0;
        }
        Document doc = collection.find()
                .sort(Sorts.ascending("_id"))   // import sirasi = satir sirasi
                .skip(index)
                .limit(1)
                .first();
        index = (index + 1) % (int) total;
        return doc.get(column, Number.class).doubleValue();
    }

    @PreDestroy
    void close() {
        client.close();
    }
}
