import { Link, useLocation, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Home, KeyRound, PlusCircle, Users } from "lucide-react";
import type { ReactNode } from "react";
import { logout } from "../lib/auth";

function getSectionTitle(pathname: string) {
    if (pathname === "/") return "Dashboard";
    if (pathname === "/licenses") return "Licenses";
    if (pathname === "/licenses/new") return "New License";
    if (pathname === "/player-overrides") return "Player Overrides";
    if (pathname === "/login") return "Login";
    return "LarpAuth";
}

export default function Shell({ children }: { children: ReactNode }) {
    const location = useLocation();
    const navigate = useNavigate();

    async function handleLogout() {
        await logout();
        navigate("/login", { replace: true });
    }

    return (
        <div className="shell">
            <motion.aside
                className="glass sidebar"
                initial={{ opacity: 0, x: -12 }}
                animate={{ opacity: 1, x: 0 }}
            >
                <div className="brand">
                    <span className="aqua">Larp</span>
                    <span className="pink">Auth</span>
                </div>

                <div className="muted">Admin portal</div>

                <nav className="nav">
                    <Link to="/">
                        <Home size={16} style={{ marginRight: 8, verticalAlign: "middle" }} />
                        Dashboard
                    </Link>
                    <Link to="/licenses">
                        <KeyRound size={16} style={{ marginRight: 8, verticalAlign: "middle" }} />
                        Licenses
                    </Link>
                    <Link to="/licenses/new">
                        <PlusCircle size={16} style={{ marginRight: 8, verticalAlign: "middle" }} />
                        New License
                    </Link>
                    <Link to="/player-overrides">
                        <Users size={16} style={{ marginRight: 8, verticalAlign: "middle" }} />
                        Player Overrides
                    </Link>
                </nav>

                <div className="muted" style={{ marginTop: "auto" }}>
                    {location.pathname}
                </div>
            </motion.aside>

            <main className="content">
                <motion.div
                    className="glass topbar"
                    initial={{ opacity: 0, y: -10 }}
                    animate={{ opacity: 1, y: 0 }}
                >
                    <div style={{ fontSize: 20, fontWeight: 700 }}>
                        {getSectionTitle(location.pathname)}
                    </div>
                    <button className="button" onClick={handleLogout}>Sign out</button>
                </motion.div>

                {children}
            </main>
        </div>
    );
}
