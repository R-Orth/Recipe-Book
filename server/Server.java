package server;

import java.util.UUID;

public interface Server extends java.rmi.Remote
{   
    /**
     * Server executes the work request and call back client when done.
     * 
     * @param work
     * @param listener
     * @throws java.rmi.RemoteException
     */
    void execute(Request work, Listener listener) throws java.rmi.RemoteException;

    /**
     * Create a new user with the given login name, real name and password. The server will generate a UUID for the new user and return it to the client. 
     * If the login name already exists, the server will return an error message.
     * @param login
     * @param name
     * @param password
     * @return
     * @throws java.rmi.RemoteException
     */
    public String create(String login, String name, String password, String host) throws java.rmi.RemoteException;

    /**
     * Looks up the login name of a user and displays all relavent information about the user except for the password. 
     * If the login name does not exist, the server will return an error message.
     * @param loginname
     * @return
     * @throws java.rmi.RemoteException
     */
    public String lookup(String login) throws java.rmi.RemoteException;

    /**
     * Lookup the login name associated with the given UUID and displays all relavent information about the user except for the password.
     * If the UUID does not exist, the server will return an error message.
     * @param uuid
     * @return
     * @throws java.rmi.RemoteException
     */
    public String reverseLookup(String uuid) throws java.rmi.RemoteException;

    /**
     * Modify the login name of an existing user. The server will verify the password before modifying the login name. 
     * If the old login name does not exist, the password is incorrect, or the new login name already exists, the server will return an error message.
     * @param oldLogin
     * @param newLogin
     * @param password
     * @return
     * @throws java.rmi.RemoteException
     */
    public String modify(String oldLogin, String newLogin, String password) throws java.rmi.RemoteException;

    /**
     * Delete an existing user. The server will verify the password before deleting the user. 
     * If the login name does not exist or the password is incorrect, the server will return an error message.
     * @param login
     * @param password
     * @return
     * @throws java.rmi.RemoteException
     */
    public String delete(String login, String password) throws java.rmi.RemoteException;

    /**
     * Gets all of the instances of the requested option. These options include "users", "uuids", and all. 
     * @param getType
     * @return
     * @throws java.rmi.RemoteException
     */
    public String get(String getType) throws java.rmi.RemoteException;
}