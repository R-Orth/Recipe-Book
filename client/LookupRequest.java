package client;

import server.Server;
import server.Request;

public class LookupRequest extends Request {
    
    private String login;

    public LookupRequest(String login) {
        this.login = login;
    }

    @Override
    public Object execute(Server context) throws java.rmi.RemoteException {
        return context.lookup(this.login);
    }
}
