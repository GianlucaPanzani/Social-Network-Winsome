package WinsomeClient;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface WinsomeClientInterface extends Remote {
    public void oneMoreFollower(String username) throws RemoteException;
    public void oneLessFollower(String username) throws RemoteException;
    public void updateFollowers(List<String> list) throws RemoteException;
    public void setMulticastInfo(String IP, int PORT) throws RemoteException;
}