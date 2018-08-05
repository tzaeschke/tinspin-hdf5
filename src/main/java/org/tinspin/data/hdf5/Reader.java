/*
 * Copyright 2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package org.tinspin.data.hdf5;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

import org.tinspin.data.hdf5.HDF5BlockSNOD.SymbolTableEntry;
import org.tinspin.data.hdf5.HDF5BlockTREE.HDF5Entry;

public class Reader {

	//File source:
	//https://github.com/erikbern/ann-benchmarks/blob/master/README.md
	
	private static final String FILE = "D:\\data\\HDF5\\fashion-mnist-784-euclidean.hdf5";
	//static char L = '\n';
	static String L = "   ";
	
	private final MappedByteBuffer bb;
	private int sLen;
	private int sOffs;
	
	
	public static void main(String[] args) {
		String fileName; 
		if (args.length == 0) {
			fileName = FILE;
		} else {
			fileName = args[0];
		}
		
		Path path = Paths.get(fileName);
		
		try (FileChannel fileChannel = (FileChannel) Files.newByteChannel(
		  path, EnumSet.of(StandardOpenOption.READ))) {
		  
		    MappedByteBuffer mappedByteBuffer = fileChannel
		      .map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
		 
		    mappedByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		    
		    if (mappedByteBuffer != null) {
		    	Reader reader = new Reader(mappedByteBuffer);
		    	reader.readHeaderSB();
		    } else {
		    	
		    }
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	//									\211 H D F \r \n \032 \n
	private static long SB_FF_HEADER = 0x89_48_44_46_0d_0a_1a_0aL;
	
	//										TREE
	private static final int BLOCK_ID_TREE = 0x54_52_45_45;
	//										HEAP
	private static final int BLOCK_ID_HEAP = 0x48_45_41_50;
	//										SNOD
	private static final int BLOCK_ID_SNOD = 0x53_4E_4F_44;
	//										GCOL
	private static final int BLOCK_ID_GCOL = 0x47_43_4F_4C;
	
	private Reader(MappedByteBuffer bb) {
		this.bb = bb;
	}
	
	private void readHeaderSB() {
		int headerPos = 0;
		long header1;
		while ((header1 = Long.reverseBytes(bb.getLong())) != SB_FF_HEADER) {
			if (headerPos > 10000) {
				throw new IllegalStateException("Header not found until pos: " + headerPos);
			}
			headerPos += 512;		
			bb.position(headerPos);
		}
		
		HDF5BlockSBHeader sb = new HDF5BlockSBHeader(headerPos);
		sb.b8sbVersion = bb.get();
		if (sb.b8sbVersion != 0 && sb.b8sbVersion != 1) {
			throw new IllegalStateException("Superblock version not supported: " + sb.b8sbVersion);
		}
		sb.b9ffssVersion = bb.get();
		sb.b10rgsteVersion = bb.get();
		sb.b11zero = bb.get();
		sb.b12nshmfVersion = bb.get();
		sb.b13sizeOfOffsets = bb.get();
		int sOffs = sb.b13sizeOfOffsets;
		sb.b14sizeOfLength = bb.get();
		int sLen = sb.b14sizeOfLength;
		sb.b15zero = bb.get();
		sb.s16gLeafNodeK = read2(bb);
		sb.s18gIntNodeK = read2(bb);
		sb.i20fcf = read4(bb);
		
		if (sb.b8sbVersion >= 1) {
			sb.s24isIntNodeK = read2(bb);
			sb.s26zero = read2(bb);
		}
		
		sb.l28baseAddrO = getNBytes(bb, sOffs);
		sb.l36addrFFSIO = getNBytes(bb, sOffs);
		sb.l44eofAddrO = getNBytes(bb, sOffs);
		sb.l52dibAddrO = getNBytes(bb, sOffs);
		
		if (sb.b8sbVersion >= 1) {
			assertPosition(60);
		} else {
			assertPosition(56);
		}
		
		log(sb.toString());

		
		sb.rootGroupSymbolTableEntry = readSymbolTableEntry(bb, sLen, sOffs);
		//sb.i60rootGSTE = read4(bb);
		sb.rootGroupSymbolTableEntryHEADER = readDOHeaderPrefix(bb, sb.rootGroupSymbolTableEntry.l8ObjectHeaderAddressO);
				
		while (bb.position() < 2_000) {
			int ii = Integer.reverseBytes(bb.getInt());  //Not reversed!!!! TODO?
			switch (ii) {
			case BLOCK_ID_TREE:
				readTREE(bb, sOffs);
				break;
			case BLOCK_ID_HEAP:
				readHEAP(bb, sLen, sOffs);
				break;
			case BLOCK_ID_SNOD:
				readSNOD(bb, sLen, sOffs, sb);
				break;
			case BLOCK_ID_GCOL:
				readGCOL(bb, sLen, sOffs);
				break;
			default:
				if (ii != 0) {
					System.out.println(bb.position()-4 + " INT: " + Integer.reverseBytes(ii)
					 + " S: " + Short.reverseBytes((short) (ii >> 16)) + " " + Short.reverseBytes((short) (ii & 0xffff)));
					log(bb.position()-4, (byte)((ii>>24) & 0xFF));
					log(bb.position()-3, (byte)((ii>>16) & 0xFF));
					log(bb.position()-2, (byte)((ii>>8) & 0xFF));
					log(bb.position()-1, (byte)((ii) & 0xFF));
				}
			}
		}
		
		
		for (int i = 0; i < 200; i++) {
			log(bb.position(), bb.get());
		}
	}


	private DOHeaderPrefix readDOHeaderPrefix(MappedByteBuffer bb, int offset) {
		int pos = bb.position();
		DOHeaderPrefix h = new DOHeaderPrefix(offset);
		h.b0Version = bb.get();
		h.b1Zero = bb.get();
		h.s2TotalNumMsg = read2(bb);
		h.i4ObjRefCount = read4(bb);
		h.i8ObjHeaderSize = read4(bb);

		log(h.toString());

		//TODO for the header, thera are only 3 messages, even though it claims to be 4????
		
		h.messages = new DOHeaderMessage[h.s2TotalNumMsg];
		for (int i = 0; i < h.s2TotalNumMsg; i++) {
			h.messages[i] = readDOHeaderMessage(bb);
		}
				
		bb.position(pos);
		return h;
	}

	private DOHeaderMessage readDOHeaderMessage(MappedByteBuffer bb) {
		DOHeaderMessage h = new DOHeaderMessage(bb.position());
		h.s12HeaderMsgType = read2(bb);
		h.s14SizeHeaderMsgData = read2(bb);
		h.b16HeaderMsgFlags = read1(bb);
		h.b17Zero = read1(bb);
		h.b18Zero = read1(bb);
		h.b19Zero = read1(bb);

		//TODO read h.data
		
		log(h.toString());
		return h;
	}

	private static HDF5BlockTREE readTREE(MappedByteBuffer bb, int sOffs) {
		HDF5BlockTREE n = new HDF5BlockTREE(bb.position()-4);
		int type = n.b4nodeType = bb.get();
		n.b5nodeLevel = bb.get();
		int nEntries = n.s6entriesUsed = read2(bb);
		n.l8addreLeftSiblO = getNBytes(bb, sOffs);
		n.l16addreRightSiblO = getNBytes(bb, sOffs);
		
		log(n.toString());

		if (type == 0) {
			n.entries = new HDF5Entry[nEntries];
			
			for (int i = 0; i < nEntries; i++) {
				n.entries[i] = readTreeEntry(bb, sOffs);
			}
		} else if (type == 1) {
		}		
		return n;
	}


	private static HDF5Entry readTreeEntry(MappedByteBuffer bb, int sOffs) {
		// TODO Auto-generated method stub
		//throw new UnsupportedOperationException();
		return null;
	}

	private static HDF5BlockHEAP readHEAP(MappedByteBuffer bb, int sLen, int sOffs) {
		HDF5BlockHEAP n = new HDF5BlockHEAP(bb.position()-4);
		n.b4Version = bb.get();
		n.b5Zero = bb.get();
		n.b6Zero = bb.get();
		n.b7Zero = bb.get();
		n.l8dataSegmentSize = getNBytes(bb, sLen);
		n.l16freeListOffset = getNBytes(bb, sLen);
		n.l24dataSegementOffset = getNBytes(bb, sOffs);
		
		log(n.toString());

		return n;
	}


	private static HDF5BlockSNOD readSNOD(MappedByteBuffer bb, int sLen, int sOffs, 
			HDF5BlockSBHeader sb) {
		HDF5BlockSNOD n = new HDF5BlockSNOD(bb.position()-4);
		n.b4Version = bb.get();
		n.b5Zero = bb.get();
		n.s6NumberOfUsedEntries = read2(bb);
		n.symbols = new HDF5BlockSNOD.SymbolTableEntry[n.s6NumberOfUsedEntries];
		
		log(n.toString());

		for (int i = 0; i < n.s6NumberOfUsedEntries; i++) {
			SymbolTableEntry e = readSymbolTableEntry(bb, sLen, sOffs);
			n.symbols[i] = e;
		}
		
		//Symbol Table Entries = 2K
		int nEntries = 2*sb.getGroupLeafNodeK() - n.s6NumberOfUsedEntries;
		
		skip(bb, nEntries * SymbolTableEntry.SIZE);
		
		return n;
	}

	private static SymbolTableEntry readSymbolTableEntry(MappedByteBuffer bb, int sLen, int sOffs) {
		SymbolTableEntry e = new SymbolTableEntry(bb.position());
		e.l0LinkNameOffsetO = getNBytes(bb, sOffs);
		e.l8ObjectHeaderAddressO = (int) getNBytes(bb, sOffs);
		e.i16CachedType = read4(bb);
		e.i20Zero = read4(bb);

		//TODO read LinkName... 
		//   at position (e.l8ObjectHeaderAddressO + e.l0LinkNameOffsetO) ?
		// or is the 'localHeap' the HEAP from position 680 -> Would fit with Text!
		
		//16 bytes scratch space
		int pos = bb.position();
		
		switch ((int)e.i16CachedType) {
		case 0:
			break;
		case 1:
			e.l24ct1addressBTreeO = getNBytes(bb, sOffs);
			e.l32ct1addressNameHeapO = getNBytes(bb, sOffs);
			break;
		case 2:
			e.i24ct2offsetToLink = read4(bb);
			break;

		default:
			throw new IllegalArgumentException("Illegal cached type: " + e.i16CachedType);
		}

		skipTo(bb, pos + 16);
		
		log(e.toString());
		return e;
	}

	private static String readLinkName(MappedByteBuffer bb, int pos) {
		StringBuilder sb = new StringBuilder();
		char c = bb.getChar(pos);
		while (c != 0) {
			sb.append(c);
			c = bb.getChar(++pos);
		}
		return sb.toString();
	}
	
	private static HDF5BlockGCOL readGCOL(MappedByteBuffer bb, int sLen, int sOffs) {
		HDF5BlockGCOL n = new HDF5BlockGCOL(bb.position()-4);
		n.b4Version = bb.get();
		n.b5Zero = bb.get();
		n.b6Zero = bb.get();
		n.b7Zero = bb.get();
		n.i8CollectionSizeL = getNBytes(bb, sLen);
		
		log(n.toString());

		//entire collection size (including the size itself!)
		int nObjects = (int) ((n.i8CollectionSizeL-sLen) / HDF5BlockGCOL.GlobalHeapObject.SIZE);
		n.objects = new HDF5BlockGCOL.GlobalHeapObject[nObjects];
		for (int i = 0; i < nObjects; i++) {
			HDF5BlockGCOL.GlobalHeapObject o = new HDF5BlockGCOL.GlobalHeapObject();
			n.objects[i] = o;
			o.s0Index = read2(bb);
			o.s2ReferenceCount = read2(bb);
			o.i4Zero = read4(bb);
			o.i8ObjectSize = getNBytes(bb, sLen);
			int oSize = roundUp8((int)o.i8ObjectSize);
			System.out.println("GCOL-Obj(" + (i+1) + "):");
			System.out.println(o.toString() + "/" +oSize );
			o.data = new byte[oSize];
			bb.get(o.data);
			if (o.s0Index == 0) {
				break;
			}
		}
		
		return n;
	}



	private static long getNBytes(MappedByteBuffer bb, int nBytes) {
		switch (nBytes) {
		case 1:
			return bb.get();
		case 2: 
			return read2(bb);
		case 4:
			return read4(bb);
		case 8:
			return read8(bb);
		default:
			throw new UnsupportedOperationException("nBytes = " + nBytes);
		}
	}

	private static byte read1(MappedByteBuffer bb) {
		return bb.get();
	}

	private static short read2(MappedByteBuffer bb) {
		//return Short.reverseBytes(bb.getShort());
		return bb.getShort();
	}
	
	private static int read4(MappedByteBuffer bb) {
		//return Integer.reverseBytes(bb.getInt());
		return bb.getInt();
	}
	
	private static long read8(MappedByteBuffer bb) {
		//return Long.reverseBytes(bb.getLong());
		return bb.getLong();
	}
	
	private static void skip(MappedByteBuffer bb, int bytesToSkip) {
		bb.position(bb.position() + bytesToSkip);
	}
	
	private static void skipTo(MappedByteBuffer bb, int position) {
		bb.position(position);
	}

	private static int roundUp8(int n) {
	    return (n + 7) / 8 * 8;
	}
	
	private void assertPosition(int pos) {
		if (bb.position() != pos) {
			throw new IllegalStateException("pos=" + bb.position());
		}
	}
	
	private static void log(int pos, byte b) {
		char c = b >= 32 ? (char)b : ' ';
		int i = b & 0xff;
		System.out.println(pos + ": " + Integer.toHexString(i) + " " + i + " " + c);
	}

	private static void log(char marker, int pos, byte b) {
		char c = b >= 32 ? (char)b : ' ';
		int i = b & 0xff;
		System.out.println(pos + "" + marker + ": " + Integer.toHexString(i) + " " + i + " " + c);
	}

	private static void log(String str) {
		System.out.println(str);
	}
}
