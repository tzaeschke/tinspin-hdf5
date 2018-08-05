/*
 * Copyright 2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package org.tinspin.data.hdf5;

class HDF5BlockHEAP extends HDF5Block {
	//Signature
	int i0Signature;
	//Version
	int b4Version;
	//Reserved (zero)
	int b5Zero;
	int b6Zero;
	int b7Zero;

	//Data Segment SizeL
	long l8dataSegmentSize;

	//Offset to Head of Free-listL
	long l16freeListOffset;

	//Address of Data SegmentO
	long l24dataSegementOffset;
	
	public HDF5BlockHEAP(int offset) {
		super(offset);
	}
	
	@Override
	public String toString() {
		return "HEAP(" + getOffset() + ")" + Reader.L + 
//				//Signature
//				"Signature=" + i0Signature + Reader.L +
				//Version
				"Version=" + b4Version + Reader.L +
				//Reserved (zero)
				//"" + b5Zero + L +
				//"" + b6Zero + L +
				//"" + b7Zero + L +

				//Data Segment SizeL
				"dataSegmentSize=" + l8dataSegmentSize + Reader.L +

				//Offset to Head of Free-listL
				"freeListOffset=" + l16freeListOffset + Reader.L +

				//Address of Data SegmentO
				"dataSegementOffset=" + l24dataSegementOffset;
	}
}