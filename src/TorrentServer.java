import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class TorrentServer extends Thread {
	
	static TorrentServer instance;
	
	ServerSocket incoming;
	Torrent to;
	
	public static void init(Torrent to) {
		ServerSocket sock = openPort();
		if (sock == null) {
			System.out.println("Error opening socket");
			System.exit(1);
		}
		instance = new TorrentServer(sock, to);
	}
	
	/**
	 * Open a socket that this peer uses to listen for new connections.
	 * As per the BitTorrent protocol this only tries to open connections on ports 6881-6889
	 * @return A server socket 
	 */
	static ServerSocket openPort() {
		int start = 6881;
		ServerSocket sock = null;
		while (sock == null && start <= 6889) {
			try {
				sock = new ServerSocket(start);	
			} catch (IOException ioe) {}
			start++;
		}
		return sock;
	}
	
	public static TorrentServer getInstance() {
		return instance;
	}
	
	public TorrentServer(ServerSocket incoming, Torrent to) {
		this.incoming = incoming;
		this.to = to;
	}
	
	@Override
	public void run() {
		listenForConnections();
	}
	
	public ServerSocket getSocket() {
		return this.incoming;
	}
	
	private void listenForConnections() {
		Socket client;
		Peer newPeer;
		while (!this.to.isCompleted()) {
			try {
				client = this.incoming.accept();
				if (this.to.isCompleted()) {
					client.close();
				} else {
					System.out.println("Accepted new peer connection: "+client.toString());
					newPeer = new Peer(client, this.to);
					this.to.addPeer(newPeer);	
				}
			} 
			catch (SocketException e) {} // Do nothing, the socket has been closed
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
