/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.queries;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.apache.pinot.core.common.Operator;
import org.apache.pinot.core.operator.query.FastFilteredCountOperator;
import org.apache.pinot.core.operator.query.SelectionOnlyOperator;
import org.apache.pinot.segment.local.indexsegment.immutable.ImmutableSegmentLoader;
import org.apache.pinot.segment.local.segment.creator.impl.SegmentIndexCreationDriverImpl;
import org.apache.pinot.segment.local.segment.index.loader.IndexLoadingConfig;
import org.apache.pinot.segment.local.segment.readers.GenericRowRecordReader;
import org.apache.pinot.segment.spi.ImmutableSegment;
import org.apache.pinot.segment.spi.IndexSegment;
import org.apache.pinot.segment.spi.creator.SegmentGeneratorConfig;
import org.apache.pinot.spi.config.table.TableConfig;
import org.apache.pinot.spi.config.table.TableType;
import org.apache.pinot.spi.data.FieldSpec;
import org.apache.pinot.spi.data.Schema;
import org.apache.pinot.spi.data.readers.GenericRow;
import org.apache.pinot.spi.utils.builder.TableConfigBuilder;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;


public class RangeQueriesTest extends BaseQueriesTest {

  private static final File INDEX_DIR = new File(FileUtils.getTempDirectory(), "RangeQueriesTest");
  private static final String RAW_TABLE_NAME = "testTable";
  private static final String SEGMENT_NAME = "testSegment";

  private static final int NUM_RECORDS = 1000;
  private static final int MAX_VALUE = NUM_RECORDS * 100;
  private static final String DICTIONARIZED_INT_COL = "dictionarizedIntCol";
  private static final String RAW_INT_COL = "rawIntCol";
  private static final String RAW_LONG_COL = "rawLongCol";
  private static final String RAW_FLOAT_COL = "rawFloatCol";
  private static final String RAW_DOUBLE_COL = "rawDoubleCol";

  private static final Schema SCHEMA = new Schema.SchemaBuilder()
      .addSingleValueDimension(DICTIONARIZED_INT_COL, FieldSpec.DataType.INT)
      .addSingleValueDimension(RAW_INT_COL, FieldSpec.DataType.INT)
      .addSingleValueDimension(RAW_LONG_COL, FieldSpec.DataType.LONG)
      .addSingleValueDimension(RAW_FLOAT_COL, FieldSpec.DataType.FLOAT)
      .addSingleValueDimension(RAW_DOUBLE_COL, FieldSpec.DataType.DOUBLE)
      .build();

  private static final TableConfig TABLE_CONFIG =
      new TableConfigBuilder(TableType.OFFLINE).setTableName(RAW_TABLE_NAME)
          .setNoDictionaryColumns(Arrays.asList(RAW_INT_COL, RAW_LONG_COL, RAW_FLOAT_COL, RAW_DOUBLE_COL))
          .setRangeIndexColumns(Arrays.asList(DICTIONARIZED_INT_COL, RAW_INT_COL, RAW_LONG_COL, RAW_FLOAT_COL,
              RAW_DOUBLE_COL))
          .build();

  private IndexSegment _indexSegment;
  private List<IndexSegment> _indexSegments;

  @Override
  protected String getFilter() {
    return "";
  }

  @Override
  protected IndexSegment getIndexSegment() {
    return _indexSegment;
  }

  @Override
  protected List<IndexSegment> getIndexSegments() {
    return _indexSegments;
  }

