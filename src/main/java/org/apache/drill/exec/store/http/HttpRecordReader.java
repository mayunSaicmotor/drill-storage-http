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
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.drill.common.exceptions.ExecutionSetupException;
import org.apache.drill.common.exceptions.UserException;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.common.types.TypeProtos.MajorType;
import org.apache.drill.common.types.TypeProtos.MinorType;
import org.apache.drill.common.types.Types;
import org.apache.drill.exec.exception.SchemaChangeException;
import org.apache.drill.exec.expr.TypeHelper;
import org.apache.drill.exec.ops.FragmentContext;
import org.apache.drill.exec.ops.OperatorContext;
import org.apache.drill.exec.physical.impl.OutputMutator;
import org.apache.drill.exec.record.MaterializedField;
import org.apache.drill.exec.store.AbstractRecordReader;
import org.apache.drill.exec.store.http.util.JsonConverter;
import org.apache.drill.exec.store.http.util.SimpleHttp;
import org.apache.drill.exec.vector.NullableBigIntVector;
import org.apache.drill.exec.vector.NullableBitVector;
import org.apache.drill.exec.vector.NullableDateVector;
import org.apache.drill.exec.vector.NullableFloat4Vector;
import org.apache.drill.exec.vector.NullableFloat8Vector;
import org.apache.drill.exec.vector.NullableIntVector;
import org.apache.drill.exec.vector.NullableTimeStampVector;
import org.apache.drill.exec.vector.NullableTimeVector;
import org.apache.drill.exec.vector.NullableVarBinaryVector;
import org.apache.drill.exec.vector.NullableVarCharVector;
import org.apache.drill.exec.vector.ValueVector;
import org.apache.drill.exec.vector.complex.fn.JsonReader;
import org.apache.drill.exec.vector.complex.impl.VectorContainerWriter;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class HttpRecordReader extends AbstractRecordReader {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HttpRecordReader.class);

  private VectorContainerWriter writer;
  private JsonReader jsonReader;
  private FragmentContext fragmentContext;
  private HttpSubScanSpec scanSpec;
  private Iterator<JsonNode> jsonIt;

  private ImmutableList<ValueVector> vectors;
  private ImmutableList<Copier<?>> copiers;
  
  //private List<String> cols = Lists.newArrayList();
  
  ImmutableList.Builder<ValueVector> vectorBuilder = ImmutableList.builder();
  ImmutableList.Builder<Copier<?>> copierBuilder = ImmutableList.builder();
  

  public HttpRecordReader(FragmentContext context, HttpSubScanSpec scanSpec) {
    this.scanSpec = scanSpec;
    fragmentContext = context;
    Set<SchemaPath> transformed = Sets.newLinkedHashSet();
    transformed.add(STAR_COLUMN);
    setColumns(transformed);
  }

  @Override
  public void setup(OperatorContext context, OutputMutator output)
      throws ExecutionSetupException {
    logger.debug("HttpRecordReader setup, query {}", scanSpec.getFullURL());
    
    try{
    	logger.info("subScanSpec.getSQL()"+this.scanSpec.getSQL());
	    this.writer = new VectorContainerWriter(output);
	    this.jsonReader = new JsonReader(fragmentContext.getManagedBuffer(),
	      Lists.newArrayList(getColumns()), true, false, true);
	  
	    //TODO
/*	    cols.add("firstName");
	    cols.add("lastName");
	    cols.add("code");
	    cols.add("code_count");
	    cols.add("score");
	    cols.add("score_count");	*/    
	    List<SchemaPath> columns  = scanSpec.getColumns();
	    for(int i = 0 ; i < columns.size(); i++){
	    	
	    	String col = columns.get(i).getAsUnescapedPath();
		    MinorType minorType = col.endsWith("_count")? MinorType.BIGINT : MinorType.VARCHAR;
		     MajorType type = Types.optional(minorType);
		     MaterializedField field = MaterializedField.create(col, type);
		     Class<? extends ValueVector> clazz = (Class<? extends ValueVector>) TypeHelper.getValueVectorClass(
		        minorType, type.getMode());
		    ValueVector vector = output.addField(field, clazz);
		    vectorBuilder.add(vector);
		    copierBuilder.add(getCopier(i,col, null, vector));
	    }
	   /* 		
	    MinorType minorType = MinorType.VARCHAR;
	     MajorType type = Types.optional(minorType);
	     MaterializedField field = MaterializedField.create("firstName", type);
	     Class<? extends ValueVector> clazz = (Class<? extends ValueVector>) TypeHelper.getValueVectorClass(
	        minorType, type.getMode());
	    ValueVector vector = output.addField(field, clazz);
	    vectorBuilder.add(vector);
	    copierBuilder.add(getCopier(0, null, vector));
	    
	     minorType = MinorType.VARCHAR;
	     type = Types.optional(minorType);
	    field = MaterializedField.create("lastName", type);
	    clazz = (Class<? extends ValueVector>) TypeHelper.getValueVectorClass(
	        minorType, type.getMode());
	     vector = output.addField(field, clazz);
	    vectorBuilder.add(vector);
	    copierBuilder.add(getCopier(1, null, vector));
	    
	    
	     minorType = MinorType.VARCHAR;
	     type = Types.optional(minorType);
	    field = MaterializedField.create("code", type);
	    clazz = (Class<? extends ValueVector>) TypeHelper.getValueVectorClass(
	        minorType, type.getMode());
	     vector = output.addField(field, clazz);
	    vectorBuilder.add(vector);
	    copierBuilder.add(getCopier(2, null, vector));
	    
	     minorType = MinorType.BIGINT;
	     type = Types.optional(minorType);
	    field = MaterializedField.create("code_count", type);
	    clazz = (Class<? extends ValueVector>) TypeHelper.getValueVectorClass(
	        minorType, type.getMode());
	     vector = output.addField(field, clazz);
	    vectorBuilder.add(vector);
	    copierBuilder.add(getCopier(3, null, vector));*/
	    
	      vectors = vectorBuilder.build();
	      copiers = copierBuilder.build();
	      
	  } catch (SchemaChangeException e) {
	      throw UserException.dataReadError(e)
	          .message("The JDBC storage plugin failed while trying setup the SQL query. ")
	          .build(logger);
	    }
    
    String q = scanSpec.getFullURL();
    if (q.startsWith("file://")) {
      loadFile();
    } else {
      loadHttp();
    }
  }

  private void loadHttp() {
    String url = scanSpec.getFullURL();
    SimpleHttp http = new SimpleHttp();
    String content = http.get(url);
    logger.debug("info '{}' response {} bytes", url, content.length());
    parseResult(content);
  }

  private void loadFile() {
    logger.debug("load local file {}", scanSpec.getTableName());
    String file = scanSpec.getTableName().substring("file://".length() - 1);
    String content = JsonConverter.stringFromFile(file);
    parseResult(content);
  }

  private void parseResult(String content) {
    String key = scanSpec.getResultKey();
    JsonNode root = (key== null || key.length() == 0)? JsonConverter.parse(content) : JsonConverter.parse(content, key);
    if (root != null) {
      logger.debug("response object count {}", root.size());
      jsonIt = root.elements();
    }
  }

  @Override
  public int next() {
	  
	    if (jsonIt == null || !jsonIt.hasNext()) {
	        return 0;
	      }
	    
	    int counter = 0;

	    try {
	      while (counter < 4095 && jsonIt.hasNext()) { // loop at 4095 since nullables use one more than record count and we
	                                            // allocate on powers of two.
	    	  JsonNode node = jsonIt.next();
	
	        for (Copier<?> c : copiers) {
	          c.copy(counter, node);
	        }
	        counter++;
	      }
	    } catch (SQLException e) {
	      throw UserException
	          .dataReadError(e)
	          .message("Failure while attempting to read from database.")
	          .build(logger);
	    }

	    for (ValueVector vv : vectors) {
	      vv.getMutator().setValueCount(counter > 0 ? counter : 0);
	    }

	    return counter>0 ? counter : 0;
	    
   /* logger.debug("HttpRecordReader next");
    if (jsonIt == null || !jsonIt.hasNext()) {
      return 0;
    }
    writer.allocate();
    writer.reset();
    int docCount = 0;
    try {
      while (docCount < BaseValueVector.INITIAL_VALUE_ALLOCATION && jsonIt.hasNext()) {
        JsonNode node = jsonIt.next();
        jsonReader.setSource(node.toString().getBytes(Charsets.UTF_8));
        writer.setPosition(docCount);
        jsonReader.write(writer);
        docCount ++;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    writer.setValueCount(docCount);
    return docCount;*/
  }
 
  /*  @Override
  public void cleanup() {
    logger.debug("HttpRecordReader cleanup");
  }*/

