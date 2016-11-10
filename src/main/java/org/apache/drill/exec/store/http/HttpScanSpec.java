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


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.drill.common.logical.data.NamedExpression;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;

public class HttpScanSpec {

  protected String tableName;
  protected String startRow;
  protected String stopRow;
  //protected Filter filter;

  private Map<String, Object> filterArgs = new HashMap<String, Object>();
  protected List<NamedExpression> groupByCols;
  
  public List<NamedExpression> getGroupByCols() {
	return groupByCols;
}

public void setGroupByCols(List<NamedExpression> groupByCols) {
	this.groupByCols = groupByCols;
}

@JsonCreator
  public HttpScanSpec(@JsonProperty("tableName") String tableName,
                       @JsonProperty("startRow") String startRow,
                       @JsonProperty("stopRow") String stopRow){
                       //@JsonProperty("serializedFilter") String serializedFilter,
                       //@JsonProperty("filterString") String filterString) {
/*    if (serializedFilter != null && filterString != null) {
      throw new IllegalArgumentException("The parameters 'serializedFilter' or 'filterString' cannot be specified at the same time.");
    }*/
    this.tableName = tableName;
    this.startRow = startRow;
    this.stopRow = stopRow;
/*    if (filterString != null) {
      this.filter = HttpUtils.parseFilterString(filterString);
    } else {
      this.filter = HttpUtils.deserializeFilter(serializedFilter);
    }*/
  }

  public HttpScanSpec(String tableName, String startRow, String stopRow, String filterKey, String filterValue) {
    this.tableName = tableName;
    this.startRow = startRow;
    this.stopRow = stopRow;
    this.filterArgs.put(filterKey, filterValue);
  }
  
  public HttpScanSpec(String tableName, String startRow, String stopRow, Map<String, Object> filterArgs) {
	    this.tableName = tableName;
	    this.startRow = startRow;
	    this.stopRow = stopRow;
	    this.filterArgs.putAll(filterArgs);
	  }

  public HttpScanSpec(String tableName) {
    this.tableName = tableName;
  }

  public String getTableName() {
    return tableName;
  }
  
  @JsonIgnore
  public String getURL() {
    if (filterArgs.size() == 0) {
      return tableName;
    }
    Joiner j = Joiner.on('&');
    String url = tableName;
    String argStr = j.withKeyValueSeparator("=").join(filterArgs);
    if (url.endsWith("?")) {
      url += argStr;
    } else if (url.contains("?")) {
      url += '&' + argStr;
    } else {
      url += '?' + argStr;
    }
    if(groupByCols != null){
        return url + groupByCols;
    }
    return url;
  }
  

  public String getStartRow() {
    return startRow == null ? "" : startRow;
  }

  public String getStopRow() {
    return stopRow == null ? "" : stopRow;
  }
  
  
  @JsonIgnore
    public Map<String, Object> getFilterArgs() {
	return filterArgs;
  }

	@JsonIgnore
  public void putFilter(String filterKey, String filterValue) {
    	this.filterArgs.put(filterKey, filterValue);
  }
  
/*  @JsonIgnore
  public Filter getFilter() {
    return this.filter;
  }*/

/*  public String getSerializedFilter() {
    return (this.filter != null) ? HttpUtils.serializeFilter(this.filter) : null;
  }*/

  @Override
  public String toString() {
    return "HttpScanSpec [tableName=" + tableName
        + ", startRow=" + (startRow == null ? null : startRow)
        + ", stopRow=" + (stopRow == null ? null : stopRow)
        + ", filterArgs=" + (filterArgs == null ? null : filterArgs.toString())
        + "]";
  }

  
  public void merge(HttpScanSpec that) {
	    for (Map.Entry<String, Object> entry : that.filterArgs.entrySet()) {
	      this.filterArgs.put(entry.getKey(), entry.getValue());
	    }
	  }
  
}
