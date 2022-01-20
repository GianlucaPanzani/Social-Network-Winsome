package WinsomeServer;

import WinsomeClient.WinsomeClientInterface;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javax.naming.LimitExceededException;
import java.io.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.security.InvalidParameterException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WinsomeServer extends RemoteObject implements WinsomeServerInterface {
    /**
     * OVERVIEW: classe che modella un server che si occupa della gestione di un social network.
     *           Permette a chi la utilizza di effettuare una registrazione permanente sulla rete
     *           sociale, di effettuare il login, di seguire altri utenti, di creare post e di
     *           visualizzare i post degli utenti seguiti.
     *           Calcola inoltre una ricompensa sulla base delle azioni effettuate sulla rete sociale.
     *           Inoltre invia messaggi al client usando come formato del messaggio il carattere
     *           "/" come divisorio tra una riga e l'altra del messaggio stesso (che quindi
     *           sostituisce i "\n" permettendo di inviare e far ricevere il messaggio per intero).
     */


    /***** STRUTTURE DATI CONDIVISE *****/
    /** postMap(username) = postsSet = {(ID1,Post1),...,(IDn,Postn)} t.c. [forall IDi != IDj . Posti != Postj] **/
    private static final Map<String, Map<String,Post>> postMap = new ConcurrentHashMap<>();

    /** followersMap(username) = {follower1,...,followerN} t.c. [forall i != j . followeri != followerj] **/
    private static final Map<String, Set<String>> followersMap = new HashMap<>();

    /** followsMap(username) = {follow1,...,followN} t.c. [forall i != j . followi != followj] **/
    private static final Map<String, Set<String>> followsMap = new HashMap<>();

    /** tagsMap(username) = [tag1,...,tagN] t.c. [forall i != j . tagi != tagj]  (con N <= 5) **/
    private static final Map<String, List<String>> tagsMap = new HashMap<>();

    /** usersForCallback = [(user1,remoteObj1), ... ,(userN,remoteObjN)]   t.c.  [forall i != j . useri != userj] **/
    private static final Map<String,WinsomeClientInterface> usersForCallback = new HashMap<>();

    /** registeredUsers = {(username1,password1), ... ,(usernameN,passwordN)} **/
    private static final List<User> registeredUsers = new LinkedList<>();

    /** walletMap(username) = <total_money, [transaction1,...,transactionN]> **/
    private static final Map<String,Wallet> walletMap = new HashMap<>();

    /** loggedMap(username) = Bool      t.c. Bool appartiene a {true,false} **/
    private static final Map<String,Boolean> loggedMap = new HashMap<>();

    /** oggetto usato per invocare i metodi utili al calcolo delle ricompense **/
    private static final PeriodicRewardsTask periodicRewards = new PeriodicRewardsTask();

    /** nome dei file usati per la memorizzazione dei dati **/
    private static final String usersFileName = "WinsomeServer/Database/registeredUsers";
    private static final String followersFileName = "WinsomeServer/Database/usersFollowers";
    private static final String followsFileName = "WinsomeServer/Database/usersFollows";
    private static final String postsFileName = "WinsomeServer/Database/usersPosts";
    private static final String tagsFileName = "WinsomeServer/Database/usersTags";
    private static final String walletsFileName = "WinsomeServer/Database/usersWallet";

    /** contatore di oggetti di tipo WinsomeServer **/
    private static int counter = 0;

    /***** STRUTTURE DATI LOCALI *****/
    // oggetto che permette di inviare messaggi di testo
    private PrintWriter out = null;
    // oggetto con le informazioni utente
    private User user = null;
    // variabile che indica se l'utente e' loggato sul social (true) oppure no (false)
    private boolean loggedIn = false;




    /*******************************
     ***** CLASSE PRIVATA USER *****
     *******************************/
    private class User {
        /**
         * OVERVIEW: classe privata immutable che modella un utente identificato da username e password.
         */
        // username dell'utente
        public final String username;
        // password dell'utente
        public final String password;


        /**
         * @effects inizializza i campi 'username' e 'password' della classe.
         * @param username stringa univoca identificativa dell'utente.
         * @param password stringa che permette all'utente la registrazione e il login.
         */
        public User(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }




    /**
     * @effects costruttore che inizializza i campi privati della classe e recupera (a ogni riavvio)
     *          le informazioni degli utenti (registrazioni, followers, following, wallet, tags e
     *          post condivisi).
     */
    public WinsomeServer() {
        super();

        // incremento contatore di oggetti Winsome Server
        counter++;

        // caso in cui non e' il primo oggetto della classe
        if (counter != 1)
            return;

        // oggetto Gson per la lettura dei file in formato JSON
        Gson gson = new Gson();

        // recupero dati utenti registrati
        try (BufferedReader reader = new BufferedReader(new FileReader(usersFileName))) {
            Type ListUsersType = new TypeToken<List<User>>(){}.getType();
            List<User> users = gson.fromJson(reader, ListUsersType);
            if (users != null)
                synchronized (registeredUsers) { registeredUsers.addAll(users); }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // recupero dati followers
        try (BufferedReader reader = new BufferedReader(new FileReader(followersFileName))) {
            Type MapSetStringType = new TypeToken<Map<String,Set<String>>>(){}.getType();
            Map<String,Set<String>> followers = gson.fromJson(reader, MapSetStringType);
            if (followers != null)
                synchronized (followersMap) { followersMap.putAll(followers); }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // recupero dati following
        try (BufferedReader reader = new BufferedReader(new FileReader(followsFileName))) {
            Type MapSetStringType = new TypeToken<Map<String,Set<String>>>(){}.getType();
            Map<String,Set<String>> following = gson.fromJson(reader, MapSetStringType);
            if (following != null)
                synchronized (followsMap) { followsMap.putAll(following); }
        } catch (IOException e) {
            e.printStackTrace();
        }


        // recupero dati post
        try (BufferedReader reader = new BufferedReader(new FileReader(postsFileName))) {
            Type PostMapType = new TypeToken<Map<String,Map<String,Post>>>(){}.getType();
            Map<String,Map<String,Post>> posts = gson.fromJson(reader, PostMapType);
            if (posts != null)
                postMap.putAll(posts);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // recupero del maggiore id dei post precedentemente inseriti
        int maxId = 0;
        for (Map.Entry<String,Map<String,Post>> usersPosts : postMap.entrySet())
            for (Map.Entry<String, Post> post : usersPosts.getValue().entrySet()) {
                int idValue = Integer.parseInt(post.getValue().getId());
                if (idValue > maxId) maxId = idValue;
            }
        Post.setNextId(maxId+1);

        // recupero dati wallet
        try (BufferedReader reader = new BufferedReader(new FileReader(walletsFileName))) {
            Type WalletMapType = new TypeToken<Map<String,Wallet>>(){}.getType();
            Map<String,Wallet> wallets = gson.fromJson(reader, WalletMapType);
            if (wallets != null)
                synchronized (walletMap) { walletMap.putAll(wallets); }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // recupero dati tags
        try (BufferedReader reader = new BufferedReader(new FileReader(tagsFileName))) {
            Type TagsMapType = new TypeToken<Map<String,List<String>>>(){}.getType();
            Map<String,List<String>> tags = gson.fromJson(reader, TagsMapType);
            if (tags != null)
                synchronized (tagsMap) { tagsMap.putAll(tags); }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }





    /**
     * @effects metodo con accesso atomico alla struttura dati pubblica "registeredUsers" che
     *          restituisce true se la struttura dati contiene l'utente u passato come parametro
     *          (e quindi se l'utente e' registrato), false altrimenti.
     * @param u utente di cui vogliamo verificare la corretta registrazione con username e password.
     *	        [u != null]
     */
    private synchronized boolean registeredContains(User u) {

        // controllo parametro
        if (u == null)
            return false;

        // ricerca utente tra gli utenti registrati
        for (User user : registeredUsers)
            if (u.username.equals(user.username) && u.password.equals(user.password))
                return true;

        // caso di utente non trovato
        return false;
    }




    /**
     * @effects restituisce true se l'utente identificato dal parametro 'username' ha
     *          un account su winsome, false altrimenti.
     * @param username utente di cui vogliamo verificare la registrazione.
     *                 [username != null]
     */
    private synchronized boolean isRegistered(String username) {

        // controllo parametro
        if (username == null)
            return false;

        // ricerca dell'utente tra gli utenti registrati
        for (User user : registeredUsers)
            // caso di utente trovato
            if (username.equals(user.username))
                return true;

        // caso di utente non trovato
        return false;
    }




    /**
     * @effects ricerca tutti gli username degli utenti registrati che iniziano per 'start' e invia
     *          al client una stringa contenente i risultati trovati. Se l'utente ricerca un username
     *	        completo verra' restituito tale username se e' presente tra gli utenti registrati.
     * @param start prima parte della stringa che compone l'username che si sta cercando.
     *              [(start != null) && (start != "")]
     */
    public void searchUser(String start) {

        // controllo parametro
        if (start == null || start.equals("") || !loggedIn) {
            out.println("SearchUser Error: bad parameter or not logged");
            out.flush();
            return;
        }

        String message = "";
        synchronized (registeredUsers) {
            // ricerca degli username (degli utenti registrati) che iniziano per 'start'
            for (User u : registeredUsers)
                if (u.username.startsWith(start))
                    message = message + "/\t" + u.username;
        }
        message = message + "/";

        // invio del messaggio al client
        out.println(message);
        out.flush();

    }




    /**
     * @effects setta il l'oggetto di tipo PrintWriter, usato per la restituzione di messaggi di risposta
     *          al momento dell'invocazione dei metodi della classe, uguale all'oggetto 'out' passato
     *          come parametro.
     * @param out Permette l'invio di messaggi di risposta al momento dell'invocazione dei metodi di cui
     *            la classe dispone.
     *            [out != null]
     */
    protected void setOutputWriter(PrintWriter out) {
        if(out != null)
            this.out = out;
    }




    /**
     * @effects chiude il canale di comunicazione col client.
     */
    protected void closeWriter() {
        if (out != null)
            out.close();
    }




    /**
     * @effects restituisce true se la memorizzazione dei dati e' andata a buon fine,
     *          altrimenti false.
     */
    protected synchronized static boolean updateMemory() {

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // memorizzazione utenti registrati
        try (BufferedWriter writer = new BufferedWriter(new PrintWriter(usersFileName))) {
            Type RegisteredUsersType = new TypeToken<List<User>>(){}.getType();
            String users = gson.toJson(registeredUsers, RegisteredUsersType);
            writer.write(users);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // memorizzazione followers
        try (BufferedWriter writer = new BufferedWriter(new PrintWriter(followersFileName))) {
            Type FollowersMapType = new TypeToken<Map<String,Set<String>>>(){}.getType();
            String followers = gson.toJson(followersMap, FollowersMapType);
            if (followers != null) {
                writer.write(followers);
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // memorizzazione following
        try (BufferedWriter writer = new BufferedWriter(new PrintWriter(followsFileName))) {
            Type FollowsMapType = new TypeToken<Map<String,Set<String>>>(){}.getType();
            String follows = gson.toJson(followsMap, FollowsMapType);
            if (follows != null) {
                writer.write(follows);
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // memorizzazione post
        try (BufferedWriter writer = new BufferedWriter(new PrintWriter(postsFileName))) {
            Type PostMapType = new TypeToken<Map<String,Map<String,Post>>>(){}.getType();
            String posts = gson.toJson(postMap, PostMapType);
            if (posts != null) {
                writer.write(posts);
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // memorizzazione wallet
        try (BufferedWriter writer = new BufferedWriter(new PrintWriter(walletsFileName))) {
            Type WalletMapType = new TypeToken<Map<String,Wallet>>(){}.getType();
            String wallets = gson.toJson(walletMap, WalletMapType);
            if (wallets != null) {
                writer.write(wallets);
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // memorizzazione tags
        try (BufferedWriter writer = new BufferedWriter(new PrintWriter(tagsFileName))) {
            Type WalletMapType = new TypeToken<Map<String,List<String>>>(){}.getType();
            String tags = gson.toJson(tagsMap, WalletMapType);
            if (tags != null) {
                writer.write(tags);
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }




    /**
     * @effects permette la registrazione da remoto (e permanente) dell'utente identificato dal nome utente
     *          'username' e dalla password 'password'. Gli vengono inoltre associati i tag presenti nella
     *          lista di tag 'tags' (con un massimo di 5 tags diversi tra loro).
     * @param username stringa univoca e identificativa dell'utente nel social network.
     *                 [(username != null) && (3 < username.lenght < 21) && !socialNetwork.contains(username)]
     * @param password stringa che permette all'utente la registrazione e il login sul social network.
     *                 [(password != null) && (3 < password < 21)]
     * @param tags lista di stringhe (puo' essere anche null o vuota).
     * @throws RemoteException se avviene un'errore dovuto ad RMI (Remote Method Invocation).
     */
    public synchronized boolean register(String username, String password, LinkedList<String> tags) throws RemoteException {

        // controllo parametri
        if(username == null || username.length() < 3 || username.length() > 20 || user != null ||
           password == null || password.length() < 3 || password.length() > 20) {
            out.println("Registration Error: bad parameter's format");
            out.flush();
            return false;
        }

        // creazione dell'utente
        user = new User(username,password);

        // caso di utente gia' registrato
        if(registeredContains(user) || loggedIn) {
            this.user = null;
            out.println("Registration Error: username already exists (Suggestions: are you already registered?)");
            out.flush();
            return false;
        }

        // selezione dei tags (max 5)
        LinkedList<String> tagsList = new LinkedList<>();
        if(tags != null) {

            // ciclo che prende al piu' 5 tag (scarta i doppioni o i tag che non rispettano il formato)
            for (int i = 0; i < 5 && i < tags.size(); ++i) {

                // caso di tag da scartare
                if (tags.get(i).length() == 0 || tags.get(i).length() > 100 || tagsList.contains(tags.get(i))) {
                    tags.remove(i);
                    i--;
                    continue;
                }

                // caso di tag da considerare
                tagsList.add(tags.get(i).toLowerCase(Locale.ENGLISH));
            }
        }

        // aggiornamento delle strutture dati
        registeredUsers.add(user);
        tagsMap.put(username, tagsList);
        followersMap.put(username, new HashSet<>());
        followsMap.put(username, new HashSet<>());
        walletMap.put(username, new Wallet(username));
        loggedMap.put(username, false);

        // messaggio di risposta al client
        out.println("Registration confirmed");
        out.flush();

        return true;
    }




    /**
     * @effects restituisce l'username dell'utente o null se non e' ancora stato inserito.
     */
    protected String getUsername() {

        // caso di utente non ancora registrato
        if (user == null)
            return null;

        return user.username;
    }




    /**
     * @effects restituisce l'oggetto remoto dell'utente passato come parametro oppure null
     *          se l'utente non e' registrato alle callback, se il nome utente e' null o se
     *          il codice di autorizzazione fornito non e' valido.
     * @param username stringa identificativa dell'utente di cui viene restituito l'oggetto remoto.
     *                 [(username != null) && (0 < username.lenght < 21) && !socialNetwork.contains(username)]
     */
    protected WinsomeClientInterface getRemoteObject(String username) {

        // controllo parametri + controllo esistenza registrazione utente alla callback
        if (username == null || !usersForCallback.containsKey(username))
            return null;

        // restituzione dell'oggetto remoto
        return usersForCallback.get(username);

    }





    /**
     * @effects aggiunge l'utente alla lista di utenti da notificare al momento del verificarsi di
     *          alcuni eventi (es: nuovo follower).
     * @param clientRemoteObj oggetto remoto dell'utente da registrare per le notifiche (callback).
     *                        [clientRemoteObj != null]
     * @throws RemoteException se avviene un'errore dovuto ad RMI (Remote Method Invocation).
     */
    public void turnOnNotify(WinsomeClientInterface clientRemoteObj) throws RemoteException {

        // controllo parametro
        if(clientRemoteObj == null || user == null || !isRegistered(user.username)) {
            out.println("TurnOnNotify Error: bad parameter");
            out.flush();
            return;
        }

        synchronized (usersForCallback) {

            // caso di assenza di registrazione dell'utente alle callback (con conseguente aggiunta)
            if (!usersForCallback.containsKey(user.username)) {
                usersForCallback.put(user.username, clientRemoteObj);
                out.println("Notification On");
            } else {
                out.println("Notification already on");
            }

            // aggiornamento dei followers locali al client (in caso di login)
            synchronized (followersMap) {
                // caso in cui l'utente ha follower
                if (followersMap.containsKey(user.username))
                    try {
                        usersForCallback.get(user.username).updateFollowers(new LinkedList<>(followersMap.get(user.username)));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
            }
        }

        out.flush();
    }




    /**
     * @effects rimuove l'utente dalla lista di utenti che si erano registrati per la ricezione
     *          di notifiche al momento del verificarsi di alcuni eventi (es: nuovo follower).
     * @param clientRemoteObj oggetto remoto dell'utente da rimuovere dai registrati per le
     *                        notifiche (callback).
     *                        [clientRemoteObj != null]
     * @throws RemoteException se avviene un'errore dovuto ad RMI (Remote Method Invocation).
     */
    public void turnOffNotify(WinsomeClientInterface clientRemoteObj) throws RemoteException {

        // controllo parametro
        if(clientRemoteObj == null) {
            out.println("TurnOffNotify Error: bad parameter");
            out.flush();
            return;
        }

        synchronized (usersForCallback) {
            // caso di presenza di registrazione dell'utente alle callback (con conseguente rimozione)
            if (usersForCallback.containsKey(user.username)) {
                usersForCallback.remove(user.username);
                out.println("Notification Off");
            } else {
                out.println("Notification is already Off");
            }
        }

        out.flush();
    }




    /**
     * @effects permette all'utente (registrato) di effettuare il login in modo tale da accedere
     *          alle varie funzionalita' offerte dalla classe.
     * @param username stringa univoca e identificativa dell'utente nel social network.
     *                 [(username != null) && (0 < username.lenght < 21) && !socialNetwork.contains(username)]
     * @param password stringa che permette all'utente la registrazione e il login sul social network.
     *		       [password != null]
     */
    public synchronized void login(String username, String password) {

        // controllo parametri
        if(username == null || username.length() == 0 || password == null || password.length() == 0) {
            out.println("Login Error: bad parameter's format");
            out.flush();
            return;
        }

        // caso di utente non registrato (che quindi non puo' effettuare il login)
        if (!isRegistered(username)) {
            out.println("Login Error: you aren't registered.");
            out.flush();
            return;
        }

        // controllo della password
        for (User u : registeredUsers)
            if (u.username.equals(username) && !u.password.equals(password)) {
                out.println("Login Error: password isn't correct.");
                out.flush();
                return;
            }

        // caso di utente gia' loggato su un altro dispositivo
        if (loggedMap.containsKey(username) && loggedMap.get(username)) {
            out.println("Login Error: you are logged on another device.");
            out.flush();
            return;
        }

        // caso di utente registrato precedentemente (che quindi non ha ripetuto la registrazione)
        if (user == null)
            user = new User(username, password);
        walletMap.putIfAbsent(username, new Wallet(username));

        // aggiornamento della logged map
        loggedMap.put(username, true);

        // caso di utente gia' loggato localmente
        if(loggedIn) {
            out.println("Login confirmed (you were already logged)");
            out.flush();
            return;
        }

        // aggiornamento struttura dati locale
        loggedIn = true;

        // messaggio di risposta al client
        out.println("Login confirmed");
        out.flush();
    }




    /**
     * @effects permette di effettuare il logout dal social network impedendo pero' di accedere alle
     *          funzionalita' da questo offerte.
     * @param username stringa univoca e identificativa dell'utente nel social network.
     *                 [(username != null) && (0 < username.lenght < 21)]
     */
    public void logout(String username) {

        // controllo parametri
        if(username == null || !user.username.equals(username)) {
            out.println("Logout Error: bad parameter");
            out.flush();
            return;
        }

        // caso di utente che non ha ancora fatto il login
        if(!loggedIn) {
            out.println("Logout Error: user [" + username + "] isn't logged in");
            out.flush();
            return;
        }

        // aggiornamento dati
        loggedMap.put(username, false);
        loggedIn = false;

        // messaggio di risposta al client
        out.println("Logout confirmed");
        out.flush();
    }




    /**
     * @effects restituisce i tags dell'utente passato come parametro se e' presente tra i
     *          followers dell'utente che ha fatto la richiesta.
     * @param username nome dell'utente di cui si vuole ottenere i tags.
     * 	      [(username != null) && (this.follows.contains(username))]
     */
    public void getTags(String username) {

        // controllo parametro
        if (username == null || !loggedIn) {
            out.println("GetTags Error: bad parameter or not logged");
            out.flush();
            return;
        }

        String message = "";
        synchronized (tagsMap) {
            // recupero dei tags dell'utente
            if (tagsMap.containsKey(username))
                for (String tag : tagsMap.get(username))
                    message = message + "/\t" + tag;
            else
                message = "[" + username + "] hasn't tags";
        }
        message = message + "/";

        // caso in cui si vuole visualizzare i nostri tags
        if (user != null && username.equals(user.username)) {
            out.println(message);
            out.flush();
            return;
        }

        synchronized (followsMap) {
            // caso in cui non segue l'utente di cui vuole visualizzare i tags
            if (!followsMap.containsKey(user.username) || !followsMap.get(user.username).contains(username)) {
                out.println("GetTags Error: you don't follow this user");
                out.flush();
                return;
            }
        }

        // invio del messaggio al client
        out.println(message);
        out.flush();

    }




    /**
     * @effects restituisce la lista degli utenti aventi almeno un tag in comune
     *          con l'utente che ha invocato il metodo.
     */
    public List<String> listUsers() {

        // controllo login
        if (!loggedIn)
            return null;

        // lista degli utenti da restituire
        List<String> returnUsers = new LinkedList<>();

        synchronized (tagsMap) {

            // lista dei tag dell'utente che ha invocato il metodo
            List<String> tags = tagsMap.get(user.username);

            // per ogni utente si va a controllare se almeno uno dei suoi tag e' in comune con quelli dell'utente chiamante
            for (Map.Entry<String,List<String>> userTagsList : tagsMap.entrySet()) {
                String user_i = userTagsList.getKey();
                List<String> list = userTagsList.getValue();
                for (String tag : tags)
                    if (list.contains(tag) && !user_i.equals(user.username) && !returnUsers.contains(user_i)) {
                        returnUsers.add(user_i);
                        break;
                    }
            }
        }

        return returnUsers;
    }




    /**
     * @effects restituisce la lista di utenti seguiti dall'utente che ha invocato il metodo.
     */
    public synchronized List<String> listFollowing() {

        // controllo condizioni utente
        if (!loggedIn || !followersMap.containsKey(user.username))
            return null;

        return List.copyOf(followsMap.get(user.username));
    }




    /**
     * @effects aggiunge l'utente identificato da 'username' ai propri utenti seguiti e restituisce
     *          true, altrimenti, se il parametro non e' corretto o se l'utente e' gia' tra gli utenti
     *          seguiti, restituisce false.
     * @param username stringa univoca e identificativa dell'utente nel social network.
     *                 [(username != null) && (0 < username.lenght < 21) && socialNetwork.contains(username)]
     */
    public synchronized boolean followUser(String username) {

        // controllo parametro + controllo condizioni utente
        if (username == null || username.length() == 0 || username.equals(user.username) || !loggedIn) {
            out.println("FollowUser Error: bad parameter or not registered yet");
            out.flush();
            return false;
        }

        // caso in cui l'utente che si vuole seguire non e' registrato su winsome
        if (!isRegistered(username)) {
            out.println("FollowUser Error: user not found");
            out.flush();
            return false;
        }


        // Aggiornamento della FOLLOWS MAP
        // caso in cui e' il primo utente a seguire
        followsMap.putIfAbsent(user.username, new HashSet<>());

        // caso in cui l'utente segue gia' l'utente identificato da 'username'
        if (followsMap.get(user.username).contains(username)) {
            out.println("You are already following [" + username + "]");
            out.flush();
            return false;
        }

        // aggiunta dell'utente tra i follows
        followsMap.get(user.username).add(username);


        // Aggiornamento della FOLLOWERS MAP
        // caso in cui e' il primo utente a seguire
        followersMap.putIfAbsent(username, new HashSet<>());

        // caso in cui l'utente segue gia' l'utente identificato da 'username'
        if (followersMap.get(username).contains(user.username)) {
            System.err.println("FollowUser Error: a memory inconsistency occurred");
            out.println("FollowUser Error: something goes wrong");
            out.flush();
            return false;
        }

        // aggiunta dell'utente tra i follows
        followersMap.get(username).add(user.username);


        // messaggio di risposta al client
        out.println("You are following [" + username + "]");
        out.flush();
        return true;

    }




    /**
     * @effects permette di rimuovere l'utente identificato da 'username' dai propri utenti seguiti.
     * @param username stringa univoca e identificativa dell'utente nel social network.
     *                 [(username != null) && (0 < username.lenght < 21) && socialNetwork.contains(username)]
     */
    public synchronized boolean unFollowUser(String username) {

        // controllo parametri + controllo condizioni utente
        if (username == null || username.length() == 0 || !loggedIn || !isRegistered(username)
                || username.equals(user.username) || !followersMap.containsKey(user.username)
                || !followsMap.get(user.username).contains(username)) {
            out.println("UnFollowUser Error: bad parameter or not registered yet");
            out.flush();
            return false;
        }

        // rimozione dell'utente dai follows dell'utente "this"
        followsMap.get(user.username).remove(username);

        // rimozione dell'utente "this" dai followers dell'utente
        followersMap.get(username).remove(user.username);

        // messaggio di risposta al client
        out.println("[" + username + "] has been removed from your follows");
        out.flush();
        return true;

    }




    /**
     * @effects crea un nuovo post che sara' visible dall'autore del post e da tutti i suoi followers.
     * @param text stringa di lunghezza limitata che rappresenta il testo del post.
     *             [text != null]
     * @param title stringa di lunghezza limitata che rappresenta il titolo del post.
     *              [title != null]
     */
    public void createPost(String title, String text) {

        // controllo parametri + controllo condizioni utente
        if (title == null || title.length() == 0 || text == null || text.length() == 0 || !loggedIn) {
            out.println("CreatePost Error: bad parameters or not registered yet");
            out.flush();
            return;
        }

        // creazione post
        Post p = null;
        try {
            p = new Post(
            	user.username,
            	title.startsWith(" ")? title.substring(1) : title,
            	text.startsWith(" ")? text.substring(1) : text,
            	null
            );
        } catch (LimitExceededException | NullPointerException | InvalidPropertiesFormatException e) {
            out.println(
                    "CreatePost Error: bad parameters (Suggestions: is text's length between 1 and 500" +
                    "characters? Is the title's length between 1 and 20 characters?)"
            );
            out.flush();
            return;
        }

        // caso di creazione del primo post
        postMap.putIfAbsent(user.username, new HashMap<>());

        // aggiunta del post alla postMap
        postMap.get(user.username).put(p.getId(), p);

        // messaggio di risposta al client
        out.println("The post [" + p.getId() + "] is now visible on Winsome");
        out.flush();
    }




    /**
     * @effects permette di visualizzare il contenuto del post con id uguale alla stringa
     *          passata come parametro.
     * @param postId id del post che si vuole visionare.
     *               [(postId != null) && (postMap.contains(postId)) && (postMap.get(postId).author == this.user)]
     */
    public void showPost(String postId) {

        // controllo parametro + controllo condizioni utente
        if (postId == null || !loggedIn) {
            out.println("ShowPost Error: bad parameter or not registered yet");
            out.flush();
            return;
        }

        Post post = null;

        // caso di post di cui l'utente e' autore
        if (postMap.containsKey(user.username) && postMap.get(user.username).containsKey(postId))
            post = postMap.get(user.username).get(postId);


        // caso in cui l'utente non segue l'utente di cui vuole visionare il post
        synchronized (followsMap) {

            // ricerca del post tra i post condivisi dai follows dell'utente
            for (String followed : followsMap.get(user.username))
                if (postMap.containsKey(followed) && postMap.get(followed).containsKey(postId)) {
                    post = postMap.get(followed).get(postId);
                    break;
                }
        }

        // caso di post trovato
        if (post != null) {

            // concatenazione dei commenti
            List<String> commentsList = post.getComments();
            String comments = "";
            int i = 0;
            if (commentsList != null)
                for (String s : commentsList) {
                    if (i == 0)
                        comments = comments + s + "/";
                    else
                        comments = comments + " |          | " + s + "/";
                    i++;
                }
            comments = comments + "/";

            // messaggio di risposta al client con le informazioni del post
            out.println(
                    "/ | WHEN     | " + post.getTimestamp() +
                    "/ | ID       | " + postId +
                    "/ | AUTHOR   | " + post.getAuthor() +
                    "/ | TITLE    | " + post.getTitle() +
                    "/ | TEXT     | " + post.getText() +
                    "/ | LIKES    | " + post.getLikes() +
                    "/ | DISLIKES | " + post.getDislikes() +
                    "/ | COMMENTS | " + comments
            );
            out.flush();

            return;
        }

        // caso di post non trovato
        out.println("ShowPost Error: post not found");
        out.flush();

    }




    /**
     * @effects permette all'utente di visualizzare tutti i post da lui condivisi.
     */
    public void viewBlog() {

        // controllo login
        if (!loggedIn) {
            out.println("ViewBlog Error: you aren't logged.");
            out.flush();
            return;
        }

        // messaggio da restituire al client
        String message = "/\tBlog:/\t";

        // caso in cui l'utente ha condiviso almeno un post
        if (postMap.containsKey(user.username))
            // aggiunta delle informazioni dei post al messaggio
            for (Map.Entry<String,Post> posts : postMap.get(user.username).entrySet()) {
                Post p = posts.getValue();
                message = message + "| " + p.getId() + " | " + p.getAuthor() + " | " + p.getTitle() + " |/\t";
            }
        message = message + "/";

        // messaggio di risposta al client
        out.println(message);
        out.flush();

    }




    /**
     * @effects permette di visualizzare tutti i post condivisi dagli utenti seguiti dall'utente
     *          che ha richiesto il servizio.
     */
    public void showFeed() {

        // controllo login
        if (!loggedIn) {
            out.println("ShowFeed Error: you aren't logged.");
            out.flush();
            return;
        }

        // stringa contenente il messaggio di risposta
        String message = "/\t Feed:/\t";

        synchronized (followsMap) {
            // per ogni utente seguito andiamo a reperire le informazioni dei post condivisi
            for (String user : followsMap.get(user.username))
                // caso di utente che ha condiviso almeno un post
                if (postMap.containsKey(user))
                    for (Map.Entry<String, Post> post : postMap.get(user).entrySet()) {
                        Post p = post.getValue();
                        // concatenazione delle informazioni del post nel messaggio
                        message = message + " | " + p.getId() + " | " + p.getAuthor() + " | " + p.getTitle() + " |/\t";
                    }
        }
        message = message + "/";

        // messaggio di risposta al client
        out.println(message);
        out.flush();

    }




    /**
     * @effects elimina il post con id passato come parametro dai post precedentemente caricati
     *          sul social network se chi lo richiede e' l'autore del post. Inoltre causa
     *          un'eliminazione a cascata di tutti i rewin di tale post.
     * @param idPost id del post che si vuole eliminare.
     *               [(postId != null) && (postMap.contains(postId)) && (postMap.get(postId).author == this.user)]
     */
    public void deletePost(String idPost) {

        // controllo parametro + controllo condizioni utente
        if (idPost == null || !loggedIn) {
            out.println("DeletePost Error: bad parameters or not registered yet");
            out.flush();
            return;
        }

        // caso di post inesistente
        if (!postMap.containsKey(user.username) || !postMap.get(user.username).containsKey(idPost)) {
            out.println("DeletePost Error: post doesn't exist or you aren't post's author");
            out.flush();
            return;
        }

        // rimozione del post dai post recenti
        periodicRewards.deletePostFromRecentPosts(postMap.get(user.username).get(idPost));

        // rimozione del post dalla postMap
        postMap.get(user.username).remove(idPost);


        // rimozione dei post che hanno fatto il rewin del post da eliminare
        for (Map.Entry<String,Map<String,Post>> usersPosts : postMap.entrySet())
            for (Map.Entry<String,Post> post : usersPosts.getValue().entrySet()) {
                Post p = post.getValue().getRewinned();
                if (p != null && p.getId().equals(idPost)){
                    periodicRewards.deletePostFromRecentPosts(post.getValue());
                    usersPosts.getValue().remove(post.getValue().getId());
                }
            }

        // messaggio di risposta al client
        out.println("The post [" + idPost + "] has been removed correctly");
        out.flush();
    }




    /**
     * @effects permette di ricondividere (rewin) il post con id passato come parametro se questo
     *          e' stato condiviso da un utente seguito.
     * @param idPost id del post di cui si vuole fare il rewin.
     *               [(postId != null) && (postMap.contains(postId)) && (postMap.get(postId).author == this.user)]
     */
    public void rewinPost(String idPost) {

        // controllo parametro + controllo condizioni utente
        if (idPost == null || !loggedIn) {
            out.println("RewinPost Error: bad parameters or not registered yet");
            out.flush();
            return;
        }

        // ricerca del post di cui fare il rewin
        for (Map.Entry<String,Map<String,Post>> userPosts : postMap.entrySet()) {

            // caso di post trovato e di conseguente ricondivisione
            if (userPosts.getValue().containsKey(idPost)) {
                Map<String,Post> posts = userPosts.getValue();

                // caso di post creato da un utente non seguito
                synchronized (followsMap) {
                    if (!followsMap.get(user.username).contains(userPosts.getKey())) {
                        out.println("RewinPost Error: you don't follow the author of this post");
                        out.flush();
                        return;
                    }
                }


                String title = posts.get(idPost).getTitle();
                String author = posts.get(idPost).getAuthor();

                // caso di rewin di un rewin in cui il post ricondiviso e' dello stesso autore
                if (posts.get(idPost).getRewinned() != null && posts.get(idPost).getRewinned().getAuthor().equals(user.username)) {
                    out.println("RewinPost Error: you can't rewin one of your posts.");
                    out.flush();
                    return;
                }

                // creazione del nuovo post
                Post post = null;
                try {
                    title = posts.get(idPost).getTitle();
                    if (!posts.get(idPost).getTitle().startsWith("{")) {
                        title = "{" + author + "} " + title;
                        post = new Post(
                                user.username,
                                title,
                                posts.get(idPost).getText(),
                                posts.get(idPost)
                        );
                    } else {
                        post = new Post(
                                user.username,
                                title,
                                posts.get(idPost).getText(),
                                posts.get(idPost).getRewinned()
                        );
                    }
                } catch (LimitExceededException | InvalidPropertiesFormatException e) {
                    try {
                        // title.length - author.length - "{}".length - "...".length
                        title = "{" + author + "} " + posts.get(idPost).getTitle();
                        title = title.substring(0, Math.min(title.length(), 47)) + "...";
                        if (!posts.get(idPost).getTitle().startsWith("{")) {
                            post = new Post(
                                    user.username,
                                    title,
                                    posts.get(idPost).getText(),
                                    posts.get(idPost)
                            );
                        } else {
                            post = new Post(
                                    user.username,
                                    title,
                                    posts.get(idPost).getText(),
                                    posts.get(idPost).getRewinned()
                            );
                        }
                    } catch (LimitExceededException | InvalidPropertiesFormatException e1) {
                        e1.printStackTrace();
                        return;
                    }
                }

                // caso di creazione del primo post
                postMap.putIfAbsent(user.username, new HashMap<>());

                // condivisione del nuovo post
                postMap.get(user.username).put(post.getId(),post);

                // messaggio di risposta al client
                out.println("You rewin the post [" + idPost + "] correctly");
                out.flush();
                return;
            }
        }

        out.println("RewinPost Error: The post [" + idPost + "] is absent");
        out.flush();
    }




    /**
     * @effects permette di votare il post con id passato come parametro con un voto positivo
     *          o con un voto negativo.
     * @param idPost id del post che si vuole votare.
     *               [(postId != null) && (postMap.contains(postId)) && (postMap.get(postId).author == this.user)]
     * @param vote stringa che indica il voto da aggiungere al post.
     *             [(vote == "-1") || (vote == "+1")]
     */
    public void rate(String idPost, String vote) {

        // controllo parametri + controllo condizioni utente
        if (idPost == null || vote == null || !loggedIn) {
            out.println("Rate Error: bad parameters or not registered yet");
            out.flush();
            return;
        }

        // caso di tentativo di rate del proprio post
        if (postMap.containsKey(user.username) && postMap.get(user.username).containsKey(idPost)) {
            out.println("Rate Error: you can't rate your posts");
            out.flush();
            return;
        }

        // ricerca del post nella postMap
        for (Map.Entry<String,Map<String,Post>> userPosts : postMap.entrySet()) {

            // caso di post trovato e di conseguente assegnazione del voto
            if (userPosts.getValue().containsKey(idPost)) {

                // caso di post creato da un utente non seguito
                synchronized (followsMap) {
                    if (!followsMap.get(user.username).contains(userPosts.getKey())) {
                        out.println("Rate Error: you don't follow the author of this post");
                        out.flush();
                        return;
                    }
                }

                // assegnazione del voto
                if (vote.equals("+1")) {
                    // caso di voto gia' effettuato precedentemente
                    if (!userPosts.getValue().get(idPost).putLike(user.username)) {
                        out.println("Rate Error: you have already vote this post");
                        out.flush();
                        return;
                    }
                } else if (vote.equals("-1")) {
                    // caso di voto gia' effettuato precedentemente
                    if (!userPosts.getValue().get(idPost).putDislike(user.username)) {
                        out.println("Rate Error: you have already vote this post");
                        out.flush();
                        return;
                    }
                } else {
                    // caso di formato del voto errato
                    out.println("Rate Error: the vote's format has to be \"+1\" or \"-1\"");
                    out.flush();
                    return;
                }

                // aggiunta like/dislike al post recente
                userPosts.getValue().get(idPost).incrementIterationsCounter();
                periodicRewards.rateRecentPost(userPosts.getValue().get(idPost), user.username, vote);

                // messaggio di risposta al client
                out.println("You vote \"" + vote + "\" to the post [" + idPost + "] correctly");
                out.flush();
                return;
            }
        }

        // caso di post assente
        out.println("Rate Error: The post [" + idPost + "] is absent");
        out.flush();
    }




    /**
     * @effects aggiunge il commento 'comment' al post con id passato come parametro.
     * @param idPost id del post che si vuole commentare.
     *               [(postId != null) && (postMap.contains(postId)) && (postMap.get(postId).author == this.user)]
     * @param comment stringa che rappresenta il commento da aggiungere al post.
     *                [(comment != null) && (comment.length > 0)]
     */
    public void addComment(String idPost, String comment) {

        // controllo parametri + controllo condizioni utente
        if (idPost == null || comment == null || comment.length() == 0 || !loggedIn) {
            out.println("AddComment Error: bad parameters or not registered yet");
            out.flush();
            return;
        }

        // caso in cui l'autore del post e l'utente che vuole commentare coincidono
        if (postMap.containsKey(user.username) && postMap.get(user.username).containsKey(idPost)) {
            out.println("AddComment Error: you can't comment a post that you have created");
            out.flush();
            return;
        }

        // ricerca del post nella postMap
        for (Map.Entry<String, Map<String, Post>> userPosts : postMap.entrySet()) {

            // caso di post trovato e di conseguente assegnazione del voto
            if (userPosts.getValue().containsKey(idPost)) {

                // caso di post creato da un utente non seguito
                synchronized (followsMap) {
                    if (!followsMap.get(user.username).contains(userPosts.getKey())) {
                        out.println("AddComment Error: you don't follow the author of this post");
                        out.flush();
                        return;
                    }
                }

                try {
                    // aggiunta del commento al post
                    userPosts.getValue().get(idPost).addComment(user.username + ": " + comment);

                    // aggiunta del commento al post recente
                    userPosts.getValue().get(idPost).incrementIterationsCounter();
                    periodicRewards.addCommentToRecentPost(
                            user.username+": "+comment,
                            userPosts.getValue().get(idPost)
                    );
                } catch (LimitExceededException e) {
                    out.println("AddComment Error: bad comment's format");
                    out.flush();
                    return;
                }

                // messaggio di risposta al client
                out.println("You have commented the post [" + idPost + "] correctly");
                out.flush();
                return;
            }
        }

        // caso di post assente
        out.println("AddComment Error: The post [" + idPost + "] is absent");
        out.flush();
    }





    /**********************************
     ****** CLASSE PRIVATA WALLET *****
     **********************************/
    private class Wallet {
        /**
         * OVERVIEW: classe privata mutable che modella il portafogli dell'utente 'username'
         *           con la relativa lista di transazioni di incremento del conto.
         */

        // username utente
        public final String username;
        // lista di transazioni (ognuna con: quantita' di denaro e timestamp)
        public final List<String> history;
        // lista di transazioni in bitcoin (ognuna con: quantita' di btc e timestamp)
        public final List<String> historyBtc;
        // valore totale di denaro presente nel portafogli
        public double wincoins;
        // valore totale di denaro presente nel portafogli in bitcoin
        public double wincoinsBtc;


        /**
         * @effects costruttore che crea un oggetto di tipo wallet associato all'utente 'username'
         * @param username stringa identificativa dell'utente che possiede il portafogli.
         *                 [username != null]
         */
        public Wallet(String username) {

            // controllo parametro
            if (username == null)
                throw new NullPointerException();

            // inizializzazioni
            wincoins = wincoinsBtc = 0;
            history = new LinkedList<>();
            historyBtc = new LinkedList<>();
            this.username = username;
        }


        /**
         * @effects converte il parametro 'newMoney' in Bitcoin e aggiorna le informazioni
         *          relative alla history e al portafogli in formato Btc.
         * @param newMoney valore double che indica la quantita' di soldi da aggiungere al conto (in btc).
         *                 [newMoney > 0]
         * @param timestamp oggetto che identifica il momento in cui avviene la transazione (data, ora, ecc..).
         *                  [timestamp != null]
         */
        private void addInBitcoin(double newMoney, Timestamp timestamp) throws MalformedURLException {

            // controllo parametro
            if (newMoney <= 0 || timestamp == null)
                return;

            // creazione oggetto URL
            URL url = null;
            URLConnection urlConnection = null;
            try {
                url = new URL("https://www.random.org/integers/?num=1&min=1&max=100&col=1&base=10&format=plain&rnd=new");
                urlConnection = url.openConnection();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            // invio richiesta al sito RANDOM.ORG
            double random;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
                String inputString;

                // lettura del valore ricevuto
                if ((inputString = reader.readLine()) == null)
                    return;

                // conversione del valore
                random = Integer.parseInt(inputString);

            } catch (NumberFormatException e) {
                System.out.println("Something goes wrong with RANDOM.ORG");
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            // calcolo del valore in bitcoin
            random = random * newMoney;

            // aggiornamento strutture dati
            wincoinsBtc += random;
            historyBtc.add(timestamp + ": +" + random + " BTC");

        }


        /**
         * @effects aggiunge la nuova transazione del saldo 'newMoney' all'utente 'username'.
         * @param newMoney valore double che indica il saldo da aggiungere al conto.
         *                 [newMoney > 0]
         */
        public void addTransaction(double newMoney) {

            // controllo parametro
            if (newMoney <= 0)
                return;

            // aggiornamento strutture dati
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            wincoins += newMoney;
            history.add(timestamp + ": +" + newMoney + " $");
            try {
                addInBitcoin(newMoney, timestamp);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

    }





    /**
     * @effects incrementa il portafogli dell'utente con username 'username', passato come
     *          parametro, del valore 'value', passato anch'esso come parametro.
     * @param username stringa univoca e identificativa dell'utente.
     *                 [(username != null) && (0 < username.lenght < 21) && hasAtLeastOnePost(username)]
     * @param value valore che rappresenta il saldo della transazione da aggiungere al portafogli.
     *              [value > 0]
     */
    protected void addTransactionOnWallet(String username, double value) {

        // controllo parametri
        if (username == null || !(value > 0))
            throw new InvalidParameterException();

        // aggiornamento del wallet
        synchronized (walletMap) {

            // caso di prima transazione
            walletMap.putIfAbsent(username, new Wallet(username));

            // aggiunta della transazione
            walletMap.get(username).addTransaction(value);
        }
    }


    

    /**
     * @effects invia al client le informazioni relative al proprio portafoglio (che comprende
     *          il conto totale e la history delle transazioni).
     */
    public synchronized void getWallet() {

        // controllo login
        if (!loggedIn) {
            out.println("GetWallet Error: you aren't logged.");
            out.flush();
            return;
        }

        Wallet w = walletMap.get(user.username);

        // creazione del messaggio
        String message = "/\tWallet Value: " + w.wincoins + " $/" +
                "\tTransactions History:/" + (w.history.isEmpty()? "\tEMPTY" : "");
        for (String s : w.history)
            message = message + "\t- " + s + "/";

        // invio del messaggio al client
        out.println(message);
        out.flush();
    }




    /**
     * @effects invia al client le informazioni relative al proprio portafoglio (che comprende
     *          il conto totale e la history delle transazioni) convertite in bitcoin.
     */
    public void getWalletInBitcoin() {

        // controllo login
        if (!loggedIn) {
            out.println("GetWalletInBitcoin Error: you aren't logged.");
            out.flush();
            return;
        }

        Wallet w = walletMap.get(user.username);

        // creazione del messaggio
        String message = "/\tWallet Value: " + w.wincoinsBtc + " BTC/" +
                "\tTransactions History:/" + (w.historyBtc.isEmpty()? "\tEMPTY" : "");
        for (String s : w.historyBtc)
            message = message + "\t" + s + "/";

        // invio del messaggio al client
        out.println(message);
        out.flush();
    }


    /**
     * @effects setta tutti i post della post map come ancora non aggiunti ai post recenti
     *          in modo tale da permettere una nuova iterazione per il calcolo delle ricompense.
     */
    protected void startNewIteration() {
        for (Map.Entry<String, Map<String,Post>> posts : postMap.entrySet())
            for (Map.Entry<String,Post> post : posts.getValue().entrySet())
                post.getValue().lastIter = false;
    }


}




