// Mirrors RoleEntity.RoleEnum. The API sends the plain names; the ROLE_ prefix only exists inside Spring Security.
export type Role = 'UPLOADER' | 'ADMIN_DOCUMENTS';

export interface AuthRequest {
    username: string;
    password: string;
}

export interface AuthResponse {
    username: string;
    roles: Role[];
}

export interface UserRequest {
    username: string;
    password: string;
    role: Role;
}

export interface UserResponse {
    id: number;
    username: string;
    role: Role;
}
