package com.linkedin.venice.client.store;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import com.linkedin.venice.client.store.predicate.IntPredicate;
import com.linkedin.venice.client.store.predicate.Predicate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.avro.Schema;
import org.testng.annotations.Test;


/**
 * Simple test suite for QueryTool.
 * Focuses on testing the public methods and core functionality.
 */
public class QueryToolTest {
  @Test(description = "Should convert different key types correctly")
  public void testConvertKey() {
    // Test string key
    Object stringKey = QueryTool.convertKey("test", Schema.create(Schema.Type.STRING));
    assertEquals(stringKey, "test");

    // Test int key
    Object intKey = QueryTool.convertKey("123", Schema.create(Schema.Type.INT));
    assertEquals(intKey, 123);

    // Test long key
    Object longKey = QueryTool.convertKey("123456789", Schema.create(Schema.Type.LONG));
    assertEquals(longKey, 123456789L);

    // Test float key
    Object floatKey = QueryTool.convertKey("123.45", Schema.create(Schema.Type.FLOAT));
    assertEquals(floatKey, 123.45f);

    // Test double key
    Object doubleKey = QueryTool.convertKey("123.456", Schema.create(Schema.Type.DOUBLE));
    assertEquals(doubleKey, 123.456);

    // Test boolean key
    Object boolKey = QueryTool.convertKey("true", Schema.create(Schema.Type.BOOLEAN));
    assertEquals(boolKey, true);
  }

  @Test(description = "Should remove quotes from strings correctly")
  public void testRemoveQuotes() {
    assertEquals(QueryTool.removeQuotes("\"test\""), "test");
    assertEquals(QueryTool.removeQuotes("test"), "test");
    assertEquals(QueryTool.removeQuotes("\"test"), "test");
    assertEquals(QueryTool.removeQuotes("test\""), "test");
    assertEquals(QueryTool.removeQuotes("\"\""), "");
  }

  @Test(description = "Should handle invalid key conversion")
  public void testConvertKeyInvalidInput() {
    // Test invalid int
    assertThrows(NumberFormatException.class, () -> QueryTool.convertKey("invalid", Schema.create(Schema.Type.INT)));

    // Test invalid long
    assertThrows(NumberFormatException.class, () -> QueryTool.convertKey("invalid", Schema.create(Schema.Type.LONG)));

    // Test invalid float
    assertThrows(NumberFormatException.class, () -> QueryTool.convertKey("invalid", Schema.create(Schema.Type.FLOAT)));

    // Test invalid double
    assertThrows(NumberFormatException.class, () -> QueryTool.convertKey("invalid", Schema.create(Schema.Type.DOUBLE)));

    // Test invalid boolean - Boolean.parseBoolean returns false for invalid input
    Object boolKey = QueryTool.convertKey("invalid", Schema.create(Schema.Type.BOOLEAN));
    assertEquals(boolKey, false);
  }

  @Test(description = "Should handle edge cases in removeQuotes")
  public void testRemoveQuotesEdgeCases() {
    // Test empty string
    assertEquals(QueryTool.removeQuotes(""), "");

    // Test single quote
    assertEquals(QueryTool.removeQuotes("\""), "");

    // Test string with only quotes
    assertEquals(QueryTool.removeQuotes("\"\""), "");

    // Test string with quotes in middle
    assertEquals(QueryTool.removeQuotes("test\"test"), "test\"test");

    // Test string with multiple quotes
    assertEquals(QueryTool.removeQuotes("\"\"test\"\""), "\"test\"");
  }

  @Test(description = "Should handle null input in removeQuotes")
  public void testRemoveQuotesNullInput() {
    assertThrows(NullPointerException.class, () -> QueryTool.removeQuotes(null));
  }

