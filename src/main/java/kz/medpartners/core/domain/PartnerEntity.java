package kz.medpartners.core.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "partners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerEntity {

    @Id
    @Column(name = "partner_id")
    private UUID partner_id; // [cite: 87]

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name; // [cite: 87]

    @Column(name = "city", length = 100)
    private String city; // [cite: 87]

    @Column(name = "address", columnDefinition = "TEXT")
    private String address; // [cite: 87]

    @Column(name = "bin", length = 12)
    private String bin; // [cite: 87]

    @Column(name = "contact_email", length = 100)
    private String contact_email; // [cite: 87]

    @Column(name = "contact_phone", length = 50)
    private String contact_phone; // [cite: 87]

    @Column(name = "is_active")
    @Builder.Default
    private Boolean is_active = true; // [cite: 87]

    @Column(name = "created_at", updatable = false)
    private LocalDateTime created_at; // [cite: 87]

    @Column(name = "updated_at")
    private LocalDateTime updated_at; // [cite: 87]

    @PrePersist
    protected void onCreate() {
        created_at = LocalDateTime.now(); // [cite: 87]
        updated_at = LocalDateTime.now(); // [cite: 87]
    }

    @PreUpdate
    protected void onUpdate() {
        updated_at = LocalDateTime.now(); // [cite: 87]
    }
}