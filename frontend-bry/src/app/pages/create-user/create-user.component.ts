import { Component } from '@angular/core';
import { HeaderComponent } from "../../components/header/header.component";
import { UsersService } from '../../services/users.service';
import { User } from '../../models/user';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, tap } from 'rxjs/operators';
import { of } from 'rxjs';

@Component({
    selector: 'app-create-user',
    standalone: true,
    templateUrl: './create-user.component.html',
    styleUrl: './create-user.component.css',
    imports: [HeaderComponent, CommonModule, FormsModule]
})
export class CreateUserComponent {

    user: User = new User(0, '', '', '');

    message: string = '';

    constructor(private userService: UsersService){}

    createUser(){
        if(this.user.name.trim() !== '' && this.user.cpf.trim() !== ''){
        this.userService.createUser(this.user).pipe(
            tap(response => {
              this.message = 'Usuário criado com sucesso.';
              this.user.cpf = '';
              this.user.name = ''
            }),
            catchError(error => {
              if (error.status === 500) {
                this.message = 'Um erro ocorreu! Tente novamente.';
              }
              return of(null);
            })
          ).subscribe();
        } else{
            this.message = 'Preecha ambos os campos'
        }
    }
}
