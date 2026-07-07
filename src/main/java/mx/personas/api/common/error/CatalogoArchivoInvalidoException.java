package mx.personas.api.common.error;

/**
 * Lanzada cuando la estructura general de un archivo de catalogo SEPOMEX (encabezado o
 * numero de columnas) es invalida - antes de aplicar cualquier upsert, para que el
 * catalogo quede intacto por construccion (FR-011).
 */
public class CatalogoArchivoInvalidoException extends ApiException {

    public CatalogoArchivoInvalidoException(String mensaje) {
        super(ErrorCode.CATALOGO_ARCHIVO_INVALIDO, mensaje);
    }
}
