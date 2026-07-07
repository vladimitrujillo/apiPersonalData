package mx.personas.api.common.error;

/**
 * Lanzada cuando se intenta disparar una importacion mientras otra ya esta en curso
 * (FR-009/FR-010): el candado de concurrencia no se pudo obtener.
 */
public class CatalogoImportacionEnCursoException extends ApiException {

    public CatalogoImportacionEnCursoException(String mensaje) {
        super(ErrorCode.CATALOGO_IMPORTACION_EN_CURSO, mensaje);
    }
}
