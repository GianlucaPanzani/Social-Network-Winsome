package WinsomeServer;

import javax.naming.LimitExceededException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.*;

public class PeriodicRewardsTask implements Runnable {
    /**
     * OVERVIEW: classe che permette la gestione di un gruppo multicast al quale gli utenti,
     *           se registrati su Winsome, possono iscriversi per ricevere notifiche periodiche
     *           sull'aggiornamento del proprio portafoglio virtuale.
     */
    // porta multicast
    private final int PORT;
    // periodo in ms dopo il quale calcolare le ricompense
    private final int TIMEOUT;
    // percentuale della ricompensa periodica che spetta all'utente
    private final int PERC;
    // indirizzo multicast
    private final String IP;
    // oggetto per la comunicazione con la classe WinsomeServer
    private final WinsomeServer winsomeServer;

    /** struttura dati che memorizza i dati degli utenti nell'ultimo periodo **/
    private static final Map<String,Map<String,Post>> recentPostMap = new HashMap<>();

    /** messaggio di default da inviare ai client connessi al gruppo multicast **/
    private static final String standardMessage = "$$$ Wallets has been updated $$$";



    /**
     * @effects costruttore che inizializza i campi della classe con valori di default.
     */
    protected PeriodicRewardsTask() {
        winsomeServer = null;
        IP = null;
        PORT = 0;
        TIMEOUT = 0;
        PERC = 10;
    }



    /**
     * @effects costruttore che inizializza i campi della classe per permettere la creazione
     *          di un gruppo multicast.
     * @param ws oggetto che permette l'invocazione dei metodi forniti dalla classe che
     *           modella il social network.
     *           [winsomeServer != null]
     * @param MC_IP indirizzo multicast.
     *              [MC_IP != null]
     * @param MC_PORT porta multicast.
     *                [(MC_PORT >= 1024) && (MC_PORT <= 65535)]
     * @param MC_TIMEOUT tempo in ms oltre il quale ciclicamente si invia un messaggio a tutti gli
     *                   utenti connessi al gruppo.
     *                   [MC_TIMEOUT >= 5000]
     * @param REWARD_PERC indica quale percentuale della ricompensa calcolata periodicamente
     *                    verra' assegnata all'autore del post. La restante andra' ai creatori
     *                    del social network.
     *                    [(REWARD_PERC >= 0) && (REWARD_PERC <= 100)]
     */
    public PeriodicRewardsTask(WinsomeServer ws, String MC_IP, int MC_PORT, int MC_TIMEOUT, int REWARD_PERC) {

        // controllo parametri
        if (ws == null || MC_IP == null || MC_PORT < 1024 || MC_PORT > 65535 || MC_TIMEOUT < 5000
                || REWARD_PERC < 0 || REWARD_PERC > 100) {
            System.err.println("PeriodicRewards Error: bad parameters");
            throw new InvalidParameterException();
        }

        // inizializzazioni
        IP = MC_IP;
        PORT = MC_PORT;
        TIMEOUT = MC_TIMEOUT;
        PERC = REWARD_PERC;
        winsomeServer = ws;
    }




    /**
     * @effects elimina il post passato come parametro dai post recenti (se presente).
     * @param p post da eliminare dai post recenti.
     *          [p != null]
     */
    protected synchronized void deletePostFromRecentPosts(Post p) {

        // controllo parametri
        if (p == null) {
            System.err.println("AddPostToRecentPosts Error: bad parameters");
            throw new InvalidParameterException();
        }

        // caso di primo post nell'ultimo periodo
        if (!recentPostMap.containsKey(p.getAuthor()))
            return;

        // aggiunta del post tra i recenti se non e' gia' presente
        recentPostMap.get(p.getAuthor()).remove(p.getId());

        // caso di autore senza post (ma che aveva precedentemente creato post)
        if (recentPostMap.get(p.getAuthor()).isEmpty())
            recentPostMap.remove(p.getAuthor());

    }



    /**
     * @effects permette di aggiornare i valori di like/dislike del post passato come parametro
     *          in base al voto 'vote' e di aggiungere tale post ai recenti (se assente).
     * @param post oggetto post da aggiornare col nuovo voto.
     *             [post != null]
     * @param username nome utente di chi ha messo like/dislike al post.
     *                 [username != null]
     * @param vote voto da aggiungere al post.
     *             [(vote != null) && ((vote == "+1") || (vote == "-1"))]
     */
    protected synchronized void rateRecentPost(Post post, String username, String vote) {

        // controllo parametri
        if (post == null || vote == null || username == null) {
            System.err.println("AddPostToRecentPosts Error: bad parameters");
            throw new InvalidParameterException();
        }

        Post p = post.getCopy();

        // in caso di assenza dell'autore
        recentPostMap.putIfAbsent(p.getAuthor(), new HashMap<>());

        // in caso di assenza del post
        recentPostMap.get(p.getAuthor()).putIfAbsent(p.getId(),p);

        // caso di assegnamento del like
        if (vote.equals("+1"))
            recentPostMap.get(p.getAuthor()).get(p.getId()).putLike(username);

        // caso di assegnamento dislike
        else if (vote.equals("-1"))
            recentPostMap.get(p.getAuthor()).get(p.getId()).putDislike(username);

        // caso di formato errato del voto
        else
            System.err.println("AddPostToRecentPosts Error: bad rate format");

    }



