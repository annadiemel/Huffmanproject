import java.util.*;
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}
	
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) { //end of bits
				String code = codings[PSEUDO_EOF];
				out.writeBits(code.length(), Integer.parseInt(code, 2));
				break;
			}
			String code = codings[bits]; 
			out.writeBits(code.length(), Integer.parseInt(code, 2));
		}	
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myLeft== null && root.myRight==null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue); //writing the bits for the tree
		}
		else { // node is not a leaf
			out.writeBits(1, root.myValue);
			writeHeader(root.myLeft, out); // two recursive calls
			writeHeader(root.myRight, out);
		}
	}
	// returns an array of Strings such that each is the encoding for a val
	private String[] makeCodingsFromTree(HuffNode root) {
		
		String[] encodings = new String[ALPH_SIZE+1];
		codingHelper(encodings, "", root); //call to helper method
		return encodings;
	}

	private void codingHelper(String[] encodings, String path, HuffNode root) {
		if (root.myLeft==null && root.myRight==null) { //checks if node is leaf
			encodings[root.myValue]=path;
			return;
		}
		else { // if the node is not a leaf
			codingHelper(encodings,path+"0", root.myLeft); // left	
			codingHelper(encodings, path+"1", root.myRight); // right
		}
	}

	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		// adding all freq more than 0
		for (int i = 0; i < counts.length; i++) {
			if (counts[i]>0) pq.add(new HuffNode(i, counts[i]));
		}
		pq.add(new HuffNode(PSEUDO_EOF, 0)); //adding PSEUDO_EOF node to queue
		while (pq.size()>1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}

	private int[] readForCounts(BitInputStream in) {
		int[] counts = new int[ALPH_SIZE+1]; //one extra for PSEUDO_EOF
		while (true) {
			int value = in.readBits(BITS_PER_WORD);
			if (value==-1) break; // indicates no more bits
			counts[value]++;
		}
		counts[PSEUDO_EOF]=1;
		return counts;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		// reading bits per int to check if the file is huffman coded
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		
		//reading the tree used to decompress
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}

	// reading the tree with a pre order traversal
	private HuffNode readTreeHeader(BitInputStream in) {
		int currBits = in.readBits(1);
		
		//throwing exception
		if (currBits==-1) throw new HuffException("Cannot work with this bit value (-1)");
		
		// differentiating between 0 nodes and 1 nodes
		if (currBits==0) { // recursive case
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right); 
		}
		
		else { // reading nine bits from input
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value,0,null,null);
		}	
	}

	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		// traversing three from the root
		HuffNode curr = root;
		while (true) {
			int currBits = in.readBits(1);
			if (currBits == -1) throw new HuffException("Bad input, no PSEUDO_EOF");
			else {
				if (currBits == 0) curr=curr.myLeft;
				else curr = curr.myRight;
				if (curr.myLeft==null && curr.myRight==null) { // at a leaf
					if (curr.myValue==PSEUDO_EOF) break; // ends if at PSEUDO_EOF
					else {
						out.writeBits(BITS_PER_WORD, curr.myValue);
						curr=root; // starting back after leaf
					}
				}
			}
		}
	}

	
}