/*
 * Copyright 2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package org.tinspin.data.hdf5;

import java.util.Arrays;

public class DOHeaderMessage extends HDF5Block {
	
	//Header Message Type #1 /2
	int s12HeaderMsgType;
	//Size of Header Message Data #1 / 2
	int s14SizeHeaderMsgData;
	//Header Message #1 Flags / 1
	int b16HeaderMsgFlags;
	//Reserved (zero) /3
	byte b17Zero;
	byte b18Zero;
	byte b19Zero;
	
	//Header Message Data #1
	byte[] b20data;
	
	public DOHeaderMessage(int offset) {
		super(offset);
	}
	
	
	@Override
	public String toString() {
		return "DOHeaderMessage(" + getOffset() + ")" + Reader.L + 
				"HeaderMsgType=" + s12HeaderMsgType + Reader.L +
				"SizeHeaderMsgData=" + s14SizeHeaderMsgData + Reader.L +
				"HeaderMsgFlags=" + b16HeaderMsgFlags + "=" + Integer.toBinaryString(b16HeaderMsgFlags) + Reader.L + 
				"data=" + Arrays.toString(b20data);

				//Reserved (zero)
				//"" + b17Zero + L +
				//"" + b18Zero + L +
				//"" + b19Zero + L +
	}
}
