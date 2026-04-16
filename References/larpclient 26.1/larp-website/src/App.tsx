import { Navigate, Route, Routes } from "react-router-dom";
import ProtectedRoute from "./components/ProtectedRoute";
import HomePage from "./pages/HomePage";
import WikiPage from "./pages/WikiPage";
import LoginPage from "./pages/LoginPage";
import PortalPage from "./pages/PortalPage";
import AdminPage from "./pages/AdminPage";

export default function App() {
    return (
        <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/wiki" element={<WikiPage audience="public" />} />
            <Route path="/wiki/:slug" element={<WikiPage audience="public" />} />
            <Route path="/login" element={<LoginPage />} />
            <Route
                path="/addons"
                element={
                    <ProtectedRoute allow={["admin", "license"]} requireAddon>
                        <PortalPage />
                    </ProtectedRoute>
                }
            />
            <Route
                path="/addons/wiki"
                element={
                    <ProtectedRoute allow={["admin", "license"]} requireAddon>
                        <WikiPage audience="addon" />
                    </ProtectedRoute>
                }
            />
            <Route
                path="/addons/wiki/:slug"
                element={
                    <ProtectedRoute allow={["admin", "license"]} requireAddon>
                        <WikiPage audience="addon" />
                    </ProtectedRoute>
                }
            />
            <Route
                path="/portal"
                element={
                    <ProtectedRoute allow={["admin", "license"]} requireAddon>
                        <PortalPage />
                    </ProtectedRoute>
                }
            />
            <Route
                path="/portal/wiki"
                element={
                    <ProtectedRoute allow={["admin", "license"]} requireAddon>
                        <WikiPage audience="addon" />
                    </ProtectedRoute>
                }
            />
            <Route
                path="/portal/wiki/:slug"
                element={
                    <ProtectedRoute allow={["admin", "license"]} requireAddon>
                        <WikiPage audience="addon" />
                    </ProtectedRoute>
                }
            />
            <Route
                path="/admin"
                element={
                    <ProtectedRoute allow={["admin"]}>
                        <AdminPage />
                    </ProtectedRoute>
                }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}
