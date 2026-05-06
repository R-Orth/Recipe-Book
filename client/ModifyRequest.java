package client;

import server.Server;
import server.Request;

public class ModifyRequest extends Request {
    
    private String login;
    private String newLogin;
    private String password;

    public ModifyRequest(String login, String newLogin, String password) {
        this.login = login;
        this.newLogin = newLogin;
        this.password = password;
    }

    @Override
    public Object execute(Server context) throws java.rmi.RemoteException {
        return context.modify(this.login, this.newLogin, this.password);
    }
}
