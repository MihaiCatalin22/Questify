export type Role = 'USER' | 'REVIEWER' |'ADMIN';

export interface UserDTO {
    id: string;
    username: string;
    email: string;
    displayName?: string;
    roles: Role[];
    createdAt: string; // ISO
    updatedAt: string; // ISO
}

export interface CreateUserInput {
    username: string;
    email: string;
    password: string;
    displayName?: string;
}

export interface UpdateUserInput {
    email?: string;
    password?: string;
    displayName?: string;
}

export interface LoginRequest {
    usernameOrEmail: string;
    password: string;
}

export interface LoginResponse {
    user: UserDTO;
    jwt: string;
    expiresAt?: string; // ISO
}