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

import java.math.BigDecimal;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptRuleOperand;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexLiteral;
import org.apache.drill.exec.physical.base.GroupScan;
import org.apache.drill.exec.planner.logical.RelOptHelper;
import org.apache.drill.exec.planner.physical.LimitPrel;
import org.apache.drill.exec.planner.physical.ProjectPrel;
import org.apache.drill.exec.planner.physical.ScanPrel;
import org.apache.drill.exec.store.StoragePluginOptimizerRule;
import org.apache.drill.exec.store.http.util.NodeProcessUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public abstract class PushDownLimitToScan extends StoragePluginOptimizerRule {
  private static final Logger logger = LoggerFactory .getLogger(PushDownLimitToScan.class);

  private PushDownLimitToScan(RelOptRuleOperand operand, String description) {
	    super(operand, description);
	  }

	public static final StoragePluginOptimizerRule LIMIT_ON_SCAN = new PushDownLimitToScan(
			RelOptHelper.some(LimitPrel.class, RelOptHelper.any(ScanPrel.class)), "PushDownLimitToScan:Limit_On_Scan") {
		@Override
		public void onMatch(RelOptRuleCall call) {
			final ScanPrel scan = (ScanPrel) call.rel(1);
			final LimitPrel limit = (LimitPrel) call.rel(0);

/*			HttpGroupScan groupScan = (HttpGroupScan) scan.getGroupScan();
			if (groupScan.isLimitPushedDown()) {
				return;
			}
			BigDecimal limitValue = (BigDecimal) ((RexLiteral) (limit.getFetch())).getValue();
			// final RexNode condition = limit.get;
			logger.debug("Http onMatch");

			groupScan.getHttpScanSpec().setLimitValue(limitValue.longValueExact());
			groupScan.setLimitPushedDown(true);
			call.transformTo(scan);*/
			
			doPushDownLimitToScan(call, scan, null, limit);

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
	
	public static final StoragePluginOptimizerRule LIMIT_ON_PROJECT_SCAN = new PushDownLimitToScan(
			RelOptHelper.some(LimitPrel.class, RelOptHelper.some(ProjectPrel.class, RelOptHelper.any(ScanPrel.class))), "PushDownLimitToScan:Limit_On_Project_Scan") {
		@Override
		public void onMatch(RelOptRuleCall call) {
			final ScanPrel scan = (ScanPrel) call.rel(2);
			final ProjectPrel project = (ProjectPrel) call.rel(1);
			final LimitPrel limit = (LimitPrel) call.rel(0);

			doPushDownLimitToScan(call, scan, project, limit);

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
	

	private static void doPushDownLimitToScan(RelOptRuleCall call, final ScanPrel scan, final ProjectPrel project,
			final LimitPrel limit) {
		HttpGroupScan groupScan = (HttpGroupScan) scan.getGroupScan();
		if (groupScan.isLimitPushedDown()) {
			return;
		}
		BigDecimal limitValue = (BigDecimal) ((RexLiteral) (limit.getFetch())).getValue();
		// final RexNode condition = limit.get;
		logger.debug("Http onMatch");

		groupScan.getHttpScanSpec().setLimitValue(limitValue.longValueExact());
		groupScan.setLimitPushedDown(true);
		
/*		if(project != null){
			call.transformTo(project);
		} else {
			call.transformTo(scan);
		}*/
		
/*	    final HttpGroupScan newGroupsScan = new HttpGroupScan(groupScan.getUserName(), groupScan.getStoragePlugin(),
	            newScanSpec, groupScan.getColumns());*/
	    
		//final ScanPrel newScanPrel = ScanPrel.create(scan, limit.getTraitSet(), groupScan, scan.getRowType());
		if (project != null) {
			//call.transformTo(project.copy(limit.getTraitSet(), ImmutableList.of((RelNode) scan)));
			call.transformTo(project);
		} else {

			call.transformTo(scan);
		}

		
	}	
	
	//TODO consider can't push down limit when has group by.
	//it can get the  limit number but didn't do the pushdown(call.transformTo(scan))
	public static final StoragePluginOptimizerRule COMMON_LIMIT_ON_SCAN = new PushDownLimitToScan(
			RelOptHelper.any(LimitPrel.class), "PushDownLimitToScan:Common_Limit_On_Scan") {
		@Override
		public void onMatch(RelOptRuleCall call) {

			final LimitPrel limit = (LimitPrel) call.rel(0);
			BigDecimal limitValue = (BigDecimal) ((RexLiteral) (limit.getFetch())).getValue();
			logger.debug("Http onMatch");


/*			RelSubset relSubset = ((RelSubset) limit.getInput());
			List<RelNode> relNodes = relSubset.getRelList();*/
			
			GroupScan scan = NodeProcessUtil.findGroupScan(limit);
			if(scan == null || !(scan instanceof HttpGroupScan)) {
				return;
			}
			
			//AbstractConverter ss ;
			HttpGroupScan groupScan = (HttpGroupScan) scan;
			if (groupScan.isLimitPushedDown()) {
				return;
			}
			// final RexNode condition = limit.get;
			logger.debug("Http onMatch");

			// TODO actually if has group by, we can't push limit to scan
			groupScan.getHttpScanSpec().setLimitValue(limitValue.longValueExact());
/*			groupScan.setLimitPushedDown(true);
			call.transformTo(scan);*/

		}

	


		@Override
		public boolean matches(RelOptRuleCall call) {
			final LimitPrel limit = (LimitPrel) call.rel(0);
/*			if (limit.isPushDown()) {

				return false;
			}*/
			return super.matches(call);
		}

	};

}

