package com.chessinsights.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "openings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"eco_code", "variation"})
})
public class Opening {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "eco_code", nullable = false, length = 5)
    private String ecoCode;

    @Column(nullable = false)
    private String name;

    @Column
    private String variation;

    @Column(columnDefinition = "TEXT")
    private String pgn;

    public Opening() {}

    public Opening(String ecoCode, String name, String variation, String pgn) {
        this.ecoCode = ecoCode;
        this.name = name;
        this.variation = variation;
        this.pgn = pgn;
    }

    // Getters and setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEcoCode() { return ecoCode; }
    public void setEcoCode(String ecoCode) { this.ecoCode = ecoCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVariation() { return variation; }
    public void setVariation(String variation) { this.variation = variation; }

    public String getPgn() { return pgn; }
    public void setPgn(String pgn) { this.pgn = pgn; }
}
