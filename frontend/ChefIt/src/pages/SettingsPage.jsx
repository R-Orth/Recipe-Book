import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
    getCurrentUser,
    fetchSelf,
    updateAccount,
    deleteAccount,
    logout,
} from "../utils/auth.js";
import "./SettingsPage.css";

// Turns an error thrown by the auth/users helpers into a friendly message. The users
// service returns { error, details[] } bodies with the status codes Task 6 locked in.
function formatError(err, fallback) {
    const body = err?.body ?? {};
    if (Array.isArray(body.details) && body.details.length > 0) {
        return body.details.join(", ").replaceAll("_", " ");
    }
    switch (body.error) {
        case "invalid_credentials":
            return "Current password is incorrect.";
        case "no_password_auth":
            return "This account signs in with Google and has no password to verify.";
        case "identifier_taken":
            return "That username or email is already taken.";
        default:
            return fallback;
    }
}

function SettingsPage() {
    const [user, setUser] = useState(() => getCurrentUser());
    const [profile, setProfile] = useState(null);
    const navigate = useNavigate();

    // Profile-edit form
    const [realname, setRealname] = useState("");
    const [identifier, setIdentifier] = useState("");
    const [profileMsg, setProfileMsg] = useState(null);
    const [profileBusy, setProfileBusy] = useState(false);

    // Password-change form
    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [passwordMsg, setPasswordMsg] = useState(null);
    const [passwordBusy, setPasswordBusy] = useState(false);

    // Delete-account danger zone
    const [confirmingDelete, setConfirmingDelete] = useState(false);
    const [deletePassword, setDeletePassword] = useState("");
    const [deleteMsg, setDeleteMsg] = useState(null);
    const [deleteBusy, setDeleteBusy] = useState(false);

    useEffect(() => {
        const refresh = () => setUser(getCurrentUser());
        window.addEventListener("chefit-auth", refresh);
        return () => window.removeEventListener("chefit-auth", refresh);
    }, []);

    // Pull the authoritative profile (providers, current realname/identifier) from the
    // server. Falls back to the token claims if the request fails.
    useEffect(() => {
        if (!user) return;
        let cancelled = false;
        fetchSelf()
            .then((p) => {
                if (cancelled) return;
                setProfile(p);
                setRealname(p.realname ?? "");
                setIdentifier(p.identifier ?? "");
            })
            .catch(() => {
                if (cancelled) return;
                setProfile(null);
                setRealname(user.name ?? "");
                setIdentifier(user.identifier ?? "");
            });
        return () => { cancelled = true; };
    }, [user]);

    if (!user) {
        return (
            <>
                <div id="settings-title-container">
                    <h2 id="settings-title">Settings</h2>
                </div>
                <div id="settings-not-signed-in">
                    <p>You are not signed in. <Link to="/login">Sign in</Link> to manage your account.</p>
                </div>
            </>
        );
    }

    const hasPassword = !profile || (profile.providers ?? []).includes("local");

    const handleSignOut = () => {
        logout();
        navigate("/");
    };

    const handleProfileSave = async (e) => {
        e.preventDefault();
        setProfileMsg(null);
        const updates = {};
        const currentRealname = profile?.realname ?? user.name ?? "";
        const currentIdentifier = profile?.identifier ?? user.identifier ?? "";
        if (realname.trim() && realname !== currentRealname) updates.realname = realname.trim();
        if (identifier.trim() && identifier !== currentIdentifier) updates.identifier = identifier.trim();

        if (Object.keys(updates).length === 0) {
            setProfileMsg({ kind: "info", text: "No changes to save." });
            return;
        }

        setProfileBusy(true);
        try {
            const updated = await updateAccount(updates);
            setProfile(updated);
            setRealname(updated.realname ?? "");
            setIdentifier(updated.identifier ?? "");
            setProfileMsg({
                kind: "success",
                text: "Profile updated. Sign out and back in to refresh your session.",
            });
        } catch (err) {
            setProfileMsg({ kind: "error", text: formatError(err, "Could not update profile.") });
        } finally {
            setProfileBusy(false);
        }
    };

    const handlePasswordChange = async (e) => {
        e.preventDefault();
        setPasswordMsg(null);
        if (!currentPassword || !newPassword) {
            setPasswordMsg({ kind: "error", text: "Enter both your current and new password." });
            return;
        }

        setPasswordBusy(true);
        try {
            await updateAccount({ currentPassword, newPassword });
            setCurrentPassword("");
            setNewPassword("");
            setPasswordMsg({ kind: "success", text: "Password changed." });
        } catch (err) {
            setPasswordMsg({ kind: "error", text: formatError(err, "Could not change password.") });
        } finally {
            setPasswordBusy(false);
        }
    };

    const handleDelete = async (e) => {
        e.preventDefault();
        setDeleteMsg(null);
        if (!deletePassword) {
            setDeleteMsg({ kind: "error", text: "Enter your password to confirm deletion." });
            return;
        }

        setDeleteBusy(true);
        try {
            await deleteAccount(deletePassword);
            navigate("/");
        } catch (err) {
            setDeleteMsg({ kind: "error", text: formatError(err, "Could not delete account.") });
            setDeleteBusy(false);
        }
    };

    const message = (msg) =>
        msg ? <p className={`settings-msg settings-msg-${msg.kind}`}>{msg.text}</p> : null;

    return (
        <>
            <div id="settings-title-container">
                <h2 id="settings-title">Account Settings</h2>
            </div>

            <div id="settings-content">
                <section>
                    <h3>Profile</h3>
                    <p><strong>Identifier:</strong> {profile?.identifier ?? user.identifier}</p>
                    {(profile?.realname ?? user.name) && (
                        <p><strong>Name:</strong> {profile?.realname ?? user.name}</p>
                    )}
                    <p><strong>User ID:</strong> {user.sub}</p>
                    {profile?.createDate && (
                        <p><strong>Member since:</strong> {new Date(profile.createDate).toLocaleDateString()}</p>
                    )}
                </section>

                <section>
                    <h3>Sign-in methods</h3>
                    {profile?.providers?.length ? (
                        <ul>
                            {profile.providers.map((p) => (
                                <li key={p}>{p === "google" ? "Google" : "Email & password"}</li>
                            ))}
                        </ul>
                    ) : (
                        <p>Unavailable.</p>
                    )}
                </section>

                <form className="settings-form" onSubmit={handleProfileSave}>
                    <h3>Edit profile</h3>
                    <label>
                        Name
                        <input
                            type="text"
                            value={realname}
                            onChange={(e) => setRealname(e.target.value)}
                            placeholder="Your name"
                        />
                    </label>
                    <label>
                        Username or email
                        <input
                            type="text"
                            value={identifier}
                            onChange={(e) => setIdentifier(e.target.value)}
                            placeholder="username or email"
                        />
                    </label>
                    {message(profileMsg)}
                    <button type="submit" className="settings-btn" disabled={profileBusy}>
                        {profileBusy ? "Saving…" : "Save changes"}
                    </button>
                </form>

                {hasPassword && (
                    <form className="settings-form" onSubmit={handlePasswordChange}>
                        <h3>Change password</h3>
                        <label>
                            Current password
                            <input
                                type="password"
                                value={currentPassword}
                                onChange={(e) => setCurrentPassword(e.target.value)}
                                autoComplete="current-password"
                            />
                        </label>
                        <label>
                            New password
                            <input
                                type="password"
                                value={newPassword}
                                onChange={(e) => setNewPassword(e.target.value)}
                                autoComplete="new-password"
                            />
                        </label>
                        {message(passwordMsg)}
                        <button type="submit" className="settings-btn" disabled={passwordBusy}>
                            {passwordBusy ? "Updating…" : "Update password"}
                        </button>
                    </form>
                )}

                <button id="settings-sign-out" onClick={handleSignOut}>Sign out</button>

                <section className="settings-danger">
                    <h3>Delete account</h3>
                    <p>This permanently removes your account and cannot be undone.</p>
                    {!confirmingDelete ? (
                        <button
                            type="button"
                            className="settings-btn settings-btn-danger"
                            onClick={() => { setConfirmingDelete(true); setDeleteMsg(null); }}
                        >
                            Delete my account
                        </button>
                    ) : (
                        <form className="settings-form" onSubmit={handleDelete}>
                            {hasPassword ? (
                                <label>
                                    Confirm your password
                                    <input
                                        type="password"
                                        value={deletePassword}
                                        onChange={(e) => setDeletePassword(e.target.value)}
                                        autoComplete="current-password"
                                    />
                                </label>
                            ) : (
                                <p className="settings-msg settings-msg-error">
                                    Google-only accounts can't be deleted here yet — password confirmation is required.
                                </p>
                            )}
                            {message(deleteMsg)}
                            <div className="settings-danger-actions">
                                <button
                                    type="button"
                                    className="settings-btn"
                                    onClick={() => {
                                        setConfirmingDelete(false);
                                        setDeletePassword("");
                                        setDeleteMsg(null);
                                    }}
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="settings-btn settings-btn-danger"
                                    disabled={deleteBusy || !hasPassword}
                                >
                                    {deleteBusy ? "Deleting…" : "Permanently delete"}
                                </button>
                            </div>
                        </form>
                    )}
                </section>
            </div>
        </>
    );
}

export default SettingsPage;
