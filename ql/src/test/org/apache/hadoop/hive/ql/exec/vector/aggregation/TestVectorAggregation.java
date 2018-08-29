/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.exec.vector.aggregation;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.sql.Timestamp;

import org.apache.hadoop.hive.ql.exec.vector.VectorRandomBatchSource;
import org.apache.hadoop.hive.ql.exec.vector.VectorRandomRowSource;
import org.apache.hadoop.hive.ql.exec.vector.VectorRandomRowSource.GenerationSpec;
import org.apache.hadoop.hive.ql.exec.vector.expressions.aggregates.VectorAggregateExpression;
import org.apache.hadoop.hive.ql.plan.ExprNodeColumnDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFCount.GenericUDAFCountEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFVariance;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator.AggregationBuffer;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.WritableShortObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.CharTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.DecimalTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;
import org.apache.hadoop.hive.serde2.typeinfo.VarcharTypeInfo;
import org.apache.hadoop.hive.serde2.io.HiveDecimalWritable;
import org.apache.hadoop.hive.serde2.io.ShortWritable;

import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Test;

public class TestVectorAggregation extends AggregationBase {

  @Test
  public void testAvgIntegers() throws Exception {
    Random random = new Random(7743);

    doIntegerTests("avg", random);
  }

  @Test
  public void testAvgFloating() throws Exception {
    Random random = new Random(7743);

    doFloatingTests("avg", random);
  }

  @Test
  public void testAvgDecimal() throws Exception {
    Random random = new Random(7743);

    doDecimalTests("avg", random);
  }

  @Test
  public void testAvgTimestamp() throws Exception {
    Random random = new Random(7743);

    doTests(
        random, "avg", TypeInfoFactory.timestampTypeInfo);
  }

  @Test
  public void testCount() throws Exception {
    Random random = new Random(7743);

    doTests(
        random, "count", TypeInfoFactory.shortTypeInfo);
    doTests(
        random, "count", TypeInfoFactory.longTypeInfo);
    doTests(
        random, "count", TypeInfoFactory.doubleTypeInfo);
    doTests(
        random, "count", new DecimalTypeInfo(18, 10));
    doTests(
        random, "count", TypeInfoFactory.stringTypeInfo);
  }

  @Test
  public void testCountStar() throws Exception {
    Random random = new Random(7743);

    doTests(
        random, "count", TypeInfoFactory.shortTypeInfo, true);
    doTests(
        random, "count", TypeInfoFactory.longTypeInfo, true);
    doTests(
        random, "count", TypeInfoFactory.doubleTypeInfo, true);
    doTests(
        random, "count", new DecimalTypeInfo(18, 10), true);
    doTests(
        random, "count", TypeInfoFactory.stringTypeInfo, true);
  }

  @Test
  public void testMax() throws Exception {
    Random random = new Random(7743);

    doIntegerTests("max", random);
    doFloatingTests("max", random);
    doDecimalTests("max", random);

    doTests(
        random, "max", TypeInfoFactory.timestampTypeInfo);

    doStringFamilyTests("max", random);
  }

  @Test
  public void testMin() throws Exception {
    Random random = new Random(7743);

    doIntegerTests("min", random);
    doFloatingTests("min", random);
    doDecimalTests("min", random);

    doTests(
        random, "min", TypeInfoFactory.timestampTypeInfo);

    doStringFamilyTests("min", random);
  }

  @Test
  public void testSum() throws Exception {
    Random random = new Random(7743);

    doTests(
        random, "sum", TypeInfoFactory.shortTypeInfo);
    doTests(
        random, "sum", TypeInfoFactory.longTypeInfo);
    doTests(
        random, "sum", TypeInfoFactory.doubleTypeInfo);

    // doDecimalTests("sum", random);

    // doTests(
    //     random, "sum", TypeInfoFactory.timestampTypeInfo);
  }

  private final static Set<String> varianceNames = new HashSet<String>();
  static {
    // Don't include synonyms.
    varianceNames.add("variance");
    varianceNames.add("var_samp");
    varianceNames.add("std");
    varianceNames.add("stddev_samp");
  }

  @Test
  public void testVarianceIntegers() throws Exception {
    Random random = new Random(7743);

    for (String aggregationName : varianceNames) {
      doIntegerTests(aggregationName, random);
    }
  }

  @Test
  public void testVarianceFloating() throws Exception {
    Random random = new Random(7743);

    for (String aggregationName : varianceNames) {
      doFloatingTests(aggregationName, random);
    }
  }

