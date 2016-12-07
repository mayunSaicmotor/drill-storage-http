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


import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;

public class HttpScanSpec {

  protected String tableName;
  protected String startRow;
  protected String stopRow;
  //protected Filter filter;

  private List<FilterOperator> filterArgs = new ArrayList<FilterOperator>();
  protected List<Boolean> filterBooleanAnds = new ArrayList<Boolean>();
  protected List<String> groupByCols;
  protected List<String> orderByCols;
  protected List<String> OriginalOrderByCols;
  protected Long limitValue;
  
  




public List<Boolean> getFilterBooleanAnds() {
	return filterBooleanAnds;
}

public void setFilterBooleanAnds(List<Boolean> filterBooleanAnds) {
	this.filterBooleanAnds = filterBooleanAnds;
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

  public HttpScanSpec(String tableName, String startRow, String stopRow, FilterOperator filterOperator) {
    this.tableName = tableName;
    this.startRow = startRow;
    this.stopRow = stopRow;
    this.filterArgs.add(filterOperator);
  }
  
  public HttpScanSpec(String tableName, String startRow, String stopRow, List<FilterOperator> filterArgs) {
	    this.tableName = tableName;
	    this.startRow = startRow;
	    this.stopRow = stopRow;
	    this.filterArgs.addAll(filterArgs);
	  }

  public HttpScanSpec(String tableName) {
    this.tableName = tableName;
  }

  public String getTableName() {
    return tableName;
  }
  
  @JsonIgnore
  public String getURL() {
    if (filterArgs.size() == 0 &&(groupByCols==null || groupByCols.size()==0)) {
      return tableName;
    }
    Joiner j = Joiner.on('&');
    String url = tableName;
    String argStr = filterArgs.toString();
    if (url.endsWith("?")) {
      url += argStr;
      
    } else if (url.contains("?")) {
      url += '&' + argStr;
    } else {
      url += '?' + argStr;
    }
    if(groupByCols != null){
    	return url += '&' +"groupByCols=" + groupByCols.toString();
    }
    return url;
  }
  

  public String getStartRow() {
    return startRow == null ? "" : startRow;
  }

  public String getStopRow() {
    return stopRow == null ? "" : stopRow;
  }
  
  

    public List<FilterOperator> getFilterArgs() {
	return filterArgs;
  }
    
    public List<String> getGroupByCols() {
    	return groupByCols;
    }

	@JsonIgnore
    public void setGroupByCols(List<String> groupByCols) {
    	this.groupByCols = groupByCols;
    }

	public List<String> getOrderByCols() {
		return orderByCols;
	}

	@JsonIgnore
	public void setOrderByCols(List<String> orderByCols) {
		this.orderByCols = orderByCols;
	}
	@JsonIgnore
	public List<String> getOriginalOrderByCols() {
		return OriginalOrderByCols;
	}
	@JsonIgnore
	public void setOriginalOrderByCols(List<String> originalOrderByCols) {
		OriginalOrderByCols = originalOrderByCols;
	}

	public Long getLimitValue() {
		return limitValue;
	}
	@JsonIgnore
	public void setLimitValue(Long limitValue) {
		this.limitValue = limitValue;
	}
	@JsonIgnore
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}
	@JsonIgnore
	public void setStartRow(String startRow) {
		this.startRow = startRow;
	}
	@JsonIgnore
	public void setStopRow(String stopRow) {
		this.stopRow = stopRow;
	}

	@JsonIgnore
	public void setFilterArgs(List<FilterOperator> filterArgs) {
		this.filterArgs = filterArgs;
	}

	@JsonIgnore
  public void putFilter(FilterOperator filterOperator) {
    	this.filterArgs.add(filterOperator);
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
        + ", groupByCols=" + (groupByCols == null ? null : groupByCols.toString())
        + "]";
  }

  
  public void merge(Boolean filterBooleanAnd, HttpScanSpec that) {
	    //for (Map.Entry<String, Object> entry : that.filterArgs.entrySet()) {
	  this.filterBooleanAnds.add(filterBooleanAnd);
	  if(that.getFilterArgs() != null){
	      this.filterArgs.addAll(that.getFilterArgs());
	  }
	  if(that.getGroupByCols()!= null){
	      this.groupByCols.addAll(that.groupByCols);
	  }
  }
}
