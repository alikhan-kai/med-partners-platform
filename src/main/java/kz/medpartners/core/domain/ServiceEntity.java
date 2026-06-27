package kz.medpartners.core.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceEntity {

    @Id
    @Column(name = "service_id")
    private UUID service_id;

    @Column(name = "service_name", nullable = false, columnDefinition = "TEXT")
    private String service_name;

    // Храним синонимы как обычный TEXT через запятую.
    @Column(name = "synonyms", columnDefinition = "TEXT")
    private String synonyms;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "icd_code", length = 50)
    private String icd_code;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean is_active = true;
}