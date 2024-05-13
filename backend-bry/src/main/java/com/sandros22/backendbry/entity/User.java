package com.sandros22.backendbry.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

@Entity
public class User {

    @Id
    private String cpf;
    private String name;
    @Lob
    private byte[] face;

    public User() {
    }

    public User(String cpf, String name, byte[] face) {
        this.cpf = cpf;
        this.name = name;
        this.face = face;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getFace() {
        return face;
    }

    public void setFace(byte[] face) {
        this.face = face;
    }
}