  @BeforeClass
  public void setUp()
      throws Exception {
    FileUtils.deleteQuietly(INDEX_DIR);
    List<GenericRow> records = new ArrayList<>(NUM_RECORDS);
    for (int i = 0; i < NUM_RECORDS; i++) {
      GenericRow record = new GenericRow();
      int intValue = ((MAX_VALUE + NUM_RECORDS / 2) - (i * 100)) % MAX_VALUE;
      record.putValue(DICTIONARIZED_INT_COL, intValue);
      record.putValue(RAW_INT_COL, intValue);
      record.putValue(RAW_LONG_COL, (long) intValue);
      record.putValue(RAW_FLOAT_COL, (float) intValue);
      record.putValue(RAW_DOUBLE_COL, (double) intValue);
      records.add(record);
    }

    SegmentGeneratorConfig segmentGeneratorConfig = new SegmentGeneratorConfig(TABLE_CONFIG, SCHEMA);
    segmentGeneratorConfig.setTableName(RAW_TABLE_NAME);
    segmentGeneratorConfig.setSegmentName(SEGMENT_NAME);
    segmentGeneratorConfig.setOutDir(INDEX_DIR.getPath());

    SegmentIndexCreationDriverImpl driver = new SegmentIndexCreationDriverImpl();
    driver.init(segmentGeneratorConfig, new GenericRowRecordReader(records));
    driver.build();

    IndexLoadingConfig indexLoadingConfig = new IndexLoadingConfig();
    indexLoadingConfig.setRangeIndexColumns(
        new HashSet<>(Arrays.asList(DICTIONARIZED_INT_COL, RAW_INT_COL, RAW_LONG_COL, RAW_FLOAT_COL, RAW_DOUBLE_COL)));

    ImmutableSegment immutableSegment =
        ImmutableSegmentLoader.load(new File(INDEX_DIR, SEGMENT_NAME), indexLoadingConfig);
    _indexSegment = immutableSegment;
    _indexSegments = Arrays.asList(immutableSegment, immutableSegment);
  }

  @DataProvider
  public static Object[][] selectionTestCases() {
    return new Object[][]{
        {buildSelectionQuery(DICTIONARIZED_INT_COL, 250, 500), 250, 500},
        {buildSelectionQuery(RAW_INT_COL, 250, 500), 250, 500},
        {buildSelectionQuery(RAW_LONG_COL, 250, 500), 250, 500},
        {buildSelectionQuery(RAW_FLOAT_COL, 250, 500), 250, 500},
        {buildSelectionQuery(RAW_DOUBLE_COL, 250, 500), 250, 500},
    };
  }

  private static String buildSelectionQuery(String filterCol, Number min, Number max) {
    return "select " + RAW_INT_COL + " from " + RAW_TABLE_NAME + " where " + filterCol + " between "
        + buildFilter(filterCol, min, max);
  }

  @DataProvider
  public static Object[][] countTestCases() {
    return new Object[][]{
        {buildCountQuery(DICTIONARIZED_INT_COL, 250, 500), 3},
        {buildCountQuery(RAW_INT_COL, 250, 500), 3},
        {buildCountQuery(RAW_LONG_COL, 250, 500), 3},
        {buildCountQuery(RAW_FLOAT_COL, 250, 500), 3},
        {buildCountQuery(RAW_DOUBLE_COL, 250, 500), 3},
    };
  }

  private static String buildCountQuery(String filterCol, Number min, Number max) {
    return "select count(*) from " + RAW_TABLE_NAME + " where " + filterCol + " between "
        + buildFilter(filterCol, min, max);
  }

  private static String buildFilter(String filterCol, Number min, Number max) {
    switch (filterCol) {
      case DICTIONARIZED_INT_COL:
      case RAW_INT_COL:
      case RAW_LONG_COL:
        return min.intValue() + " and " + max.intValue();
      case RAW_FLOAT_COL:
      case RAW_DOUBLE_COL:
        return min.doubleValue() + " and " + max.doubleValue();
      default:
        throw new AssertionError("unexpected column: " + filterCol);
    }
  }

  @Test(dataProvider = "selectionTestCases")
  public void testSelectionOverRangeFilter(String query, int min, int max) {
    Operator<?> operator = getOperator(query);
    assertTrue(operator instanceof SelectionOnlyOperator);
    for (Object[] row : Objects.requireNonNull(((SelectionOnlyOperator) operator).nextBlock().getSelectionResult())) {
      int value = (int) row[0];
      assertTrue(value >= min);
      assertTrue(value <= max);
    }
  }

  @Test(dataProvider = "countTestCases")
  public void testCountOverRangeFilter(String query, int expectedCount) {
    Operator<?> operator = getOperator(query);
    assertTrue(operator instanceof FastFilteredCountOperator);
    List<Object> aggregationResult = ((FastFilteredCountOperator) operator).nextBlock().getAggregationResult();
    assertNotNull(aggregationResult);
    assertEquals(aggregationResult.size(), 1);
    assertEquals(((Number) aggregationResult.get(0)).intValue(), expectedCount, query);
  }
}
