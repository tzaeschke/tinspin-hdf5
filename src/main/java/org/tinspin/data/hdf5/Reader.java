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

public class Reader {

	//File source:
	//https://github.com/erikbern/ann-benchmarks/blob/master/README.md
	
	private static final String FILE = "D:\\data\\HDF5\\fashion-mnist-784-euclidean.hdf5";
	//private static final String FILE = "D:\\data\\HDF5\\glove-25-angular.hdf5";
	//private static final String FILE = "D:\\data\\HDF5\\sift-128-euclidean.hdf5";
	
	//static char L = '\n';
	static String L = "   ";
	static String NL = "\n    ";
	
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
		    	//reader.search((short) 2144); //-> 892
		    	//reader.search((short) 800); //-> 120
		    	reader.readHeaderSB();
		    } else {
		    	
		    }
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void search(short x) {
		short x2 = Short.reverseBytes(x);
		for (int i = 9; i < 3000; i++) {
			short s = bb.getShort();
			if (s == x || s == x2) {
				System.out.println("Pos: " + (bb.position() - 2));
			}
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
	
	private static final short MSG_0001_DATA_SPACE = 0x0001;
	private static final short MSG_0003_DATA_TYPE = 0x0003;
	private static final short MSG_0010_CONTINUATION = 0x0010;
	
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
		//sb.rootGroupSymbolTableEntryHEADER = readDOHeaderPrefix(bb, sb.rootGroupSymbolTableEntry.l8ObjectHeaderAddressO);
		skipTo(bb, sb.rootGroupSymbolTableEntry.l8ObjectHeaderAddressO);
		sb.rootGroupSymbolTableEntryHEADER = readDOHeaderPrefix(bb, sb);
		
		//Not specified, but appears to be the case...
		alignPos8(bb);
				
		Group rootGroup = new Group();
		
		skipTo(bb, sb.rootGroupSymbolTableEntry.getOffsetTREE());
		readSignature(bb, BLOCK_ID_TREE);
		rootGroup.tree = readTREE(bb, sLen, sOffs, sb);

		skipTo(bb, sb.rootGroupSymbolTableEntry.getOffsetHEAP());
		readSignature(bb, BLOCK_ID_HEAP);
		rootGroup.heap = readHEAP(bb, sLen, sOffs);

		alignPos8(bb);
		
		readAny(bb, sb, 1_200);
		
		//read B-Tree SNOD
		skipTo(bb, (int) rootGroup.tree.childPointers[0]);
		readSignature(bb, BLOCK_ID_SNOD);
		HDF5BlockSNOD rootSNOD = readSNOD(bb, sLen, sOffs, sb);
		for (int i = 0; i < rootSNOD.symbols.length; i++) {
			SymbolTableEntry ste = rootSNOD.symbols[i];
			String name = rootGroup.heap.getLinkName(ste);
			log("Reading: " + name);
			skipTo(bb, (int) ste.l8ObjectHeaderAddressO);
			readDOHeaderPrefix(bb, sb);
		}
		
		
		bb.position(1800 + 256);
		
		readAny(bb, sb, 8200);
		
		for (int i = 0; i < 200; i++) {
			log(bb.position(), bb.get());
		}
	}


	private static void readAny(MappedByteBuffer bb, HDF5BlockSBHeader sb, int max) {
		int sLen = sb.getSizeLen();
		int sOffs = sb.getSizeOffset();
		while (bb.position() < max) {
			int ii = Integer.reverseBytes(bb.getInt());  //Not reversed!!!! TODO?
			switch (ii) {
			case BLOCK_ID_TREE:
				readTREE(bb, sLen, sOffs, sb);
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
	}
	
	private static int readSignature(MappedByteBuffer bb, int blockId) {
		int ii = Integer.reverseBytes(bb.getInt());  //Not reversed!!!! TODO?
		if (ii != blockId) {
			throw new IllegalStateException();
		}
		return ii;
	}

	private DOHeaderPrefix readDOHeaderPrefix(MappedByteBuffer bb, HDF5BlockSBHeader sb, int offset) {
		int pos = bb.position();
		bb.position(pos + offset);
		DOHeaderPrefix h = readDOHeaderPrefix(bb, sb);
		bb.position(pos);
		return h;
	}
	
	private DOHeaderPrefix readDOHeaderPrefix(MappedByteBuffer bb, HDF5BlockSBHeader sb) {
		//IV.A.1.a. Version 1 Data Object Header Prefix
		DOHeaderPrefix h = new DOHeaderPrefix(bb.position());
		h.b0Version = bb.get();
		h.b1Zero = bb.get();
		h.s2TotalNumMsg = read2(bb);
		h.i4ObjRefCount = read4(bb);
		h.i8ObjHeaderSize = read4(bb);

		log(h.toString());
		alignPos8(bb);

		//The message count may be too large, because it may contain "continuation messages" that are stored elsewhere (?) 
		int currentPos = bb.position();
		int maxPos = currentPos + h.i8ObjHeaderSize;
		
		h.messages = new DOMsg[h.s2TotalNumMsg];
		for (int i = 0; i < h.s2TotalNumMsg; i++) {
			DOMsg m = readDOHeaderMessage(bb, sb);
			h.messages[i] = m;
			if (m.s12HeaderMsgType == MSG_0010_CONTINUATION) {
				bb.position((int) ((DOMsg0010)m).offsetO);
				//TODO use lengthL
			}
		}
//		if (bb.position() >= maxPos) {
//			if (bb.position() > maxPos) {
//				//TODO?!?!?
//				throw new IllegalStateException("Exceeded data boundary for Object Headers");
		//for Continuation messages
				bb.position(maxPos);
//			}
//		}
		return h;
	}

	private DOMsg readDOHeaderMessage(MappedByteBuffer bb, HDF5BlockSBHeader sb) {
		//As defined in LOWER PART of 
		//IV.A.1.a. Version 1 Data Object Header Prefix
		short type = read2(bb);
		DOMsg h = DOMsg.create(type, bb.position() - 2);

		h.s12HeaderMsgType = type;
		h.s14SizeHeaderMsgData = read2(bb);
		int posStart = bb.position();
		int posEnd = posStart + h.s14SizeHeaderMsgData + 4;  
		h.b16HeaderMsgFlags = Byte.toUnsignedInt(read1(bb));
		h.b17Zero = read1(bb);
		h.b18Zero = read1(bb);
		h.b19Zero = read1(bb);
		
		readDOMessage(bb, sb, h);
		log(h.toString());

		int pos = bb.position();
		if (pos != posEnd) {
			new IllegalStateException("pos/size = " + pos + " / " + posEnd).printStackTrace();;
		}
		bb.position(posEnd); 

		return h;
	}

	private static DOMsg readDOMessage(MappedByteBuffer bb, HDF5BlockSBHeader sb, DOMsg h) {
		switch (h.s12HeaderMsgType) {
		case 0:
			break;
		case 0x0001:
			DOMsg0001 m0001 = (DOMsg0001) h;
			m0001.b0Version = read1(bb);
			int dim = m0001.b1Dimensionality = read1(bb);
			m0001.b2Flags = read1(bb);
			m0001.b3Zero = read1(bb);
			m0001.i4Zero = read4(bb);
			m0001.dataDim = new long[dim];
			m0001.dataDimMax = new long[dim];
			for (int i = 0; i < dim; i++) {
				m0001.dataDim[i] = getNBytes(bb, sb.getL());
			}
			for (int i = 0; i < dim; i++) {
				m0001.dataDimMax[i] = getNBytes(bb, sb.getL());
			}
			break;
		case 0x0003: {
			DOMsg0003 m = (DOMsg0003) h;
			m.b0Version = read1(bb);
			m.b1Bits7 = read1(bb);
			m.b2Bits15 = read1(bb);
			m.b3Bits23 = read1(bb);
			m.i4Size = read4(bb);
			if (m.i4Size > 0) {
				m.properties = new byte[m.i4Size];
				bb.get(m.properties);
			}
			break;
		}
		case 0x0005: {
			DOMsg0005 m = (DOMsg0005) h;
			m.b0Version = read1(bb);
			m.b1SpacAllocTime = read1(bb);
			m.b2FillValueWriteTime = read1(bb);
			m.b3FillValueDefined = read1(bb);
			if (m.b0Version < 2 || m.b3FillValueDefined > 0) {
				m.i4Size = read4(bb);
				if (m.i4Size > 0) {
					m.l8FillValue = getNBytes(bb, m.i4Size);
				}
			}
			break;
		}
		case 0x0008: {
			DOMsg0008 m = (DOMsg0008) h;
			m.b0Version = read1(bb);
			m.b1Dimensionality = read1(bb);
			m.b2LayoutClass = read1(bb);
			m.b3Zero = read1(bb);
			m.i4Zero = read4(bb);
			if (m.b2LayoutClass >=1) {
				m.l8DataAddressO = getNBytes(bb, sb.getO());
			}
			break;
		}
		case 0x000C: {
			DOMsg000C m = (DOMsg000C) h;
			m.b0Version = read1(bb);
			m.b1Zero = read1(bb);
			m.s2NameSize = read2(bb);
			m.s4DatatypeSize = read2(bb);
			m.s6DataspaceSize = read2(bb);
			m.name = readLinkName(bb);
			alignPos8(bb);
			m.datatype = (DOMsg0003) readDOMessage(bb, sb, 
					//TODO position?
					DOMsg.create(MSG_0003_DATA_TYPE, -bb.position()));
			alignPos8(bb);
			m.dataspace = (DOMsg0001) readDOMessage(bb, sb, 
					//TODO position?
					DOMsg.create(MSG_0001_DATA_SPACE, -bb.position()));
			alignPos8(bb);
			int dataSize = 1; 
//			if (m.b2LayoutClass >=1) {
//				m.l8DataAddressO = getNBytes(bb, sb.getO());
//			}
			break;
		}
		case 0x0010: {
			DOMsg0010 m = (DOMsg0010) h;
			m.offsetO = getNBytes(bb, sb.getO());
			m.lengthL = getNBytes(bb, sb.getL());
			break;
		}
		case 0x0011: {
			DOMsg0011 m = (DOMsg0011) h;
			m.l0V1BTreeAddressO = getNBytes(bb, sb.getO());
			m.l8LocalHeapAddressO = getNBytes(bb, sb.getO());
			break;
		}
		case 0x0012: {
			DOMsg0012 m = (DOMsg0012) h;
			m.b0Version = read1(bb);
			m.b0Version = read1(bb);
			m.b0Version = read1(bb);
			m.b0Version = read1(bb);
			m.i4SecondEpoch = read4(bb);
			break;
		}
		default:
			if (h.s14SizeHeaderMsgData > 0) {
				h.b20data = new byte[h.s14SizeHeaderMsgData];
				bb.get(h.b20data);
			}
			break;
		}
		
		return h;
	}

	private static HDF5BlockTREE readTREE(MappedByteBuffer bb, int sLen, int sOffs, HDF5BlockSBHeader sb) {
		HDF5BlockTREE n = new HDF5BlockTREE(bb.position()-4);
		int type = n.b4nodeType = bb.get();
		n.b5nodeLevel = bb.get();
		int nEntries = n.s6entriesUsed = read2(bb);
		n.l8addreLeftSiblO = getNBytes(bb, sOffs);
		n.l16addreRightSiblO = getNBytes(bb, sOffs);
		
		log(n.toString());
		
		boolean isLeaf = n.b5nodeLevel == 0;
		
		//0 	This tree points to group nodes.
		//1 	This tree points to raw data chunk nodes.
		int maxNChildren;
		if (type == 0) {
			//group nodes
			if (isLeaf) {
				maxNChildren = sb.s16gLeafNodeK;
			} else {
				maxNChildren = sb.s18gIntNodeK;
			}
		} else if (type == 1) {
			//raw data chunk nodes
			if (isLeaf) {
				//What is the K here????
				throw new IllegalArgumentException("????");
			} else {
				maxNChildren = sb.s24isIntNodeK;
			}
		} else {
			throw new IllegalArgumentException("type == " + type);
		}
		int maxNKeys = maxNChildren + 1;

		if (type == 0) {
			long[] childPointers = new long[maxNChildren];
			long[] keys = new long[maxNKeys];
			//Keys are an offset into the local HEAP
			keys[0] = getNBytes(bb, sLen);
			for (int i = 0; i < nEntries; i++) {
				
				//TODO read Child -> is this really OFFSET sized?
				childPointers[i] = getNBytes(bb, sOffs);
				keys[i+1] = getNBytes(bb, sLen);
				log("ChildPointer: " + keys[i] + " -- " + childPointers[i] + " -- " + keys[i+1]);
			}
			n.keys = keys;
			n.childPointers = childPointers;

			//Example for CHILD: a SNOD NODE!
			
		} else if (type == 1) {
			throw new UnsupportedOperationException(); //TODO
		}		
		return n;
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

		//data is 8-byte aligned. We can estimate a max-number of data entries:
		int maxN = (int) (n.l8dataSegmentSize / 8);
		int currentFreeOffset = (int) n.l16freeListOffset;
		int maxOffset = (int) (n.l24dataSegementOffset + n.l8dataSegmentSize);
		
		n.heapOffset = new int[maxN];
		n.heap = new String[maxN];
		int heapId = 0;
		
		//TODO dangerous? Maybe we should restore the position afterwards?
		skipTo(bb, (int) n.l24dataSegementOffset);
		while (bb.position() < maxOffset) {
			int localOffset = (int) (bb.position() - n.l24dataSegementOffset);
			if (localOffset == currentFreeOffset) {
				//read free (new pos may be 1 (indicating last free block)
				currentFreeOffset = (int) getNBytes(bb, sLen);
				int sizeFB = (int) getNBytes(bb, sLen);
				skipTo(bb, bb.position() - 2*sLen + sizeFB);
			} else {
				//read data
				n.heapSize++;
				n.heapOffset[heapId] = localOffset;
				n.heap[heapId++] = readLinkName(bb);
				alignPos8(bb);
				log("HeapObject: " + n.heapOffset[heapId-1] + " - " + n.heap[heapId-1]);
			}
//			System.out.println("xxxx " + bb.position() + " / " + maxOffset);
		}
		
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
		//III.C. Disk Format: Level 1C - Symbol Table Entry
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
	
	private static String readLinkName(MappedByteBuffer bb) {
		StringBuilder sb = new StringBuilder();
		char c = (char) bb.get();
		while (c != 0) {
			sb.append(c);
			c = (char) bb.get();
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

	private static void alignPos8(MappedByteBuffer bb) {
		bb.position(roundUp8(bb.position()));
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
