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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.drill.common.exceptions.ExecutionSetupException;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.exec.physical.EndpointAffinity;
import org.apache.drill.exec.physical.base.AbstractGroupScan;
import org.apache.drill.exec.physical.base.GroupScan;
import org.apache.drill.exec.physical.base.PhysicalOperator;
import org.apache.drill.exec.physical.base.ScanStats;
import org.apache.drill.exec.physical.base.ScanStats.GroupScanProperty;
import org.apache.drill.exec.proto.CoordinationProtos.DrillbitEndpoint;
import org.apache.drill.exec.store.StoragePluginRegistry;
import org.apache.drill.exec.store.http.util.DBUtil;
import org.apache.drill.exec.store.schedule.CompleteWork;
import org.apache.drill.exec.store.schedule.EndpointByteMap;
import org.apache.drill.exec.store.schedule.EndpointByteMapImpl;

import com.beust.jcommander.internal.Lists;
import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;

@JsonTypeName("http-scan")
public class HttpGroupScan extends AbstractGroupScan implements HttpConstants {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HttpGroupScan.class);

  //private static final Comparator<List<HttpSubScanSpec>> LIST_SIZE_COMPARATOR = new Comparator<List<HttpSubScanSpec>>() {
/*    @Override
    public int compare(List<HttpSubScanSpec> list1, List<HttpSubScanSpec> list2) {
      return list1.size() - list2.size();
    }
  };*/

  //private static final Comparator<List<HttpSubScanSpec>> LIST_SIZE_COMPARATOR_REV = Collections.reverseOrder(LIST_SIZE_COMPARATOR);

  private Stopwatch watch = Stopwatch.createUnstarted();
  private HttpStoragePluginConfig storagePluginConfig;

  private List<SchemaPath> columns;

  private HttpScanSpec httpScanSpec;

  private HttpStoragePlugin storagePlugin;
  private List<HttpWork> httpWorks = Lists.newArrayList();

 // private Stopwatch watch = Stopwatch.createUnstarted();

  private Map<Integer, List<HttpSubScanSpec>> endpointFragmentMapping;

  //private NavigableMap<HttpTestDto, ServerName> regionsToScan;
  //private NavigableMap<HRegionInfo, ServerName> regionsToScanTest;

 // private HTableDescriptor hTableDesc;

  private boolean filterPushedDown = false;
  private boolean groupByPushedDown = false;
  private boolean orderByPushedDown = false;
  private boolean orderByAggFunc = false;
  private boolean limitPushedDown = false;

  
  //private final static int INIT_TASK_NUM = 8;

  //private TableStatsCalculator statsCalculator;

  //private long scanSizeInBytes = 0;

  @JsonCreator
  public HttpGroupScan(@JsonProperty("userName") String userName,
                        @JsonProperty("httpScanSpec") HttpScanSpec httpScanSpec,
                        @JsonProperty("storage") HttpStoragePluginConfig storagePluginConfig,
                        @JsonProperty("columns") List<SchemaPath> columns,
                        @JacksonInject StoragePluginRegistry pluginRegistry) throws IOException, ExecutionSetupException {
    this (userName, (HttpStoragePlugin) pluginRegistry.getPlugin(storagePluginConfig), httpScanSpec, columns);
  }

  public HttpGroupScan(String userName, HttpStoragePlugin storagePlugin, HttpScanSpec scanSpec,
      List<SchemaPath> columns) {
    super(userName);
    this.storagePlugin = storagePlugin;
    this.storagePluginConfig = storagePlugin.getConfig();
    this.httpScanSpec = scanSpec;
    this.columns = columns == null ? ALL_COLUMNS : columns;
    init();
  }

  /**
   * Private constructor, used for cloning.
   * @param that The HttpGroupScan to clone
   */
  private HttpGroupScan(HttpGroupScan that) {
    super(that);
    this.columns = (that.columns == null || that.columns.size() == 0) ? ALL_COLUMNS : that.columns;
    this.httpScanSpec = that.httpScanSpec;
    this.endpointFragmentMapping = that.endpointFragmentMapping;
    //this.regionsToScan = that.regionsToScan;
    this.storagePlugin = that.storagePlugin;
    this.storagePluginConfig = that.storagePluginConfig;
    //this.hTableDesc = that.hTableDesc;
    this.filterPushedDown = that.filterPushedDown;
    this.groupByPushedDown = that.groupByPushedDown;
    this.orderByPushedDown = that.orderByPushedDown;
    this.limitPushedDown = that.limitPushedDown;
    this.orderByAggFunc = that.orderByAggFunc;  
	this.httpWorks = that.httpWorks;
    //this.statsCalculator = that.statsCalculator;
    //this.scanSizeInBytes = that.scanSizeInBytes;
  }

  @Override
  public GroupScan clone(List<SchemaPath> columns) {
    HttpGroupScan newScan = new HttpGroupScan(this);
		newScan.columns = (columns == null || columns.size() == 0) ? ALL_COLUMNS : columns;;
    //newScan.verifyColumns();
    return newScan;
  }

  private void init() {
    logger.debug("Getting region locations");
    
    //Collection<DrillbitEndpoint> endpoints = storagePlugin.getContext().getBits();
    List<DrillbitEndpoint> drillbits = Lists.newArrayList(storagePlugin.getContext().getBits());
    logger.info("drillbits: " + drillbits.toString());
    
    Map<String,DrillbitEndpoint> endpointMap = Maps.newHashMap();
    for (DrillbitEndpoint endpoint : drillbits) {
      endpointMap.put(endpoint.getAddress(), endpoint);
    }
    try {
      
    	//TODO init TASK
      for (int i= 0; i<DBUtil.getPart_data_DBs().size();i++) {
        HttpWork work = new HttpWork("key"+i*100, "key"+i*100+99, "mayun", DBUtil.getPart_data_DBs().get(i));
        
        int bitIndex = i % drillbits.size();
		work.getByteMap().add(drillbits.get(bitIndex), 1000);
        httpWorks.add(work);
      }	
		
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    /*
    TableName tableName = TableName.valueOf(httpScanSpec.getTableName());
    Connection conn = storagePlugin.getConnection();

    try (Admin admin = conn.getAdmin();
         RegionLocator locator = conn.getRegionLocator(tableName)) {
      this.hTableDesc = admin.getTableDescriptor(tableName);
      List<HRegionLocation> regionLocations = locator.getAllRegionLocations();
      //statsCalculator = new TableStatsCalculator(conn, httpScanSpec, storagePlugin.getContext().getConfig(), storagePluginConfig);
      
      //TODO
      logger.info("regionLocations size: " + regionLocations.size());
      regionLocations.add(regionLocations.get(0));
      regionLocations.add(regionLocations.get(0));
      regionLocations.add(regionLocations.get(0));
      logger.info("regionLocations size: " + regionLocations.size());
      
      boolean foundStartRegion = false;
      regionsToScan = new TreeMap<HttpTestDto, ServerName>();
      for ( int i =0 ; i < regionLocations.size(); i++) {
    	  
    	  HRegionLocation regionLocation = regionLocations.get(i);
        HRegionInfo regionInfo = regionLocation.getRegionInfo();
        if (!foundStartRegion && httpScanSpec.getStartRow() != null && httpScanSpec.getStartRow().length != 0 && !regionInfo.containsRow(httpScanSpec.getStartRow())) {
          continue;
        }
        foundStartRegion = true;
        
        HttpTestDto testDto =  new HttpTestDto();
        testDto.setRegionInfo(regionInfo);  
        testDto.setRegionId(i);
        
        regionsToScan.put(testDto, regionLocation.getServerName());
        //scanSizeInBytes += statsCalculator.getRegionSizeInBytes(regionInfo.getRegionName());
        if (httpScanSpec.getStopRow() != null && httpScanSpec.getStopRow().length != 0 && regionInfo.containsRow(httpScanSpec.getStopRow())) {
          break;
        }
      }
    } catch (IOException e) {
      throw new DrillRuntimeException("Error getting region info for table: " + httpScanSpec.getTableName(), e);
    }
    
    logger.info("regionsToScan size: " + regionsToScan.size());
    verifyColumns();*/
  }

/*  private void verifyColumns() {
    if (AbstractRecordReader.isStarQuery(columns)) {
      return;
    }
    for (SchemaPath column : columns) {
      if (!(column.equals(ROW_KEY_PATH) || hTableDesc.hasFamily(HttpUtils.getBytes(column.getRootSegment().getPath())))) {
        DrillRuntimeException.format("The column family '%s' does not exist in Http table: %s .",
            column.getRootSegment().getPath(), hTableDesc.getNameAsString());
      }
    }
  }*/

  @Override
  public List<EndpointAffinity> getOperatorAffinity() {
    watch.reset();
    watch.start();
    Map<String, DrillbitEndpoint> endpointMap = new HashMap<String, DrillbitEndpoint>();
    for (DrillbitEndpoint ep : storagePlugin.getContext().getBits()) {
      endpointMap.put(ep.getAddress(), ep);
    }

    Map<DrillbitEndpoint, EndpointAffinity> affinityMap = new HashMap<DrillbitEndpoint, EndpointAffinity>();
    for (HttpWork httpWork : httpWorks) {
      DrillbitEndpoint ep = endpointMap.get(httpWork.getHostName());
      if (ep != null) {
        EndpointAffinity affinity = affinityMap.get(ep);
        if (affinity == null) {
          affinityMap.put(ep, new EndpointAffinity(ep, 1));
        } else {
          affinity.addAffinity(1);
        }
      }
    }
    logger.debug("Took {} µs to get operator affinity", watch.elapsed(TimeUnit.NANOSECONDS)/1000);
    return Lists.newArrayList(affinityMap.values());
  }

  /**
   *
   * @param incomingEndpoints
   */
  @Override
  public void applyAssignments(List<DrillbitEndpoint> incomingEndpoints) {
    watch.reset();
    watch.start();

	    final int numSlots = incomingEndpoints.size();
		  logger.info("incomingEndpoints size: " + numSlots);
		  logger.info("incomingEndpoints: " + incomingEndpoints.toString());
		  
		Preconditions.checkArgument(numSlots <= httpWorks.size(), String
				.format("Incoming endpoints %d is greater than number of scan regions %d", numSlots, httpWorks.size()));

		/*
		 * Minimum/Maximum number of assignment per slot
		 */
		final int minPerEndpointSlot = (int) Math.floor((double) httpWorks.size() / numSlots);
		final int maxPerEndpointSlot = (int) Math.ceil((double) httpWorks.size() / numSlots);

		endpointFragmentMapping = Maps.newHashMapWithExpectedSize(numSlots);

		for (int i = 0; i < httpWorks.size(); i++) {

			int slotIndex = i % numSlots;
			HttpWork work = httpWorks.get(i);

			List<HttpSubScanSpec> endpointSlotScanList = endpointFragmentMapping.get(slotIndex);
			if (endpointSlotScanList == null) {
				endpointSlotScanList = new ArrayList<HttpSubScanSpec>(maxPerEndpointSlot);
			}
			
			Boolean executeLimitFlg = calcExecuteLimitFlg();	
			//TODO
			HttpSubScanSpec tmpScanSpec = new HttpSubScanSpec(work.getDbName(),
					httpScanSpec.getTableName(),
					storagePluginConfig.getConnection(), 
					storagePluginConfig.getResultKey(),
					work.getPartitionKeyStart(), 
					work.getPartitionKeyEnd(), 
					httpScanSpec.getFilterArgs(),
					httpScanSpec.getGroupByCols(), 
					httpScanSpec.getOrderByCols(),
					executeLimitFlg?httpScanSpec.getLimitValue():null,
					this.columns);		
			
			endpointSlotScanList.add(tmpScanSpec);
			endpointFragmentMapping.put(slotIndex, endpointSlotScanList);
			logger.info("endpointSlotScanList: " + endpointSlotScanList);
		}

		logger.info("applyAssignments endpointFragmentMapping: " + endpointFragmentMapping);

	
    logger.debug("Built assignment map in {} µs.\nEndpoints: {}.\nAssignment Map: {}",
        watch.elapsed(TimeUnit.NANOSECONDS)/1000, incomingEndpoints, endpointFragmentMapping.toString());
  }

	private Boolean calcExecuteLimitFlg() {
		Boolean executeLimitFlg = true;
		// if it is only single major fragment, LimitPrel/SortPrel node can be
		// push down to scan,limitPushedDown/orderbyPushedDown can be true

		//has groupby 
		if (this.groupByPushedDown) {
			
			List<String> groupByCols = httpScanSpec.getGroupByCols();
			List<String> orderByCols = httpScanSpec.getOriginalOrderByCols();
			
			if (orderByCols == null || orderByCols.size() < groupByCols.size()) {
				
				executeLimitFlg = false;
			} else {
				
				// if order by cols don't start with group by cols and with the same cols order,  limit operation can't be push down
				for (int i = 0; i < groupByCols.size(); i++) {

					if (!groupByCols.get(i).equals(orderByCols.get(i))) {
						executeLimitFlg = false;
						break;
					}
				}
			}
			
/*			// if order by col is not AGG func
			// TODO
			if (!this.orderByAggFunc) {

			}*/
			
		}


		return executeLimitFlg;
	}

/*  private HttpSubScanSpec regionInfoToSubScanSpec(HttpTestDto rit) {
	  HRegionInfo ri = rit.getRegionInfo();
    HttpScanSpec spec = httpScanSpec;
    
    
    return new HttpSubScanSpec(httpScanSpec.getTableName(),storagePluginConfig.getResultKey(), storagePluginConfig.getConnection(), httpScanSpec.getStartRow().toString(),httpScanSpec.getStopRow().toString());
    		
    return new HttpSubScanSpec()
        .setTableName(spec.getTableName())
        .setRegionServer(regionsToScan.get(rit).getHostname())
        .setStartRow((!isNullOrEmpty(spec.getStartRow()) && ri.containsRow(spec.getStartRow())) ? spec.getStartRow() : ri.getStartKey())
        .setStopRow((!isNullOrEmpty(spec.getStopRow()) && ri.containsRow(spec.getStopRow())) ? spec.getStopRow() : ri.getEndKey())
        .setSerializedFilter(spec.getSerializedFilter());
  }*/

/*  private boolean isNullOrEmpty(byte[] key) {
    return key == null || key.length == 0;
  }*/

  @Override
  public HttpSubScan getSpecificScan(int minorFragmentId) {
    assert minorFragmentId < endpointFragmentMapping.size() : String.format(
        "Mappings length [%d] should be greater than minor fragment id [%d] but it isn't.", endpointFragmentMapping.size(),
        minorFragmentId);
    return new HttpSubScan(storagePlugin, storagePluginConfig,
        endpointFragmentMapping.get(minorFragmentId), columns);
  }

  @Override
  public int getMaxParallelizationWidth() {
		logger.debug("getMaxParallelizationWidth: " + httpWorks.size());
		return httpWorks.size();
  }

  @Override
  public ScanStats getScanStats() {
	  
/*	  logger.debug("scanSizeInBytes: " + scanSizeInBytes);
	  logger.debug("statsCalculator.getAvgRowSizeInBytes(): " + statsCalculator.getAvgRowSizeInBytes());
	  logger.debug("statsCalculator.getColsPerRow(): " + statsCalculator.getColsPerRow());
			  
    long rowCount = (long) ((scanSizeInBytes / statsCalculator.getAvgRowSizeInBytes()) * (httpScanSpec.getFilter() != null ? 0.5 : 1));
    // the following calculation is not precise since 'columns' could specify CFs while getColsPerRow() returns the number of qualifier.
    float diskCost = scanSizeInBytes * ((columns == null || columns.isEmpty()) ? 1 : columns.size()/statsCalculator.getColsPerRow());
    
	  logger.debug("rowCount: " + rowCount);
	  logger.debug("diskCost: " + diskCost);*/
	  

/*	  long totalScanRowCountOnEachNode=10000000;
	  long avgRowSizeInBytes = 10*1024;//10kb
	  long scanSizeInBytes = totalScanRowCountOnEachNode * 10*1024;
	  long colsPerRow = 100;*/
	  
	  long totalRowCountOnEachNode=10000000;
	  long avgRowSizeInBytes = 1024;//1kb
	  long colsPerRow = 100;
	  
	  if(storagePluginConfig.getScanConfig() != null){
	   totalRowCountOnEachNode=storagePluginConfig.getScanConfig().get("totalRowCountOnEachNode");
	   avgRowSizeInBytes = storagePluginConfig.getScanConfig().get("avgRowSizeInBytes");
	   colsPerRow = storagePluginConfig.getScanConfig().get("colsPerRow");;
	  }
	  
	    long rowCount = (long) (totalRowCountOnEachNode * (httpScanSpec.getFilterArgs() != null && httpScanSpec.getFilterArgs().size()>0? 0.5 : 1)* (httpScanSpec.getGroupByCols()!= null ? 0.5 : 1));
	    // the following calculation is not precise since 'columns' could specify CFs while getColsPerRow() returns the number of qualifier.
	    float diskCost = rowCount * avgRowSizeInBytes * ((columns == null || columns.isEmpty()) ? 1 : columns.size()/colsPerRow);
	    return new ScanStats(GroupScanProperty.NO_EXACT_ROW_COUNT, rowCount, 1, diskCost);			  
			  
			  
	//return new ScanStats(GroupScanProperty.NO_EXACT_ROW_COUNT, 100000000, 1, 318767104);
	//return new ScanStats(GroupScanProperty.EXACT_ROW_COUNT, 1, 1, (float) 10);  
   //return new ScanStats(GroupScanProperty.EXACT_ROW_COUNT, 4, 1, 318767104);
  }

  @Override
  @JsonIgnore
  public PhysicalOperator getNewWithChildren(List<PhysicalOperator> children) {
    Preconditions.checkArgument(children.isEmpty());
    return new HttpGroupScan(this);
  }

  @JsonIgnore
  public HttpStoragePlugin getStoragePlugin() {
    return storagePlugin;
  }

/*  @JsonIgnore
  public Configuration getHttpConf() {
    return getStorageConfig().getHttpConf();
  }*/

/*  @JsonIgnore
  public String getTableName() {
    return getHttpScanSpec().getTableName();
  }*/

  @Override
  public String getDigest() {
    return toString();
  }

  @Override
  public String toString() {
    return "HttpGroupScan [HttpScanSpec="
        + httpScanSpec + ", columns="
        + columns + "]";
  }

  @JsonProperty("storage")
  public HttpStoragePluginConfig getStorageConfig() {
    return this.storagePluginConfig;
  }

  @JsonProperty
  public List<SchemaPath> getColumns() {
    return columns;
  }
  
  @JsonProperty
  public  void setColumns(List<SchemaPath> columns) {
    this.columns = columns;
  }

  @JsonProperty
  public HttpScanSpec getHttpScanSpec() {
    return httpScanSpec;
  }

  @Override
  @JsonIgnore
  public boolean canPushdownProjects(List<SchemaPath> columns) {
    return true;
  }

  @JsonIgnore
  public void setFilterPushedDown(boolean b) {
    this.filterPushedDown = b;
  }

  @JsonIgnore
  public boolean isFilterPushedDown() {
    return filterPushedDown;
  }

  public boolean isGroupByPushedDown() {
	return groupByPushedDown;
}

public void setGroupByPushedDown(boolean groupByPushedDown) {
	this.groupByPushedDown = groupByPushedDown;
}

public boolean isOrderByPushedDown() {
	return orderByPushedDown;
}

public void setOrderByPushedDown(boolean orderByPushedDown) {
	this.orderByPushedDown = orderByPushedDown;
}

public boolean isOrderByAggFunc() {
	return orderByAggFunc;
}

public void setOrderByAggFunc(boolean orderByAggFunc) {
	this.orderByAggFunc = orderByAggFunc;
}

public boolean isLimitPushedDown() {
	return limitPushedDown;
}

public void setLimitPushedDown(boolean limitPushedDown) {
	this.limitPushedDown = limitPushedDown;
}

/**
   * Empty constructor, do not use, only for testing.
   */
  @VisibleForTesting
  public HttpGroupScan() {
    super((String)null);
  }

  /**
   * Do not use, only for testing.
   */
  @VisibleForTesting
  public void setHttpScanSpec(HttpScanSpec httpScanSpec) {
    this.httpScanSpec = httpScanSpec;
  }

  /**
   * Do not use, only for testing.
   */
/*  @JsonIgnore
  @VisibleForTesting
  public void setRegionsToScan(NavigableMap<HRegionInfo, ServerName> regionsToScan) {
    this.regionsToScanTest = regionsToScan;
  }*/

  private static class HttpWork implements CompleteWork {

	    private EndpointByteMapImpl byteMap = new EndpointByteMapImpl();
	    private String partitionKeyStart;
	    private String partitionKeyEnd;
	    private String hostName;
	    private String dbName;

	    public HttpWork(String partitionKeyStart, String partitionKeyEnd, String hostName, String dbName) {
	      this.partitionKeyStart = partitionKeyStart;
	      this.partitionKeyEnd = partitionKeyEnd;
	      this.hostName = hostName;
	      this.dbName = dbName;
	    }

	    public String getPartitionKeyStart() {
	      return partitionKeyStart;
	    }

	    public String getPartitionKeyEnd() {
	      return partitionKeyEnd;
	    }

	    public String getHostName() {
			return hostName;
		}

		public String getDbName() {
			return dbName;
		}

		@Override
	    public long getTotalBytes() {
	      return 1000;
	    }

	    @Override
	    public EndpointByteMap getByteMap() {
	      return byteMap;
	    }

	    @Override
	    public int compareTo(CompleteWork o) {
	      return 0;
	    }
	  }
}
