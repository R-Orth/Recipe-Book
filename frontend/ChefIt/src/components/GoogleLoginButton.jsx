import { useEffect, useRef } from "react";
import { GOOGLE_CLIENT_ID, loginWithGoogle } from "../utils/auth.js";

const GIS_SRC = "https://accounts.google.com/gsi/client";

function loadGis() {
    return new Promise((resolve, reject) => {
        if (window.google?.accounts?.id) return resolve();
        const existing = document.getElementById("gis-script");
        if (existing) {
            existing.addEventListener("load", () => resolve());
            existing.addEventListener("error", () => reject(new Error("Failed to load Google Identity Services")));
            return;
        }
        const script = document.createElement("script");
        script.id = "gis-script";
        script.src = GIS_SRC;
        script.async = true;
        script.onload = () => resolve();
        script.onerror = () => reject(new Error("Failed to load Google Identity Services"));
        document.head.appendChild(script);
    });
}

function GoogleLoginButton({ onSuccess, onError }) {
    const divRef = useRef(null);
    const onSuccessRef = useRef(onSuccess);
    const onErrorRef = useRef(onError);

    useEffect(() => {
        onSuccessRef.current = onSuccess;
        onErrorRef.current = onError;
    });

    useEffect(() => {
        let cancelled = false;
        loadGis()
            .then(() => {
                if (cancelled || !divRef.current) return;
                window.google.accounts.id.initialize({
                    client_id: GOOGLE_CLIENT_ID,
                    callback: async (response) => {
                        try {
                            const data = await loginWithGoogle(response.credential);
                            onSuccessRef.current?.(data);
                        } catch (err) {
                            onErrorRef.current?.(err);
                        }
                    },
                });
                window.google.accounts.id.renderButton(divRef.current, {
                    theme: "outline",
                    size: "large",
                });
            })
            .catch((err) => onErrorRef.current?.(err));
        return () => { cancelled = true; };
    }, []);

    return <div ref={divRef} />;
}

export default GoogleLoginButton;
