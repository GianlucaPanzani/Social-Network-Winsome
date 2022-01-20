package WinsomeServer;

import WinsomeClient.WinsomeClientInterface;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.LinkedList;

public interface WinsomeServerInterface extends Remote {
    public boolean register(String username, String password, LinkedList<String> tags) throws RemoteException;
    public void turnOnNotify(WinsomeClientInterface clientRemoteObj) throws RemoteException;
    public void turnOffNotify(WinsomeClientInterface clientRemoteObj) throws RemoteException;
}