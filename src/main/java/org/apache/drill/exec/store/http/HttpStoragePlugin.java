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

import java.io.IOException;
import java.util.Set;

import org.apache.calcite.schema.SchemaPlus;
import org.apache.drill.common.JSONOptions;
import org.apache.drill.exec.ops.OptimizerRulesContext;
import org.apache.drill.exec.server.DrillbitContext;
import org.apache.drill.exec.store.AbstractStoragePlugin;
import org.apache.drill.exec.store.SchemaConfig;
import org.apache.drill.exec.store.StoragePluginOptimizerRule;
import org.apache.drill.exec.store.http.util.DBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;

//import jersey.repackaged.com.google.common.collect.ImmutableSet;

public class HttpStoragePlugin extends AbstractStoragePlugin {
  //private static final HttpConnectionManager hbaseConnectionManager = HttpConnectionManager.INSTANCE;
  static final Logger logger = LoggerFactory.getLogger(HttpStoragePlugin.class);
  private final DrillbitContext context;
  private final HttpStoragePluginConfig storeConfig;
  private final HttpSchemaFactory schemaFactory;
  //private final HBaseConnectionKey connectionKey;

  //private final String name;

	public HttpStoragePlugin(HttpStoragePluginConfig storeConfig, DrillbitContext context, String name)
			throws IOException {

		logger.info("create HttpStoragePlugin");
		this.context = context;
		this.schemaFactory = new HttpSchemaFactory(this, name);
		this.storeConfig = storeConfig;
		// this.name = name;
		// this.connectionKey = new HBaseConnectionKey();
		// TODO init the datasource cache
		DBUtil.createDataSourceCache(storeConfig);
	}

  public DrillbitContext getContext() {
    return this.context;
  }

  @Override
  public boolean supportsRead() {
    return true;
  }

  @Override
  public HttpGroupScan getPhysicalScan(String userName, JSONOptions selection) throws IOException {
    HttpScanSpec scanSpec = selection.getListWith(new ObjectMapper(), new TypeReference<HttpScanSpec>() {});
    return new HttpGroupScan(userName, this, scanSpec, null);
  }

  @Override
  public void registerSchemas(SchemaConfig schemaConfig, SchemaPlus parent) throws IOException {
	  logger.info("schemaConfig: " + schemaConfig.toString());
	  logger.info("parent: " + parent.toString());
    schemaFactory.registerSchemas(schemaConfig, parent);
  }

  @Override
  public HttpStoragePluginConfig getConfig() {
    return storeConfig;
  }

  @Override
  public Set<StoragePluginOptimizerRule> getPhysicalOptimizerRules(OptimizerRulesContext optimizerRulesContext) {
    //return ImmutableSet.of(HttpPushDownFilterForScan.INSTANCE);
	  //return ImmutableSet.of(PushGroupIntoScan.GROUPBY_ON_SCAN, PushGroupIntoScan.GROUPBY_ON_PROJECT);
		return ImmutableSet.of(
				PushDownOrderByToScan.SORT_ON_SCAN,
				PushDownOrderByToScan.SORT_ON_PROJECT_SCAN,
				PushDownOrderByToScan.TOPN_ON_SCAN,
				PushDownOrderByToScan.TOPN_ON_PROJECT_SCAN,
				PushDownOrderByToScan.COMMON_SORT_ON_SCAN,
				PushDownLimitToScan.LIMIT_ON_PROJECT_SCAN, 
				PushDownLimitToScan.LIMIT_ON_SCAN,
				PushDownLimitToScan.COMMON_LIMIT_ON_SCAN,
				PushDownFilterToScan.INSTANCE,
				PushDownGroupByToScan.GROUPBY_ON_STREAMAGG_SCAN, 
				PushDownGroupByToScan.GROUPBY_ON_STREAMAGG_PROJECT_SCAN,				
				PushDownGroupByToScan.GROUPBY_ON_HASHAGG_SCAN, 
				PushDownGroupByToScan.GROUPBY_ON_HASHAGG_PROJECT_SCAN);
		// , PushGroupAndFilterIntoScan.GROUPBY_AND_FILTER_ON_HASHAGG_PROJECT_FILTER_SCAN);
		// return ImmutableSet.of(HttpPushFilterIntoScan.FILTER_ON_SCAN,
		// HttpPushFilterIntoScan.FILTER_ON_PROJECT);
	}
  
/*  @Override
  public Set<PushDownGroupByToScan> getOptimizerRules(OptimizerRulesContext optimizerRulesContext) {
    return ImmutableSet.of(PushDownGroupByToScan.INSTANCE);
    //return ImmutableSet.of(HttpPushFilterIntoScan.FILTER_ON_SCAN, HttpPushFilterIntoScan.FILTER_ON_PROJECT);
  }
  
  @Override
  public Set<PushDownGroupByToScan> getLogicalOptimizerRules(OptimizerRulesContext optimizerRulesContext) {
    return ImmutableSet.of(PushDownGroupByToScan.INSTANCE);
    //return ImmutableSet.of(HttpPushFilterIntoScan.FILTER_ON_SCAN, HttpPushFilterIntoScan.FILTER_ON_PROJECT);
  }*/
  

/*  @Override
  public void close() throws Exception {
    //hbaseConnectionManager.closeConnection(connectionKey);
  }*/

/*  public Connection getConnection() {
    return hbaseConnectionManager.getConnection(connectionKey);
  }*/

  /**
   * An internal class which serves the key in a map of {@link HttpStoragePlugin} => {@link Connection}.
   */
 /* class HBaseConnectionKey {

    private final ReentrantLock lock = new ReentrantLock();

    private HBaseConnectionKey() {}

    public void lock() {
      lock.lock();
    }

    public void unlock() {
      lock.unlock();
    }

    public Configuration getHBaseConf() {
      return storeConfig.getHBaseConf();
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((name == null) ? 0 : name.hashCode());
      result = prime * result + ((storeConfig == null) ? 0 : storeConfig.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      } else if (obj == null) {
        return false;
      } else if (getClass() != obj.getClass()) {
        return false;
      }

      HttpStoragePlugin other = ((HBaseConnectionKey) obj).getHBaseStoragePlugin();
      if (name == null) {
        if (other.name != null) {
          return false;
        }
      } else if (!name.equals(other.name)) {
        return false;
      }
      if (storeConfig == null) {
        if (other.storeConfig != null) {
          return false;
        }
      } else if (!storeConfig.equals(other.storeConfig)) {
        return false;
      }
      return true;
    }

    private HttpStoragePlugin getHBaseStoragePlugin() {
      return HttpStoragePlugin.this;
    }

  }*/

}
