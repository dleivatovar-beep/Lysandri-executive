package com.lysandri.api.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "INSTRUCTOR")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Instructor {

    @Id
    @Column(name = "id_instructor_dni", length = 20)
    private String idInstructorDni;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private Usuario usuario;

    @Column(name = "especialidad", length = 150)
    private String especialidad;

    @Column(name = "direccion_instructor", length = 200)
    private String direccionInstructor;
}
