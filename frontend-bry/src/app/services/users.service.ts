import { Injectable } from '@angular/core';
import { User } from '../models/user';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class UsersService {
  private apiUrl: string = 'http://localhost:8080';
  constructor(private httpClient: HttpClient) {}

  createUser(user: User): Observable<User> {
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.httpClient.post<User>(
      `${this.apiUrl}/users/create-user`,
      user,
      { headers }
    );
  }

  getAll(): Observable<User[]> {
    return this.httpClient.get<User[]>(`${this.apiUrl}/users`);
  }

  deleterUser(id: number): Observable<User> {
    return this.httpClient.delete<User>(
      `${this.apiUrl}/users/delete-user/${id}`
    );
  }

  findById(id: number): Observable<User> {
    return this.httpClient.get<User>(`${this.apiUrl}/user/${id}`);
  }

  updateUser(user: User): Observable<User> {
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.httpClient.patch<User>(
      `${this.apiUrl}/users/update-user`,
      user,
      { headers }
    );
  }
}