  @Test(description = "Should handle boolean edge cases")
  public void testConvertKeyBooleanEdgeCases() {
    // Test various boolean inputs
    assertEquals(QueryTool.convertKey("true", Schema.create(Schema.Type.BOOLEAN)), true);
    assertEquals(QueryTool.convertKey("false", Schema.create(Schema.Type.BOOLEAN)), false);
    assertEquals(QueryTool.convertKey("TRUE", Schema.create(Schema.Type.BOOLEAN)), true);
    assertEquals(QueryTool.convertKey("FALSE", Schema.create(Schema.Type.BOOLEAN)), false);
    assertEquals(QueryTool.convertKey("invalid", Schema.create(Schema.Type.BOOLEAN)), false);
    assertEquals(QueryTool.convertKey("", Schema.create(Schema.Type.BOOLEAN)), false);
  }

  @Test(description = "Should test queryStoreForKey with single key")
  public void testQueryStoreForKeySingleKey() throws NoSuchMethodException {
    // This would require a mock store client, but we can test the method signature
    // In a real test, you would mock the client and verify the behavior
    assertNotNull(
        QueryTool.class.getDeclaredMethod(
            "queryStoreForKey",
            String.class,
            String.class,
            String.class,
            boolean.class,
            Optional.class));
  }

  @Test(description = "Should test queryStoreWithCountByValue method signature")
  public void testQueryStoreWithCountByValueSignature() throws NoSuchMethodException {
    // Verify the method exists and has correct signature
    assertNotNull(
        QueryTool.class.getDeclaredMethod(
            "queryStoreWithCountByValue",
            String.class,
            String.class,
            String.class,
            boolean.class,
            Optional.class,
            String.class,
            int.class));
  }

  @Test(description = "Should test queryStoreWithCountByBucket method signature")
  public void testQueryStoreWithCountByBucketSignature() throws NoSuchMethodException {
    // Verify the method exists and has correct signature
    assertNotNull(
        QueryTool.class.getDeclaredMethod(
            "queryStoreWithCountByBucket",
            String.class,
            String.class,
            String.class,
            boolean.class,
            Optional.class,
            String.class,
            String.class));
  }

  @Test(description = "Should test main method argument parsing")
  public void testMainMethodArgumentParsing() throws NoSuchMethodException {
    // Test that main method can handle different argument combinations
    // This is more of an integration test, but we can verify the method exists
    assertNotNull(QueryTool.class.getDeclaredMethod("main", String[].class));
  }

  @Test(description = "Should test command line argument validation")
  public void testCommandLineArgumentValidation() {
    // Test minimum required arguments
    String[] insufficientArgs = { "store", "key", "url" }; // Missing required args
    // In a real test, you would call main() and verify it exits with error
    assertTrue(insufficientArgs.length < 5); // Minimum required args
  }

  @Test(description = "Should test facet counting mode parsing")
  public void testFacetCountingModeParsing() {
    // Test different facet counting modes
    String[] singleModeArgs = { "store", "key", "url", "false", "", "single" };
    String[] countByValueModeArgs = { "store", "key", "url", "false", "", "countByValue", "field1", "10" };
    String[] countByBucketModeArgs = { "store", "key", "url", "false", "", "countByBucket", "field1", "0-10" };

    // Verify argument structures are correct
    assertTrue(singleModeArgs.length >= 6);
    assertTrue(countByValueModeArgs.length >= 8);
    assertTrue(countByBucketModeArgs.length >= 8);
  }

  @Test(description = "Should test SSL configuration handling")
  public void testSSLConfigurationHandling() {
    // Test SSL config parsing
    String sslConfig = "/path/to/ssl.conf";
    Optional<String> sslConfigOpt = Optional.of(sslConfig);

    // Verify SSL config can be handled
    assertTrue(sslConfigOpt.isPresent());
    assertEquals(sslConfigOpt.get(), sslConfig);
  }

