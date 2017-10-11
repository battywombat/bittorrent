// Paul Warner and Jared Patriarca
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Piece implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -90604028788748171L;
	int index;
	int length;
	long downloadStarted;
	long downloadFinished;
	public long getDownloadStarted() {
		return downloadStarted;
	}

	public void setDownloadStarted(long downloadStarted) {
		this.downloadStarted = downloadStarted;
	}

	public long getDownloadFinished() {
		return downloadFinished;
	}
	
	public long getDownloadTime() {
		return downloadFinished - downloadStarted;
	}

	public void setDownloadFinished(long downloadFinished) {
		this.downloadFinished = downloadFinished;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}
	public int getIndex() {
		return index;
	}
	public int getLength() {
		return length;
	}
	public ByteBuffer getInfoHash() {
		return infoHash;
	}

	public ByteBuffer infoHash;
	byte[] data;
	
	public Piece(int index, int length, ByteBuffer infoHash) {
		this.index = index;
		this.length = length;
		this.infoHash = infoHash;
	}
	
	public boolean matchHash(byte[] data) {
		MessageDigest sha1;
		try {
			sha1 = MessageDigest.getInstance("SHA-1");
			sha1.reset();
			ByteBuffer data_hash = ByteBuffer.wrap(sha1.digest(data));
			return data_hash.equals(this.infoHash);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return true; // unreachable
	}
	
	public boolean matchHash() {
		return matchHash(this.data);
	}
	
	private void readObject(ObjectInputStream inputStream) {
		try {
			this.length = (int)inputStream.readObject();
			this.index = (int)inputStream.readObject();
			this.downloadStarted = (long)inputStream.readObject();
			this.downloadFinished = (long)inputStream.readObject();
			this.data = (byte[])inputStream.readObject();
			this.infoHash = ByteBuffer.wrap((byte[])inputStream.readObject());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	private void writeObject(ObjectOutputStream outputStream) {
		try {
			outputStream.writeObject(this.length);
			outputStream.writeObject(this.index);
			outputStream.writeObject(this.downloadStarted);
			outputStream.writeObject(this.downloadFinished);
			outputStream.writeObject(this.data);
			outputStream.writeObject(this.infoHash.array());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}
