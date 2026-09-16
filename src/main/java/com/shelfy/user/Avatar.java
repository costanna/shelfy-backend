package com.shelfy.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Guardada en su propia tabla (no como columna en {@link User}) para que
 * cargar un usuario en cualquier otro sitio (login, búsqueda, perfil...)
 * nunca arrastre los bytes de la imagen — solo se lee cuando se pide
 * explícitamente {@code GET /api/users/{id}/avatar}.
 *
 * {@code @JdbcTypeCode(SqlTypes.VARBINARY)} en vez de {@code @Lob}: con
 * Hibernate 6 + PostgreSQL, {@code @Lob} sobre un {@code byte[]} mapea al
 * tipo "large object" ({@code oid}), pensado para blobs enormes con su
 * propia gestión de ciclo de vida; para una imagen ya redimensionada a
 * unos pocos KB, {@code bytea} vía VARBINARY es el tipo correcto y evita
 * esa complejidad. {@code length} explícito porque, sin él, Hibernate usa
 * su longitud de columna por defecto (255) para generar el DDL — PostgreSQL
 * ignora ese límite para {@code bytea}, pero H2 (perfil local) sí lo
 * aplica de verdad y truncaría cualquier imagen real. Tiene que quedarse
 * por debajo de {@code Dialect.DEFAULT_LOB_LENGTH} (1_000_000): a partir de
 * ahí Hibernate escala el tipo de columna a BLOB en vez de VARBINARY, y el
 * modo PostgreSQL de H2 no reconoce ese "BLOB" (rompe el `create table` en
 * local, aunque en Postgres real no habría pasado nada). 500 KB da de
 * sobra para un JPEG 320x320 ya comprimido.
 */
@Entity
@Table(name = "user_avatars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avatar {

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "image_data", nullable = false, length = 500_000)
    private byte[] imageData;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
