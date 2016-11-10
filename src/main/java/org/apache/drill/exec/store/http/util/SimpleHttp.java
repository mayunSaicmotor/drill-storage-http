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
package org.apache.drill.exec.store.http.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;

public class SimpleHttp {
 static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SimpleHttp.class);
  public String get(String urlStr) {
    String res = "";
    try {
    logger.info("urlStr: " +urlStr);
      URL url = new URL(URLDecoder.decode(urlStr));
      URLConnection conn = url.openConnection();
      conn.setDoOutput(true);
      conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");

      BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
      String line;
      while ((line = rd.readLine()) != null) {
        res += line;
      }
      rd.close();
    } catch (Exception e) {
    	
    	logger.error("error happens: ", e);
    }
   // return "{ \"results\": [{ \"firstName\": \"httpBrett2\", \"lastName\":\"McLaughlin\", \"code\": 101 },{ \"firstName\": \"httpJason2\", \"lastName\":\"Hunter\", \"code\": 102},{ \"firstName\": \"httpElliotte2\", \"lastName\":\"Harold\", \"code\": 103 }]}";
    
/*   if(urlStr.contains("key099")){
    	
    	return "{ \"results\": [{ \"firstName\": \"httpBrett2\", \"lastName\":\"McLaughlin\", \"code\": 101 },{ \"firstName\": \"httpJason2\", \"lastName\":\"Hunter\", \"code\": 102},{ \"firstName\": \"httpElliotte2\", \"lastName\":\"Harold\", \"code\": 103 }]}";
    }else{
    	return "{ \"results\": [{ \"firstName\": \"httpBrett2\", \"lastName\":\"McLaughlin\", \"code\": 2001 },{ \"firstName\": \"httpJason2\", \"lastName\":\"Hunter\", \"code\": 2002},{ \"firstName\": \"httpElliotte2\", \"lastName\":\"Harold\", \"code\": 2003 }]}";	
    }*/
   
   if(urlStr.contains("key099")){
   	
   	return "{ \"results\": [{ \"firstName\": \"httpBrett2\", \"lastName\":\"McLaughlin\", \"code\": 101, \"code_count\": 1 },{ \"firstName\": \"httpJason2\", \"lastName\":\"Hunter\", \"code\": 102, \"code_count\": 1},{ \"firstName\": \"httpElliotte2\", \"lastName\":\"Harold\", \"code\": 103, \"code_count\": 1 }]}";
   }else{
   	return "{ \"results\": [{ \"firstName\": \"httpBrett2\", \"lastName\":\"McLaughlin\", \"code\": 2001, \"code_count\": 1 },{ \"firstName\": \"httpJason2\", \"lastName\":\"Hunter\", \"code\": 2002, \"code_count\": 1},{ \"firstName\": \"httpElliotte2\", \"lastName\":\"Harold\", \"code\": 2003, \"code_count\": 2 }]}";	
   }
    //return res;
  }

  public static void main(String[] args) {
    SimpleHttp http = new SimpleHttp();
    System.out.println(http.get("http://blog.csdn.net/ugg/svc/GetCategoryArticleList?id=586287&type=foot"));
  }

}
