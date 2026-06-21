package com.karmayogi.form.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FormEsIndexer {

    private static final Logger log = LoggerFactory.getLogger(FormEsIndexer.class);
    private static final String DOC_TYPE = "_doc";

    private final RestHighLevelClient client;
    private final ObjectMapper objectMapper;
    private final Gson gson;
    private final String index;
    private final MeterRegistry meterRegistry;

    public FormEsIndexer(RestHighLevelClient client,
                         ObjectMapper objectMapper,
                         @Value("${form.elasticsearch.index}") String index,
                         MeterRegistry meterRegistry) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.gson = new Gson();
        this.index = index;
        this.meterRegistry = meterRegistry;
    }

    public void index(String formId, String envelope) throws IOException {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            JsonNode root = objectMapper.readTree(envelope);
            JsonNode formDoc  = root.get("formDoc");
            JsonNode fieldDocs = root.get("fieldDocs");

            Boolean formSaved = writeToEs(formDoc, formId);
            if (Boolean.FALSE.equals(formSaved)) {
                meterRegistry.counter("form.es.index.failure").increment();
                throw new IOException("Failed to index form header formId=" + formId);
            }
            log.info("FormEsIndexer form header indexed formId={}", formId);

            if (fieldDocs != null && fieldDocs.isArray() && fieldDocs.size() > 0) {
                List<String> indexedFieldIds = bulkWriteToEs(formId, fieldDocs);
                if (indexedFieldIds == null) {
                    log.error("FormEsIndexer bulk failed formId={} — rolling back", formId);
                    rollback(formId, new ArrayList<>());
                    meterRegistry.counter("form.es.bulk.failure").increment();
                    throw new IOException("Bulk index failed formId=" + formId + " — rolled back");
                }
                meterRegistry.counter("form.es.bulk.success").increment();
                log.info("FormEsIndexer bulk indexed formId={} fieldCount={}",
                        formId, indexedFieldIds.size());
            }

            meterRegistry.counter("form.es.index.success").increment();
            log.info("FormEsIndexer completed formId={}", formId);

        } catch (IOException e) {
            meterRegistry.counter("form.es.index.failure").increment();
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("form.es.index.duration", "index", index));
        }
    }

    public Boolean writeToEs(Object object, String id) {
        try {
            String json = (object instanceof com.fasterxml.jackson.databind.JsonNode)
                    ? objectMapper.writeValueAsString(object)
                    : gson.toJson(object);
            IndexRequest indexRequest = new IndexRequest()
                    .index(index)
                    .id(id)
                    .source(json, XContentType.JSON)
                    .type(DOC_TYPE);
            IndexResponse response = client.index(indexRequest, RequestOptions.DEFAULT);
            log.info("FormEsIndexer writeToEs id={} result={}", id, response.getResult());
            return Boolean.TRUE;
        } catch (Exception e) {
            log.error("FormEsIndexer writeToEs failed id={}: {}", id, e.getMessage(), e);
            return Boolean.FALSE;
        }
    }

    public List<String> bulkWriteToEs(String formId, JsonNode fieldDocs) {
        try {
            BulkRequest bulkRequest = new BulkRequest();
            List<String> ids = new ArrayList<>();

            for (JsonNode fieldDoc : fieldDocs) {
                String fieldId = UUID.randomUUID().toString();
                ids.add(fieldId);
                IndexRequest indexRequest = new IndexRequest()
                        .index(index)
                        .id(fieldId)
                        .source(objectMapper.writeValueAsString(fieldDoc), XContentType.JSON)
                        .type(DOC_TYPE);
                bulkRequest.add(indexRequest);
            }

            BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
            if (bulkResponse.hasFailures()) {
                log.error("FormEsIndexer bulk failures formId={}: {}",
                        formId, bulkResponse.buildFailureMessage());
                List<String> successIds = new ArrayList<>();
                int i = 0;
                for (BulkItemResponse item : bulkResponse.getItems()) {
                    if (!item.isFailed()) successIds.add(ids.get(i));
                    i++;
                }
                rollback(formId, successIds);
                return null;
            }

            return ids;
        } catch (Exception e) {
            log.error("FormEsIndexer bulkWriteToEs failed formId={}: {}", formId, e.getMessage(), e);
            return null;
        }
    }

    public void delete(String id) {
        try {
            client.delete(new DeleteRequest(index, DOC_TYPE, id), RequestOptions.DEFAULT);
            log.info("FormEsIndexer deleted id={}", id);
        } catch (Exception e) {
            log.error("FormEsIndexer delete failed id={}: {}", id, e.getMessage(), e);
        }
    }

    private void rollback(String formId, List<String> indexedFieldIds) {
        delete(formId);
        indexedFieldIds.forEach(this::delete);
        log.info("FormEsIndexer rollback completed formId={} fieldsDeleted={}",
                formId, indexedFieldIds.size());
    }
}