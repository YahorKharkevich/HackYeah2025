package org.bebraradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "trust_level", nullable = false)
    private Double trustLevel;

    protected UserAccount() {
    }

    public UserAccount(Double trustLevel) {
        this.trustLevel = trustLevel;
    }

    public Long getId() {
        return id;
    }

    public Double getTrustLevel() {
        return trustLevel;
    }

    public void setTrustLevel(Double trustLevel) {
        this.trustLevel = trustLevel;
    }
}
