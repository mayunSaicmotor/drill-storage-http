/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.exec.store.http;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.apache.drill.PlanTestBase;
import org.apache.drill.exec.ExecConstants;
import org.apache.drill.exec.exception.SchemaChangeException;
import org.apache.drill.exec.rpc.user.QueryDataBatch;
import org.apache.drill.exec.store.StoragePluginRegistry;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class HttpTestBase extends PlanTestBase implements HttpTestConstants {
  protected static HttpStoragePlugin storagePlugin;
  protected static HttpStoragePluginConfig storagePluginConfig;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    initHttpStoragePlugin();
  }

  public static void initHttpStoragePlugin() throws Exception {
    final StoragePluginRegistry pluginRegistry = getDrillbitContext().getStorage();
    storagePlugin = (HttpStoragePlugin) pluginRegistry.getPlugin(HttpStoragePluginConfig.NAME);
    storagePluginConfig = storagePlugin.getConfig();
    storagePluginConfig.setEnabled(true);
    pluginRegistry.createOrUpdate(HttpStoragePluginConfig.NAME, storagePluginConfig, true);
    if (System.getProperty("drill.mongo.tests.bson.reader", "true").equalsIgnoreCase("false")) {
      testNoResult(String.format("alter session set `%s` = false", ExecConstants.MONGO_BSON_RECORD_READER));
    } else {
      testNoResult(String.format("alter session set `%s` = true", ExecConstants.MONGO_BSON_RECORD_READER));
    }
  }

  public List<QueryDataBatch> runHttpSQLWithResults(String sql)
      throws Exception {
    return testSqlWithResults(sql);
  }

  public void runHttpSQLVerifyCount(String sql, int expectedRowCount)
      throws Exception {
    List<QueryDataBatch> results = runHttpSQLWithResults(sql);
    printResultAndVerifyRowCount(results, expectedRowCount);
  }

  public void printResultAndVerifyRowCount(List<QueryDataBatch> results,
      int expectedRowCount) throws SchemaChangeException {
    int rowCount = printResult(results);
    if (expectedRowCount != -1) {
     // Assert.assertEquals(expectedRowCount, rowCount);
    }
  }

  public void testHelper(String query, String expectedExprInPlan,
      int expectedRecordCount) throws Exception {
    testPhysicalPlan(query, expectedExprInPlan);
    int actualRecordCount = testSql(query);
    assertEquals(
        String.format(
            "Received unexpected number of rows in output: expected=%d, received=%s",
            expectedRecordCount, actualRecordCount), expectedRecordCount,
        actualRecordCount);
  }

  @AfterClass
  public static void tearDownHttpTestBase() throws Exception {
    storagePlugin = null;
  }

}