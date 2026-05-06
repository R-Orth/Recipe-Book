package client;

import server.Server;
import server.Request;

public class CreateRequest extends Request {

    private String login;
    private String name;
    private String password;
    private String host;

    public CreateRequest(String login, String name, String password, String host) {
        this.login = login;
        this.name = name;
        this.password = password;
        this.host = host;
    }
    
    @Override
    public Object execute(Server context) throws java.rmi.RemoteException {
        return context.create(login, name, password, host);
    }
}
