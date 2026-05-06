package server;

import java.util.UUID;

public interface DataAccessObjectInterface{
    
    /**
     * Create a new user with the given login name, real name and password. The server will generate a UUID for the new user and return it to the client. 
     * If the login name already exists, the server will return an error message.
     * @param loginname
     * @param realname
     * @param password
     * @param ip
     * @return
     */
    public String create(String loginname, String realname, String password, String ip);

    /**
     * Looks up the login name of a user and displays all relavent information about the user except for the password. 
     * If the login name does not exist, the server will return an error message.
     * @param loginname
     * @return
     */
    public String lookup(String loginname);
 
    /**
     * Lookup the login name associated with the given UUID and displays all relavent information about the user except for the password.
     * If the UUID does not exist, the server will return an error message.
     * @param uuid
     * @return
     */
    public String reverseLookup(UUID uuid);

    /**
     * Modify the login name of an existing user. The server will verify the password before modifying the login name. 
     * If the old login name does not exist, the password is incorrect, or the new login name already exists, the server will return an error message.
     * @param oldLoginname
     * @param newLoginname
     * @param password
     * @return
     */
    public String modify(String oldLoginname, String newLoginname, String password);

    /**
     * Delete an existing user. The server will verify the password before deleting the user. 
     * If the login name does not exist or the password is incorrect, the server will return an error message.
     * @param loginname
     * @param password
     * @return
     */
    public String delete(String loginname, String password);

    /**
     * Gets all of the instances of the requested option. These options include "users", "uuids", and all. 
     * @param getType
     * @return
     */
    public String get(String getType);

}
