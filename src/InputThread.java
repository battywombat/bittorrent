import java.util.Scanner;

public class InputThread extends Thread {
	
	Torrent t;
	volatile boolean flag;
	
	public InputThread(Torrent t) {
		this.t = t;
		flag = true;
	}
	
	public void setFlag() {
		flag = false;
	}
	
	@Override
	public void run() {
		Scanner in = new Scanner(System.in);
		String s;
		while (flag) {
			System.out.println("Enter q to quit: ");
			s = in.next();
			if (s.equals("q")) {
				in.close();
				t.stopDownload();
				break;
			}
		}
		System.out.println("Input thread exiting...");
	}
}
