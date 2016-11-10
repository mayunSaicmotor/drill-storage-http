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

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptRuleOperand;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeFieldImpl;
import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.drill.common.expression.LogicalExpression;
import org.apache.drill.common.logical.data.NamedExpression;
import org.apache.drill.exec.planner.logical.DrillOptiq;
import org.apache.drill.exec.planner.logical.DrillParseContext;
import org.apache.drill.exec.planner.logical.RelOptHelper;
import org.apache.drill.exec.planner.physical.FilterPrel;
import org.apache.drill.exec.planner.physical.HashAggPrel;
import org.apache.drill.exec.planner.physical.PrelUtil;
import org.apache.drill.exec.planner.physical.ProjectPrel;
import org.apache.drill.exec.planner.physical.ScanPrel;
import org.apache.drill.exec.planner.types.DrillRelDataTypeSystem;
import org.apache.drill.exec.store.StoragePluginOptimizerRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public abstract class PushGroupAndFilterIntoScan extends StoragePluginOptimizerRule {
	  private static final Logger logger = LoggerFactory .getLogger(PushGroupAndFilterIntoScan.class);
  private PushGroupAndFilterIntoScan(RelOptRuleOperand operand, String description) {
    super(operand, description);
  }

  public static final StoragePluginOptimizerRule GROUPBY_ON_SCAN = new PushGroupAndFilterIntoScan(RelOptHelper.some(HashAggPrel.class, RelOptHelper.any(ScanPrel.class)), "PushGroupIntoScan:Groupby_On_Scan") {

    @Override
    public void onMatch(RelOptRuleCall call) {
      final ScanPrel scan = (ScanPrel) call.rel(1);
      final HashAggPrel hashAgg = (HashAggPrel) call.rel(0);
      List<NamedExpression> groupByCols = hashAgg.getKeys();
      
     // final RexNode condition = hashAgg.get;
     // FilterPrel f = (FilterPrel) call.rel(0);


      HttpGroupScan groupScan = (HttpGroupScan)scan.getGroupScan();
      if (groupScan.isGroupByPushedDown() || groupByCols == null || groupByCols.size() == 0) {
        /*
         * The rule can get triggered again due to the transformed "scan => filter" sequence
         * created by the earlier execution of this rule when we could not do a complete
         * conversion of Optiq Filter's condition to Http Filter. In such cases, we rely upon
         * this flag to not do a re-processing of the rule on the already transformed call.
         */
        return;
      }

      doPushGroupByToScan(call, hashAgg, null, null, scan, groupScan, groupByCols);
    }

    @Override
    public boolean matches(RelOptRuleCall call) {
      final ScanPrel scan = (ScanPrel) call.rel(1);
      if (scan.getGroupScan() instanceof HttpGroupScan) {
        return super.matches(call);
      }
      return false;
    }
  };


  public static final StoragePluginOptimizerRule GROUPBY_ON_PROJECT_AND_FILTER = new PushGroupAndFilterIntoScan(RelOptHelper.some(HashAggPrel.class, RelOptHelper.some(ProjectPrel.class, RelOptHelper.some(FilterPrel.class, RelOptHelper.any(ScanPrel.class)))), "PushGroupIntoScan:Filter_On_Project_And_Filter") {

    @Override
    public void onMatch(RelOptRuleCall call) {
      final ScanPrel scanPrel = (ScanPrel) call.rel(3);
      final FilterPrel filterPrel = (FilterPrel) call.rel(2);
      final ProjectPrel projectPrel = (ProjectPrel) call.rel(1);
      final HashAggPrel hashAggPrel = (HashAggPrel) call.rel(0);
      List<NamedExpression> groupByCols = hashAggPrel.getKeys();
      
      HttpGroupScan groupScan = (HttpGroupScan)scanPrel.getGroupScan();
      if (groupScan.isGroupByPushedDown() || groupByCols == null || groupByCols.size() == 0) {
        /*
         * The rule can get triggered again due to the transformed "scan => filter" sequence
         * created by the earlier execution of this rule when we could not do a complete
         * conversion of Optiq Filter's condition to Http Filter. In such cases, we rely upon
         * this flag to not do a re-processing of the rule on the already transformed call.
         */
         return;
      }

      // convert the filter to one that references the child of the project
     // final RexNode condition =  RelOptUtil.pushPastProject(hashAgg.getCondition(), project);

     doPushGroupByToScan(call, hashAggPrel, projectPrel, filterPrel, scanPrel, groupScan, groupByCols);
    }

    @Override
    public boolean matches(RelOptRuleCall call) {
      final ScanPrel scan = (ScanPrel) call.rel(3);
      if (scan.getGroupScan() instanceof HttpGroupScan) {
        return super.matches(call);
      }
      return false;
    }
  };
  

  public static final StoragePluginOptimizerRule GROUPBY_ON_PROJECT = new PushGroupAndFilterIntoScan(RelOptHelper.some(HashAggPrel.class, RelOptHelper.some(ProjectPrel.class, RelOptHelper.any(ScanPrel.class))), "PushGroupIntoScan:Groupby_On_Project") {

    @Override
    public void onMatch(RelOptRuleCall call) {
      final ScanPrel scan = (ScanPrel) call.rel(2);
      final ProjectPrel project = (ProjectPrel) call.rel(1);
      final HashAggPrel hashAgg = (HashAggPrel) call.rel(0);
      List<NamedExpression> groupByCols = hashAgg.getKeys();
      
      HttpGroupScan groupScan = (HttpGroupScan)scan.getGroupScan();
      if (groupScan.isGroupByPushedDown() || groupByCols == null || groupByCols.size() == 0) {
        /*
         * The rule can get triggered again due to the transformed "scan => filter" sequence
         * created by the earlier execution of this rule when we could not do a complete
         * conversion of Optiq Filter's condition to Http Filter. In such cases, we rely upon
         * this flag to not do a re-processing of the rule on the already transformed call.
         */
         return;
      }

      // convert the filter to one that references the child of the project
     // final RexNode condition =  RelOptUtil.pushPastProject(hashAgg.getCondition(), project);

     doPushGroupByToScan(call, hashAgg, project, null, scan, groupScan, groupByCols);
    }

    @Override
    public boolean matches(RelOptRuleCall call) {
      final ScanPrel scan = (ScanPrel) call.rel(2);
      if (scan.getGroupScan() instanceof HttpGroupScan) {
        return super.matches(call);
      }
      return false;
    }
  };


  protected void doPushGroupByToScan(final RelOptRuleCall call, final HashAggPrel hashAgg, final ProjectPrel project, final FilterPrel filterPrel, final ScanPrel scan, final HttpGroupScan groupScan, final List<NamedExpression> groupByCols) {

    //final LogicalExpression conditionExp = DrillOptiq.toDrill(new DrillParseContext(PrelUtil.getPlannerSettings(call.getPlanner())), scan, condition);
    //final HttpFilterBuilder httpFilterBuilder = new HttpFilterBuilder(groupScan, conditionExp);
    //final HttpScanSpec newScanSpec = httpFilterBuilder.parseTree();
	//final HttpScanSpec scanSpec =  groupScan.getHttpScanSpec();
	HttpScanSpec newScanSpec = null;
	 if(filterPrel != null){
		    final RexNode condition = filterPrel.getCondition();
		    if (!groupScan.isFilterPushedDown()) {
		    	
			    LogicalExpression conditionExp = DrillOptiq.toDrill(new DrillParseContext(PrelUtil.getPlannerSettings(call.getPlanner())), scan, condition);
				    HttpFilterBuilder httpFilterBuilder = new HttpFilterBuilder(groupScan, conditionExp);
				    newScanSpec = httpFilterBuilder.parseTree();
		    }
	 }

    if (newScanSpec == null) {
      return; //no filter pushdown ==> No transformation.
    }
    newScanSpec.setGroupByCols(groupByCols);
    final HttpGroupScan newGroupsScan = new HttpGroupScan(groupScan.getUserName(), groupScan.getStoragePlugin(),
        newScanSpec, groupScan.getColumns());

    newGroupsScan.setFilterPushedDown(true);
    newGroupsScan.setGroupByPushedDown(true);

    logger.info("hashAgg.getRowType(): "+hashAgg.getRowType());
    if(project != null){
        logger.info("project.getRowType(): "+project.getRowType());	
    }

    logger.info("scan.getRowType(): "+scan.getRowType());
    
    

    //final ScanPrel newScanPrel = ScanPrel.create(scan, scan.getTraitSet(), newGroupsScan, scan.getRowType());

    // Depending on whether is a project in the middle, assign either scan or copy of project to childRel.
    //final RelNode childRel = project == null ? newScanPrel : project.copy(project.getTraitSet(), ImmutableList.of((RelNode)newScanPrel));

    // RelNode childRel = project == null ? newScanPrel : project.copy(hashAgg.getTraitSet(), (RelNode)newScanPrel,hashAgg.getChildExps(),hashAgg.getRowType());
    // RelNode childRel = project == null ? newScanPrel : new ProjectPrel(project.getCluster(), project.getTraitSet(), (RelNode)newScanPrel,project.getChildExps(),project.getRowType());
    
    
    //if (httpFilterBuilder.isAllExpressionsConverted()) {
        /*
         * Since we could convert the entire filter condition expression into an Http filter,
         * we can eliminate the filter operator altogether.
         */
   

    if(project == null ){
    	 final ScanPrel newScanPrel = ScanPrel.create(scan, hashAgg.getTraitSet(), newGroupsScan, scan.getRowType());
    	    logger.info("newScanPrel.getRowType(): "+newScanPrel.getRowType());
    	call.transformTo(newScanPrel);   	
    } else {
    	//final RelNode childRel = project.copy(project.getTraitSet(), ImmutableList.of((RelNode)newScanPrel));
    	
    	if(hashAgg.getRowType().getFieldList().size() > project.getRowType().getFieldList().size()){
    		

        	List<RelDataTypeField> hashFields = new ArrayList<RelDataTypeField>();
        	List<RelDataTypeField> projectFields = new ArrayList<RelDataTypeField>();
        	List<RelDataTypeField> scanFields = new ArrayList<RelDataTypeField>();
        	
        	hashFields.addAll(hashAgg.getRowType().getFieldList());
        	projectFields.addAll(project.getRowType().getFieldList());
        	scanFields.addAll(scan.getRowType().getFieldList());
        	//projectFields.add(project.getRowType().getFieldList().get(0));
        	//scanFields.add(scan.getRowType().getFieldList().get(0));
        	
        	//BasicSqlType bst = (BasicSqlType)hashFields.get(2).getType();  
        	SqlTypeFactoryImpl sqlTypeFactory = new SqlTypeFactoryImpl(DrillRelDataTypeSystem.DRILL_REL_DATATYPE_SYSTEM);
        	RelDataType bigIntType =  sqlTypeFactory.createSqlType(SqlTypeName.BIGINT);
        	RelDataType doubleType =  sqlTypeFactory.createSqlType(SqlTypeName.DOUBLE);
        	
/*        	RelDataTypeField code = new RelDataTypeFieldImpl("code", 1 , doubleType);
        	scanFields.add(code);
        	projectFields.add(code);*/
        	
        	RelDataTypeField codeCount = new RelDataTypeFieldImpl("code_count", 2 , bigIntType);
        	scanFields.add(codeCount);
        	projectFields.add(new RelDataTypeFieldImpl("$f2", 2 , bigIntType));
        	
        	RelDataType scanRdt =  new RelRecordType(scanFields);
        	RelDataType projectRdt =  new RelRecordType(projectFields);

        	final ScanPrel newScanPrel = ScanPrel.create(scan, scan.getTraitSet(), newGroupsScan, scanRdt);


        	List<RexNode> projectChildExps= new ArrayList<RexNode>();
        	List<RexNode> operandsExps= new ArrayList<RexNode>();
        	projectChildExps.addAll(project.getChildExps());
        	//projectChildExps.add(project.getChildExps().get(0));
        	RexBuilder rBuilder = new RexBuilder(sqlTypeFactory);
        	RexCall oldRCall = (RexCall)(project.getChildExps().get(1));
        	RexInputRef rexInputRef = (RexInputRef)oldRCall.getOperands().get(0);
        	//create a ndw call
        	RexNode operandNode = new RexInputRef(2, bigIntType);
        	operandsExps.add(operandNode);
        	operandsExps.add(oldRCall.getOperands().get(1));
        	
        	RexCall newRCall = (RexCall)rBuilder.makeCall(bigIntType, oldRCall.getOperator(), operandsExps);
        	projectChildExps.add(newRCall);
        	
/*        	RexNode rNode = new RexInputRef(2, bigIntType);
        	projectChildExps.add(rNode);*/
        	
        	final ProjectPrel newProjectPrel =  new ProjectPrel(project.getCluster(), hashAgg.getTraitSet(), (RelNode)newScanPrel, projectChildExps, projectRdt);  
        	        	
        	 //RelNode childRel = new ProjectPrel(project.getCluster(), project.getTraitSet(), (RelNode)newScanPrel,project.getChildExps(),project.getRowType());
        	
        	// TODO
/*        	List<AggregateCall> aggCalls = hashAgg.getAggCallList();
        	List<AggregateCall> newAggCalls = new ArrayList<AggregateCall>();
        	List<Integer> newArgList = new ArrayList<Integer>();
        	newArgList.add(2);
        	AggregateCall countFunc = aggCalls.get(1);
        	
        	//SqlSumEmptyIsZeroAggFunction sumFunc = new SqlSumEmptyIsZeroAggFunction();
        	AggregateCall convertCountToSumFunc = AggregateCall.create(new SqlSumEmptyIsZeroAggFunction(), false, newArgList, -1, countFunc.getType(), null);
        	newAggCalls.add(aggCalls.get(0));
        	newAggCalls.add(convertCountToSumFunc);
        	
        	List<NamedExpression> aggExprs = hashAgg.getAggExprs();
        	final HashAggPrel newHashAggPrel  = (HashAggPrel)hashAgg.copy(hashAgg.getTraitSet(), newProjectPrel, false, hashAgg.getGroupSet(), hashAgg.getGroupSets(), newAggCalls);
        	
     	    logger.info("newScanPrel.getRowType(): "+newScanPrel.getRowType());*/
     	    // logger.info("childRel.getRowType(): "+childRel.getRowType());
     	    
     	    call.transformTo(newProjectPrel);
     	   //call.transformTo(newHashAggPrel);
			} else {

				final ScanPrel newScanPrel = ScanPrel.create(scan, hashAgg.getTraitSet(), newGroupsScan,
						scan.getRowType());
				final RelNode newProjectPrel = project.copy(project.getTraitSet(), ImmutableList.of((RelNode) newScanPrel));
				logger.info("newScanPrel.getRowType(): " + newScanPrel.getRowType());

				call.transformTo(newProjectPrel);
			}
    }
      //call.transformTo(childRel);
/*    } else {
      call.transformTo(hashAgg.copy(hashAgg.getTraitSet(), ImmutableList.of(childRel)));
    }*/
  }

}
