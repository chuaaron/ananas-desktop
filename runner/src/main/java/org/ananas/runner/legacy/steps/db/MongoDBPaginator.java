package org.ananas.runner.legacy.steps.db;

import com.github.wnameless.json.flattener.FlattenMode;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.Iterator;
import org.ananas.runner.kernel.common.BsonDocumentFlattenerReader;
import org.ananas.runner.kernel.errors.ErrorHandler;
import org.ananas.runner.kernel.paginate.AbstractPaginator;
import org.ananas.runner.kernel.paginate.Paginator;
import org.ananas.runner.kernel.schema.JsonAutodetect;
import org.ananas.runner.kernel.schema.SchemaBasedRowConverter;
import org.ananas.runner.legacy.steps.commons.json.BsonDocumentAsTextReader;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.values.Row;
import org.bson.Document;

public class MongoDBPaginator extends AbstractPaginator implements Paginator {

  MongoStepConfig config;

  public MongoDBPaginator(String id, MongoStepConfig config) {
    super(id, null);
    this.config = config;
    this.schema =
        config.isText
            ? Schema.builder().addField("text", Schema.FieldType.STRING).build()
            : this.autodetect();
  }

  @Override
  public Iterable<Row> iterateRows(Integer page, Integer pageSize) {
    FindIterable<Document> it = find().skip(pageSize * page).limit(pageSize);
    if (this.config.isText) {
      BsonDocumentAsTextReader reader = new BsonDocumentAsTextReader(this.schema);
      return it.map(e -> reader.doc2Row(e));
    }
    BsonDocumentFlattenerReader reader =
        new BsonDocumentFlattenerReader(
            SchemaBasedRowConverter.of(this.schema), new ErrorHandler());
    return it.map(e -> reader.document2BeamRow(e));
  }

  public Schema autodetect() {
    FindIterable<Document> l = find();
    Iterator<Document> it = l.limit(DEFAULT_LIMIT).iterator();
    return JsonAutodetect.autodetectBson(it, FlattenMode.KEEP_ARRAYS, false, DEFAULT_LIMIT);
  }

  private FindIterable<Document> find() {
    MongoClient mongoClient = new MongoClient(new MongoClientURI(this.config.getUrl()));
    MongoDatabase db = mongoClient.getDatabase(this.config.database);
    MongoCollection collection = db.getCollection(this.config.collection);
    if (this.config.filters == null) {
      return collection.find();
    }
    Document bson = Document.parse(this.config.filters);
    return collection.find(bson);
  }
}