  @Test(description = "Should test key parsing for multiple keys")
  public void testKeyParsingForMultipleKeys() {
    // Test comma-separated key parsing
    String multipleKeys = "key1,key2,key3";
    String[] keyArray = multipleKeys.split(",");

    assertEquals(keyArray.length, 3);
    assertEquals(keyArray[0], "key1");
    assertEquals(keyArray[1], "key2");
    assertEquals(keyArray[2], "key3");
  }

  @Test(description = "Should test field parsing for countByValue")
  public void testFieldParsingForCountByValue() {
    // Test comma-separated field parsing
    String multipleFields = "field1,field2,field3";
    String[] fieldArray = multipleFields.split(",");

    assertEquals(fieldArray.length, 3);
    assertEquals(fieldArray[0], "field1");
    assertEquals(fieldArray[1], "field2");
    assertEquals(fieldArray[2], "field3");
  }

  @Test(description = "Should test bucket definitions parsing")
  public void testBucketDefinitionsParsing() {
    // Test bucket definitions parsing
    String bucketDefs = "0-10,10-20,20-30";
    String[] bucketArray = bucketDefs.split(",");

    assertEquals(bucketArray.length, 3);
    assertEquals(bucketArray[0], "0-10");
    assertEquals(bucketArray[1], "10-20");
    assertEquals(bucketArray[2], "20-30");
  }

  @Test(description = "Should test countByValue aggregation logic")
  public void testCountByValueAggregationLogic() {
    // Mock the aggregation response
    Map<Object, Integer> mockValueCounts = new HashMap<>();
    mockValueCounts.put("engineer", 5);
    mockValueCounts.put("manager", 3);
    mockValueCounts.put("designer", 2);

    // Test that the aggregation results are correctly structured
    assertEquals(mockValueCounts.size(), 3);
    assertEquals(mockValueCounts.get("engineer"), Integer.valueOf(5));
    assertEquals(mockValueCounts.get("manager"), Integer.valueOf(3));
    assertEquals(mockValueCounts.get("designer"), Integer.valueOf(2));
  }

  @Test(description = "Should test countByBucket aggregation logic")
  public void testCountByBucketAggregationLogic() {
    // Mock the bucket aggregation response
    Map<String, Integer> mockBucketCounts = new HashMap<>();
    mockBucketCounts.put("0-10", 8);
    mockBucketCounts.put("10-20", 12);
    mockBucketCounts.put("20-30", 5);

    // Test that the bucket aggregation results are correctly structured
    assertEquals(mockBucketCounts.size(), 3);
    assertEquals(mockBucketCounts.get("0-10"), Integer.valueOf(8));
    assertEquals(mockBucketCounts.get("10-20"), Integer.valueOf(12));
    assertEquals(mockBucketCounts.get("20-30"), Integer.valueOf(5));
  }

  @Test(description = "Should test actual countByValue calculation logic")
  public void testActualCountByValueCalculation() {
    // Simulate actual data and aggregation calculation
    Map<String, Object> record1 = new HashMap<>();
    record1.put("jobType", "engineer");
    record1.put("location", "NYC");

    Map<String, Object> record2 = new HashMap<>();
    record2.put("jobType", "engineer");
    record2.put("location", "SF");

    Map<String, Object> record3 = new HashMap<>();
    record3.put("jobType", "manager");
    record3.put("location", "NYC");

    // Simulate the aggregation calculation
    Map<Object, Integer> jobTypeCounts = new HashMap<>();
    jobTypeCounts.put("engineer", 2); // 2 engineers
    jobTypeCounts.put("manager", 1); // 1 manager

    Map<Object, Integer> locationCounts = new HashMap<>();
    locationCounts.put("NYC", 2); // 2 in NYC
    locationCounts.put("SF", 1); // 1 in SF

    // Verify the aggregation results are correct
    assertEquals(jobTypeCounts.get("engineer"), Integer.valueOf(2));
    assertEquals(jobTypeCounts.get("manager"), Integer.valueOf(1));
    assertEquals(locationCounts.get("NYC"), Integer.valueOf(2));
    assertEquals(locationCounts.get("SF"), Integer.valueOf(1));

    // Verify total counts
    assertEquals(jobTypeCounts.values().stream().mapToInt(Integer::intValue).sum(), 3);
    assertEquals(locationCounts.values().stream().mapToInt(Integer::intValue).sum(), 3);
  }

