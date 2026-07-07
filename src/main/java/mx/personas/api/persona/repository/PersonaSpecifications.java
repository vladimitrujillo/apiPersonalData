package mx.personas.api.persona.repository;

import jakarta.persistence.criteria.Subquery;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Predicados combinables por criterio de busqueda (FR-009: AND implicito via
 * Specification.allOf). Reemplaza la antigua query JPQL fija de 3 parametros
 * (PersonaRepository.buscarActivas) - ver research.md #7.
 */
public final class PersonaSpecifications {

    private PersonaSpecifications() {
    }

    public static Specification<Persona> conActivoTrue() {
        return (root, query, cb) -> cb.isTrue(root.get("activo"));
    }

    /**
     * Coincidencia parcial insensible a mayusculas y a acentos/diacriticos sobre
     * nombres+apellidos (FR-001). Ambos lados de la comparacion pasan por
     * unaccent_immutable (V5__unaccent_busqueda_personas.sql, research.md #4).
     */
    public static Specification<Persona> conNombreParcial(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            var nombreCompleto = cb.concat(cb.concat(root.get("nombres"), " "), root.get("apellidos"));
            var columnaSinAcentos = cb.lower(cb.function("unaccent_immutable", String.class, nombreCompleto));
            var patronSinAcentos = cb.lower(
                    cb.function("unaccent_immutable", String.class, cb.literal("%" + nombre + "%")));
            return cb.like(columnaSinAcentos, patronSinAcentos);
        };
    }

    /**
     * Misma semantica EXISTS que usaba buscarActivas: al menos una direccion de la
     * persona cuyo municipio coincide parcialmente (research.md #6).
     */
    public static Specification<Persona> conMunicipio(String municipio) {
        if (municipio == null || municipio.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            var direccion = subquery.from(Direccion.class);
            subquery.select(cb.literal(1L))
                    .where(cb.equal(direccion.get("persona"), root),
                            cb.like(cb.lower(direccion.get("municipio")), "%" + municipio.toLowerCase() + "%"));
            return cb.exists(subquery);
        };
    }

    public static Specification<Persona> conEstadoGeografico(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            var direccion = subquery.from(Direccion.class);
            subquery.select(cb.literal(1L))
                    .where(cb.equal(direccion.get("persona"), root),
                            cb.like(cb.lower(direccion.get("estado")), "%" + estado.toLowerCase() + "%"));
            return cb.exists(subquery);
        };
    }

    public static Specification<Persona> conCurpPrefijo(String prefijo) {
        if (prefijo == null || prefijo.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.like(root.get("curp"), prefijo + "%");
    }

    /** Los limites ya vienen calculados por el servicio (research.md #5). */
    public static Specification<Persona> conFechaNacimientoEntre(LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (desde != null && hasta != null) {
                return cb.between(root.get("fechaNacimiento"), desde, hasta);
            }
            if (desde != null) {
                return cb.greaterThanOrEqualTo(root.get("fechaNacimiento"), desde);
            }
            return cb.lessThanOrEqualTo(root.get("fechaNacimiento"), hasta);
        };
    }

    public static Specification<Persona> conFechaRegistroEntre(LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (desde != null && hasta != null) {
                return cb.between(root.get("createdAt").as(LocalDate.class), desde, hasta);
            }
            if (desde != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt").as(LocalDate.class), desde);
            }
            return cb.lessThanOrEqualTo(root.get("createdAt").as(LocalDate.class), hasta);
        };
    }

    public static Specification<Persona> conSexo(String sexo) {
        if (sexo == null || sexo.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("sexo"), sexo);
    }

    /**
     * "TODAS" = sin predicado; "ELIMINADAS" = solo inactivas; cualquier otro valor
     * (incluidos {@code null}, vacio, "ACTIVAS" o uno no reconocido) cae de forma segura
     * en el comportamiento por defecto (solo activas) - nunca expone eliminadas ante un
     * valor inesperado.
     */
    public static Specification<Persona> conEstadoRegistro(String valorEfectivo) {
        if ("TODAS".equalsIgnoreCase(valorEfectivo)) {
            return null;
        }
        if ("ELIMINADAS".equalsIgnoreCase(valorEfectivo)) {
            return (root, query, cb) -> cb.isFalse(root.get("activo"));
        }
        return conActivoTrue();
    }
}
