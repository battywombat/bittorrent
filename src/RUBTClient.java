// Paul Warner and Jared Patriarca
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;


/**
 * Rutgers BitTorrentClient, Version 1
 * Takes two arguments: a torrent file, and a file path to write the data to.
 * @author paule
 *
 */
public class RUBTClient {
	
	public static void main(String[] args) {
		if (args.length < 2) {
			System.out.println("Error: too few arguments");
			System.exit(1);
		}

		String peerId = generatePeerId();
		
		Torrent t = new Torrent(args[0]);
		t.setPeerId(peerId);
		TorrentServer.init(t);
		try {
			t.fetchTrackerData(peerId, TorrentServer.getInstance().getSocket());
		} catch(IOException e) {
			System.out.println("Error connecting to tracker");
			System.exit(1);
		}
		t.beginDownload(args[1]);
	}
	
	/**
	 * Generate a random, unique peer id used to identify this peer 
	 * @return
	 */
	static String generatePeerId() {
		SecureRandom random = new SecureRandom();
		return new BigInteger(100, random).toString(32);
	}
}
 