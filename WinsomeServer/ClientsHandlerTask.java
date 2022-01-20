package WinsomeServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;

public class ClientsHandlerTask implements Runnable {
    /**
     * OVERVIEW: classe che permette la gestione di nuovi client tramite un thread pool che assegna
     *           ogni client ad un nuovo thread.
     */


    /**
     * @effects gestisce l'arrivo dei client ed affida la comunicazione con questi ai
     *          threads (1 per client) presenti in una threadpool.
     */
    public void run() {

        /** CREAZIONE THREADPOOL **/
        ExecutorService pool = Executors.newCachedThreadPool();
        System.out.println("ClientsHandlerTask | Thread Pool: opened");


        /** CREAZIONE SERVER SOCKET **/
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(ServerMain.TCP_PORT);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        /** CICLO DI ACCETTAZIONE DELLE CONNESSIONI AI CLIENT **/
        int i = 0;
        while (!ServerMain.getExitValue()) {

            // socket in attesa di connettersi ad un client
            Socket socket;
            try {
                serverSocket.setSoTimeout(ServerMain.PERIOD);
                serverSocket.setReuseAddress(true);
                socket = serverSocket.accept();
            } catch (SocketTimeoutException e) {
                continue;
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }

            // esecuzione di un thread per lo scambio di messaggi col client
            pool.execute(new ClientCommunicationTask(new WinsomeServer(), socket, i%(65535-1099)));

            i++;
        }


        /** CHIUSURA SERVER SOCKET **/
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }


        /** CHIUSURA DEL THREADPOOL **/
        pool.shutdown();
        try {
            if(!pool.awaitTermination(ServerMain.PERIOD, TimeUnit.MILLISECONDS))
                pool.shutdownNow();
        } catch (InterruptedException e) {
            e.printStackTrace();
            // chiusura del server
            if (!WinsomeServer.updateMemory()) {
                System.err.println("Error: something goes wrong closing WinsomeServer");
                return;
            }
            return;
        }

        System.out.println("ClientsHandlerTask | Thread Pool: closed");
    }
}