@Override
public void close() throws Exception {
	// TODO Auto-generated method stub
	
}

  private Copier<?> getCopier(int columnIndex, String columnName ,ResultSet result,  ValueVector v) {

	    if (v instanceof NullableBigIntVector) {
	      return new BigIntCopier(columnIndex, columnName, result,  (NullableBigIntVector.Mutator) v.getMutator());
	    } else if (v instanceof NullableFloat4Vector) {
	      return new Float4Copier(columnIndex, columnName, result,  (NullableFloat4Vector.Mutator) v.getMutator());
	    } else if (v instanceof NullableFloat8Vector) {
	      return new Float8Copier(columnIndex, columnName, result,  (NullableFloat8Vector.Mutator) v.getMutator());
	    } else if (v instanceof NullableIntVector) {
	      return new IntCopier(columnIndex, columnName, result,  (NullableIntVector.Mutator) v.getMutator());
	    } else if (v instanceof NullableVarCharVector) {
	      return new VarCharCopier(columnIndex, columnName, result,  (NullableVarCharVector.Mutator) v.getMutator());
	    } else if (v instanceof NullableVarBinaryVector) {
	      return new VarBinaryCopier(columnIndex, columnName, result,  (NullableVarBinaryVector.Mutator) v.getMutator());
	    } else if (v instanceof NullableDateVector) {
	      return new DateCopier(columnIndex, columnName, result,  (NullableDateVector.Mutator) v.getMutator());
	    } else if (v instanceof NullableTimeVector) {
	      return new TimeCopier(columnIndex, columnName, result,  (NullableTimeVector.Mutator) v.getMutator());
	    } else if (v instanceof NullableTimeStampVector) {
	      return new TimeStampCopier(columnIndex, columnName, result,  (NullableTimeStampVector.Mutator) v.getMutator());
	    } else if (v instanceof NullableBitVector) {
	      return new BitCopier(columnIndex, columnName, result,  (NullableBitVector.Mutator) v.getMutator());
	    }

	    throw new IllegalArgumentException("Unknown how to handle vector.");
	  }


  private abstract class Copier<T extends ValueVector.Mutator> {
    protected final int columnIndex;
    protected final String columnName;
    protected final ResultSet result;
    protected final T mutator;

    public Copier(int columnIndex, String columnName,ResultSet result,  T mutator) {
      super();
      this.columnIndex = columnIndex;
      this.columnName = columnName;
      this.result = result;
      this.mutator = mutator;
    }

    abstract void copy(int index, JsonNode node) throws SQLException;
  }

  private class IntCopier extends Copier<NullableIntVector.Mutator> {
    public IntCopier(int columnIndex, String columnName ,ResultSet result,  NullableIntVector.Mutator mutator) {
      super(columnIndex, columnName,result, mutator);
    }

    @Override
    void copy(int index, JsonNode node) throws SQLException {
      mutator.setSafe(index, result.getInt(columnIndex));
      if (result.wasNull()) {
        mutator.setNull(index);
      }
    }
  }

  private class BigIntCopier extends Copier<NullableBigIntVector.Mutator> {
    public BigIntCopier(int columnIndex, String columnName ,ResultSet result,  NullableBigIntVector.Mutator mutator) {
      super(columnIndex, columnName, result, mutator);
    }

    @Override
    void copy(int index, JsonNode node) throws SQLException {
      //mutator.setSafe(index, result.getLong(columnIndex));
      mutator.setSafe(index, node.get(columnName).asLong());
/*      if (result.wasNull()) {
        mutator.setNull(index);
      }*/
    }

  }

  private class Float4Copier extends Copier<NullableFloat4Vector.Mutator> {

    public Float4Copier(int columnIndex, String columnName,ResultSet result,  NullableFloat4Vector.Mutator mutator) {
      super(columnIndex, columnName, result, mutator);
    }

    @Override
    void copy(int index, JsonNode node) throws SQLException {
      mutator.setSafe(index, result.getFloat(columnIndex));
      if (result.wasNull()) {
        mutator.setNull(index);
      }
    }

  }


  private class Float8Copier extends Copier<NullableFloat8Vector.Mutator> {

    public Float8Copier(int columnIndex, String columnName,ResultSet result,  NullableFloat8Vector.Mutator mutator) {
      super(columnIndex, columnName, result, mutator);
    }

    @Override
    void copy(int index, JsonNode node) throws SQLException {
      mutator.setSafe(index, result.getDouble(columnIndex));
      if (result.wasNull()) {
        mutator.setNull(index);
      }

    }

  }

  private class DecimalCopier extends Copier<NullableFloat8Vector.Mutator> {

    public DecimalCopier(int columnIndex, String columnName,ResultSet result,  NullableFloat8Vector.Mutator mutator) {
      super(columnIndex, columnName, result, mutator);
    }

    @Override
    void copy(int index, JsonNode node) throws SQLException {
      BigDecimal decimal = result.getBigDecimal(columnIndex);
      if (decimal != null) {
        mutator.setSafe(index, decimal.doubleValue());
      }
    }

  }

  private class VarCharCopier extends Copier<NullableVarCharVector.Mutator> {

    public VarCharCopier(int columnIndex, String columnName,ResultSet result,  NullableVarCharVector.Mutator mutator) {
      super(columnIndex, columnName, result, mutator);
    }

    @Override
    void copy(int index, JsonNode node) throws SQLException {
      //String val = result.getString(columnIndex);
      String val = node.get(columnName).asText();
      if (val != null) {
        byte[] record = val.getBytes(Charsets.UTF_8);
        mutator.setSafe(index, record, 0, record.length);
      }
    }

  }

  private class VarBinaryCopier extends Copier<NullableVarBinaryVector.Mutator> {

    public VarBinaryCopier(int columnIndex, String columnName,ResultSet result,  NullableVarBinaryVector.Mutator mutator) {
      super(columnIndex, columnName, result, mutator);
    }

    @Override
    void copy(int index, JsonNode node) throws SQLException {
      byte[] record = result.getBytes(columnIndex);
      if (record != null) {
        mutator.setSafe(index, record, 0, record.length);
      }
    }

  }

  private class DateCopier extends Copier<NullableDateVector.Mutator> {

    private final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    public DateCopier(int columnIndex, String columnName,ResultSet result,  NullableDateVector.Mutator mutator) {
      super(columnIndex, columnName, result, mutator);
    }

    @Override
    void copy(int index, JsonNode node) throws SQLException {
      Date date = result.getDate(columnIndex, calendar);
      if (date != null) {
        mutator.setSafe(index, date.getTime());
      }
    }

  }

  private class TimeCopier extends Copier<NullableTimeVector.Mutator> {

    private final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    public TimeCopier(int columnIndex, String columnName,ResultSet result,  NullableTimeVector.Mutator mutator) {
      super(columnIndex, columnName, result, mutator);
    }

    @Override
    void copy(int index, JsonNode node) throws SQLException {
      Time time = result.getTime(columnIndex, calendar);
      if (time != null) {
        mutator.setSafe(index, (int) time.getTime());
      }

    }

  }


  private class TimeStampCopier extends Copier<NullableTimeStampVector.Mutator> {

    private final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    public TimeStampCopier(int columnIndex, String columnName,ResultSet result,  NullableTimeStampVector.Mutator mutator) {
      super(columnIndex, columnName, result, mutator);
    }

    @Override
    void copy(int index, JsonNode node) throws SQLException {
      Timestamp stamp = result.getTimestamp(columnIndex, calendar);
      if (stamp != null) {
        mutator.setSafe(index, stamp.getTime());
      }

    }

  }

  private class BitCopier extends Copier<NullableBitVector.Mutator> {

    public BitCopier(int columnIndex, String columnName,ResultSet result,  NullableBitVector.Mutator mutator) {
      super(columnIndex, columnName, result, mutator);
    }

    @Override
    void copy(int index, JsonNode node) throws SQLException {
      mutator.setSafe(index, result.getBoolean(columnIndex) ? 1 : 0);
      if (result.wasNull()) {
        mutator.setNull(index);
      }
    }

  }



}
