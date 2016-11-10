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

import java.util.Map;

import org.apache.drill.common.logical.StoragePluginConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

@JsonTypeName(HttpStoragePluginConfig.NAME)
public class HttpStoragePluginConfig extends StoragePluginConfig {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HttpStoragePluginConfig.class);

  private Map<String, String> config;

/*  @JsonIgnore
  private Configuration hbaseConf;*/

  @JsonIgnore
  //private Boolean sizeCalculatorEnabled;

  public static final String NAME = "http";

  private String connection;
  private String resultKey; // result key
  
/*  @JsonCreator
  public HttpStoragePluginConfig(@JsonProperty("connection") String connection,
      @JsonProperty("resultKey") String resultKey) {
    logger.info("initialize HttpStoragePluginConfig {}", connection);
    this.connection = connection;
    this.resultKey = resultKey;
  }*/
  
  public String getConnection() {
	    return connection;
	  }

	  public String getResultKey() {
	    return resultKey;
	  }
	  
  
  @JsonCreator
  public HttpStoragePluginConfig(@JsonProperty("connection") String connection,
      @JsonProperty("resultKey") String resultKey,@JsonProperty("config") Map<String, String> props, @JsonProperty("size.calculator.enabled") Boolean sizeCalculatorEnabled) {
		
	  this.connection = connection;
		this.resultKey = resultKey;
		this.config = props;
		if (config == null) {
			config = Maps.newHashMap();
		}
		logger.debug("Initializing http StoragePlugin configuration with zookeeper quorum '{}', port '{}'.",
				config.get("hbase.zookeeper.quorum"), config.get(HttpConstants.HBASE_ZOOKEEPER_PORT));
   /* if (sizeCalculatorEnabled == null) {
      this.sizeCalculatorEnabled = false;
    } else {
      this.sizeCalculatorEnabled = sizeCalculatorEnabled;
    }*/
  }

  @JsonProperty
  public Map<String, String> getConfig() {
    return ImmutableMap.copyOf(config);
  }

/*  @JsonProperty("size.calculator.enabled")
  public boolean isSizeCalculatorEnabled() {
    return sizeCalculatorEnabled;
  }
*/
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o == null || getClass() != o.getClass()) {
      return false;
    }
    HttpStoragePluginConfig that = (HttpStoragePluginConfig) o;
    return config.equals(that.config);
  }

  @Override
  public int hashCode() {
    return this.config != null ? this.config.hashCode() : 0;
  }
/*
  @JsonIgnore
  public Configuration getHBaseConf() {
    if (hbaseConf == null) {
      hbaseConf = HBaseConfiguration.create();
      if (config != null) {
        for (Map.Entry<String, String> entry : config.entrySet()) {
          hbaseConf.set(entry.getKey(), entry.getValue());
        }
      }
    }
    return hbaseConf;
  }*/

/*  @JsonIgnore
  public String getZookeeperQuorum() {
    return getHBaseConf().get("hbase.zookeeper.quorum");
  }*/

/*  @JsonIgnore
  public String getZookeeperport() {
    return getHBaseConf().get(DrillHttpConstants.HBASE_ZOOKEEPER_PORT);
  }

  @JsonIgnore
  @VisibleForTesting
  public void setZookeeperPort(int zookeeperPort) {
    this.config.put(DrillHttpConstants.HBASE_ZOOKEEPER_PORT, String.valueOf(zookeeperPort));
    getHBaseConf().setInt(DrillHttpConstants.HBASE_ZOOKEEPER_PORT, zookeeperPort);
  }*/

}
