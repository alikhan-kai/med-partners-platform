package kz.medpartners.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
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
    private UUID service_id; // [cite: 84]

    @Column(name = "service_name", nullable = false, columnDefinition = "TEXT")
    private String service_name; // [cite: 84]

    // Храним синонимы как JSONB массив строк для гибкого нечеткого поиска [cite: 93]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "synonyms", columnDefinition = "jsonb")
    private List<String> synonyms; // [cite: 84]

    @Column(name = "category", length = 100)
    private String category; // [cite: 84]

    @Column(name = "icd_code", length = 50)
    private String icd_code; // [cite: 93]

    @Column(name = "is_active")
    @Builder.Default
    private Boolean is_active = true; // [cite: 93]
}