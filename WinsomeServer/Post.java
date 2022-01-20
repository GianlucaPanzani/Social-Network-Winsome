package WinsomeServer;

import java.sql.Timestamp;
import javax.naming.LimitExceededException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Post {
    /**
     * OVERVIEW: classe mutable che permette di rappresentare un post al quale si associa:
     *           un ID, l'autore del post, il titolo del post, il testo del post, numero di
     *           like/dislike, insieme di commenti e data/ora di creazione.
     */
    // id identificativo del post
    private String id;
    // id del post di cui e' stato fatto il rewin (se null, il post non e' un rewin)
    private Post rewinnedPost;
    // autore del post
    private final String author;
    // titolo del post
    private final String title;
    // testo del post
    private final String text;
    // commenti presenti nel post
    private final LinkedList<String> comments;
    // utenti che hanno votato il post
    private final Map<String,Integer> voteUsers;
    // oggetto che rappresenta il momento di creazione del post
    private final Timestamp timestamp;
    // numero di like del post
    private int likes;
    // numero di dislike del post
    private int dislikes;
    // numero di iterazioni in cui il post e' stato sottoposto al calcolo delle ricompense
    private int n_iterations = 0;
    // indica se l'iterazione e' gia' stata incrementata nell'ultimo periodo oppure no
    protected boolean lastIter;
    // contatore statico incrementato alla creazione di ogni post
    private static final AtomicInteger counter = new AtomicInteger(10000);



    /**
     * @effects crea un nuovo Post con titolo 'title', testo 'text', genera un ID univoco associato
     *          al post e inizializza alcuni campi privati della classe (ottenibili tramite i metodi
     *          'get' della classe).
     * @param author autore del post.
     *               [(author != null) && (author.length > 0)]
     * @param text testo del post.
     *             [(text != null) && (0 < text.lenght < 501)]
     * @param title titolo del post.
     *              [(title != null) && (0 < title.lenght < 51)]
     * @param rewinnedPost post di cui e' stato fatto il rewin.
     *                     Se = a null non e' stato fatto alcun rewin.
     * @throws NullPointerException if author == null || text == null
     * @throws LimitExceededException if !(0 < text.length < 501) || !(0 < title < 51)
     * @throws InvalidPropertiesFormatException if text.startsWith("{")
     */
    public Post(String author, String title, String text, Post rewinnedPost) throws NullPointerException, LimitExceededException, InvalidPropertiesFormatException {

        // controllo parametri
        if(author == null || text == null || title == null)
            throw new NullPointerException();
        if(author.length() == 0 || title.length() == 0 || title.length() > 50 || text.length() == 0 || text.length() > 500)
            throw new LimitExceededException();
        if (title.startsWith("{") && rewinnedPost == null)
            throw new InvalidPropertiesFormatException("text parameter can't starts with \"{\"");

        // inizializzazione dei campi privati
        this.author = author;
        this.title = title;
        this.text = text;
        this.rewinnedPost = rewinnedPost;
        lastIter = false;
        likes = dislikes = 0;
        comments = new LinkedList<>();
        voteUsers = new HashMap<>();

        // generazione dell'id univoco del post
        id = String.valueOf(counter.incrementAndGet());

        // salvataggio di ora e data di creazione
        timestamp = new Timestamp(System.currentTimeMillis());
    }



    /**
     * @effects aggiunge il commento 'comment' all'insieme dei commenti del post.
     * @param comment stringa contenente il commento fatto da un utente sul post.
     *                [(comment != null) && (0 < comment.lenght < 501)]
     * @throws NullPointerException if comment == null
     * @throws LimitExceededException if (comment.lenght == 0) || (comment.lenght > 500)
     */
    public void addComment(String comment) throws NullPointerException, LimitExceededException {

        // controllo parametro
        if(comment == null)
            throw new NullPointerException();
        if(comment.length() == 0 || comment.length() > 500)
            throw new LimitExceededException();

        // aggiunta del commento
        comments.addLast(comment);
    }


    /**
     * @effects incrementa di 1 i likes se l'utente passato come parametro non ha gia' votato il post.
     * @param user stringa che indica l'utente che ha messo like al post.
     *             [(user != null) && (user's first vote)]
     */
    public boolean putLike(String user) {
        if (user != null && !voteUsers.containsKey(user)) {
            voteUsers.put(user,1);
            likes++;
            return true;
        }
        return false;
    }


    /**
     * @effects incrementa di 1 i dislikes se l'utente passato come parametro non ha gia' votato il post
     *          e retituisce true, altrimenti false.
     * @param user stringa che indica l'utente che ha messo dislike al post.
     *             [(user != null) && (user's first vote)]
     */
    public boolean putDislike(String user) {
        if (user != null && !voteUsers.containsKey(user)) {
            voteUsers.put(user,-1);
            dislikes++;
            return true;
        }
        return false;
    }



    /**
     * @effects restituisce il numero di likes.
     */
    public int getLikes() {
        return likes;
    }



    /**
     * @effects restituisce il numero di dislikes.
     */
    public int getDislikes() {
        return dislikes;
    }



    /**
     * @effects restituisce l'autore del post.
     */
    public String getAuthor() {
        return author;
    }



    /**
     * @effects restituisce il titolo del post.
     */
    public String getTitle() {
        return title;
    }



    /**
     * @effects restituisce la stringa contenente l'id del post.
     */
    public String getId() {
        return id;
    }



    /**
     * @effects restituisce la stringa contenente il testo del post.
     */
    public String getText() {
        return text;
    }



    /**
     * @effects restituisce il post di cui si e' eventualmente fatto il rewin.
     */
    public Post getRewinned() {
        return rewinnedPost;
    }



    /**
     * @effects restituisce l'insieme di stringhe che rappresentano i commenti fatti dagli
     *          utenti nel post.
     */
    public LinkedList<String> getComments() {
        return comments!=null ? new LinkedList<>(comments) : new LinkedList<>();
    }



    /**
     * @effects restituisce la stringa che indica data e ora di creazione del post.
     */
    public String getTimestamp() {
        return timestamp.toString();
    }



    /**
     * @effects restituisce il valore dato dalla differenza tra i likes e i dislikes.
     */
    public int totalRating() {
        return likes-dislikes;
    }



    /**
     * @effects incrementa il valore delle iterazioni che rappresentano metaforicamente
     *          l'eta' del post. Si assume che la classe che utilizza la classe post incrementi
     *          periodicamente il valore delle iterazioni.
     */
    protected void incrementIterationsCounter() {
        if (!lastIter) {
            n_iterations++;
            lastIter = true;
        }
    }



    /**
     * @effects restituisce il numero di iterazioni. Si assume che la classe che utilizza
     *          la classe post incrementi periodicamente il valore delle iterazioni.
     */
    protected int getIterations() {
        return n_iterations;
    }



    /**
     * @effects restituisce una copia dell'oggetto usato per chiamare il metodo.
     */
    protected Post getCopy() {
        Post p = null;
        try {
            p = new Post(author, title, text, rewinnedPost);
            p.id = id;
            p.n_iterations = n_iterations;
            p.lastIter = lastIter;
        } catch (LimitExceededException | InvalidPropertiesFormatException e) {
            e.printStackTrace();
        }
        return p;
    }



    /**
     * @effects setta il valore da cui ripartire col prossimo id se il codice 'code' e' corretto.
     * @param id valore con cui sara' settato l'id del prossimo post.
     *           [id > 9999]
     */
    protected static void setNextId(int id) {
        if (id > 10000)
            counter.set(id-1);
    }



    /**
     * @effects restituisce la map delle persone che hanno messo like o dislike al post.
     */
    protected HashMap<String,Integer> getVoters() {
        return new HashMap<>(voteUsers);
    }


}