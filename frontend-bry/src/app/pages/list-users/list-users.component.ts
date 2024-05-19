import { Component, OnInit } from '@angular/core';
import { UsersService } from '../../services/users.service';
import { User } from '../../models/user';
import { HeaderComponent } from '../../components/header/header.component';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';

@Component({
  selector: 'app-list-users',
  standalone: true,
  templateUrl: './list-users.component.html',
  styleUrl: './list-users.component.css',
  imports: [HeaderComponent, CommonModule, FormsModule],
})
export class ListUsersComponent implements OnInit {
  constructor(private userService: UsersService) {
    this.getAll();
  }

  filtered: boolean = false;

  users: User[] = [];

  cpf: string = '';

  filteredUser = new User(0, '', '', '');

  message: string = '';

  getAll() {
    this.userService.getAll().subscribe((users) => (this.users = users));
  }

  findUserbyCpf(): void {
    if (this.cpf.trim() === '') {
      this.getAll();
      this.filtered = false;
    } else {
      this.userService.findByCpf(this.cpf).subscribe({
        next: (user) => {
          this.filteredUser = user;
          this.filtered = true;
        },
        error: (err) => {
          if (err.status === 404) {
            this.message = 'Usuário não encontrado';
            this.filtered = false;
          } else {
            this.message = 'Um erro ocorreu! Tente novamente.';
          }
          return of(null);
        },
      });
    }
  }

  ngOnInit(): void {}
}