    /**
     * @effects permette di aggiornare i commenti del post passato come parametro col nuovo commento
     *          'comment' e di aggiungere tale post ai recenti (se assente).
     * @param comment stringa contenente il commento da aggiungere al post.
     *                [comment != null]
     * @param post oggetto post da aggiornare col nuovo commento.
     *             [post != null]
     */
    protected synchronized void addCommentToRecentPost(String comment, Post post) {

        // controllo parametri
        if (post == null || comment == null) {
            System.err.println("AddPostToRecentPosts Error: bad parameters");
            throw new InvalidParameterException();
        }

        Post p = post.getCopy();

        // in caso di assenza dell'autore
        recentPostMap.putIfAbsent(p.getAuthor(), new HashMap<>());

        // in caso di assenza del post
        recentPostMap.get(p.getAuthor()).putIfAbsent(p.getId(),p);

        // aggiunta del commento
        try {
            recentPostMap.get(p.getAuthor()).get(p.getId()).addComment(comment);
        } catch (LimitExceededException e) {
            recentPostMap.get(p.getAuthor()).remove(p.getId());
            if (recentPostMap.get(p.getAuthor()).isEmpty())
                recentPostMap.remove(p.getAuthor());
            e.printStackTrace();
        }
    }



    /**
     * @effects calcola periodicamente il guadagno degli utenti i cui post hanno (nell'ultimo
     *          lasso di tempo) subito variazioni di stato a causa di commenti e voti.
     *          Dopo cio' comunica il possibile aggiornamento dei portafogli degli utenti
     *          tramite gruppo multicast.
     */
    public void run() {

        try {
            System.out.println("PeriodicRewardsTask | " + Thread.currentThread() + ": opened");

            // dichiarazioni/inizializzazioni
            byte[] buf = new byte[standardMessage.length()];
            InetAddress groupIP = InetAddress.getByName(IP);
            DatagramSocket socket = new DatagramSocket(PORT);
            socket.setReuseAddress(true);

            // ciclo di calcolo periodico dei guadagni
            while (!ServerMain.getExitValue()) {

                // periodo ciclico di attesa
                try {
                    Thread.sleep(TIMEOUT);
                } catch (InterruptedException e) {
                    break;
                }

                System.out.print("PeriodicRewardsTask | Rewards Counting... ");

                // calcolo delle ricompense
                synchronized (recentPostMap) {
                    double comments, likesDislikes, tot;
                    for (Map.Entry<String,Map<String,Post>> posts : recentPostMap.entrySet())
                        for (Map.Entry<String, Post> post : posts.getValue().entrySet()) {
                            Post p = post.getValue();

                            // calcolo del valore relativo ai commenti
                            Map<String, Integer> usersComments = new HashMap<>();
                            for (String comment : p.getComments()) {
                                String user = comment.substring(0, comment.indexOf(":"));
                                if (!usersComments.containsKey(user))
                                    usersComments.put(user, 0);
                                usersComments.put(user, usersComments.get(user)+1);
                            }
                            comments = 0;
                            for (Map.Entry<String,Integer> i : usersComments.entrySet())
                                comments += 2 / (1 + 1 / Math.pow(Math.E, i.getValue()-1));

                            // calcolo del valore relativo ai likes/dislikes
                            likesDislikes = Math.max(0, p.totalRating());

                            // calcolo del guadagno totale
                            tot = (Math.log(likesDislikes+1) + Math.log(comments+1)) / p.getIterations();

                            // caso in cui non va aggiornato il wallet
                            if(tot <= 0 || Float.isNaN((float) tot))
                                continue;

                            // calcolo percentuali
                            double percAuthor = (tot/100)*PERC;
                            double percCurators = (tot/100)*(100-PERC);

                            // aggiornamento del wallet dell'autore
                            winsomeServer.addTransactionOnWallet(p.getAuthor(), percAuthor);

                            // recupero dei curatori: utenti che hanno commentato
                            Set<String> curatorsSet = new HashSet<>();
                            for (Map.Entry<String,Integer> user : usersComments.entrySet())
                                curatorsSet.add(user.getKey());

                            // recupero dei curatori: utenti che hanno votato
                            for (Map.Entry<String,Integer> user : p.getVoters().entrySet())
                                if (user.getValue() == 1)
                                    curatorsSet.add(user.getKey());

                            // aggiornamento del wallet dei curatori
                            double moneyDistribution = percCurators/curatorsSet.size();
                            for (String user : curatorsSet)
                                winsomeServer.addTransactionOnWallet(user, moneyDistribution);

                            // reset delle informazioni del post (affinche' sia "preparato" al ciclo successivo)
                            recentPostMap.clear();
                            winsomeServer.startNewIteration();
                        }

                }

                // creazione del pacchetto da spedire ai client
                DatagramPacket packet = new DatagramPacket(buf, buf.length, groupIP, PORT);

                // invio del pacchetto a tutti i client
                packet.setData(standardMessage.getBytes(StandardCharsets.UTF_8));
                socket.send(packet);

                System.out.println("done");
            }

            // chiusura gruppo multicast
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("PeriodicRewardsTask | " + Thread.currentThread() + ": closed");
    }
}