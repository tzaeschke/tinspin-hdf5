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
	Reader.MSG s12HeaderMsgType;
	int s12HeaderMsgTypeId;
	
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
	
	
	static DOMsg create(Reader.MSG type, int pos) {
		switch (type) {
		case MSG_0000_NIL:
			return new DOMsg0000(pos);
		case MSG_0001_DATA_SPACE:
			return new DOMsg0001(pos);
		case MSG_0003_DATA_TYPE:
			return new DOMsg0003(pos);
		case MSG_0005_FILL_VALUE:
			return new DOMsg0005(pos);
		case MSG_0008_DATA_LAYOUT:
			return new DOMsg0008(pos);
		case MSG_000C_ATTRIBUTE:
			return new DOMsg000C(pos);
		case MSG_0010_CONTINUATION:
			return new DOMsg0010(pos);
		case MSG_0011_SYMBOL_TABLE:
			return new DOMsg0011(pos);
		case MSG_0012_OBJ_MOD_TIME:
			return new DOMsg0012(pos);

		default:
			//return new DOMsg(pos);
			throw new UnsupportedOperationException("Message type: 0x" + type.type());
		}
	}
	
	
	public DOMsg(int offset, Reader.MSG type) {
		super(offset, Reader.NO_VERSION);
		this.s12HeaderMsgType = type;
		this.s12HeaderMsgTypeId = type.type();
	}
	
	
	@Override
	public String toString() {
		return "DOMsg(" + getOffset() + ")" + Reader.L + 
				"HeaderMsgType=0x" + Integer.toHexString(s12HeaderMsgType.type()) + Reader.L +
				"SizeHeaderMsgData=" + s14SizeHeaderMsgData + Reader.L +
				"HeaderMsgFlags=0b" + Integer.toBinaryString(b16HeaderMsgFlags) + Reader.L + 
				"data=" + Arrays.toString(b20data);// + Reader.L +

				//Reserved (zero)
//				"" + b17Zero + Reader.L +
//				"" + b18Zero + Reader.L +
//				"" + b19Zero + Reader.L
	}
	

}
