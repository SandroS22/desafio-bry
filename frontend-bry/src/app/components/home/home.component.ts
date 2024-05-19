import { Component } from '@angular/core';
import { HeaderComponent } from '../header/header.component';
import { Router } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  imports: [HeaderComponent],
})
export class HomeComponent {
  constructor(private router: Router) {}

  redirecionaCriarUsuario() {
    this.router.navigate(['/create-user']);
  }

  redirecionaListar() {
    this.router.navigate(['/users']);
  }

  redirecionaExcluir() {
    this.router.navigate(['/delete-user']);
  }

  redirecionaAtualizar() {
    this.router.navigate(['/update-user']);
  }
}
