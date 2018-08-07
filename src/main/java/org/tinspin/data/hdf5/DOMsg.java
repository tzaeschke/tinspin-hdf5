/*
 * Copyright 2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package org.tinspin.data.hdf5;

import java.util.Arrays;

public class DOMsg extends HDF5Block {
	
	//Header Message Type #1 /2
	int s12HeaderMsgType;
	
	//This value specifies the number of bytes of header message data following 
	//the header message type and length information for the current message. 
	//The size includes padding bytes to make the message a multiple of eight bytes. 
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
	
	
	static DOMsg create(short type, int pos) {
		switch (type) {
		case 0:
			return new DOMsg(pos);
		case 0x0001:
			return new DOMsg0001(pos);
		case 0x0003:
			return new DOMsg0003(pos);
		case 0x0005:
			return new DOMsg0005(pos);
		case 0x0008:
			return new DOMsg0008(pos);
		case 0x000C:
			return new DOMsg000C(pos);
		case 0x0010:
			return new DOMsg0010(pos);
		case 0x0011:
			return new DOMsg0011(pos);
		case 0x0012:
			return new DOMsg0012(pos);

		default:
			//return new DOMsg(pos);
			throw new UnsupportedOperationException("Message type: 0x" + Integer.toHexString(type));
		}
	}
	
	
	public DOMsg(int offset) {
		super(offset);
	}
	
	
	@Override
	public String toString() {
		return "DOMsg(" + getOffset() + ")" + Reader.L + 
				"HeaderMsgType=0x" + Integer.toHexString(s12HeaderMsgType) + Reader.L +
				"SizeHeaderMsgData=" + s14SizeHeaderMsgData + Reader.L +
				"HeaderMsgFlags=0b" + Integer.toBinaryString(b16HeaderMsgFlags) + Reader.L + 
				"data=" + Arrays.toString(b20data);// + Reader.L +

				//Reserved (zero)
//				"" + b17Zero + Reader.L +
//				"" + b18Zero + Reader.L +
//				"" + b19Zero + Reader.L
	}
	

}
