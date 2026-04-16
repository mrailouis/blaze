import { Link, useLocation, useNavigate } from "react-router-dom";
import { BookOpen, ExternalLink, LogIn, Shield, Sparkles } from "lucide-react";
import type { ReactNode } from "react";
import { logout } from "../lib/api";
import type { SessionInfo } from "../types";

export default function SiteFrame(
    {
        session,
        settings,
        children,
        wide = false
    }: {
        session: SessionInfo;
        settings: Record<string, string>;
        children: ReactNode;
        wide?: boolean;
    }
) {
    const location = useLocation();
    const navigate = useNavigate();

    async function handleLogout() {
        await logout();
        navigate("/", { replace: true });
    }

    return (
        <div className={`site-shell ${wide ? "site-shell-wide" : ""}`.trim()}>
            <header className="site-header">
                <Link className="site-brand" to="/">
                    <img className="brand-mark" src="/larpclient-icon.png" alt="LarpClient" />
                    <div className="brand-copy">
                        <span className="brand-kicker">LarpClient</span>
                        <strong className="brand-title">Legit client and private addon docs</strong>
                    </div>
                </Link>

                <nav className="site-nav">
                    <Link className={location.pathname === "/" ? "active" : ""} to="/">
                        Overview
                    </Link>
                    <Link className={location.pathname.startsWith("/wiki") ? "active" : ""} to="/wiki">
                        <BookOpen size={14} />
                        Wiki
                    </Link>
                    <Link className={location.pathname.startsWith("/addons") ? "active" : ""} to="/addons">
                        <Sparkles size={14} />
                        Addons
                    </Link>
                    {session?.role === "admin" ? (
                        <Link className={location.pathname.startsWith("/admin") ? "active" : ""} to="/admin">
                            <Shield size={14} />
                            Admin
                        </Link>
                    ) : null}
                </nav>

                <div className="site-actions">
                    <a className="site-link-chip" href={settings.discord_url || "https://discord.gg/replace-me"} target="_blank" rel="noreferrer">
                        Discord
                        <ExternalLink size={13} />
                    </a>
                    {session ? (
                        <>
                            <div className="session-chip">
                                <span className="session-role">{session.role === "admin" ? "root" : "licensed"}</span>
                                <strong>{session.username || session.discordName || session.productTier || "active session"}</strong>
                            </div>
                            <button className="button" onClick={handleLogout}>Logout</button>
                        </>
                    ) : (
                        <Link className="button" to="/login?mode=addon">
                            <LogIn size={14} />
                            Login
                        </Link>
                    )}
                </div>
            </header>

            <main className="page-content">{children}</main>

            <footer className="site-footer">
                <span>Dark client docs, live addon access, and content tools backed by the shared D1 database.</span>
                <div className="footer-links">
                    <a href={settings.source_code_url || "https://github.com/mrailouis/larpclient-public"} target="_blank" rel="noreferrer">
                        Public source
                    </a>
                    <Link to="/wiki">Legit wiki</Link>
                    <Link to="/addons">Addon area</Link>
                </div>
            </footer>
        </div>
    );
}
