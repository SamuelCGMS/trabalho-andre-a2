package br.edu.uva.otimizacao.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "categoria")
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome")
    private String nome;

    protected Categoria() { }

    public Long getId() { return id; }
    public String getNome() { return nome; }
}
