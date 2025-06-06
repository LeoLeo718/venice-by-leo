package com.linkedin.davinci.replication.merge;

import com.linkedin.davinci.replication.RmdWithValueSchemaId;
import com.linkedin.davinci.serializer.avro.MapOrderPreservingSerDeFactory;
import com.linkedin.davinci.serializer.avro.fast.MapOrderPreservingFastSerDeFactory;
import com.linkedin.venice.annotation.Threadsafe;
import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.schema.rmd.RmdSchemaEntry;
import com.linkedin.venice.serializer.RecordDeserializer;
import com.linkedin.venice.serializer.RecordSerializer;
import com.linkedin.venice.utils.RetryUtils;
import com.linkedin.venice.utils.SparseConcurrentList;
import com.linkedin.venice.utils.collections.BiIntKeyCache;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.OptimizedBinaryDecoder;
import org.apache.avro.io.OptimizedBinaryDecoderFactory;
import org.apache.commons.lang3.Validate;


/**
 * This class is responsible for serialization and deserialization related tasks. Specifically 3 things:
 *  1. Deserialize RMD from bytes.
 *  2. Serialize RMD record to bytes.
 *  3. Get RMD schema given its value schema ID.
 */
@Threadsafe
public class RmdSerDe {
  private final StringAnnotatedStoreSchemaCache annotatedStoreSchemaCache;
  private final int rmdVersionId;
  private final SparseConcurrentList<Schema> rmdSchemaIndexedByValueSchemaId;
  private final SparseConcurrentList<RecordSerializer<GenericRecord>> rmdSerializerIndexedByValueSchemaId;
  private BiIntKeyCache<RecordDeserializer<GenericRecord>> deserializerCache;
  private final boolean fastAvroEnabled;

  private final static int DEFAULT_MAX_RETRIES = 5;
  private final static int DEFAULT_DELAY_TIME_BETWEEN_RETRIES = 1; // seconds

  public RmdSerDe(StringAnnotatedStoreSchemaCache annotatedStoreSchemaCache, int rmdVersionId) {
    this(annotatedStoreSchemaCache, rmdVersionId, true);
  }

  public RmdSerDe(
      StringAnnotatedStoreSchemaCache annotatedStoreSchemaCache,
      int rmdVersionId,
      boolean fastAvroEnabled) {
    this.annotatedStoreSchemaCache = annotatedStoreSchemaCache;
    this.rmdVersionId = rmdVersionId;
    this.rmdSchemaIndexedByValueSchemaId = new SparseConcurrentList<>();
    this.rmdSerializerIndexedByValueSchemaId = new SparseConcurrentList<>();
    this.fastAvroEnabled = fastAvroEnabled;
    this.deserializerCache = new BiIntKeyCache<>((writerSchemaId, readerSchemaId) -> {
      Schema rmdWriterSchema = getRmdSchema(writerSchemaId);
      Schema rmdReaderSchema = getRmdSchema(readerSchemaId);
      return this.fastAvroEnabled
          ? MapOrderPreservingFastSerDeFactory.getDeserializer(rmdWriterSchema, rmdReaderSchema)
          : MapOrderPreservingSerDeFactory.getDeserializer(rmdWriterSchema, rmdReaderSchema);
    });
  }

  /**
   * This method takes in the RMD bytes with prepended value schema ID and a {@link RmdWithValueSchemaId} container object.
   * It will deserialize the RMD bytes into RMD record and fill the passed-in container.
   */
  public void deserializeValueSchemaIdPrependedRmdBytes(
      byte[] valueSchemaIdPrependedBytes,
      RmdWithValueSchemaId rmdWithValueSchemaId) {
    Validate.notNull(valueSchemaIdPrependedBytes);
    ByteBuffer rmdWithValueSchemaID = ByteBuffer.wrap(valueSchemaIdPrependedBytes);
    final int valueSchemaId = rmdWithValueSchemaID.getInt();
    OptimizedBinaryDecoder binaryDecoder = OptimizedBinaryDecoderFactory.defaultFactory()
        .createOptimizedBinaryDecoder(
            rmdWithValueSchemaID.array(), // bytes of replication metadata with NO value schema ID.
            rmdWithValueSchemaID.position(),
            rmdWithValueSchemaID.remaining());

    // We allow 5 retries of 1 sec delay between each attempt to get the deserializer in case of a slow schema
    // repository.
    GenericRecord rmdRecord = getRmdDeserializerWithRetry(valueSchemaId, valueSchemaId, 5).deserialize(binaryDecoder);
    rmdWithValueSchemaId.setValueSchemaId(valueSchemaId);
    rmdWithValueSchemaId.setRmdProtocolVersionId(rmdVersionId);
    rmdWithValueSchemaId.setRmdRecord(rmdRecord);
  }

