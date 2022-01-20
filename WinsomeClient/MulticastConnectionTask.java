package WinsomeClient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MulticastConnectionTask implements Runnable {
    /**
     * OVERVIEW: classe che gestisce la comunicazione multicast con il gruppo
     *           le cui informazioni sono passate come parametro del costruttore.
     */

    // multicast socket per ricevere i messaggi del gruppo
    private MulticastSocket ms = null;
    // indirizzo multicast
    InetAddress groupIP = null;

    // indirizzo multicast
    public String MC_IP = null;
    // porta multicast
    public int MC_PORT = 0;
    // dimensione max del pacchetto
    public int PACKET_SIZE = 128;




    /**
     * @effects inizializza i campi utili alla comunicazione multicast.
     * @param IP indirizzo multicast.
     *           [IP != null]
     * @param PORT porta multicast.
     *             [(PORT >= 1024) && (PORT <= 65535)]
     */
    public MulticastConnectionTask(String IP, int PORT) {

        // controllo parametri
        if (IP == null || PORT < 1024 || PORT > 65535) {
            System.err.println("MulticastConnectionTask Error: bad parameters");
            return;
        }

        // inizializzazione
        MC_IP = IP;
        MC_PORT = PORT;
    }



    /**
     * @effects permette di settare al valore passato come parametro l'oggetto che permette
     *          (se true) la terminazione dei thread di questa classe.
     */
    public void closeMulticast() {
        try {
            ms.leaveGroup(groupIP);
            ms.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * @effects permette di ricevere i messaggi inviati sul gruppo multicast e di stamparli.
     */
    public void run() {

        // attesa dell'inizializzazione dei valori per stabilire la connessione multicast
        while (MC_PORT == 0 && MC_IP == null)
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }

        try {
            // unione al gruppo multicast
            ms = new MulticastSocket(MC_PORT);
            groupIP = InetAddress.getByName(MC_IP);
            ms.joinGroup(groupIP);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // dichiarazioni/inizializzazioni
        byte[] buf = new byte[PACKET_SIZE];
        DatagramPacket packIn;
        String message;

        // ciclo di ricezione dei messaggi
        while (true) {
            packIn = new DatagramPacket(buf, buf.length);
            try {
                ms.receive(packIn);
            } catch (IOException e) {
                return;
            }
            message = new String(packIn.getData());
            System.out.print("\n<<< " + message + "\n>>> ");
        }

    }


}