  @Test
  public void testVarianceDecimal() throws Exception {
    Random random = new Random(7743);

    for (String aggregationName : varianceNames) {
      doDecimalTests(aggregationName, random);
    }
  }

  @Test
  public void testVarianceTimestamp() throws Exception {
    Random random = new Random(7743);

    for (String aggregationName : varianceNames) {
      doTests(
          random, aggregationName, TypeInfoFactory.timestampTypeInfo);
    }
  }

  private static TypeInfo[] integerTypeInfos = new TypeInfo[] {
    TypeInfoFactory.byteTypeInfo,
    TypeInfoFactory.shortTypeInfo,
    TypeInfoFactory.intTypeInfo,
    TypeInfoFactory.longTypeInfo
  };

  // We have test failures with FLOAT.  Ignoring this issue for now.
  private static TypeInfo[] floatingTypeInfos = new TypeInfo[] {
    // TypeInfoFactory.floatTypeInfo,
    TypeInfoFactory.doubleTypeInfo
  };

  private void doIntegerTests(String aggregationName, Random random)
          throws Exception {
    for (TypeInfo typeInfo : integerTypeInfos) {
      doTests(
          random, aggregationName, typeInfo);
    }
  }

  private void doFloatingTests(String aggregationName, Random random)
      throws Exception {
    for (TypeInfo typeInfo : floatingTypeInfos) {
      doTests(
          random, aggregationName, typeInfo);
    }
  }

  private static TypeInfo[] decimalTypeInfos = new TypeInfo[] {
    new DecimalTypeInfo(38, 18),
    new DecimalTypeInfo(25, 2),
    new DecimalTypeInfo(19, 4),
    new DecimalTypeInfo(18, 10),
    new DecimalTypeInfo(17, 3),
    new DecimalTypeInfo(12, 2),
    new DecimalTypeInfo(7, 1)
  };

  private void doDecimalTests(String aggregationName, Random random)
      throws Exception {
    for (TypeInfo typeInfo : decimalTypeInfos) {
      doTests(
          random, aggregationName, typeInfo, /* isCountStar */ false);
    }
  }

  private static TypeInfo[] stringFamilyTypeInfos = new TypeInfo[] {
    TypeInfoFactory.stringTypeInfo,
    new CharTypeInfo(25),
    new CharTypeInfo(10),
    new VarcharTypeInfo(20),
    new VarcharTypeInfo(15)
  };

  private void doStringFamilyTests(String aggregationName, Random random)
      throws Exception {
    for (TypeInfo typeInfo : stringFamilyTypeInfos) {
      doTests(
          random, aggregationName, typeInfo);
    }
  }

  public static int getLinearRandomNumber(Random random, int maxSize) {
    //Get a linearly multiplied random number
    int randomMultiplier = maxSize * (maxSize + 1) / 2;
    int randomInt = random.nextInt(randomMultiplier);

    //Linearly iterate through the possible values to find the correct one
    int linearRandomNumber = 0;
    for(int i=maxSize; randomInt >= 0; i--){
        randomInt -= i;
        linearRandomNumber++;
    }

    return linearRandomNumber;
  }

  private static final int TEST_ROW_COUNT = 100000;

