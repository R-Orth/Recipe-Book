package server;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Listener extends Remote
{
    public void workCompleted(Object result) throws RemoteException;
}
