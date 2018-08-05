/*
 * Copyright 2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package org.tinspin.data.hdf5;

public class DOHeaderPrefix extends HDF5Block {
	
	//Version / 1
	int b0Version;
	//Reserved (zero) / 1
	int b1Zero;
	//Total Number of Header Messages / 2
	int s2TotalNumMsg;
	//Object Reference Count / 4
	int i4ObjRefCount;
	// Object Header Size / 4
	int i8ObjHeaderSize;
	
	DOHeaderMessage[] messages;
	
	public DOHeaderPrefix(int offset) {
		super(offset);
	}
	
	
	@Override
	public String toString() {
		return "DOHeaderPrefix(" + getOffset() + ")" + Reader.L + 
				//Version
				"Version=" + b0Version + Reader.L +
				//Reserved (zero)
				//"" + b1Zero + L +

				"TotalNumMsg=" + s2TotalNumMsg + Reader.L +
				"ObjRefCount=" + i4ObjRefCount + Reader.L +
				"ObjHeaderSize=" + i8ObjHeaderSize;
	}
}
