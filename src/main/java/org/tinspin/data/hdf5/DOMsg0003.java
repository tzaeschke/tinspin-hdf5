/*
 * Copyright 2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package org.tinspin.data.hdf5;

import java.util.Arrays;

/**
 * 0x0003
 * Datatype message.
 * 
 * @author Tilmann Zäschke
 *
 */
public class DOMsg0003 extends DOMsg {

	
	//Class and Version
	int b0Version;
	//Class Bit Field, Bits 0-7
	int b1Bits7;
	//Class Bit Field, Bits 8-15 	
	int b2Bits15;
	//Class Bit Field, Bits 16-23
	int b3Bits23;
	//Size
	int i4Size;

	//Properties
	byte[] properties;
	
	public DOMsg0003(int offset) {
		super(offset);
	}
	
	
	@Override
	public String toString() {
		return "DataTypeMessage: " + super.toString() + Reader.L +  
				"ClassVersion=" + b0Version + Reader.L +
				"Bits7=" + b1Bits7 + Reader.L +
				"Bits15=" + b2Bits15 + Reader.L +
				"Bits23=" + b3Bits23 + Reader.L +
				"Size=" + i4Size + Reader.L +
				"Properties=" + Arrays.toString(properties);
	}

}
