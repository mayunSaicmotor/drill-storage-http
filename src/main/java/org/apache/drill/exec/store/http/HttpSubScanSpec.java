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

import java.util.List;

import javax.sql.DataSource;

import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.exec.store.AbstractRecordReader;
import org.apache.drill.exec.store.http.util.DBUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
public  class HttpSubScanSpec {

  		private final  String dbName;
	  	private final  String tableName;
	    private final String resultKey;
	    private final String connection;
	    private final  String startKey;
	    private final  String endKey;
	    private final  List<FilterOperator> filterArgs;
	    private final  List<String> groupByCols;
	    private final  List<String> orderByCols;
	    private final  Long limitValue;
	    private List<SchemaPath> columns;
	    
	    @JsonCreator
	public HttpSubScanSpec(@JsonProperty("dbName") String dbName,
			@JsonProperty("tableName") String tableName, 
			@JsonProperty("connection") String connection,
			@JsonProperty("resultKey") String resultKey, 
			@JsonProperty("startKey") String startKey,
			@JsonProperty("endKey") String endKey, 
			@JsonProperty("filterArgs") List<FilterOperator> filterArgs,
			@JsonProperty("groupByCols") List<String> groupByCols,
			@JsonProperty("orderByCols") List<String> orderByCols,
			@JsonProperty("limitValue") Long limitValue, 
			@JsonProperty("columns") List<SchemaPath> columns) {
		      this.dbName = dbName;
	      this.tableName = tableName;
	      this.connection = connection;
	      this.resultKey = resultKey;
	      this.startKey = startKey == null?"":startKey;
	      this.endKey = endKey == null?"":endKey;
	      this.filterArgs = filterArgs;
	      this.groupByCols = groupByCols;
	      this.orderByCols = orderByCols;
	      this.limitValue = limitValue;
	      this.columns = columns;
	    }
	    @JsonIgnore
	    public DataSource getDataSource() {
			//TODO
			return DBUtil.getDataSourceCache(dbName);
		}
/*		@JsonIgnore
	    public void setDataSource(DataSource dataSource) {
			this.dataSource = dataSource;
		}
		*/
		public String getTableName() {

	      return tableName;
	    }
	    
	    public String getConnection() {
		      return connection;
		    }
	    public String getResultKey() {
		      return resultKey;
		    }
	    public String getStartKey() {
	      return startKey;
	    }

	    public String getEndKey() {
	      return endKey;
	    }
	    
	    public List<FilterOperator> getFilterArgs() {
			return filterArgs;
		}

		public List<String> getGroupByCols() {
			return groupByCols;
		}
		
		public List<String> getOrderByCols() {
			return orderByCols;
		}
		public Long getLimitValue() {
			return limitValue;
		}
		public List<SchemaPath> getColumns() {
			return columns;
		}

		public String getDbName() {
			return dbName;
		}
		@JsonIgnore
	    String getFullURL() {
	      return connection + tableName + "&startKey=" + startKey + "&endKey=" + endKey;
	    }
		
		
		@JsonIgnore
	    String getSQL() {
			StringBuilder sb = new StringBuilder("select ");
			// TODO
			sb.append(getSelectedFields()).append(" from ").append(tableName).append(getFilters()).append(getGroupBy()).append(getOrderBy()).append(getLimit());
	
			return sb.toString();
	    }

		private String getGroupBy() {
			StringBuilder sb = new StringBuilder("");
			if(groupByCols != null && groupByCols.size()>0){
				sb.append(" group by ");
				int size= groupByCols.size();
				for(int i = 0; i< size; i++){
					String  nameExp =  groupByCols.get(i);
					sb.append(nameExp);
					if(i<( size -1)){
						sb.append(", ");
					}
				}					
			}
			return sb.toString();
		}
		
		private String getOrderBy() {
			StringBuilder sb = new StringBuilder("");
			if(orderByCols != null && orderByCols.size()>0){
				sb.append(" order by ");
				int size= orderByCols.size();
				for(int i = 0; i< size; i++){
					String  orderByField =  orderByCols.get(i);
					sb.append(orderByField.toString());
					if(i<( size -1)){
						sb.append(", ");
					}
				}					
			}
			return sb.toString();
		}
		
		
		private String getLimit() {
			
/*			// TODO when has groub by, don't push down limit;
			if(groupByCols != null && groupByCols.size()>0){
				return "";
			}*/
			if(limitValue != null){
				
				return " limit "+ limitValue;
			}
			
			return "";
		}
		private String getFilters() {
			StringBuilder sb = new StringBuilder("");
			if(filterArgs != null && filterArgs.size()>0){
				sb.append(" where ");
				int size= filterArgs.size();
				for(int i = 0; i< size; i++){
					FilterOperator fo =  filterArgs.get(i);
					sb.append(fo.toString());
					if(i<( size -1)){
						//TODO
						sb.append(" and ");
					}
				}					
			}
			return sb.toString();	
			
		}
	
/*		@JsonIgnore
	    public void setTableName(String tableName) {

		      this.tableName = tableName;
		    }*/
	    
		@JsonIgnore
	    String getSelectedFields() {
			if(columns == null || columns.size()==0){
				return "*";
			}
			StringBuilder sb = new StringBuilder();
			for(SchemaPath column : columns){
				sb.append(column.getAsUnescapedPath());
				sb.append(",");			
			}
	      return sb.substring(0, sb.length()-1);
			
			//return "name, sum(code) as code, count(code) as code_count, sum(score) as score, count(score) as score_count";
	    }
		

}

