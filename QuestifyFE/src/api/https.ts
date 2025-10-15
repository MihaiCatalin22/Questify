import axios from "axios";

const API_BASE_URL = '/api';

export const http = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: false,
    timeout: 10000,
});

http.interceptors.request.use((config) => {
    try {
        const raw = sessionStorage.getItem('user');
        if (raw) {
            const { jwt } = JSON.parse(raw);
            if (jwt) {
                config.headers = config.headers ?? {};
                config.headers['Authorization'] = `Bearer ${jwt}`;
            }
        }
    } catch (_) {}
    return config;
});

export function setAuthHeader(jwt?: string) {
    if (jwt) {
        http.defaults.headers.common['Authorization'] = `Bearer ${jwt}`;
    } else {
        delete http.defaults.headers.common['Authorization'];
    }
}