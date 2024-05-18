import { Component } from '@angular/core';
import { HeaderComponent } from "../../components/header/header.component";
import { FormsModule } from '@angular/forms';
import { UsersService } from '../../services/users.service';
import { catchError, tap } from 'rxjs/operators';
import { of } from 'rxjs';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-delete-user',
    standalone: true,
    templateUrl: './delete-user.component.html',
    styleUrl: './delete-user.component.css',
    imports: [HeaderComponent, FormsModule, CommonModule]
})
export class DeleteUserComponent {

    constructor(private userService: UsersService){}

    id: number = 0;

    message: string = ''

    deleteUser(){
        this.userService.deleterUser(this.id).pipe(
            tap(response => {
              this.message = 'Usuário excluído com sucesso.';
            }),
            catchError(error => {
              if (error.status === 404) {
                this.message = 'Usuário não encontrado.';
              } else {
                this.message = 'Um erro ocorreu! Tente novamente.';
              }
              return of(null); // Return a safe value to keep the observable chain alive
            })
          ).subscribe();
        }
}
