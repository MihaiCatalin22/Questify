import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthContext } from '../contexts/AuthContext';


export default function ProtectedRoute() {
    const { user } = useAuthContext();
    const location = useLocation();
    if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
    }
    return <Outlet />;
}