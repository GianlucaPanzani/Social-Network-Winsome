package WinsomeClient;

import WinsomeServer.WinsomeServerInterface;

import java.io.*;
import java.net.*;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.StringTokenizer;

public class ClientMain {
    /**
     * OVERVIEW: classe che permette l'esecuzione, la gestione e la terminazione di un client
     *           del social network Winsome.
     */

    // server host
    private String SERVER_IP = null;
    // indirizzo multicast
    private String MC_IP = null;
    // porta multicast
    private int MC_PORT = 0;
    // porta tcp
    private int TCP_PORT = 0;
    // port udp
    private int UDP_PORT = 0;
    // porta oggetto remoto
    private int REG_PORT = 0;
    // host oggetto remoto
    private String REG_HOST = null;



    /**
     * @effects permette di aprire il file con nome 'configFile' e di allocare, tramite il
     *          parsing del suo contenuto, le informazioni che permettono la corretta
     *          esecuzione del client.
     *          Se le informazioni allocate dopo la lettura del file non sono sufficienti
     *          l'esecuzione verra' interrotta.
     *          Se il file contiene righe di testo con formato non previsto verranno ignorate.
     *          Se le righe di testo contengono informazioni aggiuntive rispetto a quelle
     *          previste dal formato verranno ignorate.
     *          Formato: NOME_VARIABILE=VALORE
     * @param configFile stringa che identifica il nome del file di configurazione.
     */
    private void readConfigFile(String configFile) {

        // apertura di un Reader sul file di configurazione
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {

            // ciclo di lettura delle righe del file (in caso di formato errato di una riga del file, questa viene ignorata)
            String line;
            while((line = reader.readLine()) != null) {
                StringTokenizer tokens = new StringTokenizer(line,"=");
                String key, value;
                try {
                    key = tokens.nextToken();
                    value = tokens.nextToken();
                } catch (NoSuchElementException e) {
                    // caso di riga vuota o in cui non abbiamo nessun "="
                    continue;
                }

                // switch case per allocazione dei valori letti (con controlli di correttezza di formato)
                try {
                    // inizio switch
                    switch (key) {
                        case "SERVER_IP": {
                            tokens = new StringTokenizer(value, ".");
                            if (!value.equals("localhost") && tokens.countTokens() != 4)
                                continue;
                            SERVER_IP = value;
                            break;
                        }
                        case "MC_IP": {
                            tokens = new StringTokenizer(value, ".");
                            if (!value.equals("localhost") && tokens.countTokens() != 4)
                                continue;
                            MC_IP = value;
                            break;
                        }
                        case "TCP_PORT": {
                            TCP_PORT = Integer.parseInt(value);
                            if (TCP_PORT < 1024 || TCP_PORT > 65535)
                                TCP_PORT = 0;
                            break;
                        }
                        case "UDP_PORT": {
                            UDP_PORT = Integer.parseInt(value);
                            if (UDP_PORT < 1024 || UDP_PORT > 65535)
                                UDP_PORT = 0;
                            break;
                        }
                        case "MC_PORT": {
                            MC_PORT = Integer.parseInt(value);
                            if (MC_PORT < 1024 || MC_PORT > 65535)
                                MC_PORT = 0;
                            break;
                        }
                        case "REG_PORT": {
                            REG_PORT = Integer.parseInt(value);
                            if (REG_PORT < 1024 || REG_PORT > 65535)
                                REG_PORT = 0;
                            break;
                        }
                        case "REG_HOST": {
                            tokens = new StringTokenizer(value, ".");
                            if (!value.equals("localhost") && tokens.countTokens() != 4)
                                continue;
                            REG_HOST = value;
                            break;
                        }
                    }
                    // fine switch
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            // lettura del file terminata
        } catch (IOException | NoSuchElementException e) {
            e.printStackTrace();
            System.exit(1);
        }

        // caso di formato del file di configurazione errato
        if(MC_PORT == 0 || TCP_PORT == 0 || UDP_PORT == 0 || REG_PORT == 0
                || SERVER_IP == null || REG_HOST == null || MC_IP == null) {
            System.err.println("Error: bad format of configuration file.");
            System.out.println("ConfigurationFile's format:" +
                    "\n\tSERVER_IP=...   (ex: 192.168.1.2)" +
                    "\n\tTCP_PORT=...    (ex: 6666)" +
                    "\n\tUDP_PORT=...    (ex: 33333)" +
                    "\n\tMC_IP=...       (ex: 239.255.32.32)" +
                    "\n\tMC_PORT=...     (ex: 44444)" +
                    "\n\tREG_HOST=...    (ex: localhost)" +
                    "\n\tREG_PORT=...    (ex: 7777)");
            System.exit(1);
        }
    }



    /**
     * @effects permette il parsing della stringa passata come parametro ottenendo un output
     *          con formato piu' comprensibile.
     *          Sostituisce le occorrenze del carattere "/" con "\n".
     * @param message stringa contenente il messaggio di cui fare il parsing.
     */
    private void parseMessage(String message) {

        // controllo parametro
        if (message == null)
            return;

        // caso di primo carattere uguale a "/" (altrimenti ignorato dalla StringTokenizer)
        if (message.startsWith("/"))
            System.out.println();

        // parsing del messaggio
        StringTokenizer tokens = new StringTokenizer(message, "/");
        while (tokens.hasMoreTokens())
            System.out.println(tokens.nextToken());

    }




    /**
     * @effects stampa i possibili comandi che l'utente puo' inserire da linea di comando.
     */
    private void printCommands() {
        System.out.println(
                "\tsearch <username>" +
                "\n\tsearch <startOfUsername>" +
                "\n\ttags <username>" +
                "\n\tnotify on" +
                "\n\tnotify off" +
                "\n\thelp" +
                "\n\tlogin <username> <password>" +
                "\n\tlogout" +
                "\n\tlist users" +
                "\n\tlist followers" +
                "\n\tlist following" +
                "\n\tfollow <username>" +
                "\n\tunfollow <username>" +
                "\n\tblog" +
                "\n\tpost <title> | <content>" +
                "\n\tshow post <idPost>" +
                "\n\tshow feed" +
                "\n\tdelete <idPost>" +
                "\n\trewin <idPost>" +
                "\n\trate <idPost> <vote>        (with: <vote> +1 or -1)" +
                "\n\tcomment <idPost> <comment>" +
                "\n\twallet" +
                "\n\twallet btc" +
                "\n\texit"
        );
    }



    /**
     * @effects permette, comunicando col server, la registrazione dell'utente sul social
     *          network Winsome.
     * @param reader oggetto che permette di ricevere le risposte dal server.
     *               [reader != null]
     * @param remoteObj oggetto remoto che permette la chiamata del metodo per la registrazione.
     *                  [remoteObj != null]
     * @param tokens oggetto che deve contenere il comando inserito dall'utente e che deve essere
     *               creato con il carattere di delimitazione " ". Secondo il formato del comando
     *               di registrazione, alla seconda chiamata del metodo nextToken() verra'
     *               restituito l'username dell'utente, mentre alla terza chiamata la password.
     *               Alle chiamate successive puo' restituire dei tags da aggiungere all'account.
     *               [tokens != null]
     */
    private boolean registration(StringTokenizer tokens, WinsomeServerInterface remoteObj, BufferedReader reader) {

        // controllo parametri
        if (tokens == null || remoteObj == null || reader == null)
            return false;

        String username, password;
        LinkedList<String> tags = new LinkedList<>();
        try {
            tokens.nextToken();
            username = tokens.nextToken();
            password = tokens.nextToken();
            while (tokens.hasMoreTokens())
                tags.add(tokens.nextToken());

        // caso di formato errato del messaggio
        } catch (NoSuchElementException e) {
            System.err.println("<<< Error: bad format of registration command." +
                    "\n\tTry with: register <username> <password> <tag1_optional> ... <tag5_optional>");
            return false;
        }

        // REGISTRAZIONE
        try {
            remoteObj.register(username, password, tags);
            try {
                System.out.println("<<< " + reader.readLine());
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return true;
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


        /** LETTURA DEL FILE DI CONFIGURAZIONE (togliere i commenti per stampare le informazioni lette) **/
        ClientMain client = new ClientMain();
        client.readConfigFile(args[0]);
        /*System.out.println("ConfigurationFile's content:" +
                "\n\tSERVER_IP=" + client.SERVER_IP +
                "\n\tTCP_PORT=" + client.TCP_PORT +
                "\n\tUDP_PORT=" + client.UDP_PORT +
                "\n\tMC_IP=" + client.MC_IP +
                "\n\tMC_PORT=" + client.MC_PORT +
                "\n\tREG_HOST=" + client.REG_HOST +
                "\n\tREG_PORT=" + client.REG_PORT);*/


        /** CREAZIONE DELL'OGGETTO REMOTO **/
        WinsomeClient winsomeClient = null;
        Registry clientRegistry = null;
        int portIncrement = 0;
        try {
            winsomeClient = new WinsomeClient();
            WinsomeClientInterface stub;
            while (true) { // scelta della prima porta non in uso a partire dalla porta TCP_PORT
                try {
                    stub = (WinsomeClientInterface) UnicastRemoteObject.exportObject(winsomeClient, 40000 + portIncrement);
                    break;
                } catch (ExportException e) {
                    portIncrement++;
                }
            }
            LocateRegistry.createRegistry(client.REG_PORT+portIncrement);
            clientRegistry = LocateRegistry.getRegistry(client.REG_PORT+portIncrement);
            clientRegistry.bind("WINSOMECLIENT"+portIncrement, stub);
        } catch (RemoteException | AlreadyBoundException e) {
            e.printStackTrace();
            System.exit(1);
        }


        /** INIZIO COMUNICAZIONE COL SERVER **/
        // inizio dello scambio di messaggi
        try (Socket socket = new Socket(InetAddress.getByName(client.SERVER_IP), client.TCP_PORT);
             Scanner commandLineInput = new Scanner(System.in);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())))) {

            /** RICEZIONE/INVIO OGGETTO REMOTO **/
            // ricezione, ricerca e memorizzazione dell'oggetto remoto del server
            String response = reader.readLine();
            StringTokenizer tokens = new StringTokenizer(response, "/");
            int serverRemoteObjPort = 0;
            String serverRemoteObjName = null;
            try {
                // nome dell'oggetto remoto
                serverRemoteObjName = tokens.nextToken();
                // porta dell'oggetto remoto
                serverRemoteObjPort = Integer.parseInt(tokens.nextToken());
            } catch (NoSuchElementException | NumberFormatException e) {
                e.printStackTrace();
                System.exit(1);
            }
            Registry registry = LocateRegistry.getRegistry(serverRemoteObjPort);
            WinsomeServerInterface remoteObj = null;
            try {
                // oggetto remoto
                remoteObj = (WinsomeServerInterface) registry.lookup(serverRemoteObjName);
            } catch (NotBoundException e) {
                e.printStackTrace();
                System.exit(1);
            }

            // invio "nome/porta" dell'oggetto remoto al server
            out.println("WINSOMECLIENT"+portIncrement + "/" + (client.REG_PORT+portIncrement));
            out.flush();


            /** CREAZIONE THREAD PER CONNESSIONE AL GRUPPO MULTICAST **/
            while (winsomeClient.MC_IP == null || winsomeClient.MC_PORT == 0)
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            MulticastConnectionTask multicastConnectionObj = new MulticastConnectionTask(winsomeClient.MC_IP, winsomeClient.MC_PORT);
            Thread multicastThread = new Thread(multicastConnectionObj);
            multicastThread.start();


            /** REGISTRAZIONE **/
            // inizializzazione delle informazioni per la registrazione
            String username = null, password = null, line;
            LinkedList<String> tags = null;

            // se l'utente e' gia' registrato non richiede la registrazione obbligatoria
            System.out.print(
                    "<<< Do you have a Winsome Account? [y/n]\n" +
                    ">>> "
            );
            line = commandLineInput.nextLine();
            while (!line.equals("y") && !line.equals("n")) {
                System.out.print(
                        "<<< Error: the response's format has to be \"y\" or \"n\"\n" +
                        ">>> "
                );
                line = commandLineInput.nextLine();
            }

            // caso di utente non ancora registrato
            if((response = line).equals("n"))
                System.out.print(
                        "<<< register <username> <password> <tag1_optional> ... <tag5_optional>\n" +
                        ">>> "
                );

            while (response.equals("n")) {
                line = commandLineInput.nextLine();

                // parsing del comando per la registrazione
                tokens = new StringTokenizer(line," ");
                tags = new LinkedList<>();
                try {
                    tokens.nextToken();
                    username = tokens.nextToken();
                    password = tokens.nextToken();
                    while (tokens.hasMoreTokens())
                        tags.add(tokens.nextToken());

                // caso di formato errato del messaggio
                } catch (NoSuchElementException e) {
                    System.err.print(
                            "<<< register <username> <password> <tag1_optional> ... <tag5_optional>\n" +
                            ">>> "
                    );
                    continue;
                }

                // se la registrazione va a buon fine esce dal ciclo
                if (client.registration(new StringTokenizer(line," "), remoteObj, reader))
                    break;
            }

            /** LOGIN (automatico alla prima registrazione) **/
            if (response.equals("n")) {
                out.println("login " + username + " " + password);
                out.flush();
                System.out.println("<<< " + reader.readLine());
            } else {
                while (true) {
                    tokens = new StringTokenizer(line, " ");
                    try {
                        tokens.nextToken();
                        username = tokens.nextToken();
                        password = tokens.nextToken();
                        out.println("login " + username + " " + password);
                        out.flush();
                        response = reader.readLine();
                        System.out.println("<<< " + response);
                        if (!response.startsWith("Login Error"))
                            break;
                        else
                            while (true) tokens.nextToken();

                        // caso di formato errato del messaggio
                    } catch (NoSuchElementException e) {
                        System.out.print("<<< login <username> <password>\n>>> ");
                        line = commandLineInput.nextLine();
                        if (line.equals("exit")) {
                            multicastConnectionObj.closeMulticast();
                            System.out.println(">>> MulticastConnectionTask Thread: closed");
                            out.println("exit");
                            out.flush();
                            System.out.println(">>> WINSOME CLIENT CLOSED");
                            System.exit(0);
                        }
                    }
                }
            }

            /** REGISTRAZIONE ALLE CALLBACK (automatica) **/
            try {
                remoteObj.turnOnNotify(winsomeClient);
                System.out.println("<<< " + reader.readLine());
            } catch (RemoteException e) {
                e.printStackTrace();
            }


            /** SCAMBIO DI MESSAGGI COL SERVER (CLI) **/

            while (true) {

                // lettura del comando
                System.out.print(">>> ");
                while ((line = commandLineInput.nextLine()).equals(""))
                    System.out.print(">>> ");

                // caso di uscita
                if (line.equals("exit") || line.equals("logout")) {
                    out.println("logout " + username + " " + password);
                    out.flush();
                    break;

                // caso di comando per l'attivazione delle notifiche
                } else if (line.equals("notify on")) {
                    remoteObj.turnOnNotify(winsomeClient);

                // caso di comando per la disattivazione delle notifiche
                } else if (line.equals("notify off")) {
                    remoteObj.turnOffNotify(winsomeClient);

                // caso di comando per la stampa dei followers (memorizzati localmente al winsome client)
                } else if(line.equals("list followers")) {
                    winsomeClient.listFollowers();
                    continue;

                // caso di comando per la stampa dei comandi inseribili dall'utente
                } else if (line.equals("help")) {
                    client.printCommands();
                    continue;

                // caso di comando da inoltrare al server
                } else {
                    out.println(line);
                    out.flush();

                    // caso di parsing del messaggio di risposta del server
                    if (line.startsWith("list") || line.startsWith("show") || line.startsWith("wallet")
                            || line.equals("blog") || line.startsWith("tags") || line.startsWith("search")) {
                        System.out.print("<<< ");
                        client.parseMessage(reader.readLine());
                        continue;
                    }

                }

                // ricezione/stampa della risposta dal server
                System.out.println("<<< " + reader.readLine());
            }
            out.println("exit");
            out.flush();

            // rimozione automatica dagli utenti registrati alle callback
            remoteObj.turnOffNotify(winsomeClient);

            // setta il valore di uscita per il thread di ricezione dei messaggi multicast
            multicastConnectionObj.closeMulticast();
            System.out.print(">>> MulticastConnectionTask Thread: closed\n>>> ");

            /** UNBIND DELL'OGGETTO REMOTO **/
            try {
                clientRegistry.unbind("WINSOMECLIENT" + portIncrement);
            } catch (NotBoundException e) {
                e.printStackTrace();
                System.exit(1);
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        /** FINE COMUNICAZIONE COL SERVER **/
        System.out.println("WINSOME CLIENT CLOSED");
        System.exit(0);
    }
}
