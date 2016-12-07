package org.apache.drill.exec.store.http;

public class FilterOperator {

	String field;
	String operator;
	Object value;
	String booleanOperator;

	FilterOperator(){	
	};
	FilterOperator(String field, String operator, Object value) {
		this.field = field;
		this.operator = operator;
		this.value = value;
	}
	
	
	public String getBooleanOperator() {
		return booleanOperator;
	}
	public void setBooleanOperator(String booleanOperator) {
		this.booleanOperator = booleanOperator;
	}
	public String getField() {
		return field;
	}
	public void setField(String field) {
		this.field = field;
	}
	public String getOperator() {
		return operator;
	}
	public void setOperator(String operator) {
		this.operator = operator;
	}
	public Object getValue() {
		return value;
	}
	public void setValue(Object value) {
		this.value = value;
	}
	@Override
	public String toString(){
		
		StringBuilder sb = new StringBuilder(field);
		sb.append(operator).append("'"+value+"'");
		
		return sb.toString();
		
	}
	
}