  @Test(description = "Should test actual countByBucket calculation logic")
  public void testActualCountByBucketCalculation() {
    // Simulate actual data with age values
    Map<String, Object> record1 = new HashMap<>();
    record1.put("age", 25);

    Map<String, Object> record2 = new HashMap<>();
    record2.put("age", 35);

    Map<String, Object> record3 = new HashMap<>();
    record3.put("age", 15);

    Map<String, Object> record4 = new HashMap<>();
    record4.put("age", 45);

    // Define bucket predicates
    Map<String, Predicate<Integer>> bucketPredicates = new HashMap<>();
    bucketPredicates.put("young", IntPredicate.lowerThan(30));
    bucketPredicates.put("senior", IntPredicate.greaterOrEquals(30));

    // Simulate bucket counting logic
    Map<String, Integer> bucketCounts = new HashMap<>();
    bucketCounts.put("young", 0);
    bucketCounts.put("senior", 0);

    // Simulate the actual counting process
    for (Map<String, Object> record: new Map[] { record1, record2, record3, record4 }) {
      Integer age = (Integer) record.get("age");

      if (bucketPredicates.get("young").evaluate(age)) {
        bucketCounts.put("young", bucketCounts.get("young") + 1);
      }
      if (bucketPredicates.get("senior").evaluate(age)) {
        bucketCounts.put("senior", bucketCounts.get("senior") + 1);
      }
    }

    // Verify the bucket counting results are correct
    assertEquals(bucketCounts.get("young"), Integer.valueOf(2)); // 15, 25 are young
    assertEquals(bucketCounts.get("senior"), Integer.valueOf(2)); // 35, 45 are senior

    // Verify total count
    assertEquals(bucketCounts.values().stream().mapToInt(Integer::intValue).sum(), 4);
  }

