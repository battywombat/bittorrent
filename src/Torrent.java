// Paul Warner and Jared Patriarca
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import GivenTools.Bencoder2;
import GivenTools.BencodingException;
import GivenTools.TorrentInfo;

public class Torrent {
	
	static final ByteBuffer KEY_PEERS = ByteBuffer.wrap(new byte[] {'p', 'e', 'e', 'r', 's'});
	static final ByteBuffer KEY_COMPLETE = ByteBuffer.wrap(new byte[] {'c', 'o', 'm', 'p', 'l', 'e', 't', 'e'});
	static final ByteBuffer KEY_INCOMPLETE = ByteBuffer.wrap(new byte[] {'i', 'n', 'c', 'o', 'm', 'p', 'l', 'e', 't', 'e'});
	static final ByteBuffer KEY_MIN_INTERVAL = ByteBuffer.wrap(new byte[] {'m', 'i', 'n', ' ', 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l'});
	static final ByteBuffer KEY_DOWNLOADED = ByteBuffer.wrap(new byte[] {'d', 'o', 'w', 'n', 'l', 'o', 'a', 'd', 'e', 'd'});
	static final ByteBuffer KEY_INTERVAL = ByteBuffer.wrap(new byte[] {'i', 'n', 't', 'e', 'r', 'v', 'a', 'l'});
	
	String myPeerId;
	
	TorrentInfo ti;
	
	List<Peer> peers;
	List<Peer> activePeers;
	
	List<Piece> needed;
	List<Piece> requested;
	List<Piece> have;

	int nextPiece;
	
	BitField bfield;
	
	InputThread i;
	
	volatile boolean isDownloading;
	
	public List<Peer> getPeers() {
		return peers;
	}

	Peer seeder;
	
	public Peer getSeeder() {
		return seeder;
	}
	
	public String getMyPeerId() {
		return this.myPeerId;
	}
	
	public void setPeerId(String myPeerId) {
		this.myPeerId = myPeerId;
	}

	int complete;
	int incomplete;
	int min_interval;
	int downloaded;
	int interval;
	
	HashSet<String> validIPs = new HashSet<String>(Arrays.asList(new String[] {"172.16.97.11", "172.16.97.12", "172.16.97.13"}));
	
	String infoHash;
	
	TimerTask trackerAnnounce;
	
	Timer trackerTimer;

	public ByteBuffer getInfoHash() {
		return this.ti.info_hash;
	}
	
	public Torrent(String fp) {
		byte[] infoBuffer;
		try {
			infoBuffer = java.nio.file.Files.readAllBytes(Paths.get(fp));	
		} catch (IOException ioe) {
			System.out.println("Error reading file "+fp);
			return;
		}
		try {
			this.ti = new TorrentInfo(infoBuffer);	
		} catch (BencodingException bee) {
			System.out.println("Error with info in file "+fp+", perhaps the file is corrupted");
			return;
		}
		this.peers = null;
		this.infoHash = Utility.escapeString(ti.info_hash);
		bfield = new BitField(ti.piece_hashes.length);
		needed = new ArrayList<Piece>();
		for (int i = 0; i < ti.piece_hashes.length-1; ++i) {
			Piece p = new Piece(i, ti.piece_length, ti.piece_hashes[i]);
			needed.add(p);
		}
		// Last piece can be either equal to or shorter than piece_length, check the remainder of file_length and piece_length
		if (ti.file_length % ti.piece_length == 0) {
			needed.add(new Piece(ti.piece_hashes.length-1, ti.piece_length, ti.piece_hashes[ti.piece_hashes.length-1]));
		}
		else {
			needed.add(new Piece(ti.piece_hashes.length-1, ti.file_length % ti.piece_length, ti.piece_hashes[ti.piece_hashes.length-1]));
		}
		this.activePeers = new ArrayList<Peer>();
		requested = new ArrayList<Piece>();
		have = new ArrayList<Piece>();
		nextPiece = 0;
	}
	
	private void createTrackerAnnounceTimer() {
		Torrent that = this;
		this.trackerAnnounce = new TimerTask() {
			
			@Override
			public void run() {
				System.out.println("sending announce...");
				try {
					that.fetchTrackerData(that.getMyPeerId(), TorrentServer.getInstance().getSocket());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		
		this.trackerTimer = new Timer();
		this.trackerTimer.scheduleAtFixedRate(this.trackerAnnounce, 0, this.min_interval*1000);
	}
	
	private InputStream sendTrackerRequest(String peerId, ServerSocket listeningPort, String event) {
		HttpURLConnection trackerConnection;
		URL url = encodeURL(ti, listeningPort, peerId, null);
		try {
			trackerConnection = (HttpURLConnection)url.openConnection();
		} catch (IOException e1) {
			e1.printStackTrace();
			return null;
		}
		try {
			trackerConnection.setRequestMethod("GET");
		} catch (ProtocolException e) {
			e.printStackTrace();
			return null;
		}
		try {
			return trackerConnection.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}		
	}
	
	/**
	 * Connect to the tracker indicated in the torrent file and get current metadata on this torrent
	 * @param peerId The peer_id used to identify this peer
	 * @param listeningPort THe port on which this port is currently listening
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public void fetchTrackerData(String peerId, ServerSocket listeningPort) throws IOException {
		InputStream is = sendTrackerRequest(peerId, listeningPort, null);
		this.peers = new ArrayList<Peer>();
		ArrayList<Peer> rupeers = new ArrayList<Peer>();
		byte[] trackerData = new byte[4096]; // Arbitrary size. H
		while (is.read(trackerData) > 0)
			;
		HashMap<ByteBuffer, Object> trackerDictionary;
		try {
			trackerDictionary = (HashMap<ByteBuffer, Object>)Bencoder2.decode(trackerData);
			List<Object> peerInfo = (List<Object>)trackerDictionary.get(KEY_PEERS);
			Peer newPeer;
			for(Object o: peerInfo) {
				newPeer = new Peer((HashMap<ByteBuffer, Object>)o, this);
				this.peers.add(newPeer);
				if (newPeer.getPeer_id().startsWith("-RU1103") && validIPs.contains(newPeer.getIp()))
					rupeers.add(newPeer);
				peers.add(newPeer);
			}
		} catch (BencodingException e) {
			e.printStackTrace();
			return;
		}
		setSeeder(rupeers);
		for (Peer p : rupeers) {
			this.activePeers.add(p);
		}
		for (Peer p : peers) {
			if (!this.activePeers.contains(p)) {
				this.activePeers.add(p);
			}
		}
		this.complete = (Integer)trackerDictionary.get(KEY_COMPLETE);
		this.incomplete = (Integer)trackerDictionary.get(KEY_INCOMPLETE);
		this.min_interval = (Integer)trackerDictionary.get(KEY_MIN_INTERVAL);
		this.interval = (Integer)trackerDictionary.get(KEY_INTERVAL);
		this.downloaded = (Integer)trackerDictionary.get(KEY_DOWNLOADED);
	}
	
	void setSeeder(ArrayList<Peer> rupeers) {
		int[][] rtts = new int[rupeers.size()][2];
		for(int i = 0; i < rtts.length; ++i) {
			rtts[i][0] = i;
			rtts[i][1] = rupeers.get(i).measureRTT();
		}
		Arrays.sort(rtts, (a1, a2) -> a1[1] - a2[1]);
		int i = rtts[0][0];
		this.seeder = rupeers.get(i);
	}
	
	URL encodeURL(TorrentInfo ti, ServerSocket connectionSocket, String peerId, String event) {
		StringBuffer buf = new StringBuffer();
		buf.append(ti.announce_url.toString());
		buf.append("?info_hash=");
		buf.append(this.infoHash);
		buf.append("&peer_id=");
		try {
			buf.append(URLEncoder.encode(peerId, "UTF-8"));	
		} catch(UnsupportedEncodingException uee) {
			System.out.println(uee.getMessage());
			uee.printStackTrace();
			System.exit(1);
		}
		
		int downloaded = ti.piece_length*this.have.size();
		int left = ti.file_length-downloaded;
		buf.append("&port=");
		buf.append(connectionSocket.getLocalPort());
		buf.append("&downloaded=");
		buf.append(String.valueOf(downloaded));
		buf.append("&left=");
		buf.append(left);
		if (event != null) {
			buf.append("&event=");
			buf.append(event);
		}
		try {
			return new URL(buf.toString());
		} catch (MalformedURLException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		return null;
	}
	
	public void beginDownload(String fp) {
		this.isDownloading = true;
		i = new InputThread(this);
		i.start();
		loadProgress("");
		this.createTrackerAnnounceTimer();
		for (Peer p : this.activePeers) {
			p.start();
		}
		TorrentServer.getInstance().start();
		sendTrackerRequest(this.myPeerId, TorrentServer.getInstance().getSocket(), "started");
		synchronized(this) {
			try {
				wait();
			} catch (InterruptedException e) {
				System.out.println("Thread interrupted!");
				e.printStackTrace();
			}	
		}
		stopThreads();
		sendTrackerRequest(this.myPeerId, TorrentServer.getInstance().getSocket(), "completed");
		System.out.println("Writing file");
		writeFile(fp);
		System.out.println("Telling tracker I'm stopping");
		sendTrackerRequest(this.myPeerId, TorrentServer.getInstance().getSocket(), "stopped");
		System.out.println("exiting...");
		System.exit(0);	
	}
	
	/**
	 * Find a piece that we need and the peer has.
	 * @param p
	 * @return
	 */
	public synchronized Piece findPiece(Peer p) {
		List<Piece> piecesPeerHas = p.getPeerBitField().findNeeded(needed);
		if (piecesPeerHas.size() == 0)
			return null;
		Piece ret = piecesPeerHas.get(new Random().nextInt(piecesPeerHas.size()));
		needed.remove(ret);
		requested.add(ret);
		ret.setDownloadStarted(System.nanoTime());
		return ret;
	}
	
	public synchronized BitField getBitField() {
		return this.bfield;
	}
	
	public synchronized Piece getPieceByIndex(int index) {
		for (Piece p : requested) {
			if (p.getIndex() == index)
				return p;
		}
		for (Piece p : needed) {
			if (p.getIndex() == index)
				return p;
		}
		for (Piece p : have) {
			if (p.getIndex() == index)
				return p;
		}
		return null;
	}
	
	public synchronized void recievedPiece(int index) {
		Piece recieved = getPieceByIndex(index);
		recieved.setDownloadFinished(System.nanoTime());
		if (requested.contains(recieved))
			requested.remove(recieved);
		synchronized(this.activePeers) {
			for (Peer p : this.activePeers) {
				p.signalHave(index);
			}	
		}
		System.out.println("Pieces remaining: "+this.needed.size());
		if (this.needed.size() == 0 && this.requested.size() == 0) {
			this.notify();
		}
		have.add(recieved);
		this.bfield.setBit(index, 1);
	}
	
	private void writeFile(String fp) {
		Piece current;
		// Throw all pieces into a HashSet to remove duplicates
		// Don't know why, but this did happen to me once, so just in case...
		HashSet<Piece> h = new HashSet<>(have);
		have.clear();
		have.addAll(h);
		Collections.sort(have, (Piece p1, Piece p2) -> p1.getIndex() - p2.getIndex());
		DataOutputStream out;
		try {
			out = new DataOutputStream(new FileOutputStream(new File(fp)));
			for(int i = 0; i < have.size(); ++i) {
				current = have.get(i);
				if (current.getData() == null) {
					System.out.println("WARNING!!! MISSING DATA AT INDEX "+current.getIndex());
				} else if (!current.matchHash()) {
					System.out.println("WARNING!!! INCORRECT HASH AT INDEX "+current.getIndex());
				} else {
					out.write(current.getData());	
				}
			}
			out.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private synchronized void saveProgress(String fp) {
		for (Peer p : activePeers) {
			p.signalFinished();
			}
		try {
			FileOutputStream os = new FileOutputStream(fp+ti.info_hash+".needed");
			ObjectOutputStream out = new ObjectOutputStream(os);
			out.writeObject(this.needed);
			out.flush();
			out.close();
			os.close();
			os = new FileOutputStream(fp+ti.info_hash+".have");
			out = new ObjectOutputStream(os);
			out.writeObject(have);
			out.flush();
			out.close();
			os.close();
			os = new FileOutputStream(fp+ti.info_hash+".bfield");
			out = new ObjectOutputStream(os);
			out.writeObject(this.bfield);
			out.flush();
			out.close();
			os.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	private synchronized void loadProgress(String fp) {
		try {
			File f = new File(fp+ti.info_hash+".needed");
			if (!f.exists()) {
				return;
			}
			FileInputStream os = new FileInputStream(fp+ti.info_hash+".needed");
			ObjectInputStream out = new ObjectInputStream(os);
			this.needed = (List<Piece>)out.readObject();

			os = new FileInputStream(fp+ti.info_hash+".have");
			out = new ObjectInputStream(os);
			this.have = (List<Piece>)out.readObject();

			os = new FileInputStream(fp+ti.info_hash+".bfield");
			out = new ObjectInputStream(os);
			this.bfield = (BitField)out.readObject();
			out.close();
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public synchronized void addPeer(Peer p) {
		synchronized(this.activePeers) {
			this.activePeers.add(p);	
		}	
		p.start();
	}
	
	public synchronized void unrequestPiece(int index) {
		Piece p = this.getPieceByIndex(index);
		if (!this.requested.contains(p)) {
			return;
		}
		this.requested.remove(p);
		this.needed.add(p);
	}
	
	public void stopDownload() {
		stopThreads();
		saveProgress("");
		sendTrackerRequest(this.myPeerId, TorrentServer.getInstance().getSocket(), "stopped");
		System.exit(0);
	}
	
	private void stopThreads() {
		this.isDownloading = false;
		try {
			TorrentServer.getInstance().getSocket().close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		this.trackerTimer.cancel();
		synchronized(this) {
			for (Peer p : this.activePeers) {
				p.signalFinished();
			}
		}
		for (Peer p : this.activePeers) {
			try {
				p.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		for (Piece p : this.requested) {
			if (p.getData() == null) 
				needed.add(p);
			else
				have.add(p);
 		}
	}
	
	public synchronized boolean isCompleted() {
		return needed.size() == 0 && requested.size() == 0;
	}
}
