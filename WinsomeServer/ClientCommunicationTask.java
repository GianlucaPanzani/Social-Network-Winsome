package WinsomeServer;

import WinsomeClient.WinsomeClientInterface;

import java.security.InvalidParameterException;
import java.util.*;
import java.io.*;
import java.net.*;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ClientCommunicationTask implements Runnable {
    /**
     * OVERVIEW: classe che permette la gestione di 1 client per ogni thread attivato su
     *           un'istanza di questa classe.
     */

    // oggetto che permette comunicazione TCP col client
    private final Socket socket;
    // oggetto usato per invocare i metodi del social network Winsome
    private final WinsomeServer winsomeServer;
    // valore usato per incrementare la porta di creazione del remote object
    private final int increment;




    /**
     * @effects costruttore che inizializza i campi della classe.
     * @param winsomeServer oggetto usato per invocare i metodi del social network Winsome.
     *                      [winsomeServer != null]
     * @param increment valore usato per incrementare la porta di creazione del remote object.
     *                  [increment >= 0]
     * @param socket socket che permette di comunicare tramite connessione TCP col client.
     *               [socket != null]
     * @throws InvalidParameterException se anche solo una delle precendizioni sui parametri non vale.
     */
    public ClientCommunicationTask(WinsomeServer winsomeServer, Socket socket, int increment) throws InvalidParameterException {

        // controllo parametri
        if(winsomeServer == null || socket == null || increment < 0) {
            System.err.println("ClientCommunicationTask Error: bad parameters.");
            throw new InvalidParameterException();
        }

        // inizializzazione campi privati
        this.winsomeServer = winsomeServer;
        this.socket = socket;
        this.increment = increment;

    }




    /**
     * @effects permette di scegliere il metodo da invocare per effettuare l'operazione
     *          richiesta dall'utente contenuta nel messaggio 'message'.
     *          Se il formato del messaggio non e' corretto il metodo cerca di intuire quale
     *          richiesta volesse fare ed invia quindi un messaggio contenente il comando
     *          suggerito all'utente col giusto formato.
     *          Se non e' stato possibile intuire il comando restituisce un messaggio d'errore.
     * @param out oggetto che permette di inviare messaggi di risposta al client.
     *            [out != null]
     * @param winsomeServer oggetto che permette l'invocazione del metodo richiesto dall'utente
     *                      sulla base del comando da lui inserito.
     *                      [winsomeServer != null]
     * @param message stringa contenente il messaggio ricevuto dall'utente.
     *                [message != null]
     */
    private void selectMethod(PrintWriter out, WinsomeServer winsomeServer, String message) {

        // controllo sui parametri
        if(out == null)
            return;
        if(winsomeServer == null || message == null) {
            out.println("Error: bad message's format");
            out.flush();
            return;
        }

        // parsing del messaggio + scelta della chiamata di metodo
        StringTokenizer tokens = new StringTokenizer(message, " ");
        String method = null;
        try {
            method = tokens.nextToken();
            switch (method) {
                case "login": { winsomeServer.login(tokens.nextToken(), tokens.nextToken());        break; }
                case "logout": { winsomeServer.logout(winsomeServer.getUsername());                 break; }
                case "delete": { winsomeServer.deletePost(tokens.nextToken());                      break; }
                case "rewin": { winsomeServer.rewinPost(tokens.nextToken());                        break; }
                case "rate": { winsomeServer.rate(tokens.nextToken(),tokens.nextToken());           break; }
                case "blog": { winsomeServer.viewBlog();                                            break; }
                case "tags": { winsomeServer.getTags(tokens.nextToken());                           break; }
                case "search": { winsomeServer.searchUser(tokens.nextToken());                      break; }
                case "wallet": {
                    String token = null;
                    try {
                        token = tokens.nextToken();
                    } catch (NoSuchElementException e) {
                        winsomeServer.getWallet();
                        break;
                    }
                    if (token.equals("btc"))
                        winsomeServer.getWalletInBitcoin();
                    else
                        while (true) tokens.nextToken();
                    break;
                }
                case "comment": {
                    String idPost = tokens.nextToken();
                    String comment = null;
                    try {
                        comment = message.replaceAll("comment "+idPost+" ","");
                    } catch (IndexOutOfBoundsException e) {
                        while (true) tokens.nextToken();
                    }
                    winsomeServer.addComment(idPost,comment);
                    break;
                }
                case "follow": {
                    String username = tokens.nextToken();
                    WinsomeClientInterface clientRemoteObj = winsomeServer.getRemoteObject(username);
                    if (winsomeServer.followUser(username) && clientRemoteObj != null)
                        try {
                            clientRemoteObj.oneMoreFollower(winsomeServer.getUsername());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    break;
                }
                case "unfollow": {
                    String username = tokens.nextToken();
                    WinsomeClientInterface clientRemoteObj = winsomeServer.getRemoteObject(username);
                    if (winsomeServer.unFollowUser(username) && clientRemoteObj != null) {
                        try {
                            clientRemoteObj.oneLessFollower(winsomeServer.getUsername());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }
                case "post": {
                    StringTokenizer tokens2 = new StringTokenizer(message.substring(message.indexOf(" ")), "|");
                    winsomeServer.createPost(tokens2.nextToken(), tokens2.nextToken());
                    break;
                }
                case "show": {
                    String token = tokens.nextToken();
                    if (token.equals("post"))
                        winsomeServer.showPost(tokens.nextToken());
                    else if (token.equals("feed"))
                        winsomeServer.showFeed();
                    else
                        while (true) tokens.nextToken();
                    break;
                }
                case "list": {
                    String token = tokens.nextToken();
                    if(token.equals("following")) {
                        List<String> followingList = winsomeServer.listFollowing();
                        message = "/\tFollowing List:/";
                        if (followingList != null)
                            for (String s : followingList)
                                message = message + "/\t" + s;
                        out.println(message);
                    } else if(token.equals("users")) {
                        List<String> usersList = winsomeServer.listUsers();
                        message = "/\tUsers with common tags List:/";
                        if (usersList != null)
                            for (String s : usersList)
                                message = message + "/\t" + s;
                        out.println(message);
                    } else {
                        while (true) tokens.nextToken();
                    }
                    break;
                }
                default: {
                    out.println("Error: bad message's format");
                    break;
                }
            }

        } catch (NoSuchElementException e) {

            // switch per reperire il comando con formato corretto da suggerire all'utente
            switch (method) {
                case "search": { out.println("search <username> OR search <startOfUsername>");  break; }
                case "tags": { out.println("tags <username>");                                  break; }
                case "login": { out.println("login <username> <password>");                     break; }
                case "list": { out.println("list following OR list followers OR list users");   break; }
                case "follow": { out.println("follow <username>");                              break; }
                case "unfollow": { out.println("unfollow <username>");                          break; }
                case "delete": { out.println("delete <idPost>");                                break; }
                case "rewin": { out.println("rewin <idPost>");                                  break; }
                case "post": { out.println("post <title> | <text>");                            break; }
                case "rate": { out.println("rate <idPost> <vote>");                             break; }
                case "comment": { out.println("comment <idPost> <comment>");                    break; }
                case "wallet": { out.println("wallet btc");                                     break; }
                case "create": { out.println("create <title> | <content>");                     break; }
                case "show": { out.println("show post <idPost> OR show feed");                  break; }
                default: { out.println("Error: bad message's format");                          break; }
            }

        }
        out.flush();
    }




    /**
     * @effects gestisce lo scambio di messaggi con un client finche' non verra' settato a
     *          true il valore per la terminazione del ciclo.
     */
    public void run() {
        System.out.println("ClientCommunicationTask | " + Thread.currentThread() +": Connection established with " +
                socket.getInetAddress().getHostAddress()+"/"+socket.getPort());

        /** INIZIO DELLA COMUNICAZIONE COL CLIENT **/
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())))) {

            /** CREAZIONE DELL'OGGETTO REMOTO (DEL SERVER) **/
            Registry registry = null;
            try {
                WinsomeServerInterface stub = (WinsomeServerInterface) UnicastRemoteObject.exportObject(
                        winsomeServer,
                        1099+increment
                );
                try {
                    LocateRegistry.createRegistry(ServerMain.REG_PORT+increment);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                registry = LocateRegistry.getRegistry(ServerMain.REG_PORT+increment);
                registry.bind("WINSOMESERVER"+increment, stub);
            } catch (RemoteException | AlreadyBoundException | NullPointerException e) {
                e.printStackTrace();
            }

            /** INVIO/RICEZIONE DELLE INFORMAZIONI DI ACCESSO AL REMOTE OBJECT **/
            // invio dell'oggetto remoto del server
            out.println("WINSOMESERVER"+increment + "/" + (ServerMain.REG_PORT+increment));
            out.flush();

            // ricezione e parsing dell'oggetto remoto del client
            StringTokenizer tokens = new StringTokenizer(reader.readLine(), "/");
            try {
                // nome oggetto remoto
                String clientRemoteObjName = tokens.nextToken();
                // porta registry oggetto remoto
                int clientRemoteObjPort = Integer.parseInt(tokens.nextToken());

                // ricerca oggetto remoto
                Registry clientRegistry = LocateRegistry.getRegistry(clientRemoteObjPort);
                WinsomeClientInterface remoteObj = (WinsomeClientInterface) clientRegistry.lookup(clientRemoteObjName);

                // invio informazioni necessarie alla connessione dei client sul gruppo multicast
                remoteObj.setMulticastInfo(ServerMain.MC_IP, ServerMain.MC_PORT);

            } catch (NotBoundException | NumberFormatException | NoSuchElementException e) {
                e.printStackTrace();
            }


            /** SETTING DELL'OUTPUT WRITER DELLA CLASSE WINSOME SERVER (in modo da comunicare col client) **/
            winsomeServer.setOutputWriter(out);


            /** SCAMBIO DEI MESSAGGI COL CLIENT **/
            String message;
            while (!(message = reader.readLine()).equals("exit"))
                selectMethod(out, winsomeServer, message);


            /** UNBIND DELL'OGGETTO REMOTO **/
            try {
                registry.unbind("WINSOMESERVER"+increment);
            } catch (NotBoundException e) {
                e.printStackTrace();
            }

            // chiusura del canale di comunicazione col client
            winsomeServer.closeWriter();

        } catch (IOException e) {
            e.printStackTrace();
        }


        /** CHIUSURA SOCKET **/
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        /** FINE DELLA COMUNICAZIONE COL CLIENT **/
        System.out.println("ClientCommunicationTask | " + Thread.currentThread() +": Connection with " +
                socket.getInetAddress().getHostAddress()+"/"+socket.getPort() + " lost");

    }
}











