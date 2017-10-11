// Paul Warner and Jared Patriarca
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BitField implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6857044454372491624L;
	byte[] bits;
	
	public BitField(int len) {
		bits = new byte[(len/8) + (((len % 8) > 0) ? 1 : 0)];
		for(int i = 0; i < bits.length; ++i) {
			bits[i] = 0x0;
		}
	}
	
	public BitField(byte[] bits) {
		this.bits = bits;	
	}
	
	public synchronized void setBit(int off, int value) {
		if (value == 0) {
			bits[off/8] &= (1 << (7 - (off % 8)));
		} else if (value == 1) {
			bits[off/8] |= (1 << (7 - (off % 8)));
		}
	}
	
	public synchronized boolean getBit(int off) {
		return (bits[off/8] & (1 << (7 - (off % 8)))) != 0;	
	}
	
	public synchronized byte[] getBits() {
		return bits;
	}
	
	public synchronized List<Piece> findNeeded(List<Piece> pieces) {
		List<Piece> neededPieces = new ArrayList<>();
		for(Piece p : pieces) {
			if (getBit(p.getIndex()))
				neededPieces.add(p);
		}
		return neededPieces;	
	}
	
	/**
	 * Check if the given bit field has bits set that this one doesn't
	 * @param peerField
	 * @return
	 */
	public synchronized boolean hasNew(BitField peerField) {
		if (peerField.getBits().length != bits.length) {
			return false;
		}
		for (int i = 0; i < bits.length*8; ++i) {
			if (!this.getBit(i) && peerField.getBit(i)) {
				return true;
			}
		}
		return false;
	}
}
