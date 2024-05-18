import { Injectable } from '@angular/core';
import { User } from '../models/user';
import { HttpClientModule } from '@angular/common/http';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class UsersService {
  private apiUrl: string = 'http://localhost:8080';
  constructor(private httpClient: HttpClient) {}


  getAll(): Observable<User[]>{
    return this.httpClient.get<User[]>(`${ this.apiUrl }/users`);
  }

  deleterUser(id: number): Observable<User>{
    return this.httpClient.delete<User>(`${ this.apiUrl }/users/delete-user/${id}`);
  }

}
