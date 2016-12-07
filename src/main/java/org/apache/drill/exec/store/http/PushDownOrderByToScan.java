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
 * sortations under the License.
 */
package org.apache.drill.exec.store.http;

import java.util.List;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptRuleOperand;
import org.apache.calcite.plan.volcano.RelSubset;
import org.apache.drill.exec.physical.base.GroupScan;
import org.apache.drill.exec.planner.logical.RelOptHelper;
import org.apache.drill.exec.planner.physical.Prel;
import org.apache.drill.exec.planner.physical.ProjectPrel;
import org.apache.drill.exec.planner.physical.ScanPrel;
import org.apache.drill.exec.planner.physical.SortPrel;
import org.apache.drill.exec.planner.physical.TopNPrel;
import org.apache.drill.exec.store.StoragePluginOptimizerRule;
import org.apache.drill.exec.store.http.util.NodeProcessUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PushDownOrderByToScan extends StoragePluginOptimizerRule {
  private static final Logger logger = LoggerFactory .getLogger(PushDownOrderByToScan.class);

  private PushDownOrderByToScan(RelOptRuleOperand operand, String description) {
	    super(operand, description);
	  }

//A TopN operator is used to perform an ORDER BY with LIMIT.
  public static final StoragePluginOptimizerRule TOPN_ON_SCAN = new PushDownOrderByToScan(
			RelOptHelper.some(TopNPrel.class, RelOptHelper.any(ScanPrel.class)), "PushDownOrderByToScan:Topn_On_Scan") {
		@Override
		public void onMatch(RelOptRuleCall call) {
			final ScanPrel scan = (ScanPrel) call.rel(1);
			final TopNPrel topN = (TopNPrel) call.rel(0);

			
			doPushOrderByToScan(call, scan, null, topN);

		}

		@Override
		public boolean matches(RelOptRuleCall call) {
			final ScanPrel scan = (ScanPrel) call.rel(1);
			if (scan.getGroupScan() instanceof HttpGroupScan) {
				return super.matches(call);
			}
			logger.debug("http matches return false {}", scan.getGroupScan());
			return false;
		}

	};
	
	public static final StoragePluginOptimizerRule TOPN_ON_PROJECT_SCAN = new PushDownOrderByToScan(
			RelOptHelper.some(TopNPrel.class, RelOptHelper.some(ProjectPrel.class, RelOptHelper.any(ScanPrel.class))), "PushDownOrderByToScan:Topn_On_Project_Scan") {
		@Override
		public void onMatch(RelOptRuleCall call) {
			final ScanPrel scan = (ScanPrel) call.rel(2);
			final ProjectPrel project = (ProjectPrel) call.rel(1);
			final TopNPrel sort = (TopNPrel) call.rel(0);

			doPushOrderByToScan(call, scan, project, sort);

		}

		@Override
		public boolean matches(RelOptRuleCall call) {
			final ScanPrel scan = (ScanPrel) call.rel(2);
			if (scan.getGroupScan() instanceof HttpGroupScan) {
				return super.matches(call);
			}
			logger.debug("http matches return false {}", scan.getGroupScan());
			return false;
		}

	};
	//A TopN operator is used to perform an ORDER BY with LIMIT.
	public static final StoragePluginOptimizerRule SORT_ON_SCAN = new PushDownOrderByToScan(
			RelOptHelper.some(SortPrel.class, RelOptHelper.any(ScanPrel.class)), "PushDownOrderByToScan:Sort_On_Scan") {
		@Override
		public void onMatch(RelOptRuleCall call) {
			final ScanPrel scan = (ScanPrel) call.rel(1);
			final SortPrel sort = (SortPrel) call.rel(0);

/*			HttpGroupScan groupScan = (HttpGroupScan) scan.getGroupScan();
			if (groupScan.isOrderByPushedDown()) {
				return;
			}
			
			// final RexNode condition = sort.get
			logger.debug("Http onMatch");
		    
			List<String> orderFields = NodeProcessUtil.getOrderBycols(sort);
			if(orderFields == null){
				return;
			}
 
			groupScan.getHttpScanSpec().setOrderByCols(orderFields);
			groupScan.setOrderByPushedDown(true);
			call.transformTo(scan);*/
			
			doPushOrderByToScan(call, scan, null, sort);

		}

		@Override
		public boolean matches(RelOptRuleCall call) {
			final ScanPrel scan = (ScanPrel) call.rel(1);
			if (scan.getGroupScan() instanceof HttpGroupScan) {
				return super.matches(call);
			}
			logger.debug("http matches return false {}", scan.getGroupScan());
			return false;
		}

	};
	
	public static final StoragePluginOptimizerRule SORT_ON_PROJECT_SCAN = new PushDownOrderByToScan(
			RelOptHelper.some(SortPrel.class, RelOptHelper.some(ProjectPrel.class, RelOptHelper.any(ScanPrel.class))), "PushDownOrderByToScan:Sort_On_Project_Scan") {
		@Override
		public void onMatch(RelOptRuleCall call) {
			final ScanPrel scan = (ScanPrel) call.rel(2);
			final ProjectPrel project = (ProjectPrel) call.rel(1);
			final SortPrel sort = (SortPrel) call.rel(0);

			doPushOrderByToScan(call, scan, project, sort);

		}

		@Override
		public boolean matches(RelOptRuleCall call) {
			final ScanPrel scan = (ScanPrel) call.rel(2);
			if (scan.getGroupScan() instanceof HttpGroupScan) {
				return super.matches(call);
			}
			logger.debug("http matches return false {}", scan.getGroupScan());
			return false;
		}

	};
	
	
	
	//TODO consider whether need to pushdown order by
	//it can get the  OrderByCols but sometimes it can't be push down(call.transformTo(scan))
	// TODO for multi-tables query, need to confirm the result
	public static final StoragePluginOptimizerRule COMMON_SORT_ON_SCAN = new PushDownOrderByToScan(
			RelOptHelper.any(SortPrel.class), "PushDownOrderByToScan:Common_Sort_On_Scan") {
		@Override
		public void onMatch(RelOptRuleCall call) {

			final SortPrel sort = (SortPrel) call.rel(0);
			logger.debug("Http onMatch");

			//List<RexNode> orderByCols = sort.getChildExps();
			RelSubset relSubset = ((RelSubset) sort.getInput());
			//List<RelNode> relNodes = relSubset.getRelList();
			
			GroupScan scan = NodeProcessUtil.findGroupScan(sort);
			if(scan == null || !(scan instanceof HttpGroupScan)) {
				return;
			}
			
			//AbstractConverter ss ;
			HttpGroupScan groupScan = (HttpGroupScan) scan;
			if (groupScan.isOrderByPushedDown()) {
				return;
			}
			// final RexNode condition = limit.get;
			logger.debug("Http onMatch");
			
			List<String> orderFields = NodeProcessUtil.getOrderBycols(sort);
			if(orderFields == null){
				return;
			}
			
			setOrderFields(groupScan, orderFields);

			//groupScan.setOrderByPushedDown(true);
			
			//TODO don't do pushdown

		}


		@Override
		public boolean matches(RelOptRuleCall call) {
			final SortPrel sort = (SortPrel) call.rel(0);

			return super.matches(call);
		}

	};
	
	
	//TODO consider whether need to pushdown order by
	//it can get the  OrderByCols but sometimes it can't be push down(call.transformTo(scan))
	// TODO for multi-tables query, need to confirm the result
	public static final StoragePluginOptimizerRule COMMON_TOPN_ON_SCAN = new PushDownOrderByToScan(
			RelOptHelper.any(TopNPrel.class), "PushDownOrderByToScan:Common_TopN_On_Scan") {
		@Override
		public void onMatch(RelOptRuleCall call) {

			final TopNPrel sort = (TopNPrel) call.rel(0);
			logger.debug("Http onMatch");

			//List<RexNode> orderByCols = sort.getChildExps();
			RelSubset relSubset = ((RelSubset) sort.getInput());
			//List<RelNode> relNodes = relSubset.getRelList();
			
			GroupScan scan = NodeProcessUtil.findGroupScan(sort);
			if(scan == null || !(scan instanceof HttpGroupScan)) {
				return;
			}
			
			//AbstractConverter ss ;
			HttpGroupScan groupScan = (HttpGroupScan) scan;
			if (groupScan.isOrderByPushedDown()) {
				return;
			}
			// final RexNode condition = limit.get;
			logger.debug("Http onMatch");
			
			List<String> orderFields = NodeProcessUtil.getOrderBycols(sort);
			if(orderFields == null){
				return;
			}
			
			setOrderFields(groupScan, orderFields);

			//groupScan.setOrderByPushedDown(true);
			
			//TODO don't do pushdown

		}


		@Override
		public boolean matches(RelOptRuleCall call) {
			final SortPrel sort = (SortPrel) call.rel(0);

			return super.matches(call);
		}

	};
	
	
	private static void doPushOrderByToScan(RelOptRuleCall call, ScanPrel scan, final ProjectPrel project, final Prel sortPrelOrTopnPrel) {
		
		HttpGroupScan groupScan = (HttpGroupScan) scan.getGroupScan();
		if (groupScan.isOrderByPushedDown()) {
			return;
		}
		
		List<String> orderFields = NodeProcessUtil.getOrderBycols(sortPrelOrTopnPrel);
		if(orderFields == null){
			return;
		}

		setOrderFields(groupScan, orderFields);

		groupScan.setOrderByPushedDown(true);
		
		if(sortPrelOrTopnPrel instanceof TopNPrel){
			((TopNPrel)sortPrelOrTopnPrel).getRelTypeName();
		}

		//final ScanPrel newScanPrel = ScanPrel.create(scan, scan.getTraitSet(), groupScan, scan.getRowType());
		logger.debug("Http onMatch");
		if (project != null) {
			//call.transformTo(project.copy(project.getTraitSet(), ImmutableList.of((RelNode) scan)));
			call.transformTo(project);
		} else {

			call.transformTo(scan);
		}

	}

	private static void setOrderFields(HttpGroupScan groupScan, List<String> orderFields) {
		//TODO not sure it is can confirm it is agg func
		List<String> originalOrderByCols = groupScan.getHttpScanSpec().getOriginalOrderByCols();
		if(originalOrderByCols!=null){
			
			if(!originalOrderByCols.equals(orderFields)) {
				groupScan.setOrderByAggFunc(true);
			}

			
		} else {
			groupScan.getHttpScanSpec().setOriginalOrderByCols(orderFields);
		}
		groupScan.getHttpScanSpec().setOrderByCols(orderFields);
	}

}

