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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptRuleOperand;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeFieldImpl;
import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.drill.common.expression.LogicalExpression;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.common.logical.data.NamedExpression;
import org.apache.drill.exec.planner.logical.RelOptHelper;
import org.apache.drill.exec.planner.physical.AggPrelBase;
import org.apache.drill.exec.planner.physical.HashAggPrel;
import org.apache.drill.exec.planner.physical.Prel;
import org.apache.drill.exec.planner.physical.ProjectPrel;
import org.apache.drill.exec.planner.physical.ScanPrel;
import org.apache.drill.exec.planner.physical.StreamAggPrel;
import org.apache.drill.exec.planner.types.DrillRelDataTypeSystem;
import org.apache.drill.exec.store.StoragePluginOptimizerRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PushDownGroupByToScan extends StoragePluginOptimizerRule {
	
	private static final String FUNCTION_OPERATION_PREFIX = "$f";
	private static final Logger logger = LoggerFactory .getLogger(PushDownGroupByToScan.class);
	
	private SqlTypeFactoryImpl sqlTypeFactory = new SqlTypeFactoryImpl(DrillRelDataTypeSystem.DRILL_REL_DATATYPE_SYSTEM);
	private RelDataType bigIntType =  sqlTypeFactory.createSqlType(SqlTypeName.BIGINT);
	private RexBuilder rBuilder = new RexBuilder(sqlTypeFactory);
	
  private PushDownGroupByToScan(RelOptRuleOperand operand, String description) {
    super(operand, description);
  }

  
  public static final StoragePluginOptimizerRule GROUPBY_ON_STREAMAGG_SCAN = new PushDownGroupByToScan(RelOptHelper.some(StreamAggPrel.class, RelOptHelper.any(ScanPrel.class)), "PushGroupIntoScan:Groupby_On_StreamAgg_Scan") {

	    @Override
	    public void onMatch(RelOptRuleCall call) {
	      final ScanPrel scan = (ScanPrel) call.rel(1);
	      final StreamAggPrel streamAgg = (StreamAggPrel) call.rel(0);
	      List<NamedExpression> groupByCols = streamAgg.getKeys();
	      
	     // final RexNode condition = hashAgg.get;
	     // FilterPrel f = (FilterPrel) call.rel(0);


	      HttpGroupScan groupScan = (HttpGroupScan)scan.getGroupScan();
	      if (groupScan.isGroupByPushedDown()) {
	        /*
	         * The rule can get triggered again due to the transformed "scan => filter" sequence
	         * created by the earlier execution of this rule when we could not do a complete
	         * conversion of Optiq Filter's condition to Http Filter. In such cases, we rely upon
	         * this flag to not do a re-processing of the rule on the already transformed call.
	         */
	        return;
	      }

	      doPushGroupByToScan(call, streamAgg, null, scan, groupScan, groupByCols);
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
	  
	  public static final StoragePluginOptimizerRule GROUPBY_ON_STREAMAGG_PROJECT_SCAN = new PushDownGroupByToScan(RelOptHelper.some(StreamAggPrel.class, RelOptHelper.some(ProjectPrel.class, RelOptHelper.any(ScanPrel.class))), "PushGroupIntoScan:Groupby_On_StreamAgg_Project_Scan") {

		    @Override
		    public void onMatch(RelOptRuleCall call) {
		      final ScanPrel scan = (ScanPrel) call.rel(2);
		      final ProjectPrel project = (ProjectPrel) call.rel(1);
		      final StreamAggPrel streamAgg = (StreamAggPrel) call.rel(0);
		      List<NamedExpression> groupByCols = streamAgg.getKeys();
		      
		      HttpGroupScan groupScan = (HttpGroupScan)scan.getGroupScan();
		      if (groupScan.isGroupByPushedDown()) {
	
		         return;
		      }

		      // convert the filter to one that references the child of the project
		     // final RexNode condition =  RelOptUtil.pushPastProject(hashAgg.getCondition(), project);

		     doPushGroupByToScan(call, streamAgg, project, scan, groupScan, groupByCols);
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
	  
  public static final StoragePluginOptimizerRule GROUPBY_ON_HASHAGG_SCAN = new PushDownGroupByToScan(RelOptHelper.some(HashAggPrel.class, RelOptHelper.any(ScanPrel.class)), "PushGroupIntoScan:Groupby_On_Scan") {

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

      doPushGroupByToScan(call, hashAgg, null, scan, groupScan, groupByCols);
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
  

  public static final StoragePluginOptimizerRule GROUPBY_ON_HASHAGG_PROJECT_SCAN = new PushDownGroupByToScan(RelOptHelper.some(HashAggPrel.class, RelOptHelper.some(ProjectPrel.class, RelOptHelper.any(ScanPrel.class))), "PushGroupIntoScan:Groupby_On_Project") {

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

     doPushGroupByToScan(call, hashAgg, project, scan, groupScan, groupByCols);
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


  protected void doPushGroupByToScan(final RelOptRuleCall call, final AggPrelBase aggPrel, final ProjectPrel project, final ScanPrel scan, final HttpGroupScan groupScan, final List<NamedExpression> groupByCols) {

    //final LogicalExpression conditionExp = DrillOptiq.toDrill(new DrillParseContext(PrelUtil.getPlannerSettings(call.getPlanner())), scan, condition);
    //final HttpFilterBuilder httpFilterBuilder = new HttpFilterBuilder(groupScan, conditionExp);
    //final HttpScanSpec newScanSpec = httpFilterBuilder.parseTree();
	//final HttpScanSpec scanSpec =  groupScan.getHttpScanSpec();
	  
/*	if (hashAgg == null && filterPrel == null) {
		return; // no filter or groupby need to pushdown ==> No transformation.
	}
		
	HttpScanSpec newScanSpec = null;
	if (filterPrel != null) {
		final RexNode condition = filterPrel.getCondition();
		if (!groupScan.isFilterPushedDown()) {

			LogicalExpression conditionExp = DrillOptiq.toDrill(
					new DrillParseContext(PrelUtil.getPlannerSettings(call.getPlanner())), scan, condition);
			HttpFilterBuilder httpFilterBuilder = new HttpFilterBuilder(groupScan, conditionExp);
			newScanSpec = httpFilterBuilder.parseTree();
		}
	} else {


		newScanSpec = ((HttpGroupScan) scan.getGroupScan()).getHttpScanSpec();
	}
*/
	  
  if (aggPrel == null) {
		return; // no groupby need to pushdown ==> No transformation.
	}
	  
	HttpScanSpec newScanSpec = ((HttpGroupScan) scan.getGroupScan()).getHttpScanSpec();
	List<String> groupFields =new ArrayList<String>();
	for(NamedExpression nameExp : groupByCols){
		
		groupFields.add(nameExp.getRef().getAsUnescapedPath());
	}
    newScanSpec.setGroupByCols(groupFields);
/*    final HttpGroupScan newGroupsScan = new HttpGroupScan(groupScan.getUserName(), groupScan.getStoragePlugin(),
        newScanSpec, groupScan.getColumns());*/
    HttpGroupScan newGroupsScan = groupScan;
    newGroupsScan.setHttpScanSpec(newScanSpec);
    //newGroupsScan.setFilterPushedDown(true);
    //newGroupsScan.setFilterPushedDown(groupScan.isFilterPushedDown());
    newGroupsScan.setGroupByPushedDown(true);
    
/*    LogicalExpression exp = DrillOptiq.toDrill(new DrillParseContext(PrelUtil.getPlannerSettings(call.getPlanner())), project, project.getChildExps().get(0));
    HttpFilterBuilder httpFilterBuilder = new HttpFilterBuilder(groupScan, exp);
    newScanSpec = httpFilterBuilder.parseTree();*/
    
    

    logger.info("hashAgg.getRowType(): "+aggPrel.getRowType());
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
   

/*    //only filter to pushdown
    if(project == null ){
    	 final ScanPrel newScanPrel = ScanPrel.create(scan, hashAgg.getTraitSet(), newGroupsScan, scan.getRowType());
    	    logger.info("newScanPrel.getRowType(): "+newScanPrel.getRowType());
    	call.transformTo(newScanPrel);   	
    } else {*/
    	//final RelNode childRel = project.copy(project.getTraitSet(), ImmutableList.of((RelNode)newScanPrel));
    	//if hashagg.fields > scan.fields, usually it means avg function, need to push down avg function to scan
    	//if(hashAgg.getRowType().getFieldList().size() > scan.getRowType().getFieldList().size()){
    		
    			
    		//final ProjectPrel newPrel = createPushDownProjectPrel(hashAgg, project, scan, newGroupsScan);  
    		//final Prel newPrel = createPushDownPrel(hashAgg, project, scan, newGroupsScan);  
    		final Prel newPrel1 = createPushDownPrel(aggPrel, project, scan, newGroupsScan);  
        	// do push down
        	call.transformTo(newPrel1);
/*		} else {

			final ScanPrel newScanPrel = ScanPrel.create(scan, hashAgg.getTraitSet(), newGroupsScan,
					scan.getRowType());
			if(project == null){
				logger.info("transformTo newScanPrel");
				call.transformTo(newScanPrel);
			} else {
				final RelNode newProjectPrel = project.copy(project.getTraitSet(), ImmutableList.of((RelNode) newScanPrel));
				logger.info("transformTo newProjectPrel");
				call.transformTo(newProjectPrel);
			}

		}		
*/
/*        	List<RelDataTypeField> hashFields = new ArrayList<RelDataTypeField>();
        	List<RelDataTypeField> projectFields = new ArrayList<RelDataTypeField>();
        	List<RelDataTypeField> scanFields = new ArrayList<RelDataTypeField>();
        	
        	hashFields.addAll(hashAgg.getRowType().getFieldList());
        	projectFields.addAll(project.getRowType().getFieldList());
        	scanFields.addAll(scan.getRowType().getFieldList());*/
        	//projectFields.add(project.getRowType().getFieldList().get(0));
        	//scanFields.add(scan.getRowType().getFieldList().get(0));
        	
        	//BasicSqlType bst = (BasicSqlType)hashFields.get(2).getType();  
        	//SqlTypeFactoryImpl sqlTypeFactory = new SqlTypeFactoryImpl(DrillRelDataTypeSystem.DRILL_REL_DATATYPE_SYSTEM);
        	//RelDataType bigIntType =  sqlTypeFactory.createSqlType(SqlTypeName.BIGINT);
/*        	RelDataType doubleType =  sqlTypeFactory.createSqlType(SqlTypeName.DOUBLE);
        	
        	RelDataTypeField code = new RelDataTypeFieldImpl("code", 1 , doubleType);
        	scanFields.add(code);
        	projectFields.add(code);*/
        	
/*        	RelDataTypeField codeCount = new RelDataTypeFieldImpl("code_count", 2 , bigIntType);
        	newScanFields.add(codeCount);
        	newPojectFields.add(new RelDataTypeFieldImpl("$f2", 2 , bigIntType));*/
        	
        	//RelDataType scanRdt =  new RelRecordType(newScanFields);
        	//RelDataType projectRdt =  new RelRecordType(newPojectFields);

        	//final ScanPrel newScanPrel = ScanPrel.create(scan, scan.getTraitSet(), newGroupsScan, scanRdt);



        	
/*        	RexNode rNode = new RexInputRef(2, bigIntType);
        	projectChildExps.add(rNode);*/
        	
        	//final ProjectPrel newProjectPrel =  new ProjectPrel(project.getCluster(), hashAgg.getTraitSet(), (RelNode)newScanPrel, projectChildExps, projectRdt);  
        	        	
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
     	    
     	    //call.transformTo(newProjectPrel);
     	   //call.transformTo(newHashAggPrel);
/*			} else {

				final ScanPrel newScanPrel = ScanPrel.create(scan, hashAgg.getTraitSet(), newGroupsScan,
						scan.getRowType());
				final RelNode newProjectPrel = project.copy(project.getTraitSet(), ImmutableList.of((RelNode) newScanPrel));
				logger.info("newScanPrel.getRowType(): " + newScanPrel.getRowType());

				call.transformTo(newProjectPrel);
			}
    }*/
      //call.transformTo(childRel);
/*    } else {
      call.transformTo(hashAgg.copy(hashAgg.getTraitSet(), ImmutableList.of(childRel)));
    }*/
  }

/*	
	private Prel createPushDownPrel(final HashAggPrel hashAgg, final ProjectPrel project,
			final ScanPrel scan, final HttpGroupScan newGroupsScan) {
		
		 * SqlTypeFactoryImpl sqlTypeFactory = new
		 * SqlTypeFactoryImpl(DrillRelDataTypeSystem.DRILL_REL_DATATYPE_SYSTEM);
		 * RelDataType bigIntType =
		 * sqlTypeFactory.createSqlType(SqlTypeName.BIGINT); RexBuilder rBuilder
		 * = new RexBuilder(sqlTypeFactory);
		 
		boolean hasProjectFlg = true;
		if(project== null){
			hasProjectFlg = false;			
		}
		
		List<RelDataTypeField> hashFields = hashAgg.getRowType().getFieldList();
		List<RelDataTypeField> scanFields = scan.getRowType().getFieldList();
		List<SchemaPath> columns = newGroupsScan.getColumns();

		List<RelDataTypeField> newPojectFields =hasProjectFlg? new ArrayList<RelDataTypeField>():null;
		List<RelDataTypeField> newScanFields = new ArrayList<RelDataTypeField>();
		List<SchemaPath> newColumns = new ArrayList<SchemaPath>();

		List<RexNode> projectChildExps = new ArrayList<RexNode>();
		if(hasProjectFlg){
			projectChildExps.addAll(project.getChildExps());
			newPojectFields.addAll(project.getRowType().getFieldList());
		}


		// newHashFields.addAll(hashAgg.getRowType().getFieldList());
		newScanFields.addAll(scanFields);
		newColumns.addAll(columns);

		// [$SUM0($1), COUNT($1), $SUM0($2), COUNT($2)]
		List<AggregateCall> aggCallList = hashAgg.getAggCallList();
		List<NamedExpression> aggExprs = hashAgg.getAggExprs();

		// avg number
		// int avgNumber = 0;
		Set<Integer> newFieldIndexSet = new LinkedHashSet<Integer>();
		for (int i = 0; i < aggCallList.size() - 1; i++) {
			// $SUM0($1)
			AggregateCall aggregateCall = aggCallList.get(i);
			// $SUM0
			SqlAggFunction sqlFunction = aggregateCall.getAggregation();
			// [1]
			List<Integer> argList = aggregateCall.getArgList();
			// SUM0
			String functionName = sqlFunction.getName();

			// COUNT($1)
			AggregateCall nextAggregateCall = aggCallList.get(i + 1);
			// COUNT
			SqlAggFunction nextSqlFunction = nextAggregateCall.getAggregation();
			// [1]
			List<Integer> nextArgList = nextAggregateCall.getArgList();
			// COUNT
			String nextFunctionName = nextSqlFunction.getName();

			NamedExpression nameExpr = aggExprs.get(i);
			NamedExpression nextNameExpr = aggExprs.get(i + 1);
			// func=$sum0, args=[`$f1`]
			LogicalExpression logicalExpr = nameExpr.getExpr();
			// $f1
			String refField = nameExpr.getRef().getAsUnescapedPath();
			String nextRefField = nextNameExpr.getRef().getAsUnescapedPath();

			// TODO found avg = sum/count, sum and count's ref field should be
			// the same
			if ("$SUM0".equals(functionName) && "COUNT".equals(nextFunctionName) && (argList.equals(nextArgList))) {

				// 1 usually it should only one arg
				Integer avgFuncReferIndex = argList.get(0);
				// 2
				Integer newFieldIndex = avgFuncReferIndex + newFieldIndexSet.size() + 1;
				newFieldIndexSet.add(newFieldIndex);

				// code_count
				String scanFieldName = scanFields.get(avgFuncReferIndex).getName();
				String newScanFieldName = scanFieldName + "_count";
				RelDataTypeField newScanField = new RelDataTypeFieldImpl(newScanFieldName, newFieldIndex, bigIntType);
				// insert new Scan Field
				newScanFields.add(newFieldIndex, newScanField);
				// newColumns.add(newFieldIndex,
				// SchemaPath.getSimplePath(newScanFieldName));
				// for get SQL
				int refFieldIndex = newFieldIndex - 1;
				this.replace(newColumns, refFieldIndex, SchemaPath.getSimplePath(
						"sum(" + newColumns.get(refFieldIndex).getAsUnescapedPath() + ") as " + scanFieldName));
				newColumns.add(newFieldIndex,
						SchemaPath.getSimplePath("count(" + scanFieldName + ") as " + newScanFieldName));

				if(hasProjectFlg){
					
					// $f2
					String newProjectFieldName = "$" + newFieldIndex;
					// if it is function call
					if (projectChildExps.get(avgFuncReferIndex) instanceof RexCall) {

						RexCall tmpProjectChildExp = (RexCall) projectChildExps.get(avgFuncReferIndex);
						RexCall newRCall = cloneRexCallWithDifferentIndex(tmpProjectChildExp, newFieldIndex, null);

						newProjectFieldName = FUNCTION_OPERATION_PREFIX + newFieldIndex;
					}

					// newRefField=$f2
					RelDataTypeField projectCodeCount = new RelDataTypeFieldImpl(newProjectFieldName, newFieldIndex,
							bigIntType);
					// insert new Project Field
					newPojectFields.add(newFieldIndex, projectCodeCount);

					// projectChildExps.add(project.getChildExps().get(0));
					RexCall oldRexCall = (RexCall) (projectChildExps.get(avgFuncReferIndex));
		        	//RexInputRef rexInputRef = (RexInputRef)oldRexCall.getOperands().get(0);
		        	//create a rex call
		        	RexNode operandNode = new RexInputRef(newFieldIndex, bigIntType);
		        	List<RexNode> operandsExps= new ArrayList<RexNode>();
		        	operandsExps.add(operandNode);	        	
		        	for(int j=1;j <oldRexCall.getOperands().size();j++){
		        		operandsExps.add(oldRexCall.getOperands().get(j));	
		        	}
		        	RexCall newRCall = (RexCall)rBuilder.makeCall(bigIntType, oldRexCall.getOperator(), operandsExps);

					RexCall newRCall = cloneRexCallWithDifferentIndex(oldRexCall, newFieldIndex, bigIntType);

					projectChildExps.add(newFieldIndex, newRCall);
				}
				

				i++;

			}

		}

		// change the old index to new by order
		if (newFieldIndexSet.size() > 0) {
			int minIndex = newFieldIndexSet.iterator().next();
			for (int index = 0; index < newScanFields.size(); index++) {

				if (index > minIndex && !newFieldIndexSet.contains(index)) {

					if(hasProjectFlg){
						RelDataTypeField tmpProjectField = newPojectFields.get(index);
						String tmpProjectFieldName = tmpProjectField.getName();
						if (projectChildExps.get(index) instanceof RexCall) {

							RexCall tmpProjectChildExp = (RexCall) projectChildExps.get(index);
							RexCall newRCall = cloneRexCallWithDifferentIndex(tmpProjectChildExp, index, null);

							replace(projectChildExps, index, newRCall);

							tmpProjectFieldName = FUNCTION_OPERATION_PREFIX + index;
						} else if (projectChildExps.get(index) instanceof RexInputRef) {

							RexInputRef tmpProjectChildExp = (RexInputRef) projectChildExps.get(index);
							RexNode operandNode = new RexInputRef(index, tmpProjectChildExp.getType());
							replace(projectChildExps, index, operandNode);

						}

						RelDataTypeField newTmpProjectField = new RelDataTypeFieldImpl(tmpProjectFieldName, index,
								tmpProjectField.getType());
						replace(newPojectFields, index, newTmpProjectField);
					}
					

					RelDataTypeField tmpScanField = newScanFields.get(index);
					RelDataTypeField newTmpScanField = new RelDataTypeFieldImpl(tmpScanField.getName(), index,
							tmpScanField.getType());
					replace(newScanFields, index, newTmpScanField);

				}

			}

		}

		RelDataType scanRdt = new RelRecordType(newScanFields);
		newGroupsScan.setColumns(newColumns);
		final ScanPrel newScanPrel = ScanPrel.create(scan, hashAgg.getTraitSet(), newGroupsScan, scanRdt);
		if(hasProjectFlg){
			RelDataType projectRdt = new RelRecordType(newPojectFields);
			final ProjectPrel newProjectPrel = new ProjectPrel(project.getCluster(), hashAgg.getTraitSet(),
					(RelNode) newScanPrel, projectChildExps, projectRdt);
			return newProjectPrel;
		}

		return newScanPrel;
	}*/



	private Prel createPushDownPrel(final AggPrelBase hashAgg, final ProjectPrel project,
			final ScanPrel scan, final HttpGroupScan newGroupsScan) {
		/*
		 * SqlTypeFactoryImpl sqlTypeFactory = new
		 * SqlTypeFactoryImpl(DrillRelDataTypeSystem.DRILL_REL_DATATYPE_SYSTEM);
		 * RelDataType bigIntType =
		 * sqlTypeFactory.createSqlType(SqlTypeName.BIGINT); RexBuilder rBuilder
		 * = new RexBuilder(sqlTypeFactory);
		 */
		boolean hasProjectFlg = true;
		if(project== null){
			hasProjectFlg = false;			
		}
		
		List<RelDataTypeField> hashFields = hashAgg.getRowType().getFieldList();
		List<RelDataTypeField> scanFields = scan.getRowType().getFieldList();
		//List<SchemaPath> columns = newGroupsScan.getColumns();

		//List<RelDataTypeField> newPojectFields =hasProjectFlg? new ArrayList<RelDataTypeField>():null;
		List<RelDataTypeField> newScanFields = new ArrayList<RelDataTypeField>();
		List<SchemaPath> newColumns = new ArrayList<SchemaPath>();

		List<RexNode> newProjectChildExps = hasProjectFlg?new ArrayList<RexNode>() : null;
/*		if(hasProjectFlg){
			newProjectChildExps.addAll(project.getChildExps());
			newPojectFields.addAll(project.getRowType().getFieldList());
		}*/


		// newHashFields.addAll(hashAgg.getRowType().getFieldList());
		//newScanFields.addAll(scanFields);
		//newColumns.addAll(columns);

		// [$SUM0($1), COUNT($1), $SUM0($2), COUNT($2)]
		List<AggregateCall> aggCallList = hashAgg.getAggCallList();
		List<NamedExpression> aggExprs = hashAgg.getAggExprs();
		
		Map<String, LogicalExpression> aggExprsMap = new HashMap<String, LogicalExpression>();
		for(NamedExpression tmpNameExp : aggExprs){		
			aggExprsMap.put(tmpNameExp.getRef().getAsUnescapedPath(), tmpNameExp.getExpr());
		}

		int aggCallListIndex = 0;
		int oldProjectIndex = -1;
		for (int newFieldIndex = 0; newFieldIndex < hashFields.size(); newFieldIndex++) {
			//int newFieldIndex = i;
			RelDataTypeField oldHashField = hashFields.get(newFieldIndex);
			String oldHashFieldName = oldHashField.getName();


			
			int refScanIndex = newFieldIndex;
			int refProjectIndex = newFieldIndex;
			//AggregateCall aggregateCall = null;
			SqlAggFunction sqlAggFunction = null;
			if (aggCallList != null && aggCallList.size() > aggCallListIndex) {
				AggregateCall aggregateCall = aggCallList.get(aggCallListIndex);
				
				// [1]
				List<Integer> argList = aggregateCall.getArgList();
				// when execute like "select count(*) from student", the argList is 0 size
				 refScanIndex = argList.size() > 0 ?argList.get(0):0;
				 refProjectIndex = argList.size() > 0 ?argList.get(0):0;
			 
				// COUNT
				sqlAggFunction = aggregateCall.getAggregation();
			}
			
			//AggregateCall aggregateCall = aggCallList.get(aggCallListIndex);


			
			
			if(hasProjectFlg){
				List<RelDataTypeField> oldPojectFields = project.getRowType().getFieldList();
				oldPojectFields.get(refProjectIndex);
				RelDataTypeField oldProjectField = oldPojectFields.get(refScanIndex);
				
				
				List<RexNode> oldProjectChildExps = project.getChildExps();
				RexNode oldProjectChildExp = oldProjectChildExps.get(refProjectIndex);
				
				//caculate refScanIndex
				refScanIndex = reCaculateRefScanIndex(refScanIndex, oldProjectChildExp);	
				
			}
			
			//TODO
			//if(oldHashFieldName.startsWith("$f") || oldHashFieldName.startsWith("EXPR$")){
			if(aggExprsMap.get(oldHashFieldName) !=null){
				
				// for new scan fields
				//RelDataTypeField oldScanField= scanFields.get(refScanIndex);
				// when select count(*), scanFields is 0 size
				//TODO
				String oldScanFieldName = null;
				if(scanFields == null || scanFields.size() == 0){
					oldScanFieldName = "*";
				} else {
					oldScanFieldName = scanFields.get(refScanIndex).getName();	
				}
				//String oldScanFieldName = oldScanField.getName();	
				String functionName = sqlAggFunction.getName();			
				String newScanFieldName = null;
				String newScanFieldNameForSQL = null;
				RelDataType newType = oldHashField.getType();
				if("$SUM0".equals(functionName)){
					

					newScanFieldName = oldScanFieldName +"_sum";
					//newScanFieldNameForSQL = "SUM(" + oldScanFieldName + ") as " + newScanFieldName;
					//TODO
					newScanFieldNameForSQL = "SUM(" + oldScanFieldName + ") as " + oldHashFieldName;
					
				} else {		

					newScanFieldName = oldScanFieldName +"_"+functionName.toLowerCase();
					// newScanFieldNameForSQL = functionName + "(" + oldScanFieldName+ ") as " + newScanFieldName;
					newScanFieldNameForSQL = functionName + "(" + oldScanFieldName+ ") as " + oldHashFieldName;
				}
				
				newColumns.add(SchemaPath.getSimplePath(newScanFieldNameForSQL));
				RelDataTypeField newScanField = new RelDataTypeFieldImpl(newScanFieldName, newFieldIndex, newType);
				newScanFields.add(newScanField);
				
				// for new project fields
				if(hasProjectFlg){
					
/*					 aggregateCall = aggCallList.get(aggCallListIndex);
					// COUNT
					 sqlAggFunction = aggregateCall.getAggregation();
					// [1]
					  aggregateCall.getArgList();*/
					  
					List<RelDataTypeField> oldPojectFields = project.getRowType().getFieldList();
					List<RexNode> oldProjectChildExps = project.getChildExps();
					RelDataTypeField oldProjectField = oldPojectFields.get(refProjectIndex);
					RexNode oldProjectChildExp = oldProjectChildExps.get(refProjectIndex);
					//String oldProjectFieldName = oldProjectField.getName();

					String newProjectFieldName = "$f" + newFieldIndex;
					
					//TODO
					if (oldProjectIndex != refScanIndex) {
						//newPojectFields.add(oldProjectField);
						//newProjectChildExps.add(oldProjectChildExp);
						newType = oldProjectField.getType();
						oldProjectIndex = refScanIndex;
					}
					
					// newRefField=$f2
					RelDataTypeField newProjectField = new RelDataTypeFieldImpl(newProjectFieldName, newFieldIndex, newType);
					// add new Project Field
					//newPojectFields.add(newProjectField);
					
					// if it is function call
					if (oldProjectChildExp instanceof RexCall) {

						//RexCall oldRexCall = (RexCall) (oldProjectChildExp);
						RexCall newRCall = cloneRexCallWithDifferentIndex((RexCall) (oldProjectChildExp), newFieldIndex, newType);
						newProjectChildExps.add(newFieldIndex, newRCall);
					} else if(oldProjectChildExp instanceof RexInputRef){
						
						RexNode operandNode = new RexInputRef(newFieldIndex, newType);
						
						newProjectChildExps.add(newFieldIndex, operandNode);
					} else if(oldProjectChildExp instanceof RexLiteral){
						//RexLiteral  newRexLiteral= RexBuilder.makeLiteral(((RexLiteral)oldProjectChildExp).getValue(),((RexLiteral)oldProjectChildExp).getType(), oldHashField.getType().getSqlTypeName());
						RexNode operandNode = new RexInputRef(newFieldIndex, oldHashField.getType());
						newProjectChildExps.add(newFieldIndex, operandNode);
					} else{
						
						//TODO
						logger.error("unknow type");
						//throw new Exception("unknow type");
					}
					
/*					RexCall oldRexCall = (RexCall) (oldProjectChildExp);
					RexCall newRCall = cloneRexCallWithDifferentIndex(oldRexCall, newFieldIndex, newType);
					newProjectChildExps.add(newFieldIndex, newRCall);*/
				}

				
				aggCallListIndex++;
				
			} else {
				newScanFields.add(hashFields.get(newFieldIndex));
				newColumns.add(SchemaPath.getSimplePath(hashFields.get(newFieldIndex).getName()));
				if(hasProjectFlg){
					List<RexNode> oldProjectChildExps = project.getChildExps();
					//newPojectFields.add(hashFields.get(newFieldIndex));
					oldProjectIndex++;
					newProjectChildExps.add(newFieldIndex, oldProjectChildExps.get(oldProjectIndex));
				}
			}

		}

		//TODO
		//RelDataType scanRdt = new RelRecordType(newScanFields);
		RelDataType scanRdt = new RelRecordType(hashFields);
		newGroupsScan.setColumns(newColumns);
		final ScanPrel newScanPrel = ScanPrel.create(scan, hashAgg.getTraitSet(), newGroupsScan, scanRdt);
		if(hasProjectFlg){
			//RelDataType projectRdt = new RelRecordType(newPojectFields);
			RelDataType projectRdt = new RelRecordType(hashFields);
			final ProjectPrel newProjectPrel = new ProjectPrel(project.getCluster(), hashAgg.getTraitSet(),
					(RelNode) newScanPrel, newProjectChildExps, projectRdt);
			return newProjectPrel;
		}

		return newScanPrel;
	}


	private int reCaculateRefScanIndex(int refScanIndex, RexNode oldProjectChildExp) {
		// if it is function call
		if (oldProjectChildExp instanceof RexCall) {

			List<RexNode>  oldOperands= ((RexCall) oldProjectChildExp).getOperands();
			
			for (int j = 0; j < oldOperands.size(); j++) {
				RexNode oldOperand = oldOperands.get(j);
				if(oldOperand instanceof RexInputRef){
					refScanIndex = ((RexInputRef) oldOperand).getIndex();
					break;
				}
			}
			
		} else if(oldProjectChildExp instanceof RexInputRef){
			
			refScanIndex = ((RexInputRef) oldProjectChildExp).getIndex();
		} else{
			
			logger.error("unknow type");
			//throw new Exception("unknow type");
		}
		return refScanIndex;
	}


	private RexCall cloneRexCallWithDifferentIndex(RexCall rexCall, int index, RelDataType dataType) {
		
		dataType = dataType != null ? dataType : rexCall.getType();
		
		RexNode operandNode = new RexInputRef(index, dataType);
		List<RexNode> operandsExps = new ArrayList<RexNode>();
		operandsExps.add(operandNode);
		for (int j = 1; j < rexCall.getOperands().size(); j++) {
			operandsExps.add(rexCall.getOperands().get(j));
		}

		return (RexCall) rBuilder.makeCall(dataType, rexCall.getOperator(), operandsExps);
	}


  //replace the no.i element with the newElement
	private void replace(List projectChildExps, int i, Object newElement) {
		projectChildExps.remove(i);
		projectChildExps.add(i, newElement);
	}

}
