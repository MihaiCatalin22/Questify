import { http } from './https';
import type { CreateUserInput, UpdateUserInput, UserDTO, LoginRequest, LoginResponse } from '../types/user';

export const UsersApi = {
    async list(): Promise<UserDTO[]> {
        const { data } = await http.get<UserDTO[]>('/users');
        return data;
    },
    async get(id: string): Promise<UserDTO> {
        const { data } = await http.get<UserDTO>(`/users/${id}`);
        return data;
    },
    async create(input: CreateUserInput): Promise<UserDTO> {
        const { data } = await http.post<UserDTO>('/users', input);
        return data;
    },
    async update(id: string, input: UpdateUserInput): Promise<UserDTO> {
    const { data } = await http.put<UserDTO>(`/users/${id}`, input);
    return data;
    },
    async remove(id: string): Promise<void> {
        await http.delete(`/users/${id}`);
    },
};

export const AuthApi = {
    async login(req: LoginRequest): Promise<LoginResponse> {
        const { data } = await http.post<LoginResponse>('/login', req);
        return data;
    },
};