package com.example.cortebe.domain.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "problems")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String description;

    @Lob
    @Column(nullable = false)
    private String inputFormat;

    @Lob
    @Column(nullable = false)
    private String outputFormat;

    @Lob
    @Column(nullable = false)
    private String exampleInput;

    @Lob
    @Column(nullable = false)
    private String exampleOutput;

    public Problem(String title, String description, String inputFormat, String outputFormat,
                   String exampleInput, String exampleOutput) {
        this.title = title;
        this.description = description;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
        this.exampleInput = exampleInput;
        this.exampleOutput = exampleOutput;
    }

    public void update(String title, String description, String inputFormat, String outputFormat,
                       String exampleInput, String exampleOutput) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (inputFormat != null) {
            this.inputFormat = inputFormat;
        }
        if (outputFormat != null) {
            this.outputFormat = outputFormat;
        }
        if (exampleInput != null) {
            this.exampleInput = exampleInput;
        }
        if (exampleOutput != null) {
            this.exampleOutput = exampleOutput;
        }
    }
}
