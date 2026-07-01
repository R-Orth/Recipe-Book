import { useState } from "react";
import { loginWithCredentials } from "../utils/auth.js";

const GOOGLE_HINT = "If you signed up with Google, try the Sign in with Google button instead.";

function LocalLoginForm({ onSuccess }) {
    const [identifier, setIdentifier] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setError(null);
        setSubmitting(true);
        try {
            const data = await loginWithCredentials(identifier, password);
            onSuccess?.(data);
        } catch (err) {
            setError(err);
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="local-auth-form">
            <div className="form-group">
                <label htmlFor="login-identifier">Username/Email:</label>
                <input
                    id="login-identifier"
                    name="login-identifier"
                    type="text"
                    value={identifier}
                    onChange={(e) => setIdentifier(e.target.value)}
                    autoComplete="username"
                />
            </div>

            <div className="form-group">
                <label htmlFor="login-password">Password:</label>
                <input
                    id="login-password"
                    name="login-password"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="current-password"
                />
            </div>

            <div className="form-group">
                <button type="submit" disabled={submitting}>Sign in</button>
            </div>

            {error && (
                <div role="alert" className="auth-error">
                    {error.status === 400 && error.body?.details?.length ? (
                        <ul>
                            {error.body.details.map((code) => (
                                <li key={code}>{code}</li>
                            ))}
                        </ul>
                    ) : (
                        <p>Sign in failed. {GOOGLE_HINT}</p>
                    )}
                </div>
            )}
        </form>
    );
}

export default LocalLoginForm;
