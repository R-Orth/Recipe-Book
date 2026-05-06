package client;

import server.Server;
import server.Request;

public class DeleteRequest extends Request {
    
    private String login;
    private String password;

    public DeleteRequest(String login, String password) {
        this.login = login;
        this.password = password;
    }

    @Override
    public Object execute(Server context) throws java.rmi.RemoteException {
        return context.delete(this.login, this.password);
    }
}