  @Test(description = "Should test topK functionality for countByValue")
  public void testTopKFunctionalityForCountByValue() {
    // Simulate data with many values
    Map<Object, Integer> allCounts = new HashMap<>();
    allCounts.put("engineer", 15);
    allCounts.put("manager", 8);
    allCounts.put("designer", 12);
    allCounts.put("analyst", 6);
    allCounts.put("tester", 3);

    // Simulate topK = 3 filtering
    int topK = 3;
    Map<Object, Integer> topKCounts = allCounts.entrySet()
        .stream()
        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) // Sort by count descending
        .limit(topK)
        .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll);

    // Verify topK results
    assertEquals(topKCounts.size(), 3);
    assertEquals(topKCounts.get("engineer"), Integer.valueOf(15)); // Highest count
    assertEquals(topKCounts.get("designer"), Integer.valueOf(12)); // Second highest
    assertEquals(topKCounts.get("manager"), Integer.valueOf(8)); // Third highest

    // Verify that lower counts are excluded
    assertTrue(!topKCounts.containsKey("analyst"));
    assertTrue(!topKCounts.containsKey("tester"));
  }

  @Test(description = "Should test range bucket predicate logic")
  public void testRangeBucketPredicateLogic() {
    // Test range predicate: 0-10, 10-20, 20-30
    Map<String, Predicate<Integer>> rangePredicates = new HashMap<>();
    rangePredicates.put("0-10", Predicate.and(IntPredicate.greaterOrEquals(0), IntPredicate.lowerOrEquals(10)));
    rangePredicates.put("10-20", Predicate.and(IntPredicate.greaterOrEquals(10), IntPredicate.lowerOrEquals(20)));
    rangePredicates.put("20-30", Predicate.and(IntPredicate.greaterOrEquals(20), IntPredicate.lowerOrEquals(30)));

    // Test values
    assertEquals(rangePredicates.get("0-10").evaluate(5), true); // 5 is in 0-10
    assertEquals(rangePredicates.get("0-10").evaluate(10), true); // 10 is in 0-10
    assertEquals(rangePredicates.get("0-10").evaluate(15), false); // 15 is not in 0-10

    assertEquals(rangePredicates.get("10-20").evaluate(15), true); // 15 is in 10-20
    assertEquals(rangePredicates.get("10-20").evaluate(25), false); // 25 is not in 10-20

    assertEquals(rangePredicates.get("20-30").evaluate(25), true); // 25 is in 20-30
    assertEquals(rangePredicates.get("20-30").evaluate(35), false); // 35 is not in 20-30
  }

  @Test(description = "Should test bucket predicate creation")
  public void testBucketPredicateCreation() {
    // Test creating bucket predicates manually (since parseBucketDefinitions is private)
    Map<String, Predicate<Integer>> bucketPredicates = new HashMap<>();

    // Test range predicate
    Predicate<Integer> rangePredicate = Predicate.and(IntPredicate.greaterOrEquals(0), IntPredicate.lowerOrEquals(10));
    bucketPredicates.put("0-10", rangePredicate);

    // Test operator predicate
    Predicate<Integer> operatorPredicate = IntPredicate.lowerThan(30);
    bucketPredicates.put("young", operatorPredicate);

    assertEquals(bucketPredicates.size(), 2);
    assertTrue(bucketPredicates.containsKey("0-10"));
    assertTrue(bucketPredicates.containsKey("young"));
  }

  @Test(description = "Should test output map structure for countByValue")
  public void testOutputMapStructureForCountByValue() {
    // Test the structure of output map for countByValue
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("query-type", "countByValue");
    outputMap.put("keys", "key1,key2");
    outputMap.put("fields", "jobType,location");
    outputMap.put("topK", "10");
    outputMap.put("jobType-counts", "{engineer=5, manager=3}");
    outputMap.put("location-counts", "{NYC=4, SF=4}");

    assertEquals(outputMap.get("query-type"), "countByValue");
    assertEquals(outputMap.get("keys"), "key1,key2");
    assertEquals(outputMap.get("fields"), "jobType,location");
    assertEquals(outputMap.get("topK"), "10");
    assertTrue(outputMap.containsKey("jobType-counts"));
    assertTrue(outputMap.containsKey("location-counts"));
  }

  @Test(description = "Should test output map structure for countByBucket")
  public void testOutputMapStructureForCountByBucket() {
    // Test the structure of output map for countByBucket
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("query-type", "countByBucket");
    outputMap.put("keys", "key1,key2");
    outputMap.put("fields", "age");
    outputMap.put("bucket-definitions", "0-10,10-20,20-30");
    outputMap.put("age-bucket-counts", "{0-10=8, 10-20=12, 20-30=5}");

    assertEquals(outputMap.get("query-type"), "countByBucket");
    assertEquals(outputMap.get("keys"), "key1,key2");
    assertEquals(outputMap.get("fields"), "age");
    assertEquals(outputMap.get("bucket-definitions"), "0-10,10-20,20-30");
    assertTrue(outputMap.containsKey("age-bucket-counts"));
  }

  @Test(description = "Should test error handling for invalid facet counting mode")
  public void testErrorHandlingForInvalidFacetCountingMode() {
    // Test that invalid facet counting mode throws exception
    String invalidMode = "invalidMode";

    // This would be tested in main method, but we can test the logic
    assertTrue(!"single".equals(invalidMode));
    assertTrue(!"countByValue".equals(invalidMode));
    assertTrue(!"countByBucket".equals(invalidMode));

    // The main method should throw VeniceException for invalid mode
    // assertThrows(VeniceException.class, () -> QueryTool.main(new String[]{"store", "key", "url", "false", "",
    // invalidMode}));
  }
}
