package WinsomeServer;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerMain {
    /**
     * OVERVIEW: classe che permette l'esecuzione, la gestione e la terminazione del server
     *           del social network Winsome.
     */

    // indirizzo multicast
    protected static String MC_IP = null;
    // porta multicast
    protected static int MC_PORT = 0;
    // porta tcp
    protected static int TCP_PORT = 0;
    // porta udp
    protected static int UDP_PORT = 0;
    // porta oggetto remoto
    protected static int REG_PORT = 0;
    // host oggetto remoto
    protected static String REG_HOST = null;
    // tempo in ms usato per le attese cicliche
    protected static int PERIOD = 0;
    // tempo in ms che indica ogni quanto effettuare il calcolo delle ricompense
    protected static int REWARD_TIME = 0;
    // percentuale della ricompensa che spetta all'autore del post
    protected static int REWARD_PERC = 0;

    // variabile che se settata a true causa la terminazione del server
    private static final AtomicBoolean exit = new AtomicBoolean(false);





    /**
     * @effects legge dal file di configurazione i dati che serviranno all'avvio del server.
     *          Se i dati non sono validi o non sono sufficienti non avvia l'esecuzione e
     *          restituisce un messaggio di errore.
     * @param configFileName nome del file di configurazione.
     */
    private void readConfigFile(String configFileName) {
        try (BufferedReader fileInputStream = new BufferedReader(new FileReader(configFileName))) {
            String line;

            // ciclo di lettura delle righe del file (in caso di formato errato di una riga del file, questa viene ignorata)
            while((line = fileInputStream.readLine()) != null) {
                StringTokenizer tokens = new StringTokenizer(line,"=");
                String key = null, value = null;
                try {
                    key = tokens.nextToken();
                    value = tokens.nextToken();
                } catch (NoSuchElementException e) { // caso di riga vuota o in cui non abbiamo nessun "="
                    continue;
                }

                // switch case per controlli e allocazione dei valori letti
                try {
                    switch (key) {
                        case "MC_IP": { /** INDIRIZZO DI MULTICAST **/
                            tokens = new StringTokenizer(value, ".");
                            if (tokens.countTokens() != 4)
                                continue;
                            MC_IP = value;
                            break;
                        }
                        case "TCP_PORT": { /** PORTA PER CONNESSIONE TCP **/
                            TCP_PORT = Integer.parseInt(value);
                            if (TCP_PORT < 1024 || TCP_PORT > 65535-Runtime.getRuntime().availableProcessors())
                                TCP_PORT = 0;
                            break;
                        }
                        case "UDP_PORT": { /** PORTA PER CONNESSIONE UDP **/
                            UDP_PORT = Integer.parseInt(value);
                            if (UDP_PORT < 1024 || UDP_PORT > 65535-Runtime.getRuntime().availableProcessors())
                                UDP_PORT = 0;
                            break;
                        }
                        case "MC_PORT": { /** PORTA PER MULTICAST **/
                            MC_PORT = Integer.parseInt(value);
                            if (MC_PORT < 1024 || MC_PORT > 65535-Runtime.getRuntime().availableProcessors())
                                MC_PORT = 0;
                            break;
                        }
                        case "REG_PORT": { /** PORTA DELL'OGGETTO REMOTO **/
                            REG_PORT = Integer.parseInt(value);
                            if (REG_PORT < 1024 || REG_PORT > 65535-Runtime.getRuntime().availableProcessors())
                                REG_PORT = 0;
                            break;
                        }
                        case "REG_HOST": { /** HOST DELL'OGGETTO REMOTO **/
                            REG_HOST = value;
                        }
                        case "PERIOD": { /** TEMPO DI ESECUZIONE DEL SERVER **/
                            int period = Integer.parseInt(value);
                            if (period > 0)
                                PERIOD = period;
                            break;
                        }
                        case "REWARD_TIME": { /** INTERVALLO DI TEMPO DOPO IL QUALE CALCOLARE LA RICOMPENSA **/
                            int time = Integer.parseInt(value);
                            if (time > 0)
                                REWARD_TIME = time;
                            break;
                        }
                        case "REWARD_PERC": { /** PERCENTUALE DELLA RICOMPENSA PERIODICA CHE SPETTA ALL'UTENTE **/
                            int perc = Integer.parseInt(value);
                            if (perc < 0 || perc > 100)
                                REWARD_PERC = 10;
                            else
                                REWARD_PERC = perc;
                            break;
                        }
                    }
                } catch (NumberFormatException e) {
                    continue;
                }
            }
        } catch (IOException | NoSuchElementException e) {
            e.printStackTrace();
            System.exit(1);
        }

        if(REWARD_TIME < PERIOD*10)
            REWARD_TIME = PERIOD*10;

        // caso di formato del file di configurazione errato
        if(MC_PORT == 0 || TCP_PORT == 0 || UDP_PORT == 0 || REG_PORT == 0 ||
                PERIOD == 0 || REG_HOST == null || MC_IP == null) {
            System.err.println("Error: bad format of configuration file.");
            System.out.println("ConfigurationFile's format:" +
                    "\n\tTCP_PORT=...    (ex: 6666)" +
                    "\n\tUDP_PORT=...    (ex: 33333)" +
                    "\n\tMC_IP=...       (ex: 239.255.32.32)" +
                    "\n\tMC_PORT=...     (ex: 44444)" +
                    "\n\tREG_HOST=...    (ex: localhost)" +
                    "\n\tREG_PORT=...    (ex: 7777)" +
                    "\n\tPERIOD=...      (ex: 10000)" +
                    "\n\tREWARD_TIME=... (ex: 200000)");
            System.exit(1);
        }
    }




    /**
     * @effects restituisce il valore true se il server deve essere chiuso, false altrimenti.
     */
    public static boolean getExitValue() {
        return exit.get();
    }




    /*********************************************************
     ************************* MAIN **************************
     *********************************************************/
    public static void main(String[] args) {

        // controllo sugli argomenti passati da linea di comando
        if(args == null || args.length == 0) {
            System.err.println("Usage: \"java ServerMain <configurationFile>\"");
            System.exit(1);
        }

        /** LETTURA DEL FILE DI CONFIGURAZIONE **/
        ServerMain server = new ServerMain();
        server.readConfigFile(args[0]);
        System.out.println("ConfigurationFile's content:" +
                "\n\tTCP_PORT=" + TCP_PORT +
                "\n\tUDP_PORT=" + UDP_PORT +
                "\n\tMC_IP=" + MC_IP +
                "\n\tMC_PORT=" + MC_PORT +
                "\n\tREG_HOST=" + REG_HOST +
                "\n\tREG_PORT=" + REG_PORT +
                "\n\tPERIOD=" + PERIOD +
                "\n\tREWARD_TIME=" + REWARD_TIME +
                "\n\tREWARD_PERC=" + REWARD_PERC
        );


        /** CREAZIONE DEL THREAD PER IL CALCOLO PERIODICO DELLE RICOMPENSE **/
        PeriodicRewardsTask periodicRewards = new PeriodicRewardsTask(
                new WinsomeServer(),
                MC_IP,
                MC_PORT,
                REWARD_TIME,
                REWARD_PERC
        );
        Thread rewardThread = new Thread(periodicRewards);
        rewardThread.start();

        /** CREAZIONE THREAD GESTORE DEI CLIENT **/
        Thread clientsThread = new Thread(new ClientsHandlerTask());
        clientsThread.start();

        /** CREAZIONE THREAD DI AGGIORNAMENTO PERIODICO DEI DATI IN MEMORIA **/
        Runnable memoryTask = new Runnable() {
            public void run() {
                final int TIMEOUT = 120000;
                while (!exit.get()) {
                    try {
                        // attesa periodica di 2 minuti
                        Thread.sleep(TIMEOUT);
                    } catch (InterruptedException e) {
                        WinsomeServer.updateMemory();
                        System.out.println("MemorizationThread | Memory Updated");
                        break;
                    }
                    // memorizzazione dati
                    WinsomeServer.updateMemory();
                    System.out.println("MemorizationThread | Memory Updated");
                }
            }
        };
        Thread memoryThread = new Thread(memoryTask);
        memoryThread.start();

        /** CICLO DI ATTESA DI UN INPUT DA LINEA DI COMANDO **/
        Scanner commandLineInput = new Scanner(System.in);
        while (!commandLineInput.nextLine().equals("exit"))
            System.out.println("ServerMain | Command Ignored: try with \"exit\" for close Winsome Server.");

        // setta la variabile di chiusura a true
        exit.set(true);
        System.out.println("ServerMain | The Server is shutting down...");

        /** CHIUSURA DEL THREAD PER IL CALCOLO PERIODICO DELLE RICOMPENSE E DEL THREAD GESTORE DEI CLIENT **/
        try {
            rewardThread.interrupt();
            memoryThread.interrupt();
            clientsThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(1);
        }


        /** FINE ESECUZIONE DEL SERVER **/
        System.out.println("WISOME SERVER IS CLOSED");
        System.exit(0);

    }
}









