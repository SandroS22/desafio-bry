import { Component } from '@angular/core';
import { HeaderComponent } from '../../components/header/header.component';
import { UsersService } from '../../services/users.service';
import { User } from '../../models/user';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, tap } from 'rxjs/operators';
import { of } from 'rxjs';

@Component({
  selector: 'app-update-user',
  standalone: true,
  templateUrl: './update-user.component.html',
  styleUrl: './update-user.component.css',
  imports: [HeaderComponent, CommonModule, FormsModule],
})
export class UpdateUserComponent {
  user: User = new User(0, '', '', '');

  id: number = 0;

  message: string = '';

  visible: boolean = false;

  constructor(private userService: UsersService) {}

  buscarUsuario() {
    this.userService.findById(this.id).subscribe({
      next: (user) => {
        this.user = user;
        this.message = '';
        this.visible = true;
      },
      error: (err) => {
        this.user = new User(0, '', '', '');
        if (err.status === 404) {
          this.message = 'Usuário não encontrado';
          this.visible = false;
        } else {
          this.message = 'Um erro ocorreu! Tente novamente.';
        }
        return of(null);
      },
    });
  }

  updateUser() {
    this.userService
      .updateUser(this.user)
      .pipe(
        tap((response) => {
          this.message = 'Usuário atualizado com sucesso.';
        }),
        catchError((error) => {
          this.message = 'Um erro ocorreu! Tente novamente.';
          return of(null);
        })
      )
      .subscribe();
  }
}
