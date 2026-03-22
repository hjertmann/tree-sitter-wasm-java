import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface User {
    id: number;
    name: string;
    email: string;
}

export interface ApiResponse<T> {
    data: T;
    message: string;
    success: boolean;
}

@Injectable({ providedIn: 'root' })
export class UserService {
    private readonly baseUrl = '/api/users';

    constructor(private http: HttpClient) {}

    getAll(): Observable<User[]> {
        return this.http.get<User[]>(this.baseUrl);
    }

    getById(id: number): Observable<User> {
        return this.http.get<User>(`${this.baseUrl}/${id}`);
    }

    create(user: Partial<User>): Observable<User> {
        return this.http.post<User>(this.baseUrl, user);
    }
}

export function formatUser(user: User): string {
    return `${user.name} <${user.email}>`;
}
