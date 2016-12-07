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

//G:groupby L:limit O;order by S: select F: filter
public class HttpQueriesTest extends HttpTestBase {

  @Test
  public void test() throws Exception {
	  //String testSql = "select * from http.`/testquery`";
    //String testSql = "select * from http.`/testquery` where firstName = 'Jason' and lastName = 'Hunter'";
    //String testSql = "select * from http.`/testquery` group firstName = 'Jason'";  
    //String testSql = "select firstName,  from  http.`test` where firstName = 'Jason' group by firstName";
	 //String testSql ="select firstName , sum(TO_NUMBER(code, '######')) , avg(TO_NUMBER(code, '######')) , avg(TO_NUMBER(score, '######'))  from  http.`test` where firstName = 'Jason' group by firstName";
	  
	  //String testSql ="select firstName, avg(TO_NUMBER(code, '######')) , avg(TO_NUMBER(score, '######'))  from  http.`test` where firstName = 'Jason' or firstName = 'Jason1' group by firstName";
	  
	  //OK

	  String testSql = "select name, score , sum(code)  as sum_code  from http.`student` group by name,score  order by sum_code desc  limit 3";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  @Test
  public void test_1sum_2G_L_1F() throws Exception {
	
	  String mysql ="select name, code, sum(code)  from student where name = 'Tom3' group by name, code limit 2";
	String testSql = "select  code , sum(code)  from http.`student` where name = 'Tom3' group by name, code limit 2";
	System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  @Test
  public void test_2G_L_1F() throws Exception {
	
	  String mysql ="select name, code from student where name = 'Tom3' group by name, code limit 2";
	String testSql = "select name,  code  from http.`student` where name = 'Tom3' group by name, code limit 2";
	System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  @Test
  public void test_2G_2O_L() throws Exception {

	  //OK
	  String mysql ="select name, avg(score) as avg_score from student group by name order by name, avg_score limit 2";
	  String testSql = "select name,  code,  avg(score) from http.`student` group by name, code  order by name, code limit 2";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  @Test
  public void test_1G_1O1avgO_L() throws Exception {

	  //OK
	  String mysql ="select name, avg(score) as avg_score from student group by name order by name, avg_score limit 2";
	  String testSql = "select name, avg(score) as avg_score from http.`student` group by name order by name, avg_score limit 2";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  @Test
  public void test_1G_1avgO_L() throws Exception {

	  //OK
	  String mysql ="select name, avg(score) as avg_score from student group by name order by avg_score limit 3";
	  String testSql = "select name, avg(score) as avg_score from http.`student` group by name order by avg_score limit 3";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  @Test
  //TODO
  public void test_1G_O1sum_L() throws Exception {

	  //OK
	  
	  //TODO
	  String mysql ="select name, sum(score) as sum_score from student group by name order by sum_score limit 3";
	  String testSql = "select name, sum(score) as sum_score from http.`student` group by name order by sum_score limit 3";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  
  
  @Test
  public void test_As_In_Select() throws Exception {

	  //OK
	  String mysql ="select name,  code, count(name) as count_name, count(name) as count_name1, sum(code) as sum_code, count(code) as count_code, avg(score) as avg_score from student where name = 'Tom0' group by name, code";
	  String testSql ="select name,  code, count(name) as count_name, count(name) as count_name1, sum(code) as sum_code, count(code) as count_code, avg(score) as avg_score from http.`student` where name = 'Tom0' group by name, code";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  
  @Test
  public void test_As_In_O() throws Exception {

	  //OK

	 String mysql ="select name as name_as,  code as code_as,  avg(score) as score_as from student group by name_as, code_as  order by name_as, code_as limit 100";
	 //GROUP  alias col name is not supported 
	 //String testSql = "select name as name_as,  code as code_as,  avg(score) as score_as from http.`student` group by name_as, code_as  order by name_as, code_as limit 100";
	  String testSql = "select name as name_as,  code as code_as,  avg(score) as score_as from http.`student` group by name, code  order by name_as, code_as limit 100";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  @Test
  public void test_1sumToNumber_1F_2G_1L() throws Exception {

	  //OK
	  String mysql ="select name,  code,  sum(score)  from student where name = 'Tom0' group by name, code limit 2";
	 // String testSql ="select name,  code,  avg(score) from http.`student` where name = 'Tom0' group by name, code limit 2";
	  String testSql = "select name,  code,  sum(TO_NUMBER(score, '######'))  from http.`student` where name = 'Tom0' group by name, code limit 2";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  
  @Test
  public void test_1avgToNumber_1F_2G_1L() throws Exception {
	  //String testSql = "select * from http.`/testquery`";
    //String testSql = "select * from http.`/testquery` where firstName = 'Jason' and lastName = 'Hunter'";
    //String testSql = "select * from http.`/testquery` group firstName = 'Jason'";  
    //String testSql = "select firstName,  from  http.`test` where firstName = 'Jason' group by firstName";
	 //String testSql ="select firstName , sum(TO_NUMBER(code, '######')) , avg(TO_NUMBER(code, '######')) , avg(TO_NUMBER(score, '######'))  from  http.`test` where firstName = 'Jason' group by firstName";
	  
	  //String testSql ="select firstName, avg(TO_NUMBER(code, '######')) , avg(TO_NUMBER(score, '######'))  from  http.`test` where firstName = 'Jason' or firstName = 'Jason1' group by firstName";
	  
	  //OK
	  String mysql ="select name,  code,  avg(code)  from student where name = 'Tom0' group by name, code limit 2";
	 // String testSql ="select name,  code,  avg(score) from http.`student` where name = 'Tom0' group by name, code limit 2";
	  String testSql = "select name,  code,  avg(TO_NUMBER(code, '######'))  from http.`student` where name = 'Tom0' group by name, code limit 2";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  
  @Test
  public void test_1avg_1F_2G_1L() throws Exception {
	  //String testSql = "select * from http.`/testquery`";
    //String testSql = "select * from http.`/testquery` where firstName = 'Jason' and lastName = 'Hunter'";
    //String testSql = "select * from http.`/testquery` group firstName = 'Jason'";  
    //String testSql = "select firstName,  from  http.`test` where firstName = 'Jason' group by firstName";
	 //String testSql ="select firstName , sum(TO_NUMBER(code, '######')) , avg(TO_NUMBER(code, '######')) , avg(TO_NUMBER(score, '######'))  from  http.`test` where firstName = 'Jason' group by firstName";
	  
	  //String testSql ="select firstName, avg(TO_NUMBER(code, '######')) , avg(TO_NUMBER(score, '######'))  from  http.`test` where firstName = 'Jason' or firstName = 'Jason1' group by firstName";
	  
	  //OK
	  String mysql ="select name,  code,  avg(score) from student where name = 'Tom0' group by name, code limit 2";
	 // String testSql ="select name,  code,  avg(score) from http.`student` where name = 'Tom0' group by name, code limit 2";
	  String testSql = "select name,  code,  avg(score) from http.`student` where name = 'Tom0' group by name, code limit 2";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  
  
  @Test
  public void test_All() throws Exception {

	  String mysql ="select * from  student limit 1000";
	  String testSql ="select * from  http.`student` limit 1000";
	  System.out.println(testSql);
	  runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  
  @Test
  public void test_Sum_All() throws Exception {

	  String mysql ="select sum(code) from  student";
	  String testSql ="select sum(code) from  http.`student`";
	  System.out.println(testSql);
	  runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  @Test
  public void test_Count_All() throws Exception {

	  String mysql ="select count(*) from  student";
	  String testSql ="select count(*) from  http.`student`";
	  System.out.println(testSql);
	  runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  
  @Test
  public void test_All_1F() throws Exception {

	  String mysql ="select * from  student where name = 'Tom0'";
	  String testSql ="select * from  http.`student` where name = 'Tom0'";
	  System.out.println(testSql);
	  runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  @Test
  //TODO
  public void test_All_2F() throws Exception {

	  String mysql ="select * from  student where sex = 0 and score=1299930200";
	  String testSql ="select * from  http.`student` where sex = 0 and score=1299930200";
	  System.out.println(testSql);
	  runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  @Test
  public void test_2count_1sum_1Avg_1F_2G() throws Exception {

	  //OK
	  String mysql ="select name,  code, count(name), sum(code), count(code), avg(score) from student where name = 'Tom0' group by name, code";
	  String testSql ="select name,  code, count(name), sum(code), count(code), avg(score) from http.`student` where name = 'Tom0' group by name, code";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  
  @Test
  public void test_1Avg_1F_2G() throws Exception {

	  //OK
	  String mysql ="select name,  code,  avg(score) from student where name = 'Tom0' group by name, code";
	  String testSql ="select name,  code,  avg(score) from http.`student` where name = 'Tom0' group by name, code";
	  System.out.println(testSql);
    runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }
  
  
  
  public void test_1Sum_1Avg_1AvgToNumber_1F_1G() throws Exception {

	  String mysql ="select name, sum(code), avg(code), avg(score) from student where name = 'Tom0' group by name";
	  String testSql ="select name, sum(code), avg(TO_NUMBER(code, '######')), avg(score) from http.`student` where name = 'Tom0' group by name";
	  System.out.println(testSql);
	  runHttpSQLVerifyCount(testSql,1);
    //List<QueryDataBatch> list = testRunAndReturn(QueryType.SQL, queryString);
    //System.out.println(list);
    
  }

	
	@Test
	public void test_1Sum_2Avg_1F_1G() throws Exception {
		
		String mysql ="select name, sum(code), avg(code), avg(score) from student where name = 'Tom0' group by name";	
		String testSql ="select name, sum(code), avg(code), avg(score) from http.`student` where name = 'Tom0' group by name";
		System.out.println(testSql);
		runHttpSQLVerifyCount(testSql, 1);
	}
	


	@Test
	//TODO
	public void test_2AvgToNumber_1F_1G() throws Exception {

		String mysql ="select name, avg(code) , avg(score)  from  student where name = 'Tom0' group by name";	
		
		String testSql ="select name, avg(TO_NUMBER(code, '######')) , avg(TO_NUMBER(score, '######'))  from  http.`student` where name = 'Tom0' group by name";
		System.out.println(testSql);
		runHttpSQLVerifyCount(testSql, 1);
	}
	
	@Test
	public void test_2Avg_1F_1G() throws Exception {
		String mysql ="select name, avg(code), avg(score) from student where name = 'Tom0' group by name";	
		String testSql ="select name, avg(code), avg(score) from http.`student` where name = 'Tom0' group by name";
		System.out.println(testSql);
		runHttpSQLVerifyCount(testSql, 1);
	}
}
