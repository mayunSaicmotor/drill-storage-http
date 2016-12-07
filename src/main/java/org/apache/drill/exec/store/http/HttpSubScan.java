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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.drill.common.exceptions.ExecutionSetupException;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.common.logical.StoragePluginConfig;
import org.apache.drill.exec.physical.base.AbstractBase;
import org.apache.drill.exec.physical.base.PhysicalOperator;
import org.apache.drill.exec.physical.base.PhysicalVisitor;
import org.apache.drill.exec.physical.base.SubScan;
import org.apache.drill.exec.proto.UserBitShared.CoreOperatorType;
import org.apache.drill.exec.store.StoragePluginRegistry;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;

// Class containing information for reading a single HBase region
@JsonTypeName("http-region-scan")
public class HttpSubScan extends AbstractBase implements SubScan {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HttpSubScan.class);

  @JsonProperty
  public final HttpStoragePluginConfig storage;
  @JsonIgnore
  private final HttpStoragePlugin hbaseStoragePlugin;
  private final List<HttpSubScanSpec> httpSubScanSpecs;
  //TODO
  private final List<SchemaPath> columns;

  @JsonCreator
  public HttpSubScan(@JacksonInject StoragePluginRegistry registry,
                      @JsonProperty("userName") String userName,
                      @JsonProperty("storage") StoragePluginConfig storage,
                      @JsonProperty("httpSubScanSpecs") LinkedList<HttpSubScanSpec> regionScanSpecList,
                      @JsonProperty("columns") List<SchemaPath> columns) throws ExecutionSetupException {
    super(userName);
    hbaseStoragePlugin = (HttpStoragePlugin) registry.getPlugin(storage);
    this.httpSubScanSpecs = regionScanSpecList;
    this.storage = (HttpStoragePluginConfig) storage;
    this.columns = columns;
  }

  public HttpSubScan(HttpStoragePlugin plugin, HttpStoragePluginConfig config,
      List<HttpSubScanSpec> regionInfoList, List<SchemaPath> columns) {
    //super(userName);
    hbaseStoragePlugin = plugin;
    storage = config;
    this.httpSubScanSpecs = regionInfoList;
    this.columns = columns;
  }

  public List<HttpSubScanSpec> getHttpSubScanSpecs() {
    return httpSubScanSpecs;
  }

  @JsonIgnore
  public HttpStoragePluginConfig getStorageConfig() {
    return storage;
  }

  public List<SchemaPath> getColumns() {
    return columns;
  }

  @Override
  public boolean isExecutable() {
    return false;
  }

  @JsonIgnore
  public HttpStoragePlugin getStorageEngine(){
    return hbaseStoragePlugin;
  }

  @Override
  public <T, X, E extends Throwable> T accept(PhysicalVisitor<T, X, E> physicalVisitor, X value) throws E {
    return physicalVisitor.visitSubScan(this, value);
  }

  @Override
  public PhysicalOperator getNewWithChildren(List<PhysicalOperator> children) {
    Preconditions.checkArgument(children.isEmpty());
    return new HttpSubScan(hbaseStoragePlugin, storage, httpSubScanSpecs, columns);
  }

  @Override
  public Iterator<PhysicalOperator> iterator() {
    return Collections.emptyIterator();
  }

  @Override
  public int getOperatorType() {
    return CoreOperatorType.HBASE_SUB_SCAN_VALUE;
  }

}
