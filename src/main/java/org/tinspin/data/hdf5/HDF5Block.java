/*
 * Copyright 2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package org.tinspin.data.hdf5;

public class HDF5Block {

	private final int offset;
	
	public HDF5Block(int offset) {
		this.offset = offset;
	}
	
	int getOffset() {
		return offset;
	}
	
}
