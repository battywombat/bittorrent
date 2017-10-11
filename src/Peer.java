// Paul Warner and Jared Patriarca
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class Peer extends Thread {
	
	static final ByteBuffer HANDSHAKE_HEADER = 
			ByteBuffer.wrap(new byte[] {19, 'B', 'i', 't', 'T', 'o', 'r', 'r', 'e', 'n', 't', ' ',
										'p', 'r', 'o', 't', 'o', 'c', 'o', 'l',
										0, 0, 0, 0, 0, 0, 0, 0});
	static final ByteBuffer KEY_PORT = ByteBuffer.wrap(new byte[] {'p', 'o', 'r', 't'});
	static final ByteBuffer KEY_IP = ByteBuffer.wrap(new byte[] {'i', 'p'});
	static final ByteBuffer KEY_PEER_ID = ByteBuffer.wrap(new byte[] {'p', 'e', 'e', 'r', ' ', 'i', 'd'});
	
	static final byte CHOKE = 0;
	static final byte UNCHOKE = 1;
	static final byte INTERESTED = 2;
	static final byte NOT_INTERESTED = 3;
	static final byte HAVE = 4;
	static final byte BITFIELD = 5;
	static final byte REQUEST = 6;
	static final byte PIECE = 7;
	static final byte CANCEL = 8;
	
	int port;
	
	int requested_index;
	String ip;
	String peer_id;
	Torrent to;
	
	boolean peerChoked;
	boolean peerInterested;
	
	boolean choked;
	boolean interested;
	
	volatile boolean isDownloading;
	Boolean isFinished;
	
	BitField peerBitField;
	
	TimerTask unrequestPiece;
	
	Timer timer;
	
	@Override
	public void run() {
		Peer that = this;
		this.unrequestPiece = new TimerTask() {
			
			@Override
			public void run() {
				that.unrequestCurrentPiece();
			}
		};
		this.beginCommunication();
	}
	
	private void unrequestCurrentPiece() {
		System.out.println("Unrequesting piece!");
		this.to.unrequestPiece(this.requested_index);
	}
	
	public BitField getPeerBitField() {
		return peerBitField;
	}
	
	public void signalFinished() {
		Peer that = this;
		TimerTask shutdown = new TimerTask() {
			
			@Override
			public void run() {
				System.out.println("Closing socket...");
				try {
					if (that.conn != null) {
						that.conn.close();	
					}
				} catch (IOException e) {}
			}
		};
		Timer t = new Timer();
		synchronized(this.isFinished) {
			this.isFinished = true;
		}
		t.schedule(shutdown, 5000);
	}

	Socket conn;
	DataInputStream in;
	DataOutputStream out;
	
	boolean handshakeCompleted;
	
	public int getPort() {
		return port;
	}

	public String getIp() {
		return ip;
	}

	public String getPeer_id() {
		return peer_id;
	}
	
	public Socket getConn() {
		return this.conn;
	}
	
	public Peer(Socket sock, Torrent to) {
		this.peerChoked = true;
		this.choked = true;
		this.peerInterested = false;
		this.interested = false;
		this.handshakeCompleted = false;
		this.isDownloading = false;
		this.isFinished = false;
		this.conn = sock;
		this.to = to;
		this.requested_index = -1;
		this.peerBitField = new BitField(this.to.getBitField().getBits().length);
	}

	public Peer(HashMap<ByteBuffer, Object> dict, Torrent to) {
		this.port = (Integer)dict.get(KEY_PORT);
		this.ip = new String(((ByteBuffer)dict.get(KEY_IP)).array());
		this.peer_id = new String(((ByteBuffer)dict.get(KEY_PEER_ID)).array());
		this.to = to;
		this.peerChoked = true;
		this.choked = true;
		this.peerInterested = false;
		this.interested = false;
		this.handshakeCompleted = false;
		this.isDownloading = false;
		this.isFinished = false;
		this.requested_index = -1;
		this.peerBitField = new BitField(this.to.getBitField().getBits().length);
	}
	
	public boolean handshake() {
		ByteBuffer infoHash = this.to.getInfoHash();
		String peerId = this.to.getMyPeerId();
		try {
			if (this.conn == null) {
				this.conn = new Socket(ip, port);	
			}
			this.out = new DataOutputStream(conn.getOutputStream());
			this.in = new DataInputStream(conn.getInputStream());
		} catch (ConnectException e) {
			System.out.println("Connection exception");
			return false;
		} catch (UnknownHostException e) {
			System.out.println("Unknown host exception");
			return false;
		} catch (IOException e) {
			System.out.println("IO exception");
			return false;
		}
		synchronized(this.out) {
			try {
				this.out.write(HANDSHAKE_HEADER.array());
				this.out.write(infoHash.array());
				this.out.write(peerId.getBytes());
				this.out.flush();
			} catch (IOException e) {
				System.out.println("IO exception 2");
				e.printStackTrace();
			}			
		}
		byte[] arr = new byte[68];
		synchronized(this.in) {
			try {
				this.in.read(arr);
//				this.in.readFully(arr);
			} catch (IOException e) {
				System.out.println("Can't read fully");
				return false; // Assume something went wrong and close
			}		
		}

		ByteBuffer recievedInfoHash = ByteBuffer.wrap(new byte[20]);
		System.arraycopy(arr, HANDSHAKE_HEADER.array().length, recievedInfoHash.array(), 0, 20);

		if (recievedInfoHash.equals(infoHash)) {
			this.handshakeCompleted = true;
			return true;
		}
		else {
			try {
				this.conn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Info hash mismatch");
			return false;
		}
	}
	
	public void sendBitField(BitField b) {
		synchronized(this.out) {
			try {
				this.out.write(b.getBits());
			} catch (IOException e) {
				e.printStackTrace();
			}	
		}
	}
	
	private void requestPiece(Piece p) {
		synchronized(this.out) {
			try {
				this.out.writeInt(13);
				this.out.writeByte(REQUEST);
				this.out.writeInt(p.getIndex());
				this.out.writeInt(0);
				this.out.writeInt(p.getLength());
				this.out.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}			
		}
		this.requested_index = p.getIndex();
		Peer that = this;
		this.unrequestPiece = new TimerTask() {
			
			@Override
			public void run() {
				that.unrequestCurrentPiece();
			}
		};
		this.timer = new Timer();
		timer.schedule(this.unrequestPiece, 10000);
	}
	
	public void beginCommunication() {
		boolean first = true;
		if (!handshake()) {
			System.out.println("Error connecting to peer");
			try {
				if (this.conn != null)
					this.conn.close();
			} catch (IOException e) {}
			return;
		}
		this.isDownloading = true;
		while (true) {
			synchronized(this.isFinished) {
				if (this.isFinished) {
					System.out.println("Thread"+this.toString()+" Shutting down...");
					break;
				}
			}
			if(!exchangeMessages()) {
				return; // Error, end this thread
			}
			if (first) {
				signalChoked(false);
				if (this.to.getBitField().hasNew(peerBitField)) {
					signalInterested(true);
				}
				first = false;
			}
			if (this.interested && this.requested_index == -1) {
				Piece p = to.findPiece(this);
				if (p == null) { // We've finished
					break;
				}
				else
					requestPiece(p);
			}
		}
		try {
			this.conn.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean exchangeMessages() {
		int messageLength;
		byte messageId;
		int pieceIndex;
		synchronized(this.in) {
			try {
				if (this.peerInterested) {
					System.out.println("Reading new message from interested peer...");
				}
				messageLength = in.readInt();
				if (messageLength == 0) {// keepalive
					return true;
				}
				messageId = in.readByte();
			} catch (IOException e) {
				return false; // error somewhere
			}			
		}
		switch(messageId) {
		case CHOKE:
			System.out.println("Choking...");
			peerChoked = true;
			break;
		case UNCHOKE:
			System.out.println("Unchoking...");
			peerChoked = false;
			break;
		case INTERESTED:
			System.out.println("Interested...");
			peerInterested = true;
			break;
		case NOT_INTERESTED:
			System.out.println("Uninterested...");
			peerInterested = false;
			break;
		case HAVE:
			System.out.println("peer has new piece...");
			try {
				pieceIndex = this.in.readInt();
				this.peerBitField.setBit(pieceIndex, 1);
				if (!this.interested && this.to.getBitField().hasNew(this.peerBitField)) {
					this.signalInterested(true);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		case BITFIELD:
			System.out.println("Recieving bitfield...");
			readBitfield();
			sendBitfield();
			break;
		case REQUEST:
			if (!serveRequestedPiece()) {
				return false;
			}
			System.out.println("peer requesting piece...");
			break;
		case PIECE:				
			if ((pieceIndex = recievePiece(messageLength)) >= 0) {
				this.to.recievedPiece(pieceIndex); // Tell everyone we now have this piece
				this.timer.cancel();
				this.requested_index = -1;
			}
			break;
		case CANCEL:
			System.out.println("Peer canceling piece...");
			break;
		default:
			System.out.println("invalid message "+messageId);
			return false;
		}
		return true;
	}
	
	private boolean serveRequestedPiece() {
		int index, offset, len;
		Piece p;
		synchronized(this.in) {
			try {
				index = this.in.readInt();
				offset = this.in.readInt();
				len = this.in.readInt();
				p = this.to.getPieceByIndex(index);
				if (p == null) {
					return false;
				}
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}
		if (offset+len > p.getLength())
			return false;
		sendPiece(p, offset, len);
		return true;
	}
	
	private void sendPiece(Piece p, int offset, int len) {
		byte[] data = p.getData();
		if (data == null)
			return;
		synchronized(this.out) {
			try {
				this.out.writeInt(len+1);
				this.out.write(PIECE);
				this.out.write(data, offset, len);
				this.out.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void signalHave(int index) {
		if (!this.isDownloading)
			return;
		synchronized(this.out) {
			try {
				this.out.writeInt(5);
				this.out.writeByte(HAVE);
				this.out.writeInt(index);
				this.out.flush();
			} catch (IOException e) {}			
		}
	}
	
	private int recievePiece(int messageLength) {
		int index = -1;
		int begin;
		int blockLength = messageLength - 9;
		byte[] buffer = new byte[blockLength];
		synchronized(this.in) {
			try {
				index = this.in.readInt();
				begin = this.in.readInt();
				int off = 0;
				while (off < buffer.length) {
					off += this.in.read(buffer, off, buffer.length-off);
				}
				Piece p = this.to.getPieceByIndex(index);
				if (begin != 0) {
					System.out.println("We need to do more work to deal with getting pieces with offsets not equal to zero...");
				}
				if (p.matchHash(buffer)) {
					p.setData(buffer);
					return index;
				} else {
					System.out.println("Hash mismatch at index "+index);
					return -1;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}			
		}
		return -1;
	}

	public void signalChoked(boolean state) {
		if (!handshakeCompleted)
			return;
		synchronized(this.out) {
			try {
				this.out.writeInt(1);
				this.out.writeByte(state == true ? CHOKE : UNCHOKE);
				this.out.flush();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
		this.interested = state;
	}
	
	private void signalInterested(boolean state) {
		if (!handshakeCompleted)
			return;
		synchronized(this.out) {
			try {
				this.out.writeInt(1);
				this.out.writeByte(state == true ? INTERESTED : NOT_INTERESTED);
				this.out.flush();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}			
		}
		this.interested = state;
	}
	
	void readBitfield() {
		byte[] bits = new byte[this.to.getBitField().getBits().length];
		synchronized(this.in) {
			try {
				this.in.read(bits);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
		peerBitField = new BitField(bits);
	}
	
	void sendBitfield() {
		BitField myBitfield = this.to.getBitField();
		synchronized(this.out) {
			byte[] b = myBitfield.getBits();
			try {
				this.out.writeInt(1+b.length);
				this.out.write(BITFIELD);
				this.out.write(b);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public int measureRTT() {
		long[] rtts = new long[10];
		for (int i = 0; i < rtts.length; ++i) {
			rtts[i] = singleRTT();
		}
		long avg = 0;
		for (long l : rtts) 
			avg += l;
		return (int)avg/rtts.length;
	}
	
	long singleRTT() {
		int timeout = 5000;
		boolean status;
		long before = System.nanoTime();
		try {
			status = InetAddress.getByName(this.ip).isReachable(timeout);
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
		if (!status)
			return -1;
		long after = System.nanoTime();
		return after - before;
	}
}
