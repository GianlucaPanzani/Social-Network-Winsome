package WinsomeClient;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.LinkedList;
import java.util.List;

public class WinsomeClient extends RemoteObject implements WinsomeClientInterface {
    /**
     * OVERVIEW: classe che modella un client per il social network Winsome e che fornisce
     *           l'implementazione dell'interfaccia WinsomeClientInterface adatta per comunicazione
     *           tramite RMI. Si concentra soprattutto sulla gestione locale dei followers dell'utente.
     */

    // lista di followers del client
    private List<String> followersList;
    // indirizzo multicast
    public String MC_IP = null;
    // porta multicast
    public int MC_PORT = 0;


    /**
     * @effects costruttore che inizializza i campi privati della classe.
     */
    public WinsomeClient() {
        followersList = new LinkedList<>();
    }



    /**
     * @effects permette di settare i campi della classe utili alla connessione sul gruppo multicast,
     *          cioe' l'indirizzo multicast e la porta multicast
     * @param IP indirizzo di multicast group al quale il client puo' unirsi.
     *           [IP != null]
     * @param PORT porta su cui il client puo' connettersi.
     *             [(PORT < 1024) || (PORT > 65535)]
     * @throws RemoteException se avviene un'errore dovuto ad RMI (Remote Method Invocation).
     */
    public void setMulticastInfo(String IP, int PORT) throws RemoteException {

        // controllo parametri
        if (IP == null || PORT < 1024 || PORT > 65535) {
            System.err.println("SetMulticastInfo Error: bad parameters");
            return;
        }

        // inizializzazione
        MC_IP = IP;
        MC_PORT = PORT;
    }



    /**
     * @effects permette di aggiungere il follower 'username' all'insieme di followers dell'utente.
     * @param username nome utente del nuovo follower.
     *                 [username != null]
     * @throws RemoteException se avviene un'errore dovuto ad RMI (Remote Method Invocation).
     */
    public void oneMoreFollower(String username) throws RemoteException {
        if(username != null && !followersList.contains(username)) {
            followersList.add(username);
            System.out.print("\n<<< [" + username + "] is following you\n>>> ");
        }
    }



    /**
     * @effects permette di rimuovere il follower 'username' dall'insieme di followers dell'utente.
     * @param username nome utente del follower da rimuovere.
     *                 [username != null]
     * @throws RemoteException se avviene un'errore dovuto ad RMI (Remote Method Invocation).
     */
    public void oneLessFollower(String username) throws RemoteException {
        if (username != null && followersList.contains(username)) {
            followersList.remove(username);
            System.out.print("\n<<< [" + username + "] no more your follower\n>>> ");
        }
    }



    /**
     * @effects permette di assegnare alla lista di follower locale la lista passata come parametro
     *          consentendo di aggiungere piu' followers in contemporanea (utile in caso di ripristino
     *          dei vecchi follower quando l'utente effettua un nuovo login).
     * @param list lista di followers.
     *             [list != null]
     * @throws RemoteException se avviene un'errore dovuto ad RMI (Remote Method Invocation).
     */
    public void updateFollowers(List<String> list) throws RemoteException {
        if (list != null)
            followersList = new LinkedList<>(list);
    }




    /**
     * @effects stampa la lista dei followers dell'utente.
     */
    public void listFollowers() {
        System.out.println("<<< \n\tFollowers List:");
        for(String username : followersList)
            System.out.println("\t" + username);
    }
}