  private void doMerge(GenericUDAFEvaluator.Mode mergeUdafEvaluatorMode,
      Random random,
      String aggregationName,
      TypeInfo typeInfo,
      GenerationSpec keyGenerationSpec,
      List<String> columns, String[] columnNames,
      int dataAggrMaxKeyCount, int reductionFactor,
      TypeInfo partial1OutputTypeInfo,
      Object[] partial1ResultsArray)
          throws Exception {

    List<GenerationSpec> mergeAggrGenerationSpecList = new ArrayList<GenerationSpec>();

    mergeAggrGenerationSpecList.add(keyGenerationSpec);

    // Use OMIT for both.  We will fill in the data from the PARTIAL1 results.
    GenerationSpec mergeGenerationSpec =
        GenerationSpec.createOmitGeneration(partial1OutputTypeInfo);
    mergeAggrGenerationSpecList.add(mergeGenerationSpec);

    ExprNodeColumnDesc mergeCol1Expr =
        new ExprNodeColumnDesc(partial1OutputTypeInfo, "col1", "table", false);
    List<ExprNodeDesc> mergeParameters = new ArrayList<ExprNodeDesc>();
    mergeParameters.add(mergeCol1Expr);
    final int mergeParameterCount = mergeParameters.size();
    ObjectInspector[] mergeParameterObjectInspectors =
        new ObjectInspector[mergeParameterCount];
    for (int i = 0; i < mergeParameterCount; i++) {
      TypeInfo paramTypeInfo = mergeParameters.get(i).getTypeInfo();
      mergeParameterObjectInspectors[i] = TypeInfoUtils
          .getStandardWritableObjectInspectorFromTypeInfo(paramTypeInfo);
    }

    VectorRandomRowSource mergeRowSource = new VectorRandomRowSource();

    mergeRowSource.initGenerationSpecSchema(
        random, mergeAggrGenerationSpecList, /* maxComplexDepth */ 0,
        /* allowNull */ false, /* isUnicodeOk */ true);

    Object[][] mergeRandomRows = mergeRowSource.randomRows(TEST_ROW_COUNT);

    // Reduce the key range to cause there to be work for each PARTIAL2 key.
    final int mergeMaxKeyCount = dataAggrMaxKeyCount / reductionFactor;

    Object[] partial1Results = (Object[]) partial1ResultsArray[0];

    short partial1Key = 0;
    for (int i = 0; i < mergeRandomRows.length; i++) {
      // Find a non-NULL entry...
      while (true) {
        if (partial1Key >= dataAggrMaxKeyCount) {
          partial1Key = 0;
        }
        if (partial1Results[partial1Key] != null) {
          break;
        }
        partial1Key++;
      }
      final short mergeKey = (short) (partial1Key % mergeMaxKeyCount);
      mergeRandomRows[i][0] = new ShortWritable(mergeKey);
      mergeRandomRows[i][1] = partial1Results[partial1Key];
      partial1Key++;
    }

    VectorRandomBatchSource mergeBatchSource =
        VectorRandomBatchSource.createInterestingBatches(
            random,
            mergeRowSource,
            mergeRandomRows,
            null);

    // We need to pass the original TypeInfo in for initializing the evaluator.
    GenericUDAFEvaluator mergeEvaluator =
        getEvaluator(aggregationName, typeInfo);

    /*
    System.out.println(
        "*DEBUG* GenericUDAFEvaluator for " + aggregationName + ", " + typeInfo.getTypeName() + ": " +
            mergeEvaluator.getClass().getSimpleName());
    */

    // The only way to get the return object inspector (and its return type) is to
    // initialize it...

    ObjectInspector mergeReturnOI =
        mergeEvaluator.init(
            mergeUdafEvaluatorMode,
            mergeParameterObjectInspectors);
    TypeInfo mergeOutputTypeInfo =
        TypeInfoUtils.getTypeInfoFromObjectInspector(mergeReturnOI);

    Object[] mergeResultsArray = new Object[AggregationTestMode.count];

    executeAggregationTests(
        aggregationName,
        partial1OutputTypeInfo,
        mergeEvaluator,
        mergeOutputTypeInfo,
        mergeUdafEvaluatorMode,
        mergeMaxKeyCount,
        columns,
        columnNames,
        mergeParameters,
        mergeRandomRows,
        mergeRowSource,
        mergeBatchSource,
        mergeResultsArray);

    verifyAggregationResults(
        partial1OutputTypeInfo,
        mergeOutputTypeInfo,
        mergeMaxKeyCount,
        mergeUdafEvaluatorMode,
        mergeResultsArray);
  }

  private void doTests(Random random, String aggregationName, TypeInfo typeInfo)
      throws Exception {
    doTests(random, aggregationName, typeInfo, false);
  }

