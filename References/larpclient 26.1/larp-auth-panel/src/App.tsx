import { Routes, Route, Navigate } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import LicensesPage from "./pages/LicensesPage";
import NewLicensePage from "./pages/NewLicensePage";
import PlayerOverridesPage from "./pages/PlayerOverridesPage";
import Shell from "./components/Shell";
import ProtectedRoute from "./components/ProtectedRoute";

export default function App() {
    return (
        <Routes>
            <Route path="/login" element={<LoginPage />} />

            <Route
                path="/"
                element={
                    <ProtectedRoute>
                        <Shell>
                            <DashboardPage />
                        </Shell>
                    </ProtectedRoute>
                }
            />

            <Route
                path="/licenses"
                element={
                    <ProtectedRoute>
                        <Shell>
                            <LicensesPage />
                        </Shell>
                    </ProtectedRoute>
                }
            />

            <Route
                path="/licenses/new"
                element={
                    <ProtectedRoute>
                        <Shell>
                            <NewLicensePage />
                        </Shell>
                    </ProtectedRoute>
                }
            />

            <Route
                path="/player-overrides"
                element={
                    <ProtectedRoute>
                        <Shell>
                            <PlayerOverridesPage />
                        </Shell>
                    </ProtectedRoute>
                }
            />

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}