  /**
   * Given a value schema ID {@param valueSchemaID} and RMD bytes {@param rmdBytes}, find the RMD schema that corresponds
   * to the given value schema ID and use that RMD schema to deserialize RMD bytes in a RMD record.
   *
   */
  public GenericRecord deserializeRmdBytes(final int writerSchemaID, final int readerSchemaID, ByteBuffer rmdBytes) {
    return getRmdDeserializer(writerSchemaID, readerSchemaID).deserialize(rmdBytes);
  }

  public ByteBuffer serializeRmdRecord(final int valueSchemaId, GenericRecord rmdRecord) {
    RecordSerializer<GenericRecord> rmdSerializer =
        this.rmdSerializerIndexedByValueSchemaId.computeIfAbsent(valueSchemaId, this::generateRmdSerializer);
    byte[] rmdBytes = rmdSerializer.serialize(rmdRecord);
    return ByteBuffer.wrap(rmdBytes);
  }

  public Schema getRmdSchema(final int valueSchemaId) {
    return this.rmdSchemaIndexedByValueSchemaId.computeIfAbsent(valueSchemaId, this::generateRmdSchemaWithRetry);
  }

  private Schema generateRmdSchemaWithRetry(final int valueSchemaId) {
    return RetryUtils.executeWithMaxAttempt(() -> {
      try {
        return generateRmdSchema(valueSchemaId);
      } catch (VeniceException e) {
        throw new VeniceException(
            String.format(
                "Failed to generate RMD schema for store: %s with value schema ID: %d and RMD version ID: %d",
                annotatedStoreSchemaCache.getStoreName(),
                valueSchemaId,
                rmdVersionId),
            e);
      }
    },
        DEFAULT_MAX_RETRIES,
        Duration.ofSeconds(DEFAULT_DELAY_TIME_BETWEEN_RETRIES),
        Collections.singletonList(VeniceException.class));
  }

  private Schema generateRmdSchema(final int valueSchemaId) {
    RmdSchemaEntry rmdSchemaEntry = this.annotatedStoreSchemaCache.getRmdSchema(valueSchemaId, this.rmdVersionId);
    if (rmdSchemaEntry == null) {
      throw new VeniceException(
          String.format(
              "Unable to fetch replication metadata schema from schema repository for store: %s and value schema ID: %d and RMD version ID: %d",
              annotatedStoreSchemaCache.getStoreName(),
              valueSchemaId,
              this.rmdVersionId));
    }
    return rmdSchemaEntry.getSchema();
  }

  private RecordDeserializer<GenericRecord> getRmdDeserializer(final int writerSchemaID, final int readerSchemaID) {
    return this.deserializerCache.get(writerSchemaID, readerSchemaID);
  }

  private RecordDeserializer<GenericRecord> getRmdDeserializerWithRetry(
      int writerSchemaID,
      int readerSchemaID,
      int maxRetry) {
    return RetryUtils.executeWithMaxAttempt(() -> {
      try {
        return this.deserializerCache.get(writerSchemaID, readerSchemaID);
      } catch (VeniceException e) {
        // Adjusting the error message to include the store name and schema IDs for better debugging.
        throw new VeniceException(
            String.format(
                "Failed to fetch RMD deserializer for store: %s with writer schema ID: %d and reader schema ID: %d",
                annotatedStoreSchemaCache.getStoreName(),
                writerSchemaID,
                readerSchemaID),
            e);
      }
    },
        maxRetry,
        Duration.ofSeconds(DEFAULT_DELAY_TIME_BETWEEN_RETRIES),
        Collections.singletonList(VeniceException.class));
  }

  private RecordSerializer<GenericRecord> generateRmdSerializer(int valueSchemaId) {
    Schema replicationMetadataSchema = getRmdSchema(valueSchemaId);
    return fastAvroEnabled
        ? MapOrderPreservingFastSerDeFactory.getSerializer(replicationMetadataSchema)
        : MapOrderPreservingSerDeFactory.getSerializer(replicationMetadataSchema);
  }

  // For testing purpose only.
  void setDeserializerCache(BiIntKeyCache<RecordDeserializer<GenericRecord>> deserializerCache) {
    this.deserializerCache = deserializerCache;
  }
}