  private void doTests(Random random, String aggregationName, TypeInfo typeInfo,
      boolean isCountStar)
          throws Exception {

    List<GenerationSpec> dataAggrGenerationSpecList = new ArrayList<GenerationSpec>();

    TypeInfo keyTypeInfo = TypeInfoFactory.shortTypeInfo;
    GenerationSpec keyGenerationSpec = GenerationSpec.createOmitGeneration(keyTypeInfo);
    dataAggrGenerationSpecList.add(keyGenerationSpec);

    GenerationSpec generationSpec = GenerationSpec.createSameType(typeInfo);
    dataAggrGenerationSpecList.add(generationSpec);

    List<String> columns = new ArrayList<String>();
    columns.add("col0");
    columns.add("col1");

    ExprNodeColumnDesc dataAggrCol1Expr = new ExprNodeColumnDesc(typeInfo, "col1", "table", false);
    List<ExprNodeDesc> dataAggrParameters = new ArrayList<ExprNodeDesc>();
    if (!isCountStar) {
      dataAggrParameters.add(dataAggrCol1Expr);
    }
    final int dataAggrParameterCount = dataAggrParameters.size();
    ObjectInspector[] dataAggrParameterObjectInspectors = new ObjectInspector[dataAggrParameterCount];
    for (int i = 0; i < dataAggrParameterCount; i++) {
      TypeInfo paramTypeInfo = dataAggrParameters.get(i).getTypeInfo();
      dataAggrParameterObjectInspectors[i] = TypeInfoUtils
          .getStandardWritableObjectInspectorFromTypeInfo(paramTypeInfo);
    }

    String[] columnNames = columns.toArray(new String[0]);

    final int dataAggrMaxKeyCount = 20000;
    final int reductionFactor = 16;

    ObjectInspector keyObjectInspector = VectorRandomRowSource.getObjectInspector(keyTypeInfo);

    /*
     * PARTIAL1.
     */

    VectorRandomRowSource partial1RowSource = new VectorRandomRowSource();

    boolean allowNull = true;
    partial1RowSource.initGenerationSpecSchema(
        random, dataAggrGenerationSpecList, /* maxComplexDepth */ 0,
        allowNull,  /* isUnicodeOk */ true);

    Object[][] partial1RandomRows = partial1RowSource.randomRows(TEST_ROW_COUNT);

    final int partial1RowCount = partial1RandomRows.length;
    for (int i = 0; i < partial1RowCount; i++) {
      final short shortKey = (short) getLinearRandomNumber(random, dataAggrMaxKeyCount);
      partial1RandomRows[i][0] =
         ((WritableShortObjectInspector) keyObjectInspector).create((short) shortKey);
    }

    VectorRandomBatchSource partial1BatchSource =
        VectorRandomBatchSource.createInterestingBatches(
            random,
            partial1RowSource,
            partial1RandomRows,
            null);

    GenericUDAFEvaluator partial1Evaluator = getEvaluator(aggregationName, typeInfo);
    if (isCountStar) {
      Assert.assertTrue(partial1Evaluator instanceof GenericUDAFCountEvaluator);
      GenericUDAFCountEvaluator countEvaluator = (GenericUDAFCountEvaluator) partial1Evaluator;
      countEvaluator.setCountAllColumns(true);
    }

    /*
    System.out.println(
        "*DEBUG* GenericUDAFEvaluator for " + aggregationName + ", " + typeInfo.getTypeName() + ": " +
            partial1Evaluator.getClass().getSimpleName());
    */

    // The only way to get the return object inspector (and its return type) is to
    // initialize it...
    final GenericUDAFEvaluator.Mode partial1UdafEvaluatorMode = GenericUDAFEvaluator.Mode.PARTIAL1;
    ObjectInspector partial1ReturnOI =
        partial1Evaluator.init(
            partial1UdafEvaluatorMode,
            dataAggrParameterObjectInspectors);
    TypeInfo partial1OutputTypeInfo =
        TypeInfoUtils.getTypeInfoFromObjectInspector(partial1ReturnOI);

    Object[] partial1ResultsArray = new Object[AggregationTestMode.count];

    executeAggregationTests(
        aggregationName,
        typeInfo,
        partial1Evaluator,
        partial1OutputTypeInfo,
        partial1UdafEvaluatorMode,
        dataAggrMaxKeyCount,
        columns,
        columnNames,
        dataAggrParameters,
        partial1RandomRows,
        partial1RowSource,
        partial1BatchSource,
        partial1ResultsArray);

    verifyAggregationResults(
        typeInfo,
        partial1OutputTypeInfo,
        dataAggrMaxKeyCount,
        partial1UdafEvaluatorMode,
        partial1ResultsArray);

    final boolean hasDifferentFinalExpr;
    if (varianceNames.contains(aggregationName)) {

      // No STRUCT support in 2.6
      hasDifferentFinalExpr = false;
    } else {
      switch (aggregationName) {
      case "avg":
        // No STRUCT support in 2.6
        hasDifferentFinalExpr = false;
        break;
      case "count":
        hasDifferentFinalExpr = true;
        break;
      case "max":
      case "min":
      case "sum":
        hasDifferentFinalExpr = false;
        break;
      default:
        throw new RuntimeException("Unexpected aggregation name " + aggregationName);
      }
    }
    if (hasDifferentFinalExpr) {

      /*
       * FINAL.
       */

      final GenericUDAFEvaluator.Mode mergeUdafEvaluatorMode = GenericUDAFEvaluator.Mode.FINAL;

      doMerge(
          mergeUdafEvaluatorMode,
          random,
          aggregationName,
          typeInfo,
          keyGenerationSpec,
          columns, columnNames,
          dataAggrMaxKeyCount,
          reductionFactor,
          partial1OutputTypeInfo,
          partial1ResultsArray);
    }
  }
}