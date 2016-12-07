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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.drill.common.logical.StoragePluginConfig;

public class InsertTestData {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InsertTestData.class);
  private static final Map<String, DataSource> dataSourceMap =new HashMap<String, DataSource>();
  
  private static String table = "student";
  private static List<String> part_data_DBs = new ArrayList<String>();
  private static int sub_students_count = 8;
  private static int batch_insert_count = 200;
  
  
  public static List<String> getPart_data_DBs() {
	return part_data_DBs;
}


public static void setPart_data_DBs(List<String> part_data_DBs) {
	InsertTestData.part_data_DBs = part_data_DBs;
}


static{
	  for(int i =1;i<=sub_students_count;i++){
		  
		  part_data_DBs.add(table+i);
	  }
  }
/*  private static String student_new = "student_new";
  private static String student_all = "student_all";*/
  
  
  public static DataSource createDataSource(String driver, String url, String userName, String password) {

		BasicDataSource source = new BasicDataSource();
		source.setDriverClassName(driver);
		source.setUrl(url);

		if (userName != null) {
			source.setUsername(userName);
		}

		if (password != null) {
			source.setPassword(password);
		}
		source.setInitialSize(1);
		source.setPoolPreparedStatements(true);
		try {
			// initial a connection
			source.getConnection();
		} catch (SQLException sqlE) {

			logger.error("db connection error: ", sqlE);
		}
		return source;

  }
  
  
	public static void createDataSourceCache() {

		dataSourceMap.put("mayun", createDataSource("com.mysql.jdbc.Driver", "jdbc:mysql://mayun:3306/drill", "root", "mayun"));
		
		dataSourceMap.put(table, createDataSource("com.mysql.jdbc.Driver", "jdbc:mysql://mayun:3306/"+ table, "root", "mayun"));
		for (int i = 0; i < sub_students_count; i++) {
			DataSource dataSource = createDataSource("com.mysql.jdbc.Driver", "jdbc:mysql://mayun:3306/"+ part_data_DBs.get(i), "root", "mayun");
			dataSourceMap.put(part_data_DBs.get(i), dataSource);
		}
		
/*		dataSourceMap.put("mayun", createDataSource("com.mysql.jdbc.Driver",
				"jdbc:mysql://localhost:3306/drill_mysql_test", "root", "mayun"));*/
		
		logger.info("dataSourceMap: " + dataSourceMap);
		

	}
	
	public static DataSource getDataSourceCache(String hostName) {

		return dataSourceMap.get(hostName);
		

	}
	public static void main(String[] args) throws Exception{
		
		createDataSourceCache();
		//deleteTestDataTable();
		//createTestDataTable();
		insertTestData();
	}
	public static void insertTestData() throws Exception{
			//createDataSourceCache(null);
		   //ResultSet resultSet;
		   Connection connection = null;
		   Statement statement = null;
		   Connection connectionForAllData = dataSourceMap.get(table).getConnection();;
		   Statement statementForAllData = connectionForAllData.createStatement(); 

			for (int i = 0; i < sub_students_count; i++) {
				
				 connection = dataSourceMap.get(part_data_DBs.get(i)).getConnection();
			     statement = connection.createStatement();
			     int fromId = (i+1)*10000000+1;
			     int toId = fromId +2999999;
				insertTestData(statement, table, fromId, toId);
				insertTestData(statementForAllData, table, fromId, toId);
			}
			
		    connectionForAllData.close();
		    statementForAllData.close();	
		    if(statement !=null){
		    	statement.close();	
		    }
		    if(connection !=null){
		    	connection.close();	
		    }
    	
			// insert to student
/*			connection = dataSourceMap.get(all_data_table).getConnection();
		    statement = connection.createStatement();
			insertTestData(statement, all_data_table, 10000001, 90000000);*/

/*		// delete data before insert
		// insert to student
		statement.execute("delete from " + student);
		insertTestata(statement, student, 1000001, 2000000);
		// delete data before insert
		// insert to student_new
		statement.execute("delete from " + student_new);
		insertTestata(statement, student_new, 2000001, 3000000);
		// delete data before insert
		// insert to student_all
		statement.execute("delete from " + student_all);
		insertTestata(statement, student_all, 1000001, 3000000);

		// insert to student
		insertTestData(statement, student, 10000001, 20000000);

		// insert to student_new
		insertTestData(statement, student_new, 20000001, 30000000);

		// insert to student_all
		insertTestData(statement, student_all, 10000001, 30000000);
		
		*/

		  
/*		// insert to student_new
		insertTestData(statement, student2, 20000001, 30000000);

		// insert to student_all
		insertTestData(statement, student_all, 10000001, 30000000);*/


		
	}


	private static void insertTestData(Statement statement, String table, int keyFrom, int keyTo)
			throws SQLException {
		
		String initSql = "insert into "+ table + "(id, name, sex, code, score) values";
		StringBuilder insertSqlSb = new StringBuilder(initSql);
		
	     // int batchCount=100;
		for(int i = keyFrom; i <= keyTo;i++){
    		  
	    	  	insertSqlSb.append("(");
				insertSqlSb.append(i + ", ");
				insertSqlSb.append("'Tom" + (i%100) + "', ");
				insertSqlSb.append("0, ");
				insertSqlSb.append(i%100 + ", ");
				insertSqlSb.append(i*100);
				insertSqlSb.append(")");
				
				if(i%batch_insert_count==0){
					statement.execute(insertSqlSb.toString());
					insertSqlSb = new StringBuilder(initSql);
				} else {
					
					insertSqlSb.append(",");
				}
		  
	      }

	}
	
	
	private static void createTestDataTable() throws SQLException {

		// ResultSet resultSet;
		Connection connection;
		Statement statement;

		connection = dataSourceMap.get(table).getConnection();
		statement = connection.createStatement();
		statement.execute("create table "+ table + "(id bigint not null primary key , name char(20) not null, sex int(4) not null default '0', code int, score bigint)");

		for (int i = 0; i < sub_students_count; i++) {
			
			 connection = dataSourceMap.get(part_data_DBs.get(i)).getConnection();
		     statement = connection.createStatement();

		     statement.execute("create table "+ table + "(id bigint not null primary key , name char(20) not null, sex int(4) not null default '0', code int, score bigint)");

		}
	    statement.close();
	    connection.close();
	}
	
	
	private static void deleteTestDataTable() throws SQLException {

		createDataSourceCache();
		// ResultSet resultSet;
		Connection connection;
		Statement statement;

		

		
		// delete data before insert
		// insert to student
		connection = dataSourceMap.get(table).getConnection();
		statement = connection.createStatement();
		statement.execute("delete from " + table);
		
		for (int i = 0; i < sub_students_count; i++) {

			connection = dataSourceMap.get(part_data_DBs.get(i)).getConnection();
			statement = connection.createStatement();
			statement.execute("delete from " + table);
		}
/*
		// delete data before insert
		// insert to student_new
		statement.execute("delete from " + student_new);

		// delete data before insert
		// insert to student_all
		statement.execute("delete from " + student_all);*/

		
		
	    statement.close();
	    connection.close();
	}


}
