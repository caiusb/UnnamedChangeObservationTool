package edu.illinois.testt;

import java.util.LinkedList;
import java.util.List;

public class BaseClassRenamed5 {

	public void method0(){
		int myInteger=5;
		long myLong= 1000;
		List<String> myStringList = new LinkedList<String>();
		String elementString = String.valueOf(myInteger) + myLong;
		myStringList.add(elementString);
		System.out.println("Element4=" + myStringList.get(0));
	}
	
}