export interface AuthRequest {
    username: string;
    password: string;
}

export interface AuthResponse {
    username: string;
    roles: string[];
}
