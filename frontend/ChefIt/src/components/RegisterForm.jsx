import { useState } from "react";
import { register } from "../utils/auth.js";

const GOOGLE_HINT = "If you signed up with Google, try the Sign in with Google button instead.";

function RegisterForm({ onSuccess }) {
    const [identifier, setIdentifier] = useState("");
    const [password, setPassword] = useState("");
    const [realname, setRealname] = useState("");
    const [error, setError] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setError(null);
        setSubmitting(true);
        try {
            const data = await register(identifier, password, realname);
            onSuccess?.(data);
        } catch (err) {
            setError(err);
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="local-auth-form">
            <label>
                Identifier
                <input
                    type="text"
                    value={identifier}
                    onChange={(e) => setIdentifier(e.target.value)}
                    autoComplete="username"
                />
            </label>
            <label>
                Password
                <input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="new-password"
                />
            </label>
            <label>
                Name
                <input
                    type="text"
                    value={realname}
                    onChange={(e) => setRealname(e.target.value)}
                    autoComplete="name"
                />
            </label>
            <button type="submit" disabled={submitting}>Register</button>

            {error && (
                <div role="alert" className="auth-error">
                    {error.status === 400 && error.body?.details?.length ? (
                        <ul>
                            {error.body.details.map((code) => (
                                <li key={code}>{code}</li>
                            ))}
                        </ul>
                    ) : (
                        <p>Registration failed. {GOOGLE_HINT}</p>
                    )}
                </div>
            )}
        </form>
    );
}

export default RegisterForm;
