import React, { createContext, useContext, useMemo, useState } from 'react';
import type { LoginRequest, LoginResponse, UserDTO } from '../types/user';
import { AuthApi } from '../api/users';
import { setAuthHeader } from '../api/https';


interface AuthState {
user: UserDTO | null;
jwt: string | null;
}


interface AuthContextValue extends AuthState {
login: (req: LoginRequest) => Promise<void>;
logout: () => void;
}


const AuthContext = createContext<AuthContextValue | undefined>(undefined);


export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
const [state, setState] = useState<AuthState>(() => {
try {
const raw = sessionStorage.getItem('user');
if (raw) {
const parsed: LoginResponse = JSON.parse(raw);
setAuthHeader(parsed.jwt);
return { user: parsed.user, jwt: parsed.jwt };
}
} catch {}
return { user: null, jwt: null };
});


const login = async (req: LoginRequest) => {
const res = await AuthApi.login(req);
sessionStorage.setItem('user', JSON.stringify(res));
setAuthHeader(res.jwt);
setState({ user: res.user, jwt: res.jwt });
};


const logout = () => {
sessionStorage.removeItem('user');
setAuthHeader(undefined);
setState({ user: null, jwt: null });
};


const value = useMemo(() => ({ ...state, login, logout }), [state]);


return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};


export const useAuthContext = () => {
const ctx = useContext(AuthContext);
if (!ctx) throw new Error('useAuthContext must be used within AuthProvider');
return ctx;
};