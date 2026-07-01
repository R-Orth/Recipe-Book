import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import logo from "../assets/logo.png";
import { getCurrentUser } from "../utils/auth.js";
import "./Header.css";

function Header() {
    const [user, setUser] = useState(() => getCurrentUser());

    useEffect(() => {
        const refresh = () => setUser(getCurrentUser());
        window.addEventListener("chefit-auth", refresh);
        window.addEventListener("storage", refresh);
        return () => {
            window.removeEventListener("chefit-auth", refresh);
            window.removeEventListener("storage", refresh);
        };
    }, []);

    return (
        <header>
            <Link to="/">
                <img id="logo" src={logo} alt="Image of a chef hat with the words 'Chef It Recipe Book'" />
            </Link>
            <nav>
                <Link to="/"><div className="nav-div"><strong>Home</strong></div></Link>
                <Link to="/add"><div className="nav-div"><strong>New Recipe</strong></div></Link>
                <Link to="/search"><div className="nav-div"><strong>Search</strong></div></Link>
            </nav>
            <Link id="auth-nav" to={user ? "/settings" : "/login"}>
                <div className="nav-div"><strong>{user ? user.identifier : "Sign In"}</strong></div>
            </Link>
        </header>
    );
}

export default Header;
