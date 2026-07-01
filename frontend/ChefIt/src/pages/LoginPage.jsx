import { useState } from "react";
import GoogleLoginButton from "../components/GoogleLoginButton.jsx";
import LocalLoginForm from "../components/LocalLoginForm.jsx";
import { logout } from "../utils/auth.js";
import "./LoginPage.css";

function LoginPage() {
    const [user, setUser] = useState(null);
    const [error, setError] = useState(null);

    const handleSuccess = (data) => {
        setError(null);
        setUser(data);
    };

    const handleLogout = () => {
        logout();
        setUser(null);
    };

    return (
        <>
            <div id="login-title-container">
                <h2 id="login-title">Welcome back, Chef!</h2>
            </div>

            {user ? (
                <div id="signed-in-area">
                    <p>Signed in as <strong>{user.realname || user.identifier}</strong></p>
                    <button onClick={handleLogout}>Sign out</button>
                </div>
            ) : (
                <>
                    <div id="login-form-area">
                        <LocalLoginForm onSuccess={handleSuccess} />
                    </div>

                    <hr id="login-separator" />

                    <div id="login-oauth-area">
                        <GoogleLoginButton
                            onSuccess={handleSuccess}
                            onError={(e) => setError(e.message)}
                        />
                    </div>
                </>
            )}

            {error && <p role="alert">{error}</p>}
        </>
    );
}

export default LoginPage;
