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

import org.junit.Test;

public class TestHttpQueries extends HttpTestBase {

  @Test
  public void test() throws Exception {
	  //String sql = "select * from http.`/testquery`";
    //String sql = "select * from http.`/testquery` where firstName = 'Jason' and lastName = 'Hunter'";
    //String sql = "select * from http.`/testquery` group firstName = 'Jason'";  
    //String sql = "select firstName,  from  http.`test` where firstName = 'Jason' group by firstName";
	  String sql ="select firstName, avg(TO_NUMBER(code, '######'))  from  http.`test` where firstName = 'Jason' group by firstName";
    System.out.println(sql);
    runHttpSQLVerifyCount(sql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
}
