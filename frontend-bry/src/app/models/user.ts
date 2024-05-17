export class User {
    id: number;
    name: string;
    cpf: string;
    face: string;


    constructor(id: number, name: string, cpf: string, face: string){
        this.id = id;
        this.name = name;
        this.cpf = cpf;
        this.face = face;
    }
}
