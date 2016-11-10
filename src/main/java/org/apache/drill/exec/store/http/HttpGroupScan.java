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
import java.util.List;
import java.util.Map;

import org.apache.drill.common.exceptions.ExecutionSetupException;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.exec.physical.base.AbstractGroupScan;
import org.apache.drill.exec.physical.base.GroupScan;
import org.apache.drill.exec.physical.base.PhysicalOperator;
import org.apache.drill.exec.physical.base.ScanStats;
import org.apache.drill.exec.physical.base.ScanStats.GroupScanProperty;
import org.apache.drill.exec.proto.CoordinationProtos.DrillbitEndpoint;
import org.apache.drill.exec.store.StoragePluginRegistry;
import org.apache.drill.exec.store.http.HttpSubScan.HttpSubScanSpec;
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
  
  private final static int INIT_TASK_NUM = 2;

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
    this.columns = that.columns == null ? ALL_COLUMNS : that.columns;
    this.httpScanSpec = that.httpScanSpec;
    this.endpointFragmentMapping = that.endpointFragmentMapping;
    //this.regionsToScan = that.regionsToScan;
    this.storagePlugin = that.storagePlugin;
    this.storagePluginConfig = that.storagePluginConfig;
    //this.hTableDesc = that.hTableDesc;
    this.filterPushedDown = that.filterPushedDown;
    this.groupByPushedDown = that.groupByPushedDown;
    
	this.httpWorks = that.httpWorks;
    //this.statsCalculator = that.statsCalculator;
    //this.scanSizeInBytes = that.scanSizeInBytes;
  }

  @Override
  public GroupScan clone(List<SchemaPath> columns) {
    HttpGroupScan newScan = new HttpGroupScan(this);
    newScan.columns = columns == null ? ALL_COLUMNS : columns;;
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
      for (int i= 0; i<INIT_TASK_NUM ;i++) {
        HttpWork work = new HttpWork("key"+i*100, "key"+i*100+99);
        
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

/*  @Override
  public List<EndpointAffinity> getOperatorAffinity() {
   // watch.reset();
    //watch.start();
    Map<String, DrillbitEndpoint> endpointMap = new HashMap<String, DrillbitEndpoint>();
    for (DrillbitEndpoint ep : storagePlugin.getContext().getBits()) {
      endpointMap.put(ep.getAddress(), ep);
    }

    Map<DrillbitEndpoint, EndpointAffinity> affinityMap = new HashMap<DrillbitEndpoint, EndpointAffinity>();
    for (ServerName sn : regionsToScan.values()) {
      DrillbitEndpoint ep = endpointMap.get(sn.getHostname());
      if (ep != null) {
        EndpointAffinity affinity = affinityMap.get(ep);
        if (affinity == null) {
          affinityMap.put(ep, new EndpointAffinity(ep, 1));
        } else {
          affinity.addAffinity(1);
        }
      }
    }
   // logger.debug("Took {} µs to get operator affinity", watch.elapsed(TimeUnit.NANOSECONDS)/1000);
    return Lists.newArrayList(affinityMap.values());
  }*/

  /**
   *
   * @param incomingEndpoints
   */
  @Override
  public void applyAssignments(List<DrillbitEndpoint> incomingEndpoints) {
   // watch.reset();
   // watch.start();

	  logger.info("incomingEndpoints size: " + incomingEndpoints.size());
	  logger.info("incomingEndpoints: " + incomingEndpoints.toString());
	  endpointFragmentMapping = Maps.newHashMapWithExpectedSize(2);
	  
	  for( int i = 0; i< httpWorks.size(); i++){
		  
		  HttpWork work =httpWorks.get(i);
		  endpointFragmentMapping.put(i, new ArrayList<HttpSubScanSpec>(1));
		  List<HttpSubScanSpec> endpointSlotScanList = endpointFragmentMapping.get(i);
	      endpointSlotScanList.add(new HttpSubScanSpec(httpScanSpec.getURL(),storagePluginConfig.getConnection(), storagePluginConfig.getResultKey(), work.getPartitionKeyStart(), work.getPartitionKeyEnd()));

		  logger.info("endpointSlotScanList: " + endpointSlotScanList);
	  }
	  
	  logger.info("applyAssignments endpointFragmentMapping: " + endpointFragmentMapping);

	    
/*	  endpointFragmentMapping.put(0, new ArrayList<HttpSubScanSpec>(2));
	  List<HttpSubScanSpec> endpointSlotScanList = endpointFragmentMapping.get(0);
      endpointSlotScanList.add(regionInfoToSubScanSpec(regionsToScan.keySet().iterator().next()));
      endpointSlotScanList.add(regionInfoToSubScanSpec(regionsToScan.keySet().iterator().next()));
      
	  endpointFragmentMapping.put(1, new ArrayList<HttpSubScanSpec>(2));s
	  endpointSlotScanList = endpointFragmentMapping.get(1);
      endpointSlotScanList.add(regionInfoToSubScanSpec(regionsToScan.keySet().iterator().next()));
      endpointSlotScanList.add(regionInfoToSubScanSpec(regionsToScan.keySet().iterator().next()));*/
	  
	/*  
      
	  
	  final int numSlots = incomingEndpoints.size();
    Preconditions.checkArgument(numSlots <= regionsToScan.size(),
        String.format("Incoming endpoints %d is greater than number of scan regions %d", numSlots, regionsToScan.size()));

    
     * Minimum/Maximum number of assignment per slot
     
    final int minPerEndpointSlot = (int) Math.floor((double)regionsToScan.size() / numSlots);
    final int maxPerEndpointSlot = (int) Math.ceil((double)regionsToScan.size() / numSlots);

    
     * initialize (endpoint index => HttpSubScanSpec list) map
     
    endpointFragmentMapping = Maps.newHashMapWithExpectedSize(numSlots);

    
     * another map with endpoint (hostname => corresponding index list) in 'incomingEndpoints' list
     
    Map<String, Queue<Integer>> endpointHostIndexListMap = Maps.newHashMap();

    
     * Initialize these two maps
     
    for (int i = 0; i < numSlots; ++i) {
      endpointFragmentMapping.put(i, new ArrayList<HttpSubScanSpec>(maxPerEndpointSlot));
      String hostname = incomingEndpoints.get(i).getAddress();
      Queue<Integer> hostIndexQueue = endpointHostIndexListMap.get(hostname);
      if (hostIndexQueue == null) {
        hostIndexQueue = Lists.newLinkedList();
        endpointHostIndexListMap.put(hostname, hostIndexQueue);
      }
      hostIndexQueue.add(i);
    }

    Set<Entry<HttpTestDto, ServerName>> regionsToAssignSet = Sets.newHashSet(regionsToScan.entrySet());

    
     * First, we assign regions which are hosted on region servers running on drillbit endpoints
     
    for (Iterator<Entry<HttpTestDto, ServerName>> regionsIterator = regionsToAssignSet.iterator(); regionsIterator.hasNext(); nothing) {
      Entry<HttpTestDto, ServerName> regionEntry = regionsIterator.next();
      
       * Test if there is a drillbit endpoint which is also an Http RegionServer that hosts the current Http region
       
      Queue<Integer> endpointIndexlist = endpointHostIndexListMap.get(regionEntry.getValue().getHostname());
      if (endpointIndexlist != null) {
        Integer slotIndex = endpointIndexlist.poll();
        List<HttpSubScanSpec> endpointSlotScanList = endpointFragmentMapping.get(slotIndex);
        endpointSlotScanList.add(regionInfoToSubScanSpec(regionEntry.getKey()));
        // add to the tail of the slot list, to add more later in round robin fashion
        endpointIndexlist.offer(slotIndex);
        // this region has been assigned
        regionsIterator.remove();
      }
    }

    
     * Build priority queues of slots, with ones which has tasks lesser than 'minPerEndpointSlot' and another which have more.
     
    PriorityQueue<List<HttpSubScanSpec>> minHeap = new PriorityQueue<List<HttpSubScanSpec>>(numSlots, LIST_SIZE_COMPARATOR);
    PriorityQueue<List<HttpSubScanSpec>> maxHeap = new PriorityQueue<List<HttpSubScanSpec>>(numSlots, LIST_SIZE_COMPARATOR_REV);
    for(List<HttpSubScanSpec> listOfScan : endpointFragmentMapping.values()) {
      if (listOfScan.size() < minPerEndpointSlot) {
        minHeap.offer(listOfScan);
      } else if (listOfScan.size() > minPerEndpointSlot) {
        maxHeap.offer(listOfScan);
      }
    }

    
     * Now, let's process any regions which remain unassigned and assign them to slots with minimum number of assignments.
     
    if (regionsToAssignSet.size() > 0) {
      for (Entry<HttpTestDto, ServerName> regionEntry : regionsToAssignSet) {
        List<HttpSubScanSpec> smallestList = minHeap.poll();
        smallestList.add(regionInfoToSubScanSpec(regionEntry.getKey()));
        if (smallestList.size() < maxPerEndpointSlot) {
          minHeap.offer(smallestList);
        }
      }
    }

    
     * While there are slots with lesser than 'minPerEndpointSlot' unit work, balance from those with more.
     
    while(minHeap.peek() != null && minHeap.peek().size() < minPerEndpointSlot) {
      List<HttpSubScanSpec> smallestList = minHeap.poll();
      List<HttpSubScanSpec> largestList = maxHeap.poll();
      smallestList.add(largestList.remove(largestList.size()-1));
      if (largestList.size() > minPerEndpointSlot) {
        maxHeap.offer(largestList);
      }
      if (smallestList.size() < minPerEndpointSlot) {
        minHeap.offer(smallestList);
      }
    }

     no slot should be empty at this point 
    assert (minHeap.peek() == null || minHeap.peek().size() > 0) : String.format(
        "Unable to assign tasks to some endpoints.\nEndpoints: {}.\nAssignment Map: {}.",
        incomingEndpoints, endpointFragmentMapping.toString());

    //logger.debug("Built assignment map in {} µs.\nEndpoints: {}.\nAssignment Map: {}",
       // watch.elapsed(TimeUnit.NANOSECONDS)/1000, incomingEndpoints, endpointFragmentMapping.toString());
*/  }

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
	return new ScanStats(GroupScanProperty.NO_EXACT_ROW_COUNT, 4194304, 4, 318767104);
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

	    public HttpWork(String partitionKeyStart, String partitionKeyEnd) {
	      this.partitionKeyStart = partitionKeyStart;
	      this.partitionKeyEnd = partitionKeyEnd;
	    }

	    public String getPartitionKeyStart() {
	      return partitionKeyStart;
	    }

	    public String getPartitionKeyEnd() {
	      return partitionKeyEnd;